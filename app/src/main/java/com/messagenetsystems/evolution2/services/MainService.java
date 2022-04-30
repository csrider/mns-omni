package com.messagenetsystems.evolution2.services;

/* MainService
 * Hosts the basic main app services, threads, processes, etc.
 * This is supposed to do everything it can to run as long as possible with minimal threat of being killed.
 *
 * CAUTION:
 * This Service is started from StartupActivity, which may mean that it is likely tied to the UI thread.
 * So to be safe against hogging the UI thread, you should avoid doing anything long/blocking in this Service.
 * Instead, if you want to do long-running or blocking stuff, you should spawn a new background thread here to do it in.
 *
 * Revisions:
 *  2019.11.15      Chris Rider     Created.
 *  2019.11.25      Chris Rider     Added processing thread for received-requests.
 *  2019.11.26      Chris Rider     Added forwarding thread for processed received-requests.
 *  2019.12.03      Chris Rider     Added message governor thread for doing stuff with messages.
 *  2019.12.06      Chris Rider     Added calls to load and set ecosystem value.
 *  2019.12.09      Chris Rider     Added MessageService.
 *                                  Moved MessageRawDataProcessor out of here to MessageService.
 *  2019.12.10      Chris Rider     Rethinking threading and UI-thread avoidance. This Service should not do anything intense.
 *                                  Added DeliveryService.
 *  2019.12.11      Chris Rider     Accommodated received_requests DB refactoring along with merging of ReceivedRequestForwarder with ReceivedRequestProcessor.
 *  2019.12.16      Chris Rider     Moved TextToSpeechServicer to DeliveryService.
 *  2019.12.20      Chris Rider     Added OmniMessages object to act as primary RAM datastore for currently-active/deliverable messages.
 *                                  Added OmniMessageHandler for initially accepting data from MessageService and DeliveryService.
 *  2020.01.03      Chris Rider     Migrated all Handler/Message communication over to BroadcastReceiver methodology.
 *  2020.01.20      Chris Rider     Refactored OmniRawMessages data-store from MessageService to static (here), and no longer need complicated Message/Handler stuff in child threads.
 *  2020.01.24      Chris Rider     Added thread to monitor child services' processes and restart all processes if needed.
 *  2020.02.19      Chris Rider     Improved Service and notification usage, to try to fix dead-app not restarting.
 *  2020.04.23      Chris Rider     Implemented IncomingCallService_AJVoIP Service (need to finish child process monitoring, though).
 *  2020.05.12      Chris Rider     Added OmniStatusBarThread thread.
 *  2020.05.14      Chris Rider     Added new HealthService.
 *  2020.07.08      Chris Rider     Added new ButtonService.
 *  2020.07.20      Chris Rider     Added new FlasherLightService.
 *  2020.07.27      Chris Rider     Updated notification to show process ID and thread ID.
 *  2020.07.28      Chris Rider     Changed logging INT to BYTE
 *  2020.08.11      Chris Rider     Reduced some thread priorities
 *  2020.09.24      Chris Rider     Added monitoring and restart of threads, SocketServerThread, ReceivedRequestProcessor, and ReceivedMessageProcessor
 *  2020.09.28      Chris Rider     Fixed theoretical potential for uncaught overflow in loop counter.
 */

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.OmniApplication;
import com.messagenetsystems.evolution2.models.OmniMessages;
import com.messagenetsystems.evolution2.models.OmniRawMessages;
import com.messagenetsystems.evolution2.receivers.MainServiceStoppedReceiver;
import com.messagenetsystems.evolution2.threads.OmniStatusBarThread;
import com.messagenetsystems.evolution2.threads.ReceivedMessageProcessor;
import com.messagenetsystems.evolution2.threads.ReceivedRequestProcessor;
import com.messagenetsystems.evolution2.threads.SocketServerThread;
import com.messagenetsystems.evolution2.utilities.SharedPrefsUtils;
import com.messagenetsystems.evolution2.utilities.ThreadUtils;

import java.lang.ref.WeakReference;

