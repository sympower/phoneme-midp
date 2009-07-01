/*
 *   
 *
 * Copyright  1990-2006 Sun Microsystems, Inc. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License version
 * 2 only, as published by the Free Software Foundation. 
 * 
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License version 2 for more details (a copy is
 * included at /legal/license.txt). 
 * 
 * You should have received a copy of the GNU General Public License
 * version 2 along with this work; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA 
 * 
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, CA 95054 or visit www.sun.com if you need additional
 * information or have any questions. 
 */
#include <kni.h>
#include <string.h>

#include <midpMalloc.h>
#include <midp_logging.h>

#include <imgdcd_image_util.h>
#include "imgdcd_intern_image_decode.h"

#if ENABLE_JPEG
#include <jpegdecoder.h>
#endif

#define CT_PALETTE  0x01
#define CT_COLOR    0x02
#define CT_ALPHA    0x04


#if ENABLE_DYNAMIC_PIXEL_FORMAT

#define IMGDCD_RGBA2PIXEL_32(r, g, b, a) ( (((a) << 24) & 0xFF000000) | \
                                       (((b) << 16) & 0xFF0000) | \
                                       (((g) << 8) & 0xFF00) | \
                                        ((r) & 0xFF) )
#define IMGDCD_MIDPTOOPAQUEPIXEL_32(x) ( ((x) & 0xFF00FF00) | \
                                     (((x) << 16) & 0xFF0000) | \
                                     (((x) >> 16) & 0xFF) | \
                                     0xFF000000)
#define IMGDCD_MIDPTOPIXEL_32(x) ( ((x) & 0xFF00FF00) | (((x) << 16) & 0xFF0000) | (((x) >> 16) & 0xFF))
#define IMGDCD_RGB2PIXEL_16(r, g, b) ((imgdcd_pixel16_type)( b + (g << 5) + (r << 11) ))


int img_enable_32bit_mode = 0;

extern lfj_select_rgb565_image_rom();
extern lfj_select_rgb888_image_rom();

void set_img_enable_32bit_mode(enable) {
    if (img_enable_32bit_mode != enable) {
        img_enable_32bit_mode = enable;
        if (img_enable_32bit_mode) {
            lfj_select_rgb888_image_rom();
        } else {
            lfj_select_rgb565_image_rom();
        }
    }
}
#elif ENABLE_RGBA8888_PIXEL_FORMAT

/** Convert separate r, g, b and alpha components to 32-bit pixel. */
#define IMGDCD_RGBA2PIXEL(r, g, b, a) ( (((r) << 24) & 0xFF000000) | \
                                        (((g) << 16) & 0xFF0000) | \
                                        (((b) << 8) & 0xFF00) | \
                                         ((a) & 0xFF) )
#define IMGDCD_MIDPTOOPAQUEPIXEL(x) ( ((x) << 8) | 0xFF )
#define IMGDCD_MIDPTOPIXEL(x) ( (((x) << 8) & 0xFFFFFF00) | (((x) >> 24) & 0xFF) )

#elif ENABLE_ABGR8888_PIXEL_FORMAT

#define IMGDCD_RGBA2PIXEL(r, g, b, a) ( (((a) << 24) & 0xFF000000) | \
                                       (((b) << 16) & 0xFF0000) | \
                                       (((g) << 8) & 0xFF00) | \
                                        ((r) & 0xFF) )
#define IMGDCD_MIDPTOOPAQUEPIXEL(x) ( ((x) & 0xFF00FF00) | \
                                     (((x) << 16) & 0xFF0000) | \
                                     (((x) >> 16) & 0xFF) | \
                                     0xFF000000)
#define IMGDCD_MIDPTOPIXEL(x) ( ((x) & 0xFF00FF00) | (((x) << 16) & 0xFF0000) | (((x) >> 16) & 0xFF))

#else
/** Convert pre-masked triplet r, g, b to 16 bit pixel. */
#define IMGDCD_RGB2PIXEL(r, g, b) ( b + (g << 5) + (r << 11) )
#endif



