package com.messagenetsystems.evolution2.services;

/* ButtonService class.
 * A service to host process(es) that listen for and do stuff with wireless buttons.
 *
 * Since each type of button might have its own ways of notifying us of button presses (and we want
 * to support them all), we delegate the work of monitoring and stuff to a class of each kind of
 * button. This service will just listen for handler-messages from those processes and do generic
 * stuff in response to those messages (beep, flash lights, initiate message launch, etc.).
 *
 * Revisions:
 *  2020.07.07-08   Chris Rider     Created (based on HealthService as a template).
 *  2020.07.09      Chris Rider     Integrated server request to launch messages as a result of a button press.
 *  2020.07.10      Chris Rider     Fixes/improvements to button press and server response beeps.
 *  2020.07.26-27   Chris Rider     Added ProcessStatus monitoring. Changed logging from INT to BYTE.
 *  2020.08.04      Chris Rider     Moved all blocking operations out of main thread.
 *  2020.08.11      Chris Rider     Implemented Volley priority methods, and implemented lowered-priority threading in the Volley callbacks that apparently run on the main thread (helped jittery scrolling quite a bit).
 *  2020.09.28      Chris Rider     Fixed theoretical potential for uncaught overflow in loop counter.
 */

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.OmniApplication;
import com.messagenetsystems.evolution2.devices.BluetoothButton_RC10BLE;
import com.messagenetsystems.evolution2.devices.BluetoothButton_iTAG;
import com.messagenetsystems.evolution2.models.ProcessStatus;
import com.messagenetsystems.evolution2.threads.HealthThreadProcessStatus;
import com.messagenetsystems.evolution2.threads.TonePlayerBeep;
import com.messagenetsystems.evolution2.utilities.SharedPrefsUtils;
import com.messagenetsystems.evolution2.utilities.ThreadUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public class ButtonService extends Service {
    private final String TAG = this.getClass().getSimpleName();

    // Constants...
    public static final byte HANDLER_ACTION_BUTTON_PRESSED = 1;
    public static final byte HANDLER_ACTION_BUTTON_BATTERY_LOW = 2;


    // Locals...
    private WeakReference<Context> appContextRef;                                                   //since this thread is very long running, we prefer a weak context reference
    private OmniApplication omniApplication;
    public volatile boolean hasFullyStarted;

    private int tid = 0;

    private MonitorChildProcesses monitorChildProcesses;

    private Handler thisServiceHandler;                                                             //message handler for passing to child processes, so they know how to talk back to this service
    private TonePlayerBeep tonePlayerBeep;

    private String serverAddressToSendButtonData;

    private BluetoothButton_iTAG bluetoothButton_iTAG;
    private BluetoothButton_RC10BLE bluetoothButton_RC10BLE;


    // Logging stuff...
    private final byte LOG_SEVERITY_V = 1;
    private final byte LOG_SEVERITY_D = 2;
    private final byte LOG_SEVERITY_I = 3;
    private final byte LOG_SEVERITY_W = 4;
    private final byte LOG_SEVERITY_E = 5;
    private byte logMethod = Constants.LOG_METHOD_LOGCAT;


    /** Constructors (singleton pattern) */
    public ButtonService(Context appContext) {
        super();
    }
    public ButtonService() {
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

        // Setup process monitoring
        omniApplication.processStatusList.addAndRegisterProcess(
                ProcessStatus.PROCESS_TYPE_SERVICE,
                this.getClass());

        this.hasFullyStarted = false;

        this.monitorChildProcesses = new MonitorChildProcesses();

        this.thisServiceHandler = new ThisServiceHandler();
        this.tonePlayerBeep = new TonePlayerBeep();

        this.serverAddressToSendButtonData = new SharedPrefsUtils(getApplicationContext(), logMethod).getStringValueFor(SharedPrefsUtils.spKeyName_serverAddrIPv4, null);
        if (serverAddressToSendButtonData == null) logW("Failed to get server address for sending button data to!");

        this.bluetoothButton_iTAG = new BluetoothButton_iTAG(appContextRef.get().getApplicationContext(), this.thisServiceHandler, logMethod);
        //this.bluetoothButton_RC10BLE = new BluetoothButton_RC10BLE(appContextRef.get().getApplicationContext(), this.thisServiceHandler, logMethod);

        // Inform processStatus about how many children processes there should be here to account for
        // Count: bluetoothButton_iTAG
        omniApplication.processStatusList.setNumberOfExpectedChildrenForProcess(this.getClass(), 0);
    }

    /** Service onStart handler **/
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        final String TAGG = "onStartCommand: ";
        logV(TAGG+"Invoked.");

        // Running in foreground better ensures Android won't kill us   //TODO: evaluate whether this is proper for this class or not?
        startForeground(0, null);

        tid = android.os.Process.myTid();

        // Ensure we have any necessary permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, TAGG + "Coarse location permission is not granted.");
                //TODO: make this obvious to user or prompt to enable somehow
            }
        }

        ////////////////////////////////////////////////////////////////////////////////////////////
        // Register receivers...

        ////////////////////////////////////////////////////////////////////////////////////////////
        // Start services...
        // Remember that any Service you start here (startService) will exist in the same thread as DeliveryService!
        // So, if you want to avoid that, you should wrap them in new-Thread (with nested new-Runnable to easily catch issues upon compile rather than runtime)

        ////////////////////////////////////////////////////////////////////////////////////////////
        // Start threads, wait for them to come up, and then continue...
        this.bluetoothButton_iTAG.start();
        //this.bluetoothButton_RC10BLE.start();

        // Finish service startup...
        this.hasFullyStarted = true;
        omniApplication.processStatusList.recordProcessStart(this.getClass(), tid);
        logI(TAGG+"Service started.");
        omniApplication.appendNotificationWithText(TAG+" started. (tid:"+tid+")");    // Update notification that everything is started and running

        // Start our child-monitoring process
        //this.monitorChildProcesses.start();

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
        omniApplication.appendNotificationWithText(TAG+" died! ("+new Date().toString()+")");

        // Unregister any receivers


        // Stop any stuff we started
        if (this.monitorChildProcesses != null) {
            this.monitorChildProcesses.cleanup();
            this.monitorChildProcesses = null;
        }

        if (this.bluetoothButton_iTAG != null) {
            this.bluetoothButton_iTAG.cleanup();
            this.bluetoothButton_iTAG = null;
        }


        if (this.bluetoothButton_RC10BLE != null) {
            this.bluetoothButton_RC10BLE.cleanup();
            this.bluetoothButton_RC10BLE = null;
        }

        this.omniApplication.processStatusList.recordProcessStop(this.getClass());

        // Explicitly release variables (not strictly necessary, but can't hurt to force garbage collection)
        this.omniApplication = null;

        // Clean up anything else
        if (this.appContextRef != null) {
            this.appContextRef.clear();
            this.appContextRef = null;
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }


    /*============================================================================================*/
    /* Utility Methods */

    private void startDevice_iTAG(boolean restartIfRunning) {
        final String TAGG = "startDevice_iTAG: ";
        logV(TAGG+"Processing start of "+BluetoothButton_iTAG.class.getSimpleName()+".");

        try {
            if (bluetoothButton_iTAG != null && bluetoothButton_iTAG.isRunning()) {
                //thread is running...
                if (restartIfRunning) {
                    logI(TAGG + "Thread is running, so we will stop/reinitialize/re-start it now...");
                    bluetoothButton_iTAG.cleanup();
                } else {
                    logI(TAGG + "Thread is running, so we will leave it be and do nothing.");
                    return;
                }
            } else {
                //thread is not running...
                logI(TAGG+"Thread is not running, so we will initialize and start it anew now...");
            }

            this.bluetoothButton_iTAG = new BluetoothButton_iTAG(appContextRef.get().getApplicationContext(), thisServiceHandler, logMethod);
            this.bluetoothButton_iTAG.start();  //note: it's on this class to start threading properly
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }
    }

    private void startDevice_RC10BLE(boolean restartIfRunning) {
        final String TAGG = "startDevice_RC10BLE: ";
        logV(TAGG+"Processing start of "+BluetoothButton_RC10BLE.class.getSimpleName()+".");

        try {
            if (bluetoothButton_RC10BLE != null && bluetoothButton_RC10BLE.isRunning()) {
                //thread is running...
                if (restartIfRunning) {
                    logI(TAGG + "Thread is running, so we will stop/reinitialize/re-start it now...");
                    bluetoothButton_RC10BLE.cleanup();
                } else {
                    logI(TAGG + "Thread is running, so we will leave it be and do nothing.");
                    return;
                }
            } else {
                //thread is not running...
                logI(TAGG+"Thread is not running, so we will initialize and start it anew now...");
            }

            this.bluetoothButton_RC10BLE = new BluetoothButton_RC10BLE(appContextRef.get().getApplicationContext(), thisServiceHandler, logMethod);
            this.bluetoothButton_RC10BLE.start();  //note: it's on this class to start threading properly
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }
    }


    /*============================================================================================*/
    /* Subclasses */

    /** Handler for working with Android-Messages from child processes. */
    private class ThisServiceHandler extends Handler {
        final String TAGG = this.getClass().getSimpleName() + ": ";

        // Constructor
        public ThisServiceHandler() {
        }

        @Override
        public void handleMessage(Message androidMessage) {
            final String TAGGG = "handleMessage: ";
            //super.handleMessage(androidMessage);  //TODO: needed??

            // First, check if this service is still running before we do anything with its resources (to avoid null pointer exceptions)
            if (omniApplication == null) {
                logI(TAGG + TAGGG + "Host service has been destroyed, aborting.");
                return;
            }

            // See what our command-request is and handle accordingly
            try {
                switch (androidMessage.arg1) {
                    case HANDLER_ACTION_BUTTON_PRESSED:
                        logI(TAGG+TAGGG+"Button pressed.");

                        ////////////////////////////////////////////////////////////////////////////
                        // Parse button press data from device
                        Bundle dataBundle = (Bundle) androidMessage.obj;

                        UUID buttonTypeUUID = UUID.fromString(dataBundle.getString("buttonTypeUUID"));
                        final String buttonMAC = dataBundle.getString("buttonMAC");
                        final String buttonNumber = dataBundle.getString("buttomNumber");

                        logD(TAGG+"Button pressed! "+buttonTypeUUID.toString()+" / "+buttonMAC+" / "+buttonNumber);
                        //logD(TAGG+"("+android.os.Process.myTid()+"/"+Thread.currentThread().getPriority()+") Button pressed!");

                        // Do heavier stuff in a background thread...
                        ThreadUtils.doStartThread(
                                appContextRef.get(),
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        //logD(TAGG+"("+android.os.Process.myTid()+"/"+Thread.currentThread().getPriority()+") Button pressed!");

                                        ////////////////////////////////////////////////////////////////////////////
                                        // Do any local actions here on the Omni in response to the button press

                                        // Immediately play the first tone to let the user know the Omni locally got their button press
                                        try {
                                            tonePlayerBeep.setToneFreqInHz(1000);
                                            tonePlayerBeep.asyncPlayTrack();
                                        } catch (Exception e) {
                                            Log.e(TAG, TAGG+"Exception caught playing tone: "+e.getMessage());
                                        }

                                        ////////////////////////////////////////////////////////////////////////////
                                        // Send request to server with button data
                                        // Create the URL for the MessageNet server...
                                        String url ="http://" + serverAddressToSendButtonData + "/~silentm/bin/smomninotify.cgi";

                                        // Create and setup the Volley stuff
                                        final RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
                                        queue.start();

                                        // Setup our request's response handlers...
                                        ButtonPressStringRequestToServer_ResponseListener responseListener = new ButtonPressStringRequestToServer_ResponseListener(queue);
                                        ButtonPressStringRequestToServer_ResponseErrorListener responseErrorListener = new ButtonPressStringRequestToServer_ResponseErrorListener(queue);

                                        // Setup our request, incorporating the device/button data, and response listeners we created above...

                                        ButtonPressStringRequestToServer stringRequest = new ButtonPressStringRequestToServer(Request.Method.GET, url, responseListener, responseErrorListener, buttonMAC, buttonNumber);
                                        stringRequest.setPriority(Request.Priority.NORMAL);

                                        // Add the request to the RequestQueue.
                                        queue.add(stringRequest);
                                    }
                                }),
                                ThreadUtils.SPAWN_NEW_THREAD_TRUE,
                                ThreadUtils.PRIORITY_LOW);

                        break;
                    case HANDLER_ACTION_BUTTON_BATTERY_LOW:
                        logI(TAGG+TAGGG+"Button battery low.");
                        //TODO: do stuff in response to button battery low
                        break;
                    default:
                        logW(TAGG + TAGGG + "Unhandled case (" + String.valueOf(androidMessage.arg1) + "). Aborting.");
                        return;
                }
            } catch (Exception e) {
                logE(TAGG + "Exception caught: " + e.getMessage());
            }
        }
    }

    private class ButtonPressStringRequestToServer extends StringRequest {
        private final String TAGG = ButtonPressStringRequestToServer.class.getSimpleName() + ": ";
        private Priority mPriority = Priority.NORMAL;
        String buttonMacFinal;
        String buttonNumberFinal;

        ButtonPressStringRequestToServer(int httpMethod, String url, Response.Listener<String> listener, Response.ErrorListener errorListener, String buttonMacFinal, String buttonNumberFinal) {
            super(httpMethod, url, listener, errorListener);
            logV(TAGG+"Instantiating (buttonMac="+buttonMacFinal+") (buttonNum="+buttonNumberFinal+").");

            this.buttonMacFinal = buttonMacFinal;
            this.buttonNumberFinal = buttonNumberFinal;

            setRetryPolicy(new DefaultRetryPolicy(
                    2000,   /*DefaultRetryPolicy.DEFAULT_TIMEOUT_MS,*/      /* timeout value in milliseconds per each retry attempt */
                    3,      /*DefaultRetryPolicy.DEFAULT_MAX_RETRIES,*/     /* max number of retries to attempt */
                    1       /*DefaultRetryPolicy.DEFAULT_BACKOFF_MULT*/     /* multiplier which is used to determine exponential time set to socket for every retry attempt */
                    /*
                     Math for backoff multiplier...
                        time = time + (time * multiplier)
                     Example of backoff multiplier...
                        Timeout = 5000ms, Retries = 2, Backoff = 2
                        Attempt #1:  5000 + (5000 * 2) = 15000ms timeout
                        Attempt #2:  15000 + (15000 * 2) = 45000ms timeout
                    */
            ));
        }

        @Override
        public Map<String, String> getHeaders() throws AuthFailureError {
            Map<String, String> headers = new HashMap<String, String>();

            // Header should look like machine=<Device ID>&<button MAC Address>&<button # (last 8 from MAC)>;
            //                     Ex. machine=TEST OMNI 1&f5afb86032dd&b86032dd;
            headers.put("Cookie", "machine="+serverAddressToSendButtonData+"&"+buttonMacFinal+"&"+buttonNumberFinal+";");
            //headers.put("Cookie", "machine="+getSharedPrefsDeviceID(getApplicationContext())+"&"+buttonIDTruncated+"&0000;");
            logD(TAGG + "Header we are sending to the server: "+headers);
            return headers;
        }

        @Override
        public Priority getPriority() {
            return mPriority;
        }
        public void setPriority(Priority priority) {
            mPriority = priority;
        }
    }
    private class ButtonPressStringRequestToServer_ResponseListener implements Response.Listener {
        private final String TAGG = ButtonPressStringRequestToServer.class.getSimpleName() + ": ";
        RequestQueue queue;

        ButtonPressStringRequestToServer_ResponseListener(RequestQueue queue) {
            logV(TAGG+"Instantiating.");
            this.queue = queue;
        }

        @Override
        public void onResponse(final Object response) {
            final String TAGG = this.TAGG+"onResponse: ";
            logD(TAGG+"Button response from CGI: " + response);

            ThreadUtils.doStartThread(appContextRef.get(),
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                //logV("Processing response in worker thread ("+android.os.Process.myTid()+"/"+Thread.currentThread().getPriority()+")");

                                JSONObject jsonObject = new JSONObject(response.toString());
                                if (jsonObject.getString("wtcwriteresult").equals("success")) {
                                    logD(TAGG+ "Button press successfully sent and response received!");

                                    tonePlayerBeep.setToneFreqInHz(1500);
                                    tonePlayerBeep.asyncPlayTrack_waitOnOthers();
                                }
                                else {
                                    logD(TAGG+ "Button press unsuccessful.");

                                    tonePlayerBeep.setToneFreqInHz(850);
                                    tonePlayerBeep.asyncPlayTrack_waitOnOthers(2);
                                }


                            } catch (JSONException e) {
                                logE(TAGG+"Error caught parsing json object ");
                                tonePlayerBeep.setToneFreqInHz(850);
                                tonePlayerBeep.asyncPlayTrack_waitOnOthers(2);
                            }

                            queue.stop();
                        }
                    }),
                    ThreadUtils.SPAWN_NEW_THREAD_TRUE,
                    ThreadUtils.PRIORITY_LOW);
        }
    }
    private class ButtonPressStringRequestToServer_ResponseErrorListener implements Response.ErrorListener {
        private final String TAGG = ButtonPressStringRequestToServer_ResponseErrorListener.class.getSimpleName() + ": ";
        RequestQueue queue;

        ButtonPressStringRequestToServer_ResponseErrorListener(RequestQueue queue) {
            logV(TAGG+"Instantiating.");
            this.queue = queue;
        }

        @Override
        public void onErrorResponse(VolleyError error) {
            final String TAGG = this.TAGG+"onErrorResponse: ";
            logW(TAGG+"Button response returned an error: " + error);

            //MessageNetUtils.alertButtonPress_medium();
            tonePlayerBeep.setToneFreqInHz(850);
            tonePlayerBeep.asyncPlayTrack_waitOnOthers(2);

            queue.stop();
        }
    }

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
            this.activeProcessingSleepDuration = 4500;
            this.pausedProcessingSleepDuration = 4500;
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

                        if (!bluetoothButton_iTAG.isRunning()) {
                            logW(TAGG+BluetoothButton_iTAG.class.getSimpleName()+" process is not alive! Restarting it...");
                            startDevice_iTAG(true);
                        }

                        /*
                        if (!bluetoothButton_RC10BLE.isRunning()) {
                            logW(TAGG+BluetoothButton_RC10BLE.class.getSimpleName()+" process is not alive! Restarting it...");
                            startDevice_RC10BLE(true);
                        }
                        */

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

        private void doSleep(int ms) {
            final String TAGG = this.TAGG+"doSleep("+Integer.toString(ms)+"): ";

            if (ms >= 10000) {
                logW(TAGG + "Time provided is long (usually should be less than 10,000ms to ensure threads are healthy). Ensure it's correct.");
            }

            try {
                Thread.sleep(ms);
            } catch (InterruptedException ie) {
                logW(TAGG+"Exception caught trying to sleep: "+ie.getMessage());
            }
        }
        private void doSleep(long msLong) {
            final String TAGG = this.TAGG+"doSleep("+Long.toString(msLong)+"): ";

            int msInt;

            if (msLong > Integer.MAX_VALUE) {
                msInt = Integer.MAX_VALUE;
                logW(TAGG+"Time provided is too long. Reduced to "+msInt+"ms.");
            } else {
                msInt = (int) msLong;
            }

            doSleep(msInt);
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
