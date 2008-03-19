/*
 *
 *
 * Copyright  1990-2007 Sun Microsystems, Inc. All Rights Reserved.
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

package com.sun.midp.pki.ocsp;

import java.util.Date;
import java.io.IOException;

import javax.microedition.pki.CertificateException;

import com.sun.midp.pki.*;


import com.sun.midp.crypto.Signature;
import com.sun.midp.crypto.SignatureException;
import com.sun.midp.crypto.InvalidKeyException;
import com.sun.midp.crypto.NoSuchAlgorithmException;

import com.sun.midp.log.Logging;
import com.sun.midp.log.LogChannels;

/**
 * This class is used to process an OCSP response.
 * The OCSP Response is defined
 * in RFC 2560 and the ASN.1 encoding is as follows:
 * <pre>
 *
 *  OCSPResponse ::= SEQUENCE {
 *      responseStatus         OCSPResponseStatus,
 *      responseBytes          [0] EXPLICIT ResponseBytes OPTIONAL }
 *
 *   OCSPResponseStatus ::= ENUMERATED {
 *       successful            (0),  --Response has valid confirmations
 *       malformedRequest      (1),  --Illegal confirmation request
 *       internalError         (2),  --Internal error in issuer
 *       tryLater              (3),  --Try again later
 *                                   --(4) is not used
 *       sigRequired           (5),  --Must sign the request
 *       unauthorized          (6)   --Request unauthorized
 *   }
 *
 *   ResponseBytes ::=       SEQUENCE {
 *       responseType   OBJECT IDENTIFIER,
 *       response       OCTET STRING }
 *
 *   BasicOCSPResponse       ::= SEQUENCE {
 *      tbsResponseData      ResponseData,
 *      signatureAlgorithm   AlgorithmIdentifier,
 *      signature            BIT STRING,
 *      certs                [0] EXPLICIT SEQUENCE OF Certificate OPTIONAL }
 *
 *   The value for signature SHALL be computed on the hash of the DER
 *   encoding ResponseData.
 *
 *   ResponseData ::= SEQUENCE {
 *      version              [0] EXPLICIT Version DEFAULT v1,
 *      responderID              ResponderID,
 *      producedAt               GeneralizedTime,
 *      responses                SEQUENCE OF SingleResponse,
 *      responseExtensions   [1] EXPLICIT Extensions OPTIONAL }
 *
 *   ResponderID ::= CHOICE {
 *      byName               [1] Name,
 *      byKey                [2] KeyHash }
 *
 *   KeyHash ::= OCTET STRING -- SHA-1 hash of responder's public key
 *   (excluding the tag and length fields)
 *
 *   SingleResponse ::= SEQUENCE {
 *      certID                       CertID,
 *      certStatus                   CertStatus,
 *      thisUpdate                   GeneralizedTime,
 *      nextUpdate         [0]       EXPLICIT GeneralizedTime OPTIONAL,
 *      singleExtensions   [1]       EXPLICIT Extensions OPTIONAL }
 *
 *   CertStatus ::= CHOICE {
 *       good        [0]     IMPLICIT NULL,
 *       revoked     [1]     IMPLICIT RevokedInfo,
 *       unknown     [2]     IMPLICIT UnknownInfo }
 *
 *   RevokedInfo ::= SEQUENCE {
 *       revocationTime              GeneralizedTime,
 *       revocationReason    [0]     EXPLICIT CRLReason OPTIONAL }
 *
 *   UnknownInfo ::= NULL -- this can be replaced with an enumeration
 *
 * </pre>
 *
 * @author      Ram Marti
 */

class OCSPResponse {
    private static final ObjectIdentifier OCSP_BASIC_RESPONSE_OID;
    private static final ObjectIdentifier OCSP_NONCE_EXTENSION_OID;
    static {
        ObjectIdentifier tmp1 = null;
        ObjectIdentifier tmp2 = null;
        try {
            tmp1 = new ObjectIdentifier("1.3.6.1.5.5.7.48.1.1");
            tmp2 = new ObjectIdentifier("1.3.6.1.5.5.7.48.1.2");
        } catch (Exception e) {
            // should not happen; log and exit
        }
        OCSP_BASIC_RESPONSE_OID = tmp1;
        OCSP_NONCE_EXTENSION_OID = tmp2;
    }

    // OCSP response status code
    private static final int OCSP_RESPONSE_OK = 0;

    // ResponderID CHOICE tags
    private static final int NAME_TAG = 1;
    private static final int KEY_TAG = 2;