typedef struct _imgDst {
  imageDstData   super;

  // gxj_screen_buffer*           vdc;
  int width;
  int height;
  imgdcd_pixel_type *pixelData;
  imgdcd_alpha_type *alphaData;

  jboolean       hasAlpha;
  jboolean       hasColorMap;
  jboolean       hasTransMap;
  long           cmap[256];
  /*
   * IMPL NOTE: investigate if 'unsigned char' type could be used for storing
   * transparency map instead of 'long' type.
   */
  long           tmap[256]; 
} _imageDstData, *_imageDstPtr;


void initImageDst(_imageDstPtr dst);

/**
 * Image Decoder call back to set the color map of the decoded image
 *
 *  @param self pointer to a structure to hold decoded image structures.
 *  @param map pointer to the color map of the decoded image.
 *  @param length length of the color map.
 */
static void
setImageColormap(imageDstPtr self, long *map, int length) {
  _imageDstPtr p = (_imageDstPtr)self;

  REPORT_CALL_TRACE(LC_LOWUI,
                    "LF:STUB:setImageColormap()\n");

  p->hasColorMap = KNI_TRUE;
  memcpy(p->cmap, map, length * sizeof(long));
}

/**
 * Image Decoder call back to set the transparency map of the decoded image
 *
 *  @param self pointer to a structure to hold decoded image structures.
 *  @param map pointer to the transparency map of the decoded image.
 *  @param length length of the transparency map.
 *  @param palLength length of the color map.
 */
static void
setImageTransparencyMap(imageDstPtr self, unsigned char *map,
        int length, int palLength) {
    /*
     * This function is used for color type 3 (indexed-color) only,
     * in this case, the tRNS chunk contains a series of _one-byte_ alpha
     * values, corresponding to entries in the PLTE chunk.
     * For all other color types, transparency map is absent or has a fixed
     * length.
     */

    _imageDstPtr p = (_imageDstPtr)self;
    int i;

    REPORT_CALL_TRACE(LC_LOWUI, "LF:STUB:setImageTransparencyMap()\n");

    /*
     * The tRNS chunk shall not contain more alpha values than there are
     * palette entries, but a tRNS chunk may contain fewer values than there are
     * palette entries. In this case, the alpha value for all remaining palette
     * entries is assumed to be 255.
     */
    for (i = 0; i < length; i++) {
        p->tmap[i] = map[i];
    }
    if (length >= 0) { 
        for (i = length; i < palLength; i++) {
            p->tmap[i] = 0xFF;
        }
    }

    p->hasTransMap = KNI_TRUE;
}

/**
 * Image Decoder call back to set the width and height of the decoded image
 *
 *  @param self pointer to a structure to hold decoded image structures.
 *  @param width the width of the decoded image
 *  @param height the height of the decoded image
 */
static void
setImageSize(imageDstPtr self, int width, int height) {
  _imageDstPtr p = (_imageDstPtr)self;

    REPORT_CALL_TRACE(LC_LOWUI,
                      "LF:STUB:setImageSize()\n");

    if (p->pixelData == NULL) {
        p->width = width;
        p->height = height;

        p->pixelData = (imgdcd_pixel_type *)
	    midpMalloc(width*height*sizeof(imgdcd_pixel_type));
    } else {
        if (p->width != width || p->height != height) {
            /* JAVA_TRACE("IMAGE DIMENSION IS INCORRECT!!\n"); */
        }
    }

    if (p->alphaData == NULL) {
        p->alphaData = (imgdcd_alpha_type *)
            midpMalloc(width*height*sizeof(imgdcd_alpha_type));
    }
}

/**
 * Image Decoder call back to set the pixels of the decoded image
 *
 *  @param self pointer to a structure to hold decoded image structures.
 *  @param y the y coordinate of the line where the pixel belong
 *  @param pixels the pixel data for the line
 *  @param pixelType the type of the pixel data
 */
