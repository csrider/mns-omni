package com.messagenetsystems.evolution2.threads;

/* DeliveryQueueProcessor
 * Using deliverableOmniMessages in MainService, manage the delivery queue-lists (what rotator then uses).
 *
 * Usage Example (declare, create, configure, and run):
 *  DeliveryQueueProcessor deliveryQueueProcessor;
 *  deliveryQueueProcessor = new DeliveryQueueProcessor(getApplicationContext(), DeliveryQueueProcessor.LOG_METHOD_FILELOGGER);
 *  deliveryQueueProcessor.start();
 *
 * Usage Example (stop the thread-loop and free up resources):
 *  deliveryQueueProcessor.cleanup();
 *
 * Usage Example (pause processing - may be easily resumed later)
 *  deliveryQueueProcessor.pauseProcessing();
 *
 * Usage Example (resume processing)
 *  deliveryQueueProcessor.resumeProcessing();
 *
 * Idea...
 *  On thread start, initially load main for-delivery list with exact copy of deliverables.
 *  Each thread iteration, check for new deliverables and add them to new-for-delivery list.
 *  Each thread iteration, check for removed deliverables and remove them from both for-delivery lists.
 *
 * Revisions:
 *  2020.01.06-09   Chris Rider     Created.
 *  2020.09.28      Chris Rider     Fixed theoretical potential for uncaught overflow in loop counter.
 */

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.models.OmniMessage;
import com.messagenetsystems.evolution2.services.DeliveryService;

import java.lang.ref.WeakReference;


public class DeliveryQueueProcessor extends Thread {
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

    private Handler androidMsgHandler_DeliveryService;  //reference to DeliveryService's OmniMessageRawHandler, so we can send data to there

    private volatile boolean isStopRequested;           //flag to set/check for the thread to interrupt itself
    private volatile boolean isThreadRunning;           //just a status flag
    private volatile boolean pauseProcessing;           //flag to set if you want to pause processing work (may double as a kind of "is paused" flag)

    private int activeProcessingSleepDuration;          //duration (in milliseconds) to sleep during active processing (to help ensure CPU cycles aren't eaten like crazy)
    private int pausedProcessingSleepDuration;          //duration (in milliseconds) to sleep during paused processing (to help ensure CPU cycles aren't eaten like crazy)

    private long loopIterationCounter;


    /** Constructor */
    public DeliveryQueueProcessor(Context appContext, int logMethod, Handler deliveryServiceHandler) {
        Log.v(TAG, "Instantiating.");

        this.logMethod = logMethod;

        this.appContextRef = new WeakReference<Context>(appContext);

        // Get our handlers from parents, so we can send Android-Messages back to them
        this.androidMsgHandler_DeliveryService = deliveryServiceHandler;                            //get our handler from DeliveryService

        // Initialize values
        this.isStopRequested = false;
        this.isThreadRunning = false;
        this.pauseProcessing = false;
        this.activeProcessingSleepDuration = 1000;
        this.pausedProcessingSleepDuration = 5000;
        this.loopIterationCounter = 1;


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

        // Command the DeliveryService to do an initial load of the main delivery list
        sendCommandToDeliveryService(DeliveryService.MSGHANDLER_ACTION_LIST_LOAD, null);

        // As long as our thread is supposed to be running...
        while (!Thread.currentThread().isInterrupted()) {

            // Our thread has started or is still running
            isThreadRunning = true;

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

                    // Command DeliveryService to check for new MainService deliverables and add them to new delivery list
                    sendCommandToDeliveryService(DeliveryService.MSGHANDLER_ACTION_LIST_SYNC, null);

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

            try {
                if (loopIterationCounter + 1 < Long.MAX_VALUE)
                    loopIterationCounter++;
                else
                    loopIterationCounter = 1;
            } catch (Exception e) {
                logW(TAGG+"Exception caught incrementing loop counter. Resetting to 1: "+e.getMessage());
                loopIterationCounter = 1;
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
        if (omniMessage != null) {
            androidMessage.obj = omniMessage;
        }

        // Actually send the Android-message (with OmniMessage object) back to DeliveryService's handler
        logV(TAGG+"Sending Android-message ("+actionToRequest+") to DeliveryService...");
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