import static com.messagenetsystems.evolution2.utilities.ThreadUtils.SPAWN_NEW_THREAD_TRUE;
import static com.messagenetsystems.evolution2.utilities.ThreadUtils.SPAWN_NEW_THREAD_FALSE;
import static com.messagenetsystems.evolution2.utilities.ThreadUtils.PRIORITY_MINIMUM;
import static com.messagenetsystems.evolution2.utilities.ThreadUtils.PRIORITY_LOW;
import static com.messagenetsystems.evolution2.utilities.ThreadUtils.PRIORITY_NORMAL;
import static com.messagenetsystems.evolution2.utilities.ThreadUtils.PRIORITY_HIGH;
import static com.messagenetsystems.evolution2.utilities.ThreadUtils.PRIORITY_MAXIMUM;
import static com.messagenetsystems.evolution2.utilities.ThreadUtils.doStartService;
import static com.messagenetsystems.evolution2.utilities.ThreadUtils.doStartThread;

public class MainService extends Service {
    private final String TAG = this.getClass().getSimpleName();

    // Constants...


    // Logging stuff...
    private final byte LOG_SEVERITY_V = 1;
    private final byte LOG_SEVERITY_D = 2;
    private final byte LOG_SEVERITY_I = 3;
    private final byte LOG_SEVERITY_W = 4;
    private final byte LOG_SEVERITY_E = 5;
    private byte logMethod = Constants.LOG_METHOD_LOGCAT;

    // Local stuff...
    private WeakReference<Context> appContextRef;                                                   //since this thread is very long running, we prefer a weak context reference
    private OmniApplication omniApplication;
    private long appPID;
    private int tid;

    // RAM data-store (raw DB data)...
    // This is intended to be a RAM-based version of the "messages" database.
    // This data will continuously reflect what's in the database, for nice and easy access.
    // Conversely, the database will continuously reflect what's in this list (flushed periodically).
    // It is kept synchronized with disk-data by the MessageService->MessageRawDataProcessor thread.
    // We do it this way, so as to reduce disk-I/O and thrashing the flash memory on the device.
    public static volatile OmniRawMessages omniRawMessages;

    // RAM data-store (deliverable messages, ready to process for delivery)...
    // Whatever is in this list will be used by DeliveryService->DeliveryRotator to do actual delivery.
    // This is a list of ALL current messages possibly eligible for delivery --all priorities, etc.
    // It is kept updated by MessageService->MessageDeliverableProcessor.
    public static volatile OmniMessages omniMessages_deliverable;

    private MonitorChildProcesses monitorChildProcesses;

    private DeliveryService deliveryService;
    private Intent deliveryServiceIntent;

    private MessageService messageService;
    private Intent messageServiceIntent;

    private SocketServerThread socketServerThread;
    private ReceivedRequestProcessor receivedRequestProcessor;
    private ReceivedMessageProcessor receivedMessageProcessor;

    private OmniStatusBarThread omniStatusBarThread;

    private IncomingCallService_AJVoIP incomingCallService_ajVoIP;
    private Intent incomingCallServiceAjvoipIntent;

    private HealthService healthService;
    private Intent healthServiceIntent;

    private ButtonService buttonService;
    private Intent buttonServiceIntent;

    private FlasherLightService flasherLightService;
    private Intent flasherLightServiceIntent;


    /** Constructor */
    public MainService(Context appContext) {
        super();
    }
    public MainService() {
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
        this.appPID = 0;

        try {
            this.omniApplication = ((OmniApplication) getApplicationContext());
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught instantiating "+TAG+": "+e.getMessage());
            return;
        }

        // Load and set the ecosystem value globally (loading is from shared-prefs for now, rather than some kind of intelligent auto-detect)
        // (DEV-NOTE: for now, all we need SharedPrefsUtils for is right here, but you can expand its usage to the rest of this class later if needed)
        int detectedEcosystem = omniApplication.loadEcosystem( new SharedPrefsUtils(getApplicationContext(), this.logMethod) );
        omniApplication.setEcosystem(detectedEcosystem);

        // Initialize our message lists
        omniRawMessages = new OmniRawMessages(getApplicationContext(), logMethod, OmniRawMessages.SYNC_DB_TRUE);
        omniMessages_deliverable = new OmniMessages(getApplicationContext(), logMethod);

        // Initialize our monitoring process
        this.monitorChildProcesses = new MonitorChildProcesses();

        // Prepare all our processes and threads
        // Note: Probably best to do this in some kind of reverse order according to data flow (ex. delivery infrastructure first, incoming socket data last)
        this.deliveryService = new DeliveryService(getApplicationContext());
        this.deliveryServiceIntent = new Intent(getApplicationContext(), DeliveryService.class);
        this.messageService = new MessageService(getApplicationContext());
        this.messageServiceIntent = new Intent(getApplicationContext(), messageService.getClass());
        this.incomingCallService_ajVoIP = new IncomingCallService_AJVoIP();
        this.incomingCallServiceAjvoipIntent = new Intent(getApplicationContext(), incomingCallService_ajVoIP.getClass());
        this.socketServerThread = new SocketServerThread(getApplicationContext(), this.logMethod, SocketServerThread.PORT_HTTP_NORMAL);
        this.receivedRequestProcessor = new ReceivedRequestProcessor(getApplicationContext(), this.logMethod);
        this.receivedMessageProcessor = new ReceivedMessageProcessor(getApplicationContext(), this.logMethod);
        this.omniStatusBarThread = new OmniStatusBarThread(getApplicationContext(), this.logMethod, null);
        this.healthService = new HealthService(getApplicationContext());
        this.healthServiceIntent = new Intent(this, HealthService.class);   //we start HealthService with MainService (this) as the parent
        this.buttonService = new ButtonService(getApplicationContext());
        this.buttonServiceIntent = new Intent(getApplicationContext(), ButtonService.class);
        this.flasherLightService = new FlasherLightService();
        this.flasherLightServiceIntent = new Intent(getApplicationContext(), FlasherLightService.class);
    }