static void
sendPixelsColor(imageDstPtr self, int y, uchar *pixels, int pixelType) {
  _imageDstPtr p = (_imageDstPtr)self;
  int x;
  int r, g, b, alpha, color, hasAlphaChannel;

  REPORT_CALL_TRACE(LC_LOWUI,
                    "LF:STUB:sendPixelsColor()\n");

  if (p->pixelData == NULL || p->alphaData == NULL) {
    return;
  }

  if ((pixelType == CT_COLOR) ||              /* color triplet */
      (pixelType == (CT_COLOR | CT_ALPHA))) { /* color triplet with alpha */

    hasAlphaChannel = (( pixelType & CT_ALPHA) || p->hasTransMap) ? 1 : 0;
    for (x = 0; x < p->width; ++x) {
      alpha = 0xff;

#if ENABLE_DYNAMIC_PIXEL_FORMAT
      if (img_enable_32bit_mode) {
          r = pixels[0];
          g = pixels[1];
          b = pixels[2];

          if (hasAlphaChannel) {
            alpha = pixels[3];
            pixels++;
          }
          pixels += 3;

          p->pixelData[y*p->width + x] = IMGDCD_RGBA2PIXEL_32(r, g, b, alpha);
      } else {
          r = pixels[0] >> 3;
          g = pixels[1] >> 2;
          b = pixels[2] >> 3;

          if (hasAlphaChannel) {
            alpha = pixels[3];
            pixels++;
          }
          pixels += 3;

          ((imgdcd_pixel16_type*)p->pixelData)[y*p->width + x] = IMGDCD_RGB2PIXEL_16(r, g, b);
      }
#elif ENABLE_32BITS_PIXEL_FORMAT
      r = pixels[0];
      g = pixels[1];
      b = pixels[2];

      if (hasAlphaChannel) {
        alpha = pixels[3];
        pixels++;
      }
      pixels += 3;

      p->pixelData[y*p->width + x] = IMGDCD_RGBA2PIXEL(r, g, b, alpha);
#else
      r = pixels[0] >> 3;
      g = pixels[1] >> 2;
      b = pixels[2] >> 3;

      if (hasAlphaChannel) {
        alpha = pixels[3];
        pixels++;
      }
      pixels += 3;

      p->pixelData[y*p->width + x] = IMGDCD_RGB2PIXEL(r, g, b);
#endif
      p->alphaData[y*p->width + x] = alpha;
      if (alpha != 0xff) {
          p->hasAlpha = KNI_TRUE;
      }
    }
  } else { /* indexed color */
    for (x = 0; x < p->width; ++x) {
      int cmapIndex = *pixels++;

      color = p->cmap[cmapIndex];
      alpha = 0xff;

      if ((pixelType & (CT_ALPHA | CT_COLOR)) == CT_ALPHA) {
        alpha = *pixels++;
      } else if (p->hasTransMap) {
        if ((pixelType & CT_COLOR) == 0) { /* grayscale */
          alpha = *pixels++;
        } else { /* indexed color */
          alpha = p->tmap[cmapIndex];
        }
      }


#if ENABLE_DYNAMIC_PIXEL_FORMAT
      if (img_enable_32bit_mode) {
          p->pixelData[y*p->width + x] = IMGDCD_MIDPTOPIXEL_32((color & 0x00FFFFFF) | ((alpha << 24) & 0xFF000000));
      } else {
          r = ((color >> 16) & 0xff) >> 3;
          g = ((color >>  8) & 0xff) >> 2;
          b = ((color >>  0) & 0xff) >> 3;

          if (r < 0) r = 0; else if (r > 0xff) r = 0xff;
          if (g < 0) g = 0; else if (g > 0xff) g = 0xff;
          if (b < 0) b = 0; else if (b > 0xff) b = 0xff;

          ((imgdcd_pixel16_type*)p->pixelData)[y*p->width + x] = IMGDCD_RGB2PIXEL_16(r, g, b);
      }
#elif ENABLE_32BITS_PIXEL_FORMAT
      p->pixelData[y*p->width + x] = IMGDCD_MIDPTOPIXEL((color & 0x00FFFFFF) | ((alpha << 24) & 0xFF000000));
#else
      r = ((color >> 16) & 0xff) >> 3;
      g = ((color >>  8) & 0xff) >> 2;
      b = ((color >>  0) & 0xff) >> 3;

      /* 
       * IMPL_NOTE: Is this check really needed?
       * Even if yes, remove it anyway and use 'unsigned char'
       * instead of 'int'
       */
      if (r < 0) r = 0; else if (r > 0xff) r = 0xff;
      if (g < 0) g = 0; else if (g > 0xff) g = 0xff;
      if (b < 0) b = 0; else if (b > 0xff) b = 0xff;

      p->pixelData[y*p->width + x] = IMGDCD_RGB2PIXEL(r, g, b);
#endif
      p->alphaData[y*p->width + x] = alpha;
      if (alpha != 0xff) {
          p->hasAlpha = KNI_TRUE;
      }
    }
  }
}

