package com.messagenetsystems.evolution2.threads;

/* DeliveryRotator
 * Using for-delivery list(s) in DeliveryService, actually perform the user-facing delivery thereof.
 *
 * Usage Example (declare, create, configure, and run):
 *  DeliveryRotator deliveryRotator;
 *  deliveryRotator = new DeliveryRotator(getApplicationContext(), DeliveryRotator.LOG_METHOD_FILELOGGER);
 *  deliveryRotator.start();
 *
 * Usage Example (stop the thread-loop and free up resources):
 *  deliveryRotator.cleanup();
 *
 * Usage Example (pause processing - may be easily resumed later)
 *  deliveryRotator.pauseProcessing();
 *
 * Usage Example (resume processing)
 *  deliveryRotator.resumeProcessing();
 *
 * Usage Example (send Android-msg with data to DeliveryService)
 *
 *
 * Message Delivery Rules!
 *  - "Delivery" means that a message gets conveyed to humans through audio and/or visual means.
 *  - Delivery of scrolling text should scroll twice upon very first dispatch.
 *  - Message must never be interrupted in mid-delivery, unless done so by explicit received command.
 *  - If multimode audio or video component of a message finishes before the other component, don't start another repeat until message repeats again (if applicable).
 *  - Only one message at a time may be delivered, so as to avoid conflicts of information.
 *  - Delivery of multiple messages should be delineated by a brief clock view.
 *  - The existence of a higher priority message should prevent delivery of lesser priority messages, until higher msg expires or is closed.
 *  - Overall delivery needs to be smooth and non-jarring to recipients.
 *  - Per Kevin, we should support priority-tolerance, as well.
 *
 * Revisions:
 *  2019.12.19      Chris Rider     Created.
 *  2020.01.03      Chris Rider     Migrated from MainService Handler/Message stuff to LocalBroadcast methodology --no longer need to instantiate with Handler that's in MainService!
 *  2020.01.09      Chris Rider     Finally trying to figure this out
 *  2020.01.13      Chris Rider     Fixed bug where 'continue' in loop was getting in the way of letting thread interrupt properly.
 *  2020.01.15      Chris Rider     Added check for ClockActivity last became visible Date, to enforce minimum visible time of the clock between messages.
 *                                  Decreased time interval between iterations from 1000ms to 500ms.
 *  2020.09.28      Chris Rider     Fixed theoretical potential for uncaught overflow in loop counter.
 */

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.OmniApplication;
import com.messagenetsystems.evolution2.activities.ClockActivity;
import com.messagenetsystems.evolution2.models.OmniMessage;
import com.messagenetsystems.evolution2.services.DeliveryService;
import com.messagenetsystems.evolution2.utilities.DatetimeUtils;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.UUID;


public class DeliveryRotator extends Thread {
    private final String TAG = this.getClass().getSimpleName();

    // Constants..


    // Logging stuff...
    private final int LOG_SEVERITY_V = 1;
    private final int LOG_SEVERITY_D = 2;
    private final int LOG_SEVERITY_I = 3;
    private final int LOG_SEVERITY_W = 4;
    private final int LOG_SEVERITY_E = 5;
    private int logMethod = Constants.LOG_METHOD_LOGCAT;

    // Local stuff...
    private WeakReference<Context> appContextRef;       //since this thread is very long running, we prefer a weak context reference
    private OmniApplication omniApplication;
    private DatetimeUtils datetimeUtils;

    private Handler androidMsgHandler_DeliveryService;  //reference to DeliveryService's OmniMessageRawHandler, so we can send data to there

    private volatile boolean isStopRequested;           //flag to set/check for the thread to interrupt itself
    private volatile boolean isThreadRunning;           //just a status flag
    private volatile boolean pauseProcessing;           //flag to set if you want to pause processing work (may double as a kind of "is paused" flag)

    private int activeProcessingSleepDuration;          //duration (in milliseconds) to sleep during active processing (to help ensure CPU cycles aren't eaten like crazy)
    private int pausedProcessingSleepDuration;          //duration (in milliseconds) to sleep during paused processing (to help ensure CPU cycles aren't eaten like crazy)

    private long loopIterationCounter;


