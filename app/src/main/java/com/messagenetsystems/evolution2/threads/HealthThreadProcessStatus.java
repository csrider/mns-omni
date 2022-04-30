package com.messagenetsystems.evolution2.threads;

/* HealthThreadProcessStatus
 * Thread to monitor the main list of ProcessStatus items.
 *
 * This is so we can have one central registry/place for that status of processes and this single
 * thread, instead of many multiple monitoring threads which seem prone to messing up the system.
 *
 * Revisions:
 *  2020.07.26-27   Chris Rider     Created (using HealthThreadHeartbeat as a template).
 *  2020.07.29      Chris Rider     Updated and improved nominal-code retrieval and analysis.
 *  //TODO: something about running this causes an ANR after random times, and disabling this results in very stable app.. so need to figure that out.. something is running on main thread?
 *  2020.09.28      Chris Rider     Fixed theoretical potential for uncaught overflow in loop counter.
 */

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.OmniApplication;
import com.messagenetsystems.evolution2.models.ProcessStatus;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;


public class HealthThreadProcessStatus extends Thread {
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




    /** Constructor */
    public HealthThreadProcessStatus(Context appContext, byte logMethod, Handler parentProcessHandler, int frequencySecs) {
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
        this.activeProcessingSleepDuration = (frequencySecs * 1000);
        this.pausedProcessingSleepDuration = 4500;
        this.loopIterationCounter = 0;


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

                    for (ProcessStatus processStatus : omniApplication.processStatusList) {

                        // Check child process count with what is expected
                        // Note: Need to do this here, so we have access to entire list to search.
                        List<ProcessStatus> childrenProcesses = getChildProcessStatusObjects(processStatus);
                        if (childrenProcesses.size() < processStatus.getProcessNumberOfExpectedChildren()) {
                            logW(TAGG+"Process \""+processStatus.getProcessClassName()+"\" has counted fewer children ("+childrenProcesses.size()+") than expected ("+processStatus.getProcessNumberOfExpectedChildren()+").");
                            //TODO
                        }

                        // Check nominal status
                        // Note: This method may return any number of different codes, so need to loop through it to get them all.
                        switch (processStatus.getNominalStatus()) {
                            case ProcessStatus.NOMINAL_TRUE:
                                logI(TAGG+"Process \""+processStatus.getProcessClassName()+"\" is nominal. No remedy/action needed.");
                                processStatus.setProcessActionNeededFlag(ProcessStatus.ACTION_NONE);
                                break;
                            case ProcessStatus.NOMINAL_FALSE:
                                logI(TAGG+"Process \""+processStatus.getProcessClassName()+"\" is NOT nominal (specific reason not provided). No remedy/action needed.");
                                processStatus.setProcessActionNeededFlag(ProcessStatus.ACTION_NONE);    //TODO: is this proper? restart it?
                                break;
                            case ProcessStatus.NOMINAL_FALSE_FAULTY_HEARTBEAT:
                                logI(TAGG+"Process \""+processStatus.getProcessClassName()+"\" is NOT nominal (faulty heartbeat).");
                                //TODO (how to handle faulty/slow heartbeat?)
                                break;
                            case ProcessStatus.NOMINAL_FALSE_MAX_DESIRED_RUNTIME:
                                logI(TAGG+"Process \""+processStatus.getProcessClassName()+"\" is NOT nominal (longer than desired runtime).");
                                processStatus.setProcessActionNeededFlag(ProcessStatus.ACTION_RESTART);
                                break;
                            case ProcessStatus.NOMINAL_FALSE_MAX_REQUIRED_RUNTIME:
                                logI(TAGG+"Process \""+processStatus.getProcessClassName()+"\" is NOT nominal (longer than required runtime).");
                                processStatus.setProcessActionNeededFlag(ProcessStatus.ACTION_RESTART);
                                break;
                            case ProcessStatus.NOMINAL_UNKNOWN:
                            default:
                                logW(TAGG+"Process \""+processStatus.getProcessClassName()+"\" has an unhandled nominal-code: "+Byte.toString(processStatus.getNominalStatus()));
                        }

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

    private int getChildCount(ProcessStatus processStatusObj) {
        final String TAGG = "updateChildCount: ";
        int ret = 0;

        try {
            String processName = processStatusObj.getProcessClassName();

            // Search all items for a parent name that matches the specified process name
            for (ProcessStatus processStatus : omniApplication.processStatusList) {
                if (processStatus.getProcessParentClassName().equals(processName)) {
                    ret++;
                }
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning: "+ret);
        return ret;
    }

    private List<ProcessStatus> getChildProcessStatusObjects(ProcessStatus processStatusObj) {
        final String TAGG = "updateChildCount: ";
        List<ProcessStatus> ret = new ArrayList<>();

        try {
            String processName = processStatusObj.getProcessClassName();

            // Search all items for a parent name that matches the specified process name
            for (ProcessStatus processStatus : omniApplication.processStatusList) {
                if (processStatus.getProcessParentClassName().equals(processName)) {
                    ret.add(processStatus);
                }
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning list of "+ret.size()+" ProcessStatus objects that are children of \""+processStatusObj.getProcessClassName()+"\".");
        return ret;
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