/**
 * Initialize the given image destination structure
 *
 *  @param p pointer to the image destination to initialize
 */
void
initImageDst(_imageDstPtr p) {
  REPORT_CALL_TRACE(LC_LOWUI,
                    "LF:STUB:initImageDst()\n");

  p->super.ptr = p;

  p->super.depth            = 8;
  p->super.setColormap      = setImageColormap;
  p->super.setTransMap      = setImageTransparencyMap;
  p->super.setSize          = setImageSize;
  p->super.sendPixels       = sendPixelsColor;

  p->width                  = 0;
  p->height                 = 0;
  p->pixelData              = NULL;
  p->alphaData              = NULL;

  p->hasColorMap            = KNI_FALSE;
  p->hasTransMap            = KNI_FALSE;
  p->hasAlpha               = KNI_FALSE;
}


/**
 * Decodes the given input data into a storage format used by immutable
 * images.  The input data should be a PNG image.
 *
 *  @param srcBuffer input data to be decoded.
 *  @param length length of the input data.
 *  @param creationErrorPtr pointer to the status of the decoding
 *         process. This function sets creationErrorPtr's value.
 */
int
imgdcd_decode_png
(unsigned char* srcBuffer, int length, 
 int width, int height,
 imgdcd_pixel_type *pixelData, 
 imgdcd_alpha_type *alphaData,
 img_native_error_codes* creationErrorPtr) {

    _imageDstData dstData;
    imageSrcPtr src = NULL;

    (void)pixelData;
    REPORT_CALL_TRACE(LC_LOWUI,
                     "LF:decode_PNG()\n");

    /* Create the image from the buffered data */
    initImageDst(&dstData);

    
    dstData.width = width;
    dstData.height = height;
    
    dstData.pixelData = pixelData;
    dstData.alphaData = alphaData;
    
    /* what about (pixelData == NULL &&
                   width > 0 &&
                   height > 0) ? */

    if ((src = create_imagesrc_from_data((char **)(void*)&srcBuffer,
                                             length)) == NULL) {
      *creationErrorPtr = IMG_NATIVE_IMAGE_OUT_OF_MEMORY_ERROR;
    } else if (!decode_png_image(src, (imageDstData *)(&dstData))) {
      *creationErrorPtr = IMG_NATIVE_IMAGE_DECODING_ERROR;
    } else {
      *creationErrorPtr = IMG_NATIVE_IMAGE_NO_ERROR;

      /* dstData.hasAlpha == KNI_FALSE */
    }

    if(src != NULL) {
        midpFree(src);
    }

    return dstData.hasAlpha;
}

#if ENABLE_JPEG
#define LCDUI_PIXEL_SIZE sizeof(imgdcd_pixel_type)


/**
 * TBD:
 * a). use imageSrcPtr & imageDstPtr instead of direct array pointers
 * b). move to a special file, like decode_png_image() in imgdcd_png_decode.c
 */