    /** Constructor */
    public DeliveryRotator(Context appContext, int logMethod, Handler deliveryServiceHandler) {
        Log.v(TAG, "Instantiating.");

        this.logMethod = logMethod;

        this.appContextRef = new WeakReference<Context>(appContext);

        try {
            this.omniApplication = ((OmniApplication) appContext);
        } catch (Exception e) {
            logE("Exception caught instantiating "+TAG+": "+e.getMessage());
            return;
        }

        // Get our handlers from parents, so we can send Android-Messages back to them
        this.androidMsgHandler_DeliveryService = deliveryServiceHandler;                            //get our handler from DeliveryService

        // Initialize values
        this.isStopRequested = false;
        this.isThreadRunning = false;
        this.pauseProcessing = false;
        //this.activeProcessingSleepDuration = 500;
        this.activeProcessingSleepDuration = 1000;
        this.pausedProcessingSleepDuration = 5000;
        this.loopIterationCounter = 0;

        // Initialize objects
        this.datetimeUtils = new DatetimeUtils(appContext, logMethod);
    }


    /*============================================================================================*/
    /* Thread Methods */

    /** Main runnable routine... executes once whenever the initialized thread is commanded to start running with .start() or .execute() method call.
     * Remember that .start() implicitly spawns a thread and calls .execute() to invoke this run() method.
     * If you directly call .execute(), this run() method will invoke on the same thread you call it from. */
    @Override
    public void run() {
        final String TAGG = "run: ";
        logV(TAGG+"Invoked.");

        long pid = Thread.currentThread().getId();
        logI(TAGG+"Thread starting as process ID #"+ pid);

        UUID uuidDelivering_loading;
        UUID uuidDelivering_currently;

        // As long as our thread is supposed to be running...
        while (!Thread.currentThread().isInterrupted()) {

            // Our thread has started or is still running
            isThreadRunning = true;

            try {
                if (loopIterationCounter + 1 < Long.MAX_VALUE)
                    loopIterationCounter++;
                else
                    loopIterationCounter = 1;
            } catch (Exception e) {
                logW(TAGG+"Exception caught incrementing loop counter. Resetting to 0: "+e.getMessage());
                loopIterationCounter = 0;
            }

            // this is the end of the loop-iteration, so check whether we will stop or continue
            if (Thread.currentThread().isInterrupted()) {
                logI(TAGG+"Thread will now stop.");
                isThreadRunning = false;
            }
            if (isStopRequested) {
                logI(TAGG+"Thread has been requested to stop and will now do so.");
                isThreadRunning = false;
                break;
            }

            // Either do nothing (if paused) or allow work to happen (if not paused)...
            if (pauseProcessing) {
                // Do a short delay to help prevent the thread loop from eating cycles
                try {
                    Thread.sleep(pausedProcessingSleepDuration);
                } catch (InterruptedException e) {
                    logW(TAGG + "Exception caught trying to sleep during pause: " + e.getMessage());
                }

                logD(TAGG + "(iteration #"+loopIterationCounter+") Processing is paused. Thread continuing to run, but no work is occurring.");
            } else {
                // Do a short delay to help prevent the thread loop from eating cycles
                try {
                    Thread.sleep(activeProcessingSleepDuration);
                } catch (InterruptedException e) {
                    logW(TAGG + "Exception caught trying to sleep: " + e.getMessage());
                }

                logV(TAGG + "(iteration #"+loopIterationCounter+") Processing...");

                try {
                    ////////////////////////////////////////////////////////////////////////////////
                    // DO THE BULK OF THE ACTUAL WORK HERE...

                    // Get our delivering UUIDs so we can figure out what's going on
                    uuidDelivering_loading = DeliveryService.omniMessageUuidDelivery_loading;
                    uuidDelivering_currently = DeliveryService.omniMessageUuidDelivery_currently;

                    if (uuidDelivering_currently == null && uuidDelivering_loading == null) {
                        logV(TAGG+"No message currently delivering or loading. Sending command to figure out what (if anything) to deliver...");

                        try {
                            //if (ClockActivity.activityLastBecameVisible)
                            if (ClockActivity.activityLastBecameVisible != null
                                && datetimeUtils.datesAreWithinMS(
                                        ClockActivity.activityLastBecameVisible,
                                        new Date(),
                                        1000)) {
                                //ClockActivity has not been visible for long enough yet
                                logV(TAGG+"ClockActivity has not been visible for long enough yet!");
                                continue;
                            }
                        } catch (Exception e) {
                            logW(TAGG+"Exception caught investigating ClockActivity became-visible Date: "+e.getMessage());
                        }

                        //send command to deliver first message in main for-delivery list (that routine should take care of setting flags and stuff)
                        sendCommandToDeliveryService(DeliveryService.MSGHANDLER_ACTION_DELIVER_MESSAGE, null);
                        continue;
                    }

                    if (uuidDelivering_currently == null && uuidDelivering_loading != null) {
                        logV(TAGG+"No message currently delivering, but one is loading ("+uuidDelivering_loading.toString()+")");
                        //TODO...
                        //build a counter mechanism to catch any potential loading-delivery problems and deliver a message anyway after so many counts
                        //if counter gets too high, need to reset loading flag so double-null test above triggers
                        continue;
                    }

                    // END THE BULK OF THE ACTUAL WORK HERE...
                    ////////////////////////////////////////////////////////////////////////////////
                } catch (NullPointerException e) {
                    logE(TAGG+"Exception caught: "+e.getMessage());

                    // This can happen if parent process dies (taking context reference with it) before this loop breaks
                    // So, let's make sure that's not what's happening (we can depend on this flag to be set by .cleanup() which should be called upon destruction of parent process)...
                    if (!isStopRequested) {
                        logW(TAGG + "Parent process's context has gone AWOL. Parent thread has died? Shutting down!");
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    /** Call this to pause processing.
     * This essentially just sets the pause flag (which prevents any work being done).
     */
    public void pauseProcessing() {
        this.pauseProcessing = true;
    }

    /** Call this to resume paused processing.
     * This essentially just resets the pause flag (which allows work to be done).
     */
    public void resumeProcessing() {
        this.pauseProcessing = false;
    }

    /** Call this to terminate the loop and release resources. */
    public void cleanup() {
        final String TAGG = "cleanup: ";

        try {
            this.isStopRequested = true;

            // Note: At this point, the thread-loop should break on its own, since we check isInterrupted in the while-loop's condition
        } catch (Exception e) {
            logE(TAGG+"Exception caught invoking .interrupt(): "+e.getMessage());
        }

        this.datetimeUtils = null;

        this.appContextRef = null;
    }


    /*============================================================================================*/
    /* Processing Methods */

    /** Populate an Android-Message object with our message data from the database and send it to MesssgeService's Handler.
     */
    private void sendCommandToDeliveryService(int actionToRequest, OmniMessage omniMessage) {
        final String TAGG = "sendCommandToDeliveryService: ";

        // Get our handler's message object so we can populate it with our DB data
        android.os.Message androidMessage = androidMsgHandler_DeliveryService.obtainMessage();

        // Send what we're wanting the handler to do
        androidMessage.arg1 = actionToRequest;

        // Supply the provided OmniMessage to the Android-msg we're going to send
        androidMessage.obj = omniMessage;

        // Actually send the Android-message (with OmniMessage object) back to DeliveryService's handler
        androidMsgHandler_DeliveryService.sendMessage(androidMessage);
    }


    /*============================================================================================*/
    /* Getter/Setter Methods */

    public boolean isThreadRunning() {
        return this.isThreadRunning;
    }


    /*============================================================================================*/
    /* Logging Methods */

    private void logV(String tagg) {
        log(LOG_SEVERITY_V, tagg);
    }
    private void logD(String tagg) {
        log(LOG_SEVERITY_D, tagg);
    }
    private void logI(String tagg) {
        log(LOG_SEVERITY_I, tagg);
    }
    private void logW(String tagg) {
        log(LOG_SEVERITY_W, tagg);
    }
    private void logE(String tagg) {
        log(LOG_SEVERITY_E, tagg);
    }
    private void log(int logSeverity, String tagg) {
        switch (logMethod) {
            case Constants.LOG_METHOD_LOGCAT:
                switch (logSeverity) {
                    case LOG_SEVERITY_V:
                        Log.v(TAG, tagg);
                        break;
                    case LOG_SEVERITY_D:
                        Log.d(TAG, tagg);
                        break;
                    case LOG_SEVERITY_I:
                        Log.i(TAG, tagg);
                        break;
                    case LOG_SEVERITY_W:
                        Log.w(TAG, tagg);
                        break;
                    case LOG_SEVERITY_E:
                        Log.e(TAG, tagg);
                        break;
                }
                break;
            case Constants.LOG_METHOD_FILELOGGER:
                switch (logSeverity) {
                    case LOG_SEVERITY_V:
                        FL.v(TAG, tagg);
                        break;
                    case LOG_SEVERITY_D:
                        FL.d(TAG, tagg);
                        break;
                    case LOG_SEVERITY_I:
                        FL.i(TAG, tagg);
                        break;
                    case LOG_SEVERITY_W:
                        FL.w(TAG, tagg);
                        break;
                    case LOG_SEVERITY_E:
                        FL.e(TAG, tagg);
                        break;
                }
                break;
        }
    }
}
