package com.messagenetsystems.evolution2.threads;

/* HealthThreadHeartbeat
 * Thread to broadcast a regular heartbeat so other processes can know our app is alive and kicking.
 * Note: HealthService is started under MainService (which is started on the main thread), so we should inherit main thread.
 *
 * Usage Example (declare, create, configure, and run):
 *  HealthThreadHeartbeat healthThreadHeartbeat;
 *  healthThreadHeartbeat = new HealthThreadHeartbeat(getApplicationContext(), Constants.LOG_METHOD_FILELOGGER);
 *  healthThreadHeartbeat.start();
 *
 * Usage Example (stop the thread-loop and free up resources):
 *  healthThreadHeartbeat.cleanup();
 *
 * Usage Example (pause processing - may be easily resumed later)
 *  healthThreadHeartbeat.pauseProcessing();
 *
 * Usage Example (resume processing)
 *  healthThreadHeartbeat.resumeProcessing();
 *
 * Usage Example (send Android-msg with data to DeliveryService)
 *
 * Revisions:
 *  2020.06.03      Chris Rider     Created (using HealthThreadStorage as a template).
 *  2020.07.26      Chris Rider     Added ProcessStatus monitoring. Changed logging INT to BYTE. Decreased sleep duration to 4500ms (though, unused).
 *  2020.09.28      Chris Rider     Fixed theoretical potential for uncaught overflow in loop counter.
 */

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.OmniApplication;
import com.messagenetsystems.evolution2.models.ProcessStatus;

import java.lang.ref.WeakReference;
import java.util.Date;


public class HealthThreadHeartbeat extends Thread {
    private final String TAG = this.getClass().getSimpleName();

    // Constants..


    // Logging stuff...
    private final byte LOG_SEVERITY_V = 1;
    private final byte LOG_SEVERITY_D = 2;
    private final byte LOG_SEVERITY_I = 3;
    private final byte LOG_SEVERITY_W = 4;
    private final byte LOG_SEVERITY_E = 5;
    private byte logMethod = Constants.LOG_METHOD_LOGCAT;

    // Local stuff...
    private WeakReference<Context> appContextRef;       //since this thread is very long running, we prefer a weak context reference
    private OmniApplication omniApplication;
    private Handler androidMsgHandler_HealthService;    //reference to HealthService's message-handler, so we can send data to there

    private volatile boolean isStopRequested;           //flag to set/check for the thread to interrupt itself
    private volatile boolean isThreadRunning;           //just a status flag
    private volatile boolean pauseProcessing;           //flag to set if you want to pause processing work (may double as a kind of "is paused" flag)

    private int activeProcessingSleepDuration;          //duration (in milliseconds) to sleep during active processing (to help ensure CPU cycles aren't eaten like crazy)
    private int pausedProcessingSleepDuration;          //duration (in milliseconds) to sleep during paused processing (to help ensure CPU cycles aren't eaten like crazy)

    private long loopIterationCounter;

    private Intent heartbeatIntent;


    /** Constructor */
    public HealthThreadHeartbeat(Context appContext, byte logMethod, Handler parentProcessHandler, int frequencySecs) {
        Log.v(TAG, "Instantiating.");

        this.logMethod = logMethod;

        this.appContextRef = new WeakReference<>(appContext);

        try {
            this.omniApplication = ((OmniApplication) appContext);
        } catch (Exception e) {
            logE("Exception caught instantiating "+TAG+": "+e.getMessage());
            return;
        }

        // Get our handlers from parents, so we can send Android-Messages back to them
        this.androidMsgHandler_HealthService = parentProcessHandler;                                //get our handler from HealthService

        // Setup process monitoring
        omniApplication.processStatusList.addAndRegisterProcess(
                ProcessStatus.PROCESS_TYPE_THREAD,
                this.getClass());
        omniApplication.processStatusList.setMaxHeartbeatIntervalForProcess(this.getClass(), frequencySecs*1000);

        // Initialize values
        this.isStopRequested = false;
        this.isThreadRunning = false;
        this.pauseProcessing = false;
        this.activeProcessingSleepDuration = frequencySecs * 1000;
        this.pausedProcessingSleepDuration = 4500;
        this.loopIterationCounter = 0;

        // Initialize our intent that we'll broadcast
        this.heartbeatIntent = new Intent();
        this.heartbeatIntent.setAction(Constants.Intents.Actions.REGISTER_MAIN_APP_HEARTBEAT);
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
        int tid = android.os.Process.myTid();      //can be used with setThreadOriority, etc.
        logI(TAGG+"Thread starting as Java process ID #"+ pid +" (OS thread ID #"+tid+")");
        if (tid == pid) {
            logW(TAGG+"WARNING: Thread has started in the main thread! Are you sure that's right?");
        }

        long nowTimeMS;

        // Inform process monitor that we have started
        omniApplication.processStatusList.recordProcessStart(this.getClass(), tid);

        // As long as our thread is supposed to be running...
        while (!Thread.currentThread().isInterrupted()) {

            // Our thread has started or is still running
            isThreadRunning = true;

            // Inform process monitor that we are still running
            omniApplication.processStatusList.recordProcessHeartbeat(this.getClass());

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

                    nowTimeMS = new Date().getTime();
                    logV(TAGG+"Broadcasting heartbeat ("+Long.toString(nowTimeMS)+")...");
                    heartbeatIntent.putExtra(Constants.Intents.ExtrasKeys.APP_STARTED_DATE_MS, omniApplication.getAppStartedDate().getTime());
                    heartbeatIntent.putExtra(Constants.Intents.ExtrasKeys.NOW_DATE_MS, nowTimeMS);
                    appContextRef.get().getApplicationContext().sendBroadcast(heartbeatIntent);

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

        omniApplication.processStatusList.recordProcessStop(this.getClass());
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

    /** Populate an Android-Message object with data and send it to parent process' Handler.
     */
    private void sendMessageToParentService(int actionToRequest, Object dataToSend) {
        final String TAGG = "sendMessageToParentService: ";

        // Get our handler's message object so we can populate it with our DB data
        android.os.Message androidMessage = androidMsgHandler_HealthService.obtainMessage();

        // Send what we're wanting the handler to do
        androidMessage.arg1 = actionToRequest;

        // Supply the provided data object to the handler
        androidMessage.obj = dataToSend;

        // Actually send the Android-message (with OmniMessage object) back to DeliveryService's handler
        androidMsgHandler_HealthService.sendMessage(androidMessage);
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
    private void log(byte logSeverity, String tagg) {
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
