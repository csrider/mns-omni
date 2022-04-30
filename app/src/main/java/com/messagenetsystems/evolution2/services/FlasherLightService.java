package com.messagenetsystems.evolution2.services;

/* FlasherLightService class.
 * A service to host process(es) that manage doing stuff with the flasher lights.
 * Also provides static/direct methods to do stuff with the lights from anywhere.
 *
 * As a reminder, here is how the flasher lights work...
 *  They are Bluetooth Low Energy, and operate in "master/server" configuration (i.e. a persistent connected state, rather than beacon).
 *  We form a connection with the light controller device, and are then able to send commands to it as needed, whenever we want.
 *  The key is that the connection is persistent, so we should also monitor the connection state and keep it alive.
 *
 * Revisions:
 *  2020.07.21-29   Chris Rider     Created (based on ButtonService as a template). Decided to migrate main light duties from standalone app to this service.
 *  2020.08.05      Chris Rider     Moved onReceive's logic into new thread so we can get it off main thread and be able to control priority.
 *  2020.09.28      Chris Rider     Fixed theoretical potential for uncaught overflow in loop counter.
 */

import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.OmniApplication;
import com.messagenetsystems.evolution2.devices.BluetoothLights_HY254117V9;
import com.messagenetsystems.evolution2.models.FlasherLights;
import com.messagenetsystems.evolution2.models.OmniMessage;
import com.messagenetsystems.evolution2.models.OmniMessages;
import com.messagenetsystems.evolution2.models.ProcessStatus;
import com.messagenetsystems.evolution2.utilities.ConversionUtils;
import com.messagenetsystems.evolution2.utilities.SharedPrefsUtils;
import com.messagenetsystems.evolution2.utilities.ThreadUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;


public class FlasherLightService extends Service {
    private static final String TAG = FlasherLightService.class.getSimpleName();

    // Constants...
    public static final String BROADCAST_RECEIVER_NAME = "com.messagenetsystems.evolution2.FlasherLightService.broadcastReceiver";
    public static final String INTENTEXTRA_LIGHTPURPOSE = "com.messagenetsystems.evolution2.FlasherLightService.lightPurpose";
    public static final String INTENTEXTRA_MSGUUID = "com.messagenetsystems.evolution2.FlasherLightService.msgUUID";
    public static final String INTENTEXTRA_LIGHTCODE = "com.messagenetsystems.evolution2.FlasherLightService.lightCode";
    public static final int LIGHTPURPOSE_UNKNOWN = 0;
    public static final int LIGHTPURPOSE_STARTLIGHT_FROMMSG = 1;
    public static final int LIGHTPURPOSE_STARTLIGHT_BYCODE = 2;
    public static final int LIGHTPURPOSE_STOPLIGHT = 3;
    public static final int LIGHTPURPOSE_CONNECT_GATT = 4;


    // Locals...
    private WeakReference<Context> appContextRef;                                                   //since this thread is very long running, we prefer a weak context reference
    private OmniApplication omniApplication;

    public volatile boolean hasFullyStarted;

    private int tid = 0;

    private FlasherLightBroadcastReceiver flasherLightBroadcastReceiver;

    private Handler thisServiceHandler;                                                             //message handler for passing to child processes, so they know how to talk back to this service

    public static FlasherLights.OmniCommandCodes flasherLightOmniCommandCodes;

    private String sharedPrefsLightMacAddress;
    private BluetoothDevice mBluetoothDevice;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothLights_HY254117V9 bluetoothLights;
    private BluetoothLights_HY254117V9.GattCallback gattCallback;

    public static volatile boolean isGattConnecting = false;
    public static volatile boolean isGattConnectedAndReady = false;


    // Logging stuff...
    private final byte LOG_SEVERITY_V = 1;
    private final byte LOG_SEVERITY_D = 2;
    private final byte LOG_SEVERITY_I = 3;
    private final byte LOG_SEVERITY_W = 4;
    private final byte LOG_SEVERITY_E = 5;
    private byte logMethod = Constants.LOG_METHOD_LOGCAT;