    /** Service onBind handler
     * This invokes when you call bindService().
     * Returns an IBinder object that defines the programming interface that clients can use to interact with this service.
     *
     * When a client binds to this service by calling bindService(), it must provide an implementation of ServiceConnection (which monitors the connection with this service.
     * The return value of bindService() indicates whether the requested service exists and whether the client is permitted access to it.
     * When Android creates the connection, it calls onServiceConnected() on the ServiceConnection. That method includes an IBinder arg, which the client then uses to communicate with the bound service.
     **/
    @Override
    public IBinder onBind(Intent intent) {
        //throw new UnsupportedOperationException("Not yet implemented");
        //return binder;
        return null;
    }

    /** Service onStart handler
     * This invokes when you call startService(). **/
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        final String TAGG = "onStartCommand: ";
        logV(TAGG+"Invoked.");

        // Begin startup...
        omniApplication.replaceNotificationWithText("Starting MainService");
        startForeground(omniApplication.getmNotifID(), omniApplication.getmNotification());
        try {
            appPID = android.os.Process.myPid();
            tid = android.os.Process.myTid();       //note: for main thread, this will also equal appPID
        } catch (Exception e) {
            logE(TAGG+"Exception caught trying to get app process/thread IDs: "+e.getMessage());
        }

        // Set this thread's (main thread and all its children's) priorities...
        //  For the main service, we set OS priority to among the highest, if it's not already, (and that should be all we need to do to make sure the whole app is super high priority in the OS and unlikely to be killed).
        //  Then later, we set the Java-VM's priority for child threads to higher or lower priorities as needed (e.g. scrolling is higher priority, DB cleanup maybe lower).
        // Note: OS/Linux Process priority values are such that higher priority is more negative, and lower priority is more positive.
        // Note: You typically cannot set higher priority than -8 (but we somehow start at -10, so that's great if we always do)
        if (android.os.Process.getThreadPriority(tid) > Process.THREAD_PRIORITY_FOREGROUND) {
            logW("Thread priority (within Android) was low, but is now set to: "+ThreadUtils.setProcessPriority(Process.THREAD_PRIORITY_FOREGROUND));
        } else {
            logD("Thread priority (within Android) is already high: "+android.os.Process.getThreadPriority(tid));
        }

        ////////////////////////////////////////////////////////////////////////////////////////////
        // Start services...
        // Remember that any Service you start here will exist in the same thread as MainService (which is the main/UI thread), no matter what!
        // Note: Order may be important, depending on whether some services need to be going before other processes are. Pay attention to comments below and/or what may be made for each class.
        doStartService(this, deliveryServiceIntent);                //necessary for actual message delivery first, so it's all ready by the time any other processes might dispatch any potential messages
        doStartService(this, messageServiceIntent);                 //main message service (this is the main message dispatching/etc. guy! - prerequisites (like TTS) should already be loaded before this)
        doStartService(this, incomingCallServiceAjvoipIntent);      //SIP stack service
        doStartService(this, healthServiceIntent);                  //health service (explicitly want this to be part of MainService process so it is less likely to die - don't worry, its hard work is done by threads
        doStartService(this, buttonServiceIntent);                  //button service
        doStartService(this, flasherLightServiceIntent);            //flasher light service