//static bool decode_jpeg_image(imageSrcPtr src, imageDstPtr dst)
static int decode_jpeg_image(char* inData, int inDataLen,
    char* outData, int outDataWidth, int outDataHeight)
{
    int result = FALSE;

    void *info = JPEG_To_RGB_init();
    if (info) {
        int width, height;
        if (JPEG_To_RGB_decodeHeader(info, inData, inDataLen,
            &width, &height) != 0) {
            if ((width < outDataWidth) || (height < outDataHeight)) {
                /*
                 * TBD:
                 * actual jpeg image size is smaller that prepared buffer
                 * (and requested image size),
                 * So initialize unused areas:
                 * right part = {width, 0, outDataWidth, height}
                 * & bottom part = {0, height, outDataWidth, outDataHeight}
                 * with default color (0,0,0) ?
                 */
            }

            if (JPEG_To_RGB_decodeData2(info, outData,
                LCDUI_PIXEL_SIZE, 0, 0, outDataWidth, outDataHeight) != 0) {
                result = TRUE;
            }
        }

        JPEG_To_RGB_free(info);
    }
    return result;
}
#endif

/**
 * Decodes the given input data into a storage format used by
 * images.  The input data should be a JPEG image.
 *
 *  @param srcBuffer input data to be decoded.
 *  @param length length of the input data.
 *  @param creationErrorPtr pointer to the status of the decoding
 *         process. This function sets creationErrorPtr's value.
 */
void
imgdcd_decode_jpeg
(unsigned char* srcBuffer, int length,
 int width, int height,
 imgdcd_pixel_type* pixelData, 
 imgdcd_alpha_type* alphaData,
 img_native_error_codes* creationErrorPtr) {

#if ENABLE_JPEG
    _imageDstData dstData;
    imageSrcPtr src = NULL;

    REPORT_CALL_TRACE(LC_LOWUI,
                     "LF:decodeJPEG()\n");
    /* Create the image from the buffered data */
    initImageDst(&dstData);

    dstData.width = width;
    dstData.height = height;
    dstData.pixelData = pixelData;
    dstData.alphaData = alphaData;

    /* what about (pixelData == NULL &&
                   width > 0 &&
                   height > 0) ? */
    if (pixelData == NULL) {
        ((imageDstPtr)&dstData)->setSize(
            ((imageDstPtr)&dstData), width, height);
        /*
         pixelData = pcsl_mem_malloc(width * height * LCDUI_PIXEL_SIZE);
        */
    }

    if ((src = create_imagesrc_from_data((char **)(void *)&srcBuffer,
        length)) == NULL) {
        *creationErrorPtr = IMG_NATIVE_IMAGE_OUT_OF_MEMORY_ERROR;
    } else if (decode_jpeg_image((char*)srcBuffer, length,
        (char*)(pixelData), width, height) != FALSE) {
#if ENABLE_DYNAMIC_PIXEL_FORMAT
            if (img_enable_32bit_mode) {
		imgdcd_pixel_type* pPtr = pixelData;
		imgdcd_pixel_type* pEndPtr = pPtr + width * height;
		for (; pPtr < pEndPtr; pPtr++) {
			*pPtr = IMGDCD_MIDPTOOPAQUEPIXEL_32(*pPtr);
		}
            }
#elif ENABLE_32BITS_PIXEL_FORMAT
		/*
		 * Decoder returns data in ARGB format therefore convert the content of
		 * the output buffer to putpixel format (RGBA or ABGR)
		 */
		imgdcd_pixel_type* pPtr = pixelData;
		imgdcd_pixel_type* pEndPtr = pPtr + width * height;
		for (; pPtr < pEndPtr; pPtr++) {
			*pPtr = IMGDCD_MIDPTOOPAQUEPIXEL(*pPtr);
		}
#endif
        *creationErrorPtr = IMG_NATIVE_IMAGE_NO_ERROR;
    } else {
        *creationErrorPtr = IMG_NATIVE_IMAGE_DECODING_ERROR;
    }

    if(src != NULL) {
        midpFree(src);
    }

#else
    (void)srcBuffer;
    (void)length;
    (void)width; (void) height; 
    (void)pixelData; (void) alphaData;
    *creationErrorPtr = IMG_NATIVE_IMAGE_UNSUPPORTED_FORMAT_ERROR;
#endif
}
