package com.messagenetsystems.evolution2.services;

/* MessageService
 * This is the primary service responsible for hosting any and all threads necessary for processing our
 * raw DB message data. This process tree is limited to working with message DATA (and not actual delivery tasks).
 *
 * Child Threads:
 *  MessageRawDataProcessor         Processes/syncs raw message data in "messages" DB --> RAM (MainService.omniRawMessages), and performs "messages" DB housekeeping.
 *  MessageDeliverableProcessor     Processes the raw data in RAM and syncs to deliverable RAM list so Delivery processes can work with it.
 *  MonitorChildProcesses           Monitoring & restart-thread for the above threads.
 *
 * Being a service, we should naturally be more resilient against Android's task-killer and can thus
 * keep delivery activities going even if the data-feeder threads are killed, network goes down, etc.
 *
 * This service is hosted by, and lives under MainService.
 *
 * Revisions:
 *  2019.12.09-11   Chris Rider     Created.
 *  2019.12.17      Chris Rider     Updated OmniMessageLoaderHandler to OmniMessageRawHandler and to accept command-request arg.
 *  2019.12.20      Chris Rider     Implemented handler from MainService for deliverable OmniMessages.
 *  2020.01.02      Chris Rider     Accommodated renamed classes and made things more clear as to what is going on.
 *  2020.01.03      Chris Rider     Migrated from MainService Handler/Message stuff to LocalBroadcast methodology --no longer need to instantiate with Handler that's in MainService!
 *  2020.01.20      Chris Rider     Refactored to no longer host any data, just child threads. Data moved to MainService.
 *  2020.01.24      Chris Rider     Added thread to monitor child processes and methods to restart them as needed.
 *  2020.01.31      Chris Rider     Cleanup comments and stuff to be up-to-date with actual latest state of the code.
 *  2020.08.05      Chris Rider     Moved child threads into doStartThread so we can control their priority (and set it to minimum).
 *  2020.08.07      Chris Rider     Added thread-ID acquisition and output to notification.
 *  2020.09.28      Chris Rider     Fixed theoretical potential for uncaught overflow in loop counter.
 */

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.OmniApplication;
import com.messagenetsystems.evolution2.threads.MessageRawDataProcessor;
import com.messagenetsystems.evolution2.threads.MessageDeliverableProcessor;
import com.messagenetsystems.evolution2.utilities.ThreadUtils;

import java.lang.ref.WeakReference;
import java.util.Date;

public class MessageService extends Service {
    private final String TAG = this.getClass().getSimpleName();

    // Constants...


    // Logging stuff...
    private final int LOG_SEVERITY_V = 1;
    private final int LOG_SEVERITY_D = 2;
    private final int LOG_SEVERITY_I = 3;
    private final int LOG_SEVERITY_W = 4;
    private final int LOG_SEVERITY_E = 5;
    private int logMethod = Constants.LOG_METHOD_LOGCAT;

    // Local stuff...
    private WeakReference<Context> appContextRef;                                                   //since this thread is very long running, we prefer a weak context reference
    private OmniApplication omniApplication;
    public volatile boolean hasFullyStarted;
    private MonitorChildProcesses monitorChildProcesses;
    public volatile boolean isThreadAlive_raw, isThreadAlive_deliverable;

    private int tid = 0;

    private MessageRawDataProcessor messageRawDataProcessor;                                        //Thread for loading in message data from database, as well as syncing it with raw RAM object (omniRawMessages)
    private MessageDeliverableProcessor messageDeliverableProcessorThread;                          //Thread for controlling the feeding of message data toward actual delivery of messages


    /** Constructor */
    public MessageService(Context appContext) {
        super();
    }
    public MessageService() {
    }


    /*============================================================================================*/
    /* Service methods */

    /** Service onCreate handler **/
    @Override
    public void onCreate() {
        super.onCreate();
        final String TAGG = "onCreate: ";
        logV(TAGG+"Invoked.");

        this.appContextRef = new WeakReference<Context>(getApplicationContext());

        this.logMethod = Constants.LOG_METHOD_FILELOGGER;

        try {
            this.omniApplication = ((OmniApplication) getApplicationContext());
        } catch (Exception e) {
            logE("Exception caught instantiating "+TAG+": "+e.getMessage());
            return;
        }

        this.hasFullyStarted = false;

        // Instantiate our custom Handler and pass its reference to our MessageRawDataProcessor instance...
        this.messageRawDataProcessor = new MessageRawDataProcessor(getApplicationContext(), logMethod);

        // Instantiate our custom Handler and pass its reference to our MessageDeliveryControllerThread instance...
        this.messageDeliverableProcessorThread = new MessageDeliverableProcessor(getApplicationContext(), logMethod);

        // Initialize our monitoring process
        this.monitorChildProcesses = new MonitorChildProcesses();
    }