        ////////////////////////////////////////////////////////////////////////////////////////////
        // Start threads
        doStartThread(this, socketServerThread, SPAWN_NEW_THREAD_TRUE, PRIORITY_LOW);            //socket server for receiving network requests
        doStartThread(this, receivedRequestProcessor, SPAWN_NEW_THREAD_TRUE, PRIORITY_LOW);      //processing thread for received requests
        doStartThread(this, receivedMessageProcessor, SPAWN_NEW_THREAD_TRUE, PRIORITY_LOW);      //processing thread for received messages (which are formed by received requests processor thread)
        doStartThread(this, omniStatusBarThread, SPAWN_NEW_THREAD_TRUE, PRIORITY_MINIMUM);          //thread for keeping our custom status bar up-to-date (version, battery, network status, etc.)

        // Start our child-monitoring process
        doStartThread(this, monitorChildProcesses, SPAWN_NEW_THREAD_TRUE, PRIORITY_MINIMUM);

        // Finish startup...
        logI(TAGG+"Service started.");
        omniApplication.replaceNotificationWithText("MainService started. (pid:"+appPID+"/tid:"+tid+")");
        return START_STICKY;                                                                        //ensure this service is very hard to keep killed and that it even restarts if needed
    }

    @Override
    public void onDestroy() {
        // This gets invoked when the app is killed either by Android or the user.
        // To absolutely ensure it gets invoked, it's best-practice to call stopService somewhere if you can.
        final String TAGG = "onDestroy: ";
        logV(TAGG+"Invoked.");

        // Update notification
        omniApplication.replaceNotificationWithText("MainService destroyed!");

        // Stop any services (you should take care of implicit cleanup in the Service class' onDestroy method)
        stopService(deliveryServiceIntent);
        stopService(messageServiceIntent);
        stopService(incomingCallServiceAjvoipIntent);
        stopService(healthServiceIntent);
        stopService(buttonServiceIntent);
        stopService(flasherLightServiceIntent);

        // Stop and cleanup any threads
        monitorChildProcesses.cleanup();
        socketServerThread.cleanup();
        receivedRequestProcessor.cleanup();
        receivedMessageProcessor.cleanup();
        omniStatusBarThread.cleanup();

        // Send our broadcast that we're about to die
        Intent broadcastIntent = new Intent(this, MainServiceStoppedReceiver.class);
        sendBroadcast(broadcastIntent);

        // Explicitly release variables (not strictly necessary, but can't hurt to force garbage collection)
        this.monitorChildProcesses = null;
        this.socketServerThread = null;
        this.receivedRequestProcessor = null;
        this.receivedMessageProcessor = null;
        this.omniApplication = null;
        this.messageService = null;
        this.messageServiceIntent = null;
        this.deliveryService = null;
        this.deliveryServiceIntent = null;
        this.incomingCallService_ajVoIP = null;
        this.incomingCallServiceAjvoipIntent = null;
        this.omniStatusBarThread = null;
        this.healthService = null;
        this.healthServiceIntent = null;
        this.buttonService = null;
        this.buttonServiceIntent = null;
        this.flasherLightService = null;
        this.flasherLightServiceIntent = null;

        // Clear up anything else
        if (omniRawMessages != null) {
            omniRawMessages.cleanup();
            omniRawMessages = null;
        }
        if (omniMessages_deliverable != null) {
            omniMessages_deliverable.cleanup();
            omniMessages_deliverable = null;
        }
        if (this.appContextRef != null) {
            this.appContextRef.clear();
            this.appContextRef = null;
        }

        super.onDestroy();
    }


    /*============================================================================================*/
    /* Subclasses */

    /** Thread to monitor child processes, and restart them if necessary. */
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
            this.activeProcessingSleepDuration = 5000;
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

                        if (!socketServerThread.isAlive()) {
                            logW(TAGG+"SocketServerThread is not alive! Restarting it...");
                            doStartThread(MainService.this, socketServerThread, SPAWN_NEW_THREAD_TRUE, PRIORITY_LOW);
                        }

                        if (!receivedRequestProcessor.isAlive()) {
                            logW(TAGG+"ReceivedRequestProcessor is not alive! Restarting it...");
                            doStartThread(MainService.this, receivedRequestProcessor, SPAWN_NEW_THREAD_TRUE, PRIORITY_LOW);
                        }

                        if (!receivedMessageProcessor.isAlive()) {
                            logW(TAGG+"ReceivedMessageProcessor is not alive! Restarting it...");
                            doStartThread(MainService.this, receivedMessageProcessor, SPAWN_NEW_THREAD_TRUE, PRIORITY_LOW);
                        }

                        //TODO: The checks below are somewhat limited, but should work for now. Later, make them more intelligent and perhaps only restart discrete services?

                        if (messageService != null) {
                            if (messageService.hasFullyStarted) {
                                // We check for all threads dead here, since each Service restarts discrete threads as needed...
                                // Meaning that if all threads are dead, then even the Service's monitor is not running.
                                if (!messageService.isThreadAlive_raw
                                        && !messageService.isThreadAlive_deliverable
                                        /* DEV-NOTE: Add additional thread checks here if you add more threads later */
                                        ) {
                                    logW(TAGG+"MessageService instance is available, but none of its threads are alive, despite it having its own child-process monitor. So, something seems majorly wrong. Restarting main/all services...");
                                    try {
                                        omniApplication.stopMainService();
                                    } catch (Exception e) {
                                        FL.e(TAG, TAGG+"Exception caught: "+e.getMessage());
                                    }
                                }
                            }
                        }

                        if (deliveryService != null) {
                            if (deliveryService.hasFullyStarted) {
                                // We check for all threads dead here, since each Service restarts discrete threads as needed...
                                // Meaning that if all threads are dead, then even the Service's monitor is not running.
                                if (!deliveryService.isThreadAlive_rotator
                                        && !deliveryService.isThreadAlive_queue
                                        /* DEV-NOTE: Add additional thread checks here if you add more threads later */
                                        ) {
                                    logW(TAGG+"DeliveryService instance is available, but none of its threads are alive, despite it having its own child-process monitor. So, something seems majorly wrong. Restarting main/all services...");
                                    try {
                                        omniApplication.stopMainService();
                                    } catch (Exception e) {
                                        FL.e(TAG, TAGG+"Exception caught: "+e.getMessage());
                                    }
                                }
                            }
                        }

                        if (incomingCallService_ajVoIP != null) {
                            //TODO
                        }

                        if (healthService != null) {
                            if (healthService.hasFullyStarted) {
                                // We check for all threads dead here, since each Service restarts discrete threads as needed...
                                // Meaning that if all threads are dead, then even the Service's monitor is not running.
                                if (!healthService.isThreadAlive_healthyStorage) {
                                    logW(TAGG+"HealthService instance is available, but none of its threads are alive, despite it having its own child-process monitor. So, something seems majorly wrong. Restarting main/all services...");
                                    try {
                                        omniApplication.stopMainService();
                                    } catch (Exception e) {
                                        FL.e(TAG, TAGG+"Exception caught: "+e.getMessage());
                                    }
                                }
                            }
                        }

                        if (buttonService != null) {
                            if (buttonService.hasFullyStarted) {
                                // We check for all threads dead here, since each Service restarts discrete threads as needed...
                                // Meaning that if all threads are dead, then even the Service's monitor is not running.
                                /*
                                if (!buttonService.isThreadAlive_healthyStorage) {
                                    logW(TAGG+"HealthService instance is available, but none of its threads are alive, despite it having its own child-process monitor. So, something seems majorly wrong. Restarting main/all services...");
                                    try {
                                        omniApplication.stopMainService();
                                    } catch (Exception e) {
                                        FL.e(TAG, TAGG+"Exception caught: "+e.getMessage());
                                    }
                                }
                                */
                            }
                        }

                        if (flasherLightService != null) {
                            if (flasherLightService.hasFullyStarted) {
                                // We check for all threads dead here, since each Service restarts discrete threads as needed...
                                // Meaning that if all threads are dead, then even the Service's monitor is not running.
                                /*
                                if (!buttonService.isThreadAlive_healthyStorage) {
                                    logW(TAGG+"HealthService instance is available, but none of its threads are alive, despite it having its own child-process monitor. So, something seems majorly wrong. Restarting main/all services...");
                                    try {
                                        omniApplication.stopMainService();
                                    } catch (Exception e) {
                                        FL.e(TAG, TAGG+"Exception caught: "+e.getMessage());
                                    }
                                }
                                */
                            }
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