    // Object identifier for the OCSPSigning key purpose
    private static final String KP_OCSP_SIGNING_OID = "1.3.6.1.5.5.7.3.9";

    private SingleResponse singleResponse;

    /*
     * Create an OCSP response from its ASN.1 DER encoding.
     */
    // used by OCSPValidatorImpl
    OCSPResponse(byte[] bytes,
        X509Certificate responderCert) throws IOException, OCSPException {

        try {
            int responseStatus;
            ObjectIdentifier  responseType;
            int version;
            Date producedAtDate;
            AlgorithmId sigAlgId;
            byte[] ocspNonce;

            // OCSPResponse
            //if (Logging.REPORT_LEVEL <= Logging.INFORMATION) {
                Logging.report(Logging.INFORMATION, LogChannels.LC_SECURITY,
                    "OCSPResponse first bytes are... " +
                        Utils.hexEncode(bytes, 0,
                                (bytes.length > 256) ? 256 : bytes.length));
            //}

            DerValue der = new DerValue(bytes);
            if (der.tag != DerValue.tag_Sequence) {
                throw new IOException("Bad encoding in OCSP response: " +
                    "expected ASN.1 SEQUENCE tag.");
            }
            DerInputStream derIn = der.getData();

            // responseStatus
            responseStatus = derIn.getEnumerated();
            //if (Logging.REPORT_LEVEL <= Logging.INFORMATION) {
                Logging.report(Logging.INFORMATION, LogChannels.LC_SECURITY,
                           "OCSP response: " + responseStatus);
            //}
            if (responseStatus != OCSP_RESPONSE_OK) {
                throw new OCSPException((byte)responseStatus,
                    "OCSP Response Failure: " + responseStatus);
            }

            // responseBytes
            der = derIn.getDerValue();
System.out.println(">>> 1");
            if (! der.isContextSpecific((byte)0)) {
                throw new IOException("Bad encoding in responseBytes element " +
                    "of OCSP response: expected ASN.1 context specific tag 0.");
            }
System.out.println(">>> 2");
            DerValue tmp = der.data.getDerValue();
System.out.println(">>> 3");
            if (tmp.tag != DerValue.tag_Sequence) {
                throw new IOException("Bad encoding in responseBytes element " +
                    "of OCSP response: expected ASN.1 SEQUENCE tag.");
            }

            // responseType
            derIn = tmp.data;
System.out.println(">>> 4");
            responseType = derIn.getOID();
System.out.println(">>> 5");
            if (responseType.equals(OCSP_BASIC_RESPONSE_OID)) {
                if (Logging.REPORT_LEVEL <= Logging.INFORMATION) {
                    Logging.report(Logging.INFORMATION, LogChannels.LC_SECURITY,
                               "OCSP response type: basic");
                }
            } else {
                if (Logging.REPORT_LEVEL <= Logging.INFORMATION) {
                    Logging.report(Logging.INFORMATION, LogChannels.LC_SECURITY,
                               "OCSP response type: " + responseType);
                }
                throw new IOException("Unsupported OCSP response type: " +
                    responseType);
            }

System.out.println(">>> 6");
            // BasicOCSPResponse
            DerInputStream basicOCSPResponse =
                new DerInputStream(derIn.getOctetString());
System.out.println(">>> 7");

            DerValue[]  seqTmp = basicOCSPResponse.getSequence(2);
            DerValue responseData = seqTmp[0];

            // Need the DER encoded ResponseData to verify the signature later
            byte[] responseDataDer = seqTmp[0].toByteArray();
System.out.println(">>> 8");

            // tbsResponseData
            if (responseData.tag != DerValue.tag_Sequence) {
                throw new IOException("Bad encoding in tbsResponseData " +
                    " element of OCSP response: expected ASN.1 SEQUENCE tag.");
            }
            DerInputStream seqDerIn = responseData.data;
            DerValue seq = seqDerIn.getDerValue();

            // version
            if (seq.isContextSpecific((byte)0)) {
                // seq[0] is version
                if (seq.isConstructed() && seq.isContextSpecific()) {
                    //System.out.println ("version is available");
                    seq = seq.data.getDerValue();
                    version = seq.getInteger();
                    if (seq.data.available() != 0) {
                        throw new IOException("Bad encoding in version " +
                            " element of OCSP response: bad format");
                    }
                    seq = seqDerIn.getDerValue();
                }
            }

System.out.println(">>> 9");
            // responderID
            short tag = (byte)(seq.tag & 0x1f);
            if (tag == NAME_TAG) {
                String responderName = parsex500Name(seq.getData());
                if (Logging.REPORT_LEVEL <= Logging.INFORMATION) {
                    Logging.report(Logging.INFORMATION, LogChannels.LC_SECURITY,
                               "OCSP Responder name: " + responderName);
                }
            } else if (tag == KEY_TAG) {
                // Ignore, for now
            } else {
                throw new IOException("Bad encoding in responderID element " +
                    "of OCSP response: expected ASN.1 context specific tag 0 " +
                    "or 1");
            }

System.out.println(">>> 10");
            // producedAt
            seq = seqDerIn.getDerValue();
            producedAtDate = seq.getGeneralizedTime();

            // responses
            DerValue[] singleResponseDer = seqDerIn.getSequence(1);
            // Examine only the first response
            singleResponse = new SingleResponse(singleResponseDer[0]);

            // responseExtensions
            if (seqDerIn.available() > 0) {
                seq = seqDerIn.getDerValue();
                if (seq.isContextSpecific((byte)1)) {
                    DerValue[]  responseExtDer = seq.data.getSequence(3);
                    Extension[] responseExtension =
                        new Extension[responseExtDer.length];
                    for (int i = 0; i < responseExtDer.length; i++) {
                        responseExtension[i] = new Extension(responseExtDer[i]);
                        if (Logging.REPORT_LEVEL <= Logging.INFORMATION) {
                            Logging.report(Logging.INFORMATION,
                                LogChannels.LC_SECURITY,
                                    "OCSP extension: " + responseExtension[i]);
                        }
                        if ((responseExtension[i].getExtensionId()).equals(
                            OCSP_NONCE_EXTENSION_OID)) {
                            ocspNonce =
                                responseExtension[i].getExtensionValue();

                        } else if (responseExtension[i].isCritical())  {
                            throw new IOException(
                                "Unsupported OCSP critical extension: " +
                                responseExtension[i].getExtensionId());
                        }
                    }
                }
            }

System.out.println(">>> 11");
            // signatureAlgorithmId
            sigAlgId = AlgorithmId.parse(seqTmp[1]);
System.out.println(">>> 12");

            // signature
            byte[] signature = seqTmp[2].getBitString();
            X509Certificate[] x509Certs = null;
System.out.println(">>> 13");

            // if seq[3] is available , then it is a sequence of certificates
            if (seqTmp.length > 3) {
                // certs are available
                DerValue seqCert = seqTmp[3];
                if (! seqCert.isContextSpecific((byte)0)) {
                    throw new IOException("Bad encoding in certs element " +
                    "of OCSP response: expected ASN.1 context specific tag 0.");
                }
                DerValue[] certs = (seqCert.getData()).getSequence(3);
                x509Certs = new X509Certificate[certs.length];
                for (int i = 0; i < certs.length; i++) {
                    byte[] data = certs[i].toByteArray();
                    x509Certs[i] = X509Certificate.generateCertificate(
                            data, 0 , data.length);
                }
            }

System.out.println(">>> 14");
            // Check whether the cert returned by the responder is trusted
            if (x509Certs != null && x509Certs[0] != null) {
                X509Certificate cert = x509Certs[0];

                // First check if the cert matches the responder cert which
                // was set locally.
                if (cert.equals(responderCert)) {
                    // cert is trusted, now verify the signed response

                    // Next check if the cert was issued by the responder cert
                    // which was set locally.
                } else if (cert.getIssuer().equals(
                    responderCert.getSubject())) {

                    /* IMPL_NOTE: key purposes should be parsed in X509Certificate
                       and validated here */
                    /*
                    // Check for the OCSPSigning key purpose
                    List<String> keyPurposes = cert.getExtendedKeyUsage();
                    if (keyPurposes == null ||
                        !keyPurposes.contains(KP_OCSP_SIGNING_OID)) {
                        if (Logging.REPORT_LEVEL <= Logging.INFORMATION) {
                            Logging.report(Logging.INFORMATION,
                                    LogChannels.LC_SECURITY,
                                    "Responder's certificate is not valid " +
                                        "for signing OCSP responses.");
                        }
                        throw new OCSPException(
                            OCSPException.INVALID_RESPONDER_CERTIFICATE,
                            "Responder's certificate not valid for signing " +
                            "OCSP responses");
                    }
                    */

                    // verify the signature
                    try {
                        cert.verify(responderCert.getPublicKey());
                        responderCert = cert;
                        // cert is trusted, now verify the signed response

                    } catch (CertificateException ce) {
                        responderCert = null;
                    }
                }
            }

System.out.println(">>> 15");
            // Confirm that the signed response was generated using the public
            // key from the trusted responder cert
            if (responderCert != null) {

System.out.println(">>> 16");
                if (!verifyResponse(responseDataDer, responderCert,
                                    sigAlgId, signature)) {
                    if (Logging.REPORT_LEVEL <= Logging.INFORMATION) {
                        Logging.report(Logging.INFORMATION,
                                LogChannels.LC_SECURITY,
                                "Error verifying OCSP Responder's signature");
                    }
                    throw new OCSPException(
                        OCSPException.CANNOT_VERIFY_SIGNATURE,
                            "Error verifying OCSP Responder's signature");
                }
            } else {
                // Need responder's cert in order to verify the signature
                if (Logging.REPORT_LEVEL <= Logging.INFORMATION) {
                    Logging.report(Logging.INFORMATION, LogChannels.LC_SECURITY,
                               "Unable to verify OCSP Responder's signature");
                }
                throw new OCSPException(OCSPException.CANNOT_VERIFY_SIGNATURE,
                    "Unable to verify OCSP Responder's signature");
            }
        } catch (OCSPException e) {
            throw e;
        } catch (Exception e) {
            throw new OCSPException(OCSPException.UNKNOWN_ERROR,
                    e.getMessage());
        }
    }

