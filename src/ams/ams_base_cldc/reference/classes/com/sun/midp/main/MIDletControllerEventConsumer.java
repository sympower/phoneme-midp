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

package com.sun.midp.main;

/**
 * This interface is to be implemnted by an event processing target
 * for MIDlet events on MIDlet controller (i.e. AMS) side.
 *
 * EventListener for these events must find appropriate
 * instance of this I/F implementor and call its methods.
 *
 * TBD: although Consumer I/F is intended to be instance specific,
 * the implementor of this one shall be assosiated with a MIDletProxyList,
 * which is a single static object that exists only in AMS isolate ...
 *
 * However, I/F implementor shall NOT assume
 * that it is a static singleton object ...
 *
 * TBD: method and parameter lists of the I/F is preliminary
 * and is a subject for changes.
 *
 * TBD: it makes sence replace some handlerXXXEvent method parameters
 * (isolateId, displayId) by MIdletProxy object that is able to provide
 * all needed information to process event.
 *
 */
public interface MIDletControllerEventConsumer {

    /**
     * MIDlet Startup Events:
     *
     * MIDLET_START_ERROR
     * MIDLET_CREATED_NOTIFICATION
     */
    /**
     * Process MIDLET_START_ERROR event
     *
     * @param midletSuiteId ID of the MIDlet suite
     * @param midletClassName Class name of the MIDlet
     * @param midletExternalAppId ID of given by an external application
     *                            manager
     * @param errorCode start error code
     * @param errorDetails start error details
     */
    public void handleMIDletStartErrorEvent(
        int midletSuiteId,
        String midletClassName,
        int midletExternalAppId,
        int errorCode,
        String errorDetails);

    /**
     * Process MIDLET_CREATED_NOTIFICATION event,
     * parameters - to create MIDletProxy object instance
     *
     * @param midletSuiteId ID of the MIDlet suite
     * @param midletClassName Class name of the MIDlet
     * @param midletIsolateId isolate ID of the sending MIDlet
     * @param midletExternalAppId ID of given by an external application
     *                            manager
     * @param midletDisplayName name to show the user
     */
    public void handleMIDletCreateNotifyEvent(
        int midletSuiteId,
        String midletClassName,
        int midletIsolateId,
        int midletExternalAppId,
        String midletDisplayName);

    /**
     * MIDlet State Management (Lifecycle) Events:
     *
     * MIDLET_ACTIVE_NOTIFICATION
     * MIDLET_PAUSED_NOTIFICATION
     * MIDLET_DESTROYED_NOTIFICATION
     * MIDLET_RS_PAUSED_NOTIFICATION
     * 
     * MIDLET_RESUME_REQUEST
     * MIDLET_DESTROY_REQUEST
     *
     * ACTIVATE_ALL - produced by native code
     * PAUSE_ALL - produced by native code
     * INTERNAL_ACTIVATE_ALL - produced by native code
     * INTERNAL_PAUSE_ALL - produced by native code
     * SHUTDOWN/DESTROY_ALL - produced by native code
     *
     * FATAL_ERROR_NOTIFICATION - produced by native code
     *
     */
    /**
     * Process MIDLET_ACTIVE_NOTIFICATION event
     *
     * TBD: param midletProxy proxy with information about MIDlet
     *
     * @param midletSuiteId ID of the MIDlet suite
     * @param midletClassName Class name of the MIDlet
     */
    public void handleMIDletActiveNotifyEvent(
        // MIDletProxy midletProxy);
        int midletSuiteId,
        String midletClassName);

    /**
     * Process MIDLET_PAUSED_NOTIFICATION event
     *
     * TBD: param midletProxy proxy with information about MIDlet
     *
     * @param midletSuiteId ID of the MIDlet suite
     * @param midletClassName Class name of the MIDlet
     */
    public void handleMIDletPauseNotifyEvent(
        // MIDletProxy midletProxy);
        int midletSuiteId,
        String midletClassName);

    /**
     * Process MIDLET_DESTROYED_NOTIFICATION event
     *
     * TBD: param midletProxy proxy with information about MIDlet
     *
     * @param midletSuiteId ID of the MIDlet suite
     * @param midletClassName Class name of the MIDlet
     */
    public void handleMIDletDestroyNotifyEvent(
        // MIDletProxy midletProxy);
        int midletSuiteId,
        String midletClassName);

    /**
     * Process a MIDLET_RESUME_REQUEST event.
     *
     * TBD: param midletProxy proxy with information about MIDlet
     *
     * @param midletSuiteId ID of the MIDlet suite
     * @param midletClassName Class name of the MIDlet
     */
    public void handleMIDletResumeRequestEvent(
        // MIDletProxy midletProxy);
        int midletSuiteId,
        String midletClassName);

    /**
     * Process MIDLET_RS_PAUSED_NOTIFICATION event
     *
     * TBD: param midletProxy proxy with information about MIDlet
     *
     * @param midletSuiteId ID of the MIDlet suite
     * @param midletClassName Class name of the MIDlet
     */
    public void handleMIDletRsPauseNotifyEvent(
        int midletSuiteId,
        String midletClassName);