    /** Service onBind handler **/
    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        //throw new UnsupportedOperationException("Not yet implemented");
        return null;
    }

    /** Service onStart handler **/
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        final String TAGG = "onStartCommand: ";
        logI(TAGG+"Service starting...");

        // Running in foreground better ensures Android won't kill us   //TODO: evaluate whether this is proper for this class or not?
        startForeground(0, null);

        tid = android.os.Process.myTid();

        // Start any threads
        ThreadUtils.doStartThread(getBaseContext(), messageRawDataProcessor, ThreadUtils.SPAWN_NEW_THREAD_TRUE, ThreadUtils.PRIORITY_MINIMUM);
        ThreadUtils.doStartThread(getBaseContext(), messageDeliverableProcessorThread, ThreadUtils.SPAWN_NEW_THREAD_TRUE, ThreadUtils.PRIORITY_MINIMUM);

        while (!this.messageRawDataProcessor.isThreadRunning() && !this.messageDeliverableProcessorThread.isThreadRunning()) {
            //wait here while threads start up
            logV(TAGG+"Waiting for child threads to start.");

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logW(TAGG + "Exception caught trying to sleep: " + e.getMessage());
            }
        }

        // Update flag that we appear healthy
        this.hasFullyStarted = true;

        // Update notification that everything is started and running
        omniApplication.appendNotificationWithText(TAG+" started. (tid:"+tid+")");

        // Start our child-monitoring process
        ThreadUtils.doStartThread(getBaseContext(), monitorChildProcesses, ThreadUtils.SPAWN_NEW_THREAD_TRUE, ThreadUtils.PRIORITY_MINIMUM);

        // Ensure this service is very hard to kill and that it even restarts if needed
        //return START_STICKY;      //NOTE: not necessary since this service lives under MainService (which itself starts sticky)???
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        // This gets invoked when the app is killed either by Android or the user.
        // To absolutely ensure it gets invoked, it's best-practice to call stopService somewhere if you can.
        final String TAGG = "onDestroy: ";
        logV(TAGG+"Invoked.");

        // Update notification so we can know something went wrong if it wasn't supposed to
        // (a legit stop will then reset the notification so you don't get a false positive)
        omniApplication.appendNotificationWithText("MessageService died! ("+new Date().toString()+")");

        // Explicitly release variables (not strictly necessary, but can't hurt to force garbage collection)
        this.omniApplication = null;

        // Clean up anything else
        if (this.monitorChildProcesses != null) {
            this.monitorChildProcesses.cleanup();
            this.monitorChildProcesses = null;
        }
        if (this.messageRawDataProcessor != null) {
            this.messageRawDataProcessor.cleanup();
            this.messageRawDataProcessor = null;
        }
        if (this.messageDeliverableProcessorThread != null) {
            this.messageDeliverableProcessorThread.cleanup();
            this.messageDeliverableProcessorThread = null;
        }
        if (this.appContextRef != null) {
            this.appContextRef.clear();
            this.appContextRef = null;
        }

        super.onDestroy();
    }


    /*============================================================================================*/
    /* Utility Methods */

    private void restartThread_raw() {
        final String TAGG = "restartThread_raw: ";
        logV(TAGG+"Trying to restart MessageRawDataProcessor...");

        int maxWaitForStart = 10;

        try {
            if (this.messageRawDataProcessor != null) {
                this.messageRawDataProcessor.cleanup();
            }

            this.messageRawDataProcessor = new MessageRawDataProcessor(appContextRef.get(), logMethod);
            this.messageRawDataProcessor.start();

            while (!this.messageRawDataProcessor.isThreadRunning()) {
                //wait here while thread starts up
                logV(TAGG+"Waiting for thread to start.");

                maxWaitForStart--;
                if (maxWaitForStart < 0) {
                    break;
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logW(TAGG + "Exception caught trying to sleep: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }
    }

    private void restartThread_deliverable() {
        final String TAGG = "restartThread_deliverable: ";
        logV(TAGG+"Trying to restart MessageDeliverableProcessor...");

        int maxWaitForStart = 10;

        try {
            if (this.messageDeliverableProcessorThread != null) {
                this.messageDeliverableProcessorThread.cleanup();
            }

            this.messageDeliverableProcessorThread = new MessageDeliverableProcessor(appContextRef.get(), logMethod);
            this.messageDeliverableProcessorThread.start();

            while (!this.messageDeliverableProcessorThread.isThreadRunning()) {
                //wait here while thread starts up
                logV(TAGG+"Waiting for thread to start.");

                maxWaitForStart--;
                if (maxWaitForStart < 0) {
                    break;
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logW(TAGG + "Exception caught trying to sleep: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }
    }


    /*============================================================================================*/
    /* Subclasses */

    /** Thread to monitor child processes, and restart them if necessary.
     * WARNING: You should start this thread only after you're sure the monitored processes have started! */
    private class MonitorChildProcesses extends Thread {
        private final String TAGG = MonitorChildProcesses.class.getSimpleName()+": ";

        private volatile boolean isStopRequested;           //flag to set/check for the thread to interrupt itself
        private volatile boolean isThreadRunning;           //just a status flag
        private volatile boolean pauseProcessing;           //flag to set if you want to pause processing work (may double as a kind of "is paused" flag)

        private int activeProcessingSleepDuration;          //duration (in milliseconds) to sleep during active processing (to help ensure CPU cycles aren't eaten like crazy)
        private int pausedProcessingSleepDuration;          //duration (in milliseconds) to sleep during paused processing (to help ensure CPU cycles aren't eaten like crazy)

        private long loopIterationCounter;

        /** Constructor */
        public MonitorChildProcesses() {
            // Initialize values
            this.isStopRequested = false;
            this.isThreadRunning = false;
            this.pauseProcessing = false;
            this.activeProcessingSleepDuration = 1000;
            this.pausedProcessingSleepDuration = 10000;
            this.loopIterationCounter = 1;
        }

        /** Main runnable routine... executes once whenever the initialized thread is commanded to start running with .start() or .execute() method call.
         * Remember that .start() implicitly spawns a thread and calls .execute() to invoke this run() method.
         * If you directly call .run(), this run() method will invoke on the same thread you call it from. */
        @Override
        public void run() {
            final String TAGG = this.TAGG+"run: ";
            logV(TAGG + "Invoked.");

            // As long as our thread is supposed to be running...
            while (!Thread.currentThread().isInterrupted()) {

                // Our thread has started or is still running
                isThreadRunning = true;

                // Either do nothing (if paused) or allow work to happen (if not paused)...
                logV(TAGG + "-------- Iteration #" + loopIterationCounter + " ------------------------");
                if (pauseProcessing) {
                    doSleepPaused();
                    logD(TAGG + "Processing is paused. Thread continuing to run, but no work is occurring.");
                } else {
                    // Do a short delay to help prevent the thread loop from eating cycles
                    doSleepActive();

                    try {
                        ////////////////////////////////////////////////////////////////////////////////
                        // DO THE BULK OF THE ACTUAL WORK HERE...

                        if (!messageRawDataProcessor.isAlive()) {
                            isThreadAlive_raw = false;
                            logW(TAGG+"MessageRawDataProcessor is not alive! Restarting it...");
                            restartThread_raw();
                        } else {
                            isThreadAlive_raw = true;
                        }

                        if (!messageDeliverableProcessorThread.isAlive()) {
                            isThreadAlive_deliverable = false;
                            logW(TAGG+"MessageDeliverableProcessorThread is not alive! Restarting it...");
                            restartThread_deliverable();
                        } else {
                            isThreadAlive_deliverable = true;
                        }

                        // END THE BULK OF THE ACTUAL WORK HERE...
                        ////////////////////////////////////////////////////////////////////////////////
                    } catch (Exception e) {
                        logE(TAGG+"Exception caught: "+e.getMessage());
                    }
                }

                doCounterIncrement();

                // this is the end of the loop-iteration, so check whether we will stop or continue
                if (doCheckWhetherNeedToStop()) {
                    isThreadRunning = false;
                    break;
                }
            }//end while
        }//end run()

        private void doSleepPaused() {
            final String TAGG = this.TAGG+"doSleepPaused: ";

            try {
                Thread.sleep(pausedProcessingSleepDuration);
            } catch (InterruptedException e) {
                logW(TAGG + "Exception caught trying to sleep during pause: " + e.getMessage());
            }
        }

        private void doSleepActive() {
            final String TAGG = this.TAGG+"doSleepActive: ";

            try {
                Thread.sleep(activeProcessingSleepDuration);
            } catch (InterruptedException e) {
                logW(TAGG + "Exception caught trying to sleep: " + e.getMessage());
            }
        }

        private void doCounterIncrement() {
            final String TAGG = this.TAGG+"doCounterIncrement: ";

            try {
                if (loopIterationCounter + 1 < Long.MAX_VALUE)
                    loopIterationCounter++;
                else
                    loopIterationCounter = 1;
            } catch (Exception e) {
                logW(TAGG+"Exception caught incrementing loop counter. Resetting to 1: "+e.getMessage());
                loopIterationCounter = 1;
            }
        }

        private boolean doCheckWhetherNeedToStop() {
            final String TAGG = this.TAGG+"doCheckWhetherNeedToStop: ";
            boolean ret = false;

            try {
                if (Thread.currentThread().isInterrupted()) {
                    logI(TAGG + "Thread will now stop.");
                    isThreadRunning = false;
                }
                if (isStopRequested) {
                    logI(TAGG + "Thread has been requested to stop and will now do so.");
                    isThreadRunning = false;
                    ret = true;
                }
            } catch (Exception e) {
                logE(TAGG+"Exception caught: "+e.getMessage());
            }

            return ret;
        }

        /** Call this to terminate the loop and release resources. */
        public void cleanup() {
            final String TAGG = "cleanup: ";

            try {
                this.isStopRequested = true;

                // Note: At this point, the thread-loop should break on its own
            } catch (Exception e) {
                logE(TAGG+"Exception caught calling stopListening(): "+e.getMessage());
            }
        }
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