    private String parsex500Name(DerInputStream in) throws IOException {
        //
        // X.500 names are a "SEQUENCE OF" RDNs, which means zero or
        // more and order matters.  We scan them in order, which
        // conventionally is big-endian.
        //
        DerValue[] nameseq = null;
        byte[] derBytes = in.toByteArray();

        try {
            nameseq = in.getSequence(5);
        } catch (IOException ioe) {
            if (derBytes == null) {
                nameseq = null;
            } else {
                DerValue derVal = new DerValue(DerValue.tag_Sequence,
                                           derBytes);
                derBytes = derVal.toByteArray();
                nameseq = new DerInputStream(derBytes).getSequence(5);
            }
        }

        if (nameseq == null) {
            return null;
        } else {
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < nameseq.length; i++) {
                if (nameseq[i].tag != DerValue.tag_Set) {
                     throw new IOException("X500 RDN");
                 }
                 DerInputStream dis = new DerInputStream(nameseq[i].toByteArray());
                 DerValue[] avaset = dis.getSet(5);

                 for (int j = 0; j < avaset.length; j++) {
                     // Individual attribute value assertions are SEQUENCE of two values.
                     // That'd be a "struct" outside of ASN.1.
                     if (avaset[j].tag != DerValue.tag_Sequence) {
                         throw new IOException("AVA not a sequence");
                     }
                     if (j != 0) {
                         sb.append(" + ");
                     }
                     sb.append(avaset[j].data.getOID().toString());
                     sb.append(avaset[j].data.getDerValue().getAsString());

                     if (avaset[j].data.available() != 0) {
                         throw new IOException("AVA, extra bytes = "
                             + avaset[j].data.available());
                     }
                 }

                sb.append(";");
            }
            return sb.toString();
        }
    }

    /*
     * Verify the signature of the OCSP response.
     * The responder's cert is implicitly trusted.
     */
    private boolean verifyResponse(byte[] responseData, X509Certificate cert,
            AlgorithmId sigAlgId, byte[] signBytes)
                    throws SignatureException, CertificateException {
        try {
            Signature respSignature = Signature.getInstance(sigAlgId.getName());
            respSignature.initVerify(cert.getPublicKey());
            respSignature.update(responseData, 0, responseData.length);

            if (respSignature.verify(signBytes)) {
                if (Logging.REPORT_LEVEL <= Logging.INFORMATION) {
                    Logging.report(Logging.INFORMATION, LogChannels.LC_SECURITY,
                               "Verified signature of OCSP Responder");
                }
                return true;

            } else {
                if (Logging.REPORT_LEVEL <= Logging.INFORMATION) {
                    Logging.report(Logging.INFORMATION, LogChannels.LC_SECURITY,
                           "Error verifying signature of OCSP Responder");
                }
                return false;
            }
        } catch (InvalidKeyException ike) {
            throw new SignatureException("Invalid key: " + ike.getMessage());

        } catch (NoSuchAlgorithmException nsae) {
            throw new SignatureException("Invalid algorithm: " +
                                         nsae.getMessage());
        }
    }

    /*
     * Return the revocation status code for a given certificate.
     */
    // used by OCSPValidatorImpl
    int getCertStatus() {
        return singleResponse.getStatus();
    }

    // used by OCSPValidatorImpl
    CertId getCertId() {
        return singleResponse.getCertId();
    }

    /*
     * Map a certificate's revocation status code to a string.
     */
    // used by OCSPValidatorImpl
    static String certStatusToText(int certStatus) {
        switch (certStatus)  {
        case CertStatus.GOOD:
            return "Good";
        case CertStatus.REVOKED:
            return "Revoked";
        case CertStatus.UNKNOWN:
            return "Unknown";
        default:
            return ("Unknown certificate status code: " + certStatus);
        }
    }

    /*
     * A class representing a single OCSP response.
     */
    private class SingleResponse {
        private CertId certId;
        private int certStatus;
        private Date thisUpdate;
        private Date nextUpdate;

        private SingleResponse(DerValue der) throws IOException {
            if (der.tag != DerValue.tag_Sequence) {
                throw new IOException("Bad ASN.1 encoding in SingleResponse");
            }
            DerInputStream tmp = der.data;

            certId = new CertId(tmp.getDerValue().data);
            DerValue derVal = tmp.getDerValue();
            short tag = (byte)(derVal.tag & 0x1f);
            if (tag ==  CertStatus.GOOD) {
                certStatus = CertStatus.GOOD;
            } else if (tag == CertStatus.REVOKED) {
                certStatus = CertStatus.REVOKED;
                // RevokedInfo
                if (Logging.REPORT_LEVEL <= Logging.INFORMATION) {
                    Date revocationTime = derVal.data.getGeneralizedTime();
                    Logging.report(Logging.INFORMATION, LogChannels.LC_SECURITY,
                               "Revocation time: " + revocationTime);
                }

            } else if (tag == CertStatus.UNKNOWN) {
                certStatus = CertStatus.UNKNOWN;
            } else {
                throw new IOException("Invalid certificate status");
            }

            thisUpdate = tmp.getGeneralizedTime();

            if (tmp.available() == 0)  {
                // we are done
            } else {
                derVal = tmp.getDerValue();
                tag = (byte)(derVal.tag & 0x1f);
                if (tag == 0) {
                    // next update
                    nextUpdate = derVal.data.getGeneralizedTime();

                    if (tmp.available() == 0)  {
                        return;
                    } else {
                        derVal = tmp.getDerValue();
                        tag = (byte)(derVal.tag & 0x1f);
                    }
                }
                // ignore extensions
            }

            Date now = new Date();
            if (Logging.REPORT_LEVEL <= Logging.INFORMATION) {
                String until = "";
                if (nextUpdate != null) {
                    until = " until " + nextUpdate;
                }
                Logging.report(Logging.INFORMATION, LogChannels.LC_SECURITY,
                    "Response's validity interval is from " +
                            thisUpdate + until);
            }
            // Check that the test date is within the validity interval
            if ((thisUpdate != null && (now.getTime() < thisUpdate.getTime())) ||
                (nextUpdate != null && (now.getTime() > nextUpdate.getTime()))) {

                if (Logging.REPORT_LEVEL <= Logging.INFORMATION) {
                    Logging.report(Logging.INFORMATION, LogChannels.LC_SECURITY,
                               "Response is unreliable: " +
                                       "its validity interval is out-of-date");
                }
                throw new IOException("Response is unreliable: its validity " +
                    "interval is out-of-date");
            }
        }

        /*
         * Return the certificate's revocation status code
         */
        private int getStatus() {
            return certStatus;
        }

        private CertId getCertId() {
            return certId;
        }

        /**
         * Construct a string representation of a single OCSP response.
         */
        public String toString() {
            StringBuffer sb = new StringBuffer();

            sb.append("SingleResponse:  \n");
            sb.append(certId);
            sb.append("\nCertStatus: ");
            sb.append(certStatusToText(getCertStatus()));
            sb.append("\n");
            sb.append("thisUpdate is ");
            sb.append(thisUpdate);
            sb.append("\n");
            if (nextUpdate != null) {
                sb.append("nextUpdate is ");
                sb.append(nextUpdate);
                sb.append("\n");
            }

            return sb.toString();
        }
    }
}