    /**
     * Process MIDLET_DESTROY_REQUEST event
     *
     * TBD: param midletProxy proxy with information about MIDlet
     *
     * @param midletIsolateId isolate ID of the sending Display
     * @param midletDisplayId ID of the sending Display
     */
    public void handleMIDletDestroyRequestEvent(
        // MIDletProxy midletProxy);
        int midletIsolateId,
        int midletDisplayId);

    /**
     * Process ACTIVATE_ALL_EVENT
     */
    public void handleActivateAllEvent();

    /**
     * Process PAUSE_ALL_EVENT
     */
    public void handlePauseAllEvent();

    /**
     * Process INTERNAL_ACTIVATE_ALL_EVENT
     */
    public void handleInternalActivateAllEvent();

    /**
     * Process INTERNAL_PAUSE_ALL_EVENT
     */
    public void handleInternalPauseAllEvent();

    /**
     * Process SHUTDOWN_ALL_EVENT
     */
    public void handleDestroyAllEvent();

    /**
     * Process FATAL_ERROR_NOTIFICATION event
     *
     * @param midletIsolateId isolate ID of the sending isolate
     * @param midletDisplayId ID of the sending Display
     */
    public void handleFatalErrorNotifyEvent(
        int midletIsolateId,
        int midletDisplayId);

    /*
     * Foreground MIDlet Management Events:
     *
     * SELECT_FOREGROUND - produced by native code
     * FOREGROUND_TRANSFER
     * SET_FOREGROUND_BY_NAME_REQUEST
     *
     */
    /**
     * Process SELECT_FOREGROUND event
     */
    public void handleMIDletForegroundSelectEvent(int onlyFromLaunched);

    /**
     * Process FOREGROUND_TRANSFER event
     *
     * @param originMIDletSuiteId ID of MIDlet from which
     *        to take forefround ownership away,
     * @param originMIDletClassName Name of MIDlet from which
     *        to take forefround ownership away
     * @param targetMIDletSuiteId ID of MIDlet
     *        to give forefround ownership to,
     * @param targetMIDletClassName Name of MIDlet
     *        to give forefround ownership to
     */
    public void handleMIDletForegroundTransferEvent(
        int originMIDletSuiteId,
        String originMIDletClassName,
        int targetMIDletSuiteId,
        String targetMIDletClassName);

    /**
     * Process SET_FOREGROUND_BY_NAME_REQUEST
     *
     * @param suiteId MIDlet's suite ID
     * @param className MIDlet's class name
     */
    public void handleSetForegroundByNameRequestEvent(
        int suiteId,
        String className);

    /**
     * Foreground Display Management Events:
     *
     * DISPLAY_CREATED_NOTIFICATION
     * 
     * FOREGROUND_REQUEST
     * BACKGROUND_REQUEST
     *
     */
    /**
     * Process DISPLAY_CREATED_NOTIFICATION event,
     * parameters - set the display id of a MIDletProxy object instance
     *
     * @param midletIsolateId isolate ID of the sending Display
     * @param midletDisplayId ID of the sending Display
     * @param midletClassName Class name of the MIDlet
     */
    public void handleDisplayCreateNotifyEvent(
        int midletIsolateId,
        int midletDisplayId,
        String midletClassName);

    /**
     * Process FOREGROUND_REQUEST event
     *
     * TBD: param midletProxy proxy with information about MIDlet
     *
     * @param midletIsolateId isolate ID of the sending Display
     * @param midletDisplayId ID of the sending Display
     * @param isAlert true if the current displayable is an Alert
     */
    public void handleDisplayForegroundRequestEvent(
        // MIDletProxy midletProxy);
        int midletIsolateId,
        int midletDisplayId,
        boolean isAlert);

    /**
     * Process BACKGROUND_REQUEST event
     *
     * TBD: param midletProxy proxy with information about MIDlet
     *
     * @param midletIsolateId isolate ID of the sending Display
     * @param midletDisplayId ID of the sending Display
     */
    public void handleDisplayBackgroundRequestEvent(
        // MIDletProxy midletProxy);
        int midletIsolateId,
        int midletDisplayId);

    /*
     * Display Preemption Management Events:
     *
     * PREEMPT
     *
     */
    /**
     * Process PREEMPT_EVENT(true),
     * parameters - to create MIDletProxy object instance
     *
     * @param midletIsolateId isolate ID of the sending Display
     * @param midletDisplayId ID of the sending Display
     */
    public void handleDisplayPreemptStartEvent(
        int midletIsolateId,
        int midletDisplayId);

    /**
     * Process PREEMPT_EVENT(false),
     *
     * @param midletIsolateId isolate ID of the sending Display
     * @param midletDisplayId ID of the sending Display
     */
    public void handleDisplayPreemptStopEvent(
        int midletIsolateId,
        int midletDisplayId);
}
