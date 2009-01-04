/*
 *
 *
 * Copyright  1990-2008 Sun Microsystems, Inc. All Rights Reserved.
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

/**
 * @file
 * @ingroup highui_lfpport
 *
 * @brief Canvas-specific porting functions and data structures.
 */
#include <lfpport_displayable.h>
#include "lfpport_canvas.h"
#include "lfpport_gtk.h"

#ifdef __cplusplus
extern "C" {
#endif

extern gint display_width;
extern gint display_height;

extern GtkWidget *main_canvas;
extern GtkWidget *main_window;
extern GdkPixmap *current_mutable;

MidpError canvas_show_cb(MidpFrame* framePtr) {
    //Same as form_show
    LIMO_TRACE(">>>%s\n", __FUNCTION__);
    GtkWidget *label;
    GtkWidget *form;
    form = (GtkWidget *)framePtr->widgetPtr;
    LIMO_TRACE("<<<%s\n", __FUNCTION__);
    return KNI_OK;
}
MidpError canvas_hide_and_delete_cb(MidpFrame* framePtr, jboolean onExit) {
    GtkWidget *widget = (GtkWidget*)framePtr->widgetPtr;
    LIMO_TRACE(">>>%s\n", __FUNCTION__);
    gtk_widget_hide_all(widget);
    LIMO_TRACE("<<<%s\n", __FUNCTION__);
    return KNI_OK;
}
MidpError canvas_set_title_cb(MidpDisplayable* screenPtr,
                                const pcsl_string* title) {
    gchar buf[MAX_TITLE_LENGTH];
    int len;
    GtkWidget *form;
    LIMO_TRACE(">>>%s\n", __FUNCTION__);
    form = screenPtr->frame.widgetPtr;
    pcsl_string_convert_to_utf8(title, buf, MAX_TITLE_LENGTH, &len);
    if (len > 0) {
        gtk_form_set_title(GTK_FORM(form), buf);
    }
    LIMO_TRACE("<<<%s\n", __FUNCTION__);
    return KNI_OK;
}


MidpError canvas_set_ticker_cb(MidpDisplayable* dispPtr, const pcsl_string* text) {
    LIMO_TRACE(">>>%s\n", __FUNCTION__);
    LIMO_TRACE("<<<%s\n", __FUNCTION__);
    (void)dispPtr;
    (void)text;
    return KNI_OK;
}

jboolean canvas_handle_event_cb(MidpFrame* screenPtr, PlatformEventPtr eventPtr){
    LIMO_TRACE(">>>%s\n", __FUNCTION__);
    LIMO_TRACE("<<<%s\n", __FUNCTION__);
    (void)screenPtr;
    (void)eventPtr;
    return KNI_OK;
}

static gboolean
lfpport_canvas_expose_event_callback(GtkWidget *widget,
                                  GdkEventExpose *event, gpointer data)
{
    GdkPixmap *gdk_pix_map;
    LIMO_TRACE(">>>%s\n", __FUNCTION__);

    //gdk_pix_map = gtk_object_get_user_data(widget);
    gdk_pix_map = current_mutable;

    /* draw pixmap to the draw area */
    gdk_draw_drawable(widget->window,
                     widget->style->fg_gc[GTK_WIDGET_STATE(widget)],
                     gdk_pix_map,
                     event->area.x,
                     event->area.y,
                     event->area.x,
                     event->area.y,
                     event->area.width,
                     event->area.height);

    LIMO_TRACE("<<<%s\n", __FUNCTION__);
    return KNI_OK;
}


/**
 * Creates a canvas's native peer but does not display it.
 * The MIDP implementation should call this function in the background and
 * display the canvas afterward it is given all of its content.
 * When this function returns successfully, *canvasPtr will be filled.
 *
 * @param canvasPtr pointer to the canvas's MidpDisplayable structure.
 * @param title title string.
 * @param tickerText ticker text.
 *
 * @return an indication of success or the reason for failure
 */
MidpError lfpport_canvas_create(MidpDisplayable* canvasPtr,
                const pcsl_string* title, const pcsl_string* tickerText) {
    GtkWidget *form;
    GtkWidget *da;
    GdkGC *gc;
    int da_width, da_height;
    int title_height, title_width;
    GtkRequisition requisition;

    LIMO_TRACE(">>>%s\n", __FUNCTION__);

    da = gtk_drawing_area_new();
    form = gtk_form_new(TRUE);
    gtk_container_add(GTK_CONTAINER(form), da);
    gtk_object_set_user_data(form, da);
    gtk_widget_show_all(form);

    gtk_widget_set_size_request(da, display_width, display_height);
    gtk_widget_size_request(da, &requisition);

    // Fill in MidpDisplayable structure
    canvasPtr->frame.widgetPtr = form;
    canvasPtr->frame.show = canvas_show_cb;
    canvasPtr->frame.hideAndDelete = canvas_hide_and_delete_cb;
    canvasPtr->frame.handleEvent = canvas_handle_event_cb;
    canvasPtr->setTitle = canvas_set_title_cb;
    canvasPtr->setTicker = canvas_set_ticker_cb;

    LIMO_TRACE("%s da=%x\n", __FUNCTION__, da);
    gtk_main_window_add_form(main_window, GTK_FORM(form));
    //TODO:  find a more appropriate location for the following line
    //Located here only because for some unknown reason show(canvas)
    //is not called
    gtk_main_window_set_current_form(main_window, GTK_FORM(form));

    lfpport_form_set_content_size(canvasPtr,
                                  display_width,
                                  display_height);


    canvasPtr->setTitle(canvasPtr, title);
    gtk_widget_size_request(da, &requisition);

//     g_signal_connect(G_OBJECT(da), "expose_event",
//                    G_CALLBACK(lfpport_canvas_expose_event_callback), NULL);


    LIMO_TRACE("<<<%s\n", __FUNCTION__);
    return KNI_OK;
}
#ifdef __cplusplus
} /* extern "C" */
#endif