    /** Constructors (singleton pattern) */
    public FlasherLightService(Context appContext) {
        super();
    }
    public FlasherLightService() {
    }


    /*============================================================================================*/
    /* Service methods */

    /** Service onCreate handler **/
    @Override
    public void onCreate() {
        super.onCreate();
        final String TAGG = "onCreate: ";
        logV(TAGG+"Invoked.");

        // Init class overhead
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
        this.flasherLightBroadcastReceiver = new FlasherLightBroadcastReceiver();
        this.thisServiceHandler = new ThisServiceHandler();

        flasherLightOmniCommandCodes = new FlasherLights.OmniCommandCodes(FlasherLights.PLATFORM_MNS);

        // Init shared-prefs light hardware address
        SharedPrefsUtils sharedPrefsUtils = new SharedPrefsUtils(getApplicationContext(), logMethod);
        sharedPrefsLightMacAddress = sharedPrefsUtils.getStringValueFor(SharedPrefsUtils.spKeyName_flasherMacAddress, null);
        sharedPrefsUtils.cleanup();

        // Init bluetooth resources
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (sharedPrefsLightMacAddress != null) {
            this.mBluetoothDevice = bluetoothAdapter.getRemoteDevice(sharedPrefsLightMacAddress.toUpperCase());
        } else {
            logE(TAGG+"No light controller MAC address saved in shared preferences.");  //TODO: scan for and get the device
        }
        this.bluetoothLights = new BluetoothLights_HY254117V9();
        this.gattCallback = new BluetoothLights_HY254117V9.GattCallback();

        // Inform processStatus about how many children processes there should be here to account for
        // Count: healthThreadProcessStatus, healthThreadStorage, healthThreadEnergy, healthThreadHeartbeat
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

        // Connect with the light controller hardware
        initiateGattConnection();

        ////////////////////////////////////////////////////////////////////////////////////////////
        // Register receivers...
        registerReceiver(flasherLightBroadcastReceiver, new IntentFilter(BROADCAST_RECEIVER_NAME));

        ////////////////////////////////////////////////////////////////////////////////////////////
        // Start threads, wait for them to come up, and then continue...


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

        // Disconnect and close any bluetooth stuff
        if (this.mBluetoothGatt != null) {
            this.mBluetoothGatt.disconnect();
            this.mBluetoothGatt.close();
            this.mBluetoothGatt = null;
        }
        this.mBluetoothDevice = null;

        // Unregister any receivers
        unregisterReceiver(flasherLightBroadcastReceiver);

        // Stop any stuff we started


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

    /*
    private void restartDevice_RC10BLE() {
        final String TAGG = "restartDevice_RC10BLE: ";
        logV(TAGG+"Trying to restart "+BluetoothButton_RC10BLE.class.getSimpleName()+"...");

        int maxWaitForStart = 10;

        try {
            if (this.bluetoothButton_RC10BLE != null) {
                this.bluetoothButton_RC10BLE.cleanup();
            }

            this.bluetoothButton_RC10BLE = new BluetoothButton_RC10BLE(appContextRef.get().getApplicationContext(), thisServiceHandler, logMethod);
            this.bluetoothButton_RC10BLE.start();

            while (!this.bluetoothButton_RC10BLE.isRunning()) {
                //wait here while thread starts up
                logV(TAGG+"Waiting for process to start running.");

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
    */

    private void doSleep(int ms) {
        final String TAGG = "doSleep("+Integer.toString(ms)+"): ";

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
        final String TAGG = "doSleep("+Long.toString(msLong)+"): ";

        int msInt;

        if (msLong > Integer.MAX_VALUE) {
            msInt = Integer.MAX_VALUE;
            logW(TAGG+"Time provided is too long. Reduced to "+msInt+"ms.");
        } else {
            msInt = (int) msLong;
        }

        doSleep(msInt);
    }

    /** This method can be invoked from anywhere, to easily start the specified flasher light code. */
    public static void startLight(Context context, byte flasherLightCode) {
        final String TAGG = "startLight("+Byte.toString(flasherLightCode)+"): ";

        try {
            // Get our intent
            Intent i = new Intent(BROADCAST_RECEIVER_NAME);
            i.putExtra(INTENTEXTRA_LIGHTPURPOSE, LIGHTPURPOSE_STARTLIGHT_BYCODE);
            i.putExtra(INTENTEXTRA_LIGHTCODE, flasherLightCode);

            // Broadcast it
            context.sendBroadcast(i);
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }
    }

    /** This method can be invoked from anywhere, to easily start the specified message's flasher light code. */
    public static boolean startLightForMessage(Context context, String msgUuidStr) {
        final String TAGG = "startLightForMessage("+msgUuidStr+"): ";

        if (!isGattConnectedAndReady) {
            FL.w(TAG, TAGG+"GATT connection not ready.");
            return false;
        }

        try {
            // Get our intent
            Intent i = new Intent(BROADCAST_RECEIVER_NAME);
            i.putExtra(INTENTEXTRA_LIGHTPURPOSE, LIGHTPURPOSE_STARTLIGHT_FROMMSG);
            i.putExtra(INTENTEXTRA_MSGUUID, msgUuidStr);

            // Broadcast it
            context.sendBroadcast(i);

            return true;
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
            return false;
        }
    }
    public static boolean startLightForMessage(Context context, UUID msgUUID) {
        return startLightForMessage(context, msgUUID.toString());
    }

    private void initiateGattConnection() {
        final String TAGG = "initiateGattConnection: ";

        if (mBluetoothDevice != null) {
            logV(TAGG+"Connecting to device's GATT server...");

            isGattConnecting = true;            //be sure to let callback reset this when it's done
            isGattConnectedAndReady = false;    //reset this flag (?)

            this.mBluetoothGatt = mBluetoothDevice.connectGatt(appContextRef.get().getApplicationContext(), false, gattCallback, BluetoothDevice.TRANSPORT_LE);
            //DEV-NOTE: From here, the callback class will handle the rest (handshake, password, setting up persistent connection)
            //When you want to send command to device, set the command in the bluetoothLights instance and write the characteristic

            // Setup a thread to loop and monitor connected/ready flags and retry if needed
            final HandlerThread handlerThread = new HandlerThread("GattConnectionSetupChecker");
            handlerThread.start();
            new Handler(handlerThread.getLooper()).post(new Runnable() {
                final String TAGGG = "Runnable(GATT ready checker): ";
                final long checkIntervalMs = 100;   // 10 intervals per second
                final long maxLoopIntervals = 300;  // 30 seconds (100ms intervals = 10 intervals per second)
                long loopIntervalCounter = 0;
                @Override
                public void run() {
                    while (FlasherLightService.isGattConnecting) {
                        ThreadUtils.doSleep(checkIntervalMs);
                        loopIntervalCounter++;
                        if (FlasherLightService.isGattConnectedAndReady) {
                            break;  //exit loop
                        }
                        if (loopIntervalCounter > maxLoopIntervals) {
                            logW(TAGG+TAGGG+"Timeout waiting for GATT connection to become ready.");
                            break;
                        }
                    }

                    if (FlasherLightService.isGattConnectedAndReady) {
                        //loop exited because everything is good
                        logI(TAGG+TAGGG+"GATT connected and ready!");
                    } else {
                        //loop exited because connecting stopped (gatt error?) and never became connected/ready
                        //so we should try again
                        logI(TAGG+TAGGG+"GATT connection process terminated due to some problem, trying again...");
                        initiateGattConnection();
                    }

                    handlerThread.quit();
                }
            });
        } else {
            logE(TAGG+"No bluetooth device initialized. Nothing to connect to!");
        }
    }


    /*============================================================================================*/
    /* Subclasses */

    /** Receiver for handling light commands. */
    public class FlasherLightBroadcastReceiver extends BroadcastReceiver {
        private final String TAGG = FlasherLightBroadcastReceiver.class.getSimpleName() + ": ";

        //constructor
        public FlasherLightBroadcastReceiver(){
            logV(TAGG+"Instance created");
        }

        @Override
        public void onReceive(Context context, final Intent intent) {
            final String TAGGG = "onReceive("+android.os.Process.myTid()+"): ";

            ThreadUtils.doStartThread(context,
                    new Thread(new Runnable() {
                        @Override
                        public void run() {

                            Bundle intentExtras;
                            int lightPurpose;
                            String uuidMsgString = null;

                            try {
                                // Get all the extras from the broadcast's intent
                                intentExtras = intent.getExtras();
                                if (intentExtras == null) {
                                    logW(TAGG+TAGGG+"No extras provided by intent, so cannot get data. Aborting any functions for this attempt.");
                                    return;
                                }

                                // Get the purpose of this broadcast from the extras
                                lightPurpose = intentExtras.getInt(INTENTEXTRA_LIGHTPURPOSE, LIGHTPURPOSE_UNKNOWN);
                                if (lightPurpose == LIGHTPURPOSE_UNKNOWN) {
                                    logW(TAGG+TAGGG+"No purpose provided by intent, so don't know what to do. Aborting any functions for this attempt.");
                                    return;
                                }

                                // Get message data passed in through intent
                                try {
                                    uuidMsgString = intentExtras.getString(INTENTEXTRA_MSGUUID);
                                } catch (Exception e) {
                                    logW(TAGG+"Exception caught getting/parsing message data from intent: "+e.getMessage());
                                }

                                // Check message data
                                if (uuidMsgString == null || uuidMsgString.isEmpty()) {
                                    logW(TAGG+TAGGG+"No/invalid message UUID provided by intent. Aborting any functions for this attempt.");
                                    //return; TODO remove this comment after testing
                                }

                                // Take appropriate action, depending on what the purpose is
                                switch (lightPurpose) {
                                    case LIGHTPURPOSE_STARTLIGHT_FROMMSG:
                                        handlePurpose_startLightByMsgUuidStr(uuidMsgString);
                                        break;
                                    case LIGHTPURPOSE_STARTLIGHT_BYCODE:
                                        handlePurpose_startLightByCode(intentExtras.getByte(INTENTEXTRA_LIGHTCODE, flasherLightOmniCommandCodes.CMD_LIGHT_STANDBY));
                                        break;
                                    case LIGHTPURPOSE_STOPLIGHT:
                                        logW(TAGG+TAGGG+"Not developed yet (stop light)");//TODO
                                        break;
                                    case LIGHTPURPOSE_CONNECT_GATT:
                                        initiateGattConnection();
                                    default:
                                        logW(TAGG+TAGGG+"Unhandled case (lightPurpose = "+lightPurpose+").");
                                        break;
                                }
                            } catch (Exception e) {
                                logE(TAGG+TAGGG+"Exception caught: "+e.getMessage());
                            }

                        }
                    }),
                    ThreadUtils.SPAWN_NEW_THREAD_TRUE,
                    ThreadUtils.PRIORITY_MINIMUM);
        }

        private void handlePurpose_startLightByMsgUuidStr(String msgUuidStr) {
            final String TAGG = "handlePurpose_startLightByMsgUuidStr: ";
            logV(TAGG+"Invoked.");

            //get light code from message
            OmniMessage omniMessage = MainService.omniMessages_deliverable.getOmniMessage(UUID.fromString(msgUuidStr), OmniMessages.GET_OMNIMESSAGE_AS_REFERENCE);
            String dbb_light_signal = omniMessage.getBannerMessage().dbb_light_signal;
            int dbb_light_signal_asInt = (int) dbb_light_signal.charAt(0);

            //encode the message's light command into a list of characteristic values that we can write to the device
            List<byte[]> charsToWrite = bluetoothLights.encodeLightCommandBytesFromBannerLightCommand(dbb_light_signal_asInt);

            //write characteristic to gatt server
            //if (mBluetoothGatt.getConnectionState(mBluetoothDevice) == BluetoothProfile.STATE_CONNECTED) {
                sendCharacteristicValues(mBluetoothGatt, BluetoothLights_HY254117V9.uuid_service, BluetoothLights_HY254117V9.uuid_char1001, charsToWrite);
            //} else {
            //    logW(TAGG+"Bluetooth GATT server not connected.");
            //}
        }

        private void handlePurpose_startLightByCode(byte lightCode) {
            final String TAGG = "handlePurpose_startLightByCode: ";
            logV(TAGG+"Invoked.");

            //encode the message's light command into a list of characteristic values that we can write to the device
            List<byte[]> charsToWrite = bluetoothLights.encodeLightCommandBytesFromBannerLightCommand(lightCode);

            //write characteristic to gatt server
            //if (mBluetoothGatt.getConnectionState(mBluetoothDevice) == BluetoothProfile.STATE_CONNECTED) {
            sendCharacteristicValues(mBluetoothGatt, BluetoothLights_HY254117V9.uuid_service, BluetoothLights_HY254117V9.uuid_char1001, charsToWrite);
            //} else {
            //    logW(TAGG+"Bluetooth GATT server not connected.");
            //}
        }

        private void handlePurpose_stopLight() {
            final String TAGG = "handlePurpose_stopLight: ";
            logV(TAGG+"Invoked.");

            //TODO
        }

        /** Send (write) a GATT characteristic value.
         * @param gatt GATT client instance
         * @param serviceUUID GATT service UUID the characteristic belongs to
         * @param characteristicUUID GATT characteristic UUID to write to
         * @param characteristicValueList GATT characteristic value list to write
         * @return Whether write operation was attempted
         */
        private boolean sendCharacteristicValues(BluetoothGatt gatt, UUID serviceUUID, UUID characteristicUUID, List<byte[]> characteristicValueList) {
            final String TAGG = "sendCharacteristicValues: ";

            try {
                // Get service
                BluetoothGattService gattService = gatt.getService(serviceUUID);
                if (gattService == null) {
                    logE(TAGG+"Failed to get service, aborting.");
                    return false;
                }

                // Get characteristic from that service
                BluetoothGattCharacteristic gattCharacteristic = gattService.getCharacteristic(characteristicUUID);
                if (gattCharacteristic == null) {
                    logE(TAGG+"Failed to get characteristic, aborting.");
                    return false;
                }

                // Set device's list
                bluetoothLights.characteristicValuesToSend = characteristicValueList;

                // Set value of that characteristic to the 0th item in the list (either the first one or the only one, we don't care here)
                gattCharacteristic.setValue(characteristicValueList.get(0));

                // Write the updated characteristic back to GATT
                // This will trigger the onCharacteristicWrite override method, which will then handle writing any subsequent commands that may be in the list
                logV(TAGG+"Sending ["+ ConversionUtils.byteArrayToHexString(characteristicValueList.get(0), " ")+"] to characteristic "+characteristicUUID.toString()+" in service "+serviceUUID.toString()+"...");
                return gatt.writeCharacteristic(gattCharacteristic);
            } catch (Exception e) {
                logE(TAGG+"Exception caught: "+e.getMessage());
                return false;
            }
        }
    }

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

                    default:
                        logW(TAGG + TAGGG + "Unhandled case (" + String.valueOf(androidMessage.arg1) + "). Aborting.");
                        return;
                }
            } catch (Exception e) {
                logE(TAGG + "Exception caught: " + e.getMessage());
            }
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

                        /*
                        if (!bluetoothButton_iTAG.isRunning()) {
                            logW(TAGG+BluetoothButton_iTAG.class.getSimpleName()+" process is not alive! Restarting it...");
                            restartDevice_iTAG();
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
