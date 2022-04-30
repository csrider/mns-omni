package com.messagenetsystems.evolution2.devices;

/* BluetoothButton_iTAG
 * Black 1-button bluetooth low energy button.
 * This monitors and informs ButtonService of any button presses.
 *
 * How to use it:
 *  BluetoothButton_iTAG bluetoothButton_iTAG = new BluetoothButton_iTAG();
 *  bluetoothButton_iTAG.start();
 *  ...
 *  bluetoothButton_iTAG.cleanup();
 *
 * Premise for how it works:
 *  This button model works by broadcasting its advertisement at various intervals, depending on how long ago button was pressed.
 *  The advertisement broadcast interval is shortest right after a button press, and gradually lengthens as time passes.
 *
 * How it actually works:
 *  This class (once its instance invokes start() method) runs a bluetooth scan for a certain period of time, restarting it every so often.
 *  That scan (targeting our button device, and handled by the callback subclass) constantly updates a list of found devices and their calculated broadcast intervals.
 *  When a button press happens, we can detect that by a found device having a shortened interval, and can then send a message to the ButtonService handler.
 *
 * Valid presses are inferred from the frequency of BLE advertisement packets, as follows under a low-latency scanning method:
 *      Idle button:                        Advertisements are detected about every 3 seconds on average (sometimes as low as 2 seconds or as high as 18+ seconds).
 *      Just pressed button (<2s):          Advertisements are detected about every 15-20 milliseconds on average (sometimes as low as 5ms or as high as 100ms). This lasts for about 2 seconds.
 *      Recently pressed button (2-90s):    Advertisements are detected about every 200-300 milliseconds on average (sometimes as low as 150ms or as high as 600ms). This lasts for about 90 seconds.
 *
 * Note we do not need to connect or anything beyond simply scanning for buttons. This is the essence of this BLE device.
 * Again, the button press is simply inferred for this particular button by frequency of advertisements detected during active scanning (since it doesn't broadcast its pressed state in the adv.).
 *
 * Optionally, you may also monitor button's battery level.
 * To do this, you must set the flag after you initialize, but before you start this process.
 *  bluetoothButton_iTAG.setBatteryMonitor(MONITOR_BATTERY_SEND_TO_LOG);
 *
 * Revisions:
 *  2020.07.07-08   Chris Rider     Created (basically migrated in v1 button service with minimal changes - might be good to refactor later when time allows to improve it further).
 *  2020.07.09      Chris Rider     Begun adding framework for battery monitoring. Challenge will be in how to handle a particular or many buttons, since this button can "roam" among Omnis.
 *  2020.07.10      Chris Rider     Now returning whether scan is happening, via isRunning() method, so ButtonService knows if we are actually active. Needs work, though, to be more reliable/accurate.
 *  2020.07.27      Chris Rider     Changed logging INT to BYTE.
 *  2020.08.04      Chris Rider     Optimized and shifted handlers to background-capable threads (they were running on same TID as main process) --note: balance overhead of creating thread vs just running the code.
 *                                  Moved startScan's scan prep stuff into separate setupScan method, to try to speed up start of scan.
 *  2020.08.06      Chris Rider     Refactored HashMaps for button advertisement data into LinkedHashMaps that can self-limit their size and remove oldest entries.
 *  2020.08.07      Chris Rider     Fixed false-positive press detection that sometimes happened on startup, due to insufficient data points in advertisement interval average calculation.
 *  2020.08.11      Chris Rider     Optimized some code, and spawning new thread for stuff around button press execution.
 */

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.services.ButtonService;
import com.messagenetsystems.evolution2.utilities.ThreadUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static android.bluetooth.BluetoothDevice.BOND_BONDING;
import static android.bluetooth.BluetoothDevice.BOND_NONE;
import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;


public class BluetoothButton_iTAG {
    private static final String TAG = BluetoothButton_iTAG.class.getSimpleName();

    // Constants...
    public static final UUID DEVICE_CLASS_UUID = UUID.nameUUIDFromBytes("iTAG".getBytes());         // Just a unique UUID for this class/type of button
    private final String BLE_UUIDSTR_BASE = Constants.BLE_UUID_BASE_96;                             // Bring in the standard BLE base UUID (i.e. "-0000-1000-8000-00805F9B34FB")

    private final static boolean RESTART_SCANNING_TRUE = true;
    private final static boolean RESTART_SCANNING_FALSE = false;

    public static final byte MONITOR_BATTERY_DISABLED = 0;
    public static final byte MONITOR_BATTERY_SEND_TO_LOG = 1;


    // Configuration...
    private final String BLE_UUIDSTR_SERVICE_GENERIC            = "00001800" + BLE_UUIDSTR_BASE;    // Primary service that gives us device information
    private final String BLE_UUIDSTR_SERVICE_BATTERY            = "0000180F" + BLE_UUIDSTR_BASE;    // Primary service that gives us battery information
    private final String BLE_UUIDSTR_SERVICE_IMMEDIATE_ALERT    = "00001802" + BLE_UUIDSTR_BASE;    // Primary service that allows us to make the device beep
    private final String BLE_UUIDSTR_SERVICE_UNKNOWN            = "0000ffe0" + BLE_UUIDSTR_BASE;    // Primary service that gives us button press information

    private final String BLE_UUIDSTR_CHARACTERISTIC_DEVICENAME  = "00002a00" + BLE_UUIDSTR_BASE;    // Device's name        NOTIFY, READ                            Ex. value: iTAG
    private final String BLE_UUIDSTR_CHARACTERISTIC_APPEARANCE  = "00002a01" + BLE_UUIDSTR_BASE;    // Unknown              READ                                    Ex. value: [0] Unknown
    private final String BLE_UUIDSTR_CHARACTERISTIC_BATTERYLEVEL= "00002a19" + BLE_UUIDSTR_BASE;    // Battery percent      NOTIFY, READ                            Ex. value: 99%
    private final String BLE_UUIDSTR_CHARACTERISTIC_ALERTLEVEL  = "00002a06" + BLE_UUIDSTR_BASE;    // Device beep ability  NOTIFY, WRITE, WRITE NO RESPONSE
    private final String BLE_UUIDSTR_CHARACTERISTIC_UNKNOWN     = "0000ffe1" + BLE_UUIDSTR_BASE;    // ?                    NOTIFY, READ                            Ex. value: (0x) 01

    private final String BLE_DEVICE_NAME = "iTAG";                                                  // BLE name advertised by the button (this could be one way to scan & find them)

    private final int BLE_ADVERTISE_INTERVAL_BUTTON_PRESS_MIN_MS = 5;                               // Minimum milliseconds interval between advertisements after a button press.
    private final int BLE_ADVERTISE_INTERVAL_BUTTON_PRESS_MAX_MS = 100;                             // Maximum milliseconds interval between advertisements after a button press.
    private final byte BLE_ADVERTISE_INTERVAL_BUTTON_PRESS_AVG_VALUES = 3;                          // How many advertisements to use to calculate average. If average falls between min/max above, we detect a press!
    private final int BLE_ADVERTISE_INTERVAL_HIGH_THRESHOLD = 60000;                                // Milliseconds between advertisements, at which we consider the interval to be high (could be some problem soon).
    private final int BLE_ADVERTISE_INTERVAL_VERY_HIGH_THRESHOLD = 120000;                          // Milliseconds between advertisements, at which we consider the interval to be too high (could be some major problem).

    private final int IGNORE_PRESS_DETECTION_AFTER_STARTUP_FOR_MS = 15000;                          // Milliseconds to ignore button-press detection after startup, to avoid false positive (say, from low average calculation).
    private final int IGNORE_PRESS_DETECTION_AFTER_PRESS_FOR_MS = 5000;                             // Milliseconds to ignore subsequent button-press detection, to avoid duplicate/repeated presses.

    private final long SCAN_PERIOD_MS = 10 * 60 * 1000;                                             // How long to scan for between scan restarts (cannot go indefinitely, but restarting takes time, so 10 minutes is happy medium).


    // Locals...
    private WeakReference<Context> appContextRef;
    private Handler parentHandler;                                                                  // Will be a reference to the parent process' message handler, so we can send messages/commands back to it.

    private ParcelUuid BLE_PARCELUUID_SERVICE_UNKNOWN;                                              // ParcelUuid object parsed from configured string

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BtleScanCallback mScanCallback;
    private ScanSettings scanSettings;
    private List<ScanFilter> scanFilters;
    private BluetoothLeScanner mBluetoothLeScanner;
    private Handler stopScanningHandler;
    private volatile boolean mScanning = false;
    private volatile Date lastScanResultDate = new Date();
    private volatile boolean scanIsUndergoingPlannedStop = false;                                   //so health monitor and intermittent stop don't collide
    private volatile boolean scanIsRestarting = false;
    private Date currentScanStartedDate = new Date();
    private long totalButtonPressCount = 0;
    private Date classInitializationDate;

    private BluetoothDevice mBluetoothDevice;

    private byte monitorButtonBattery;
    private String mostRecentBatteryLevel;


    // Logging stuff...
    private final byte LOG_SEVERITY_V = 1;
    private final byte LOG_SEVERITY_D = 2;
    private final byte LOG_SEVERITY_I = 3;
    private final byte LOG_SEVERITY_W = 4;
    private final byte LOG_SEVERITY_E = 5;
    private byte logMethod = Constants.LOG_METHOD_LOGCAT;


    /*============================================================================================*/
    /* Class Methods */

    /** Constructor */
    public BluetoothButton_iTAG(Context appContext, Handler parentHandler, byte logMethod) {
        try {
            this.appContextRef = new WeakReference<Context>(appContext);
            this.parentHandler = parentHandler;
            this.logMethod = logMethod;

            this.monitorButtonBattery = MONITOR_BATTERY_DISABLED;                                   //NOTE: this may be overridden by calling setMonitorButtonBattery
            this.mostRecentBatteryLevel = "";

            try {
                this.BLE_PARCELUUID_SERVICE_UNKNOWN = ParcelUuid.fromString(BLE_UUIDSTR_SERVICE_UNKNOWN);
            } catch (Exception e) {
                logE("Exception caught initializing ParcelUuid object(s): "+e.getMessage());
            }

            this.classInitializationDate = new Date();

            // Get things ready to go for whenever .start() is invoked
            initializeBleAdapter();
            setupScan();

            logV("Instance created.");
        } catch (Exception e) {
            logE("Exception caught creating an instance, aborting.");
        }
    }

    /** Method to start this instance */
    public void start() {
        final String TAGG = "start: ";

        setupScan();
        startScan();

        switch (this.monitorButtonBattery) {
            case MONITOR_BATTERY_SEND_TO_LOG:
                //TODO: monitor battery somehow
                logW(TAGG+"Battery monitoring not yet developed!");
                break;
            case MONITOR_BATTERY_DISABLED:
            default:
                logI(TAGG+"Battery monitoring disabled.");
                break;
        }
    }

    /** Method to cleanup the instance */
    public void cleanup() {
        final String TAGG = "cleanup: ";

        stopScan();

        if (this.parentHandler != null) {
            this.parentHandler = null;
        }

        if (this.appContextRef != null) {
            this.appContextRef.clear();
            this.appContextRef = null;
        }
    }

    /** Method to see if this process is running.
     * NOTE: You get to define what "running" is by whatever you check for in this method. */
    public boolean isRunning() {
        final String TAGG = "isRunning: ";

        if (mScanning) {
            //TODO: (couldn't get to really work reliably, not big deal) flag can be set before scan has really started to yield anything useful, so check scan started date
            /*
            DatetimeUtils datetimeUtils = new DatetimeUtils(appContextRef.get().getApplicationContext(), logMethod);
            Date nowDate = new Date();
            logV(TAGG+"Scan started ("+currentScanStartedDate.toString()+") / now is ("+nowDate.toString()+")");
            if (datetimeUtils.datesAreWithinSecs(nowDate, currentScanStartedDate, 5)) {
                return false;
            } else {
                return true;
            }
            */
            return true;
        } else {
            return false;
        }
    }


    /*============================================================================================*/
    /* Bluetooth Methods */

    /** Initialize our BLE adapter */
    private void initializeBleAdapter() {
        final String TAGG = "initializeAdapter: ";

        mBluetoothAdapter = null;
        mBluetoothManager = null;

        // Initialize the adapter (note you may need to periodically re-init this to keep things functioning reliably)
        logV(TAGG + "Initializing mBluetoothManager and getting mBluetoothAdapter.");
        mBluetoothManager = (BluetoothManager) appContextRef.get().getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
        if (mBluetoothManager == null) {
            logW(TAGG + "Failed to get BluetoothManager instance, cannot continue. Aborting.");
            return;
        }
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        // Ensure Bluetooth is available on the device and it is enabled
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            logW(TAGG + "Bluetooth adapter is either unavailable or not enabled.");
        } else {
            logV(TAGG + "Bluetooth initialized.");
        }
    }

    private void setupScan() {
        final String TAGG = "setupScan: ";

        // Setup our ScanSettings configuration
        scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        // Setup our ScanFilter configuration (so we can only scan for devices we care about)
        scanFilters = new ArrayList<>();
        ScanFilter scanFilter = new ScanFilter.Builder()
                /*.setServiceUuid(new ParcelUuid([UUID HERE]))*/        /* NOTE: on some older versions of android (maybe newer too?), this might be problematic? */
                /*.setDeviceName(BUTTON_NAME)*/
                .setServiceUuid(BLE_PARCELUUID_SERVICE_UNKNOWN)
                /*.setDeviceAddress("FF:FF:C1:12:C3:D5")*/
                .build();
        scanFilters.add(scanFilter);

        // Initialize an instance of our callback routine (which adds scan results to that list)
        mScanCallback = new BtleScanCallback();

        // Get our adapter's LE scanner instance
        if (mBluetoothAdapter == null) {
            logW(TAGG+"No available BluetoothAdapter instance. Aborting.");
            return;
        }
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
    }

    /** Start scan for devices (results governed by callback further below).
     * Note: this will also periodically stop the scan and restart it, to give us continuous scanning. */
    private boolean startScan() {
        final String TAGG = "startScan: ";
        boolean ret = false;

        // Sanity check
        if (mBluetoothLeScanner == null) {
            logW(TAGG+"No available BluetoothLeScanner instance. Aborting.");
            mScanning = false;  //reset flag just to be sure
            return ret;
        }

        // Start the scan, and set our scanning boolean to true.
        logD(TAGG+"Starting scan...");
        mBluetoothLeScanner.startScan(scanFilters, scanSettings, mScanCallback);
        mScanning = true;
        currentScanStartedDate = new Date();
        ret = true;

        // Reset this, in case other tests need it
        scanIsRestarting = false;
        scanIsUndergoingPlannedStop = false;

        // At this point, we have a Bluetooth scan that will asynchronously save all ScanResults into a map (or class vars) via BtleScanCallback

        // Since scanning will go forever on its own, setup a handler to stop it after some time
        // Actually, with Android 7, it will stop after 30 minutes. We want it continuous, so stop and restart under our control to avoid lapse in monitoring.
        stopScanningHandler = new Handler();
        stopScanningHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                scanIsUndergoingPlannedStop = true;
                stopScan();
                initializeBleAdapter();
                setupScan();
                startScan();
            }
        }, SCAN_PERIOD_MS);

        return ret;
    }

    /** Stop the scan, using the same ScanCallback we used earlier.
     * NOTE: This can be called after SCAN_PERIOD_MS has elapsed, via a Handler.postDelayed call. */
    private void stopScan() {
        final String TAGG = "stopScan: ";

        if (mBluetoothLeScanner != null) {
            // We can stop gracefully
            logD(TAGG+"Scanning will now be commanded to stop.");
            mBluetoothLeScanner.stopScan(mScanCallback);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                logW(TAGG+"Exception caught waiting for scan to stop; should be alright, so continuing.");
            }
            mBluetoothLeScanner = null;
        } else {
            try {
                // We can NOT stop gracefully
                logW(TAGG + "Could not gracefully stop scanning. (mBluetoothLeScanner=null | adapterEnabled=" + mBluetoothAdapter.isEnabled() + ")");
                mBluetoothAdapter = null;
                if (mBluetoothAdapter == null) {
                    logW(TAGG + "mBluetoothAdapter became null!");

                }
            } catch (Exception e) {
                logE(TAGG+"Exception caught: "+e.getMessage());
            }
        }

        //cleanup
        mScanCallback = null;
        stopScanningHandler = null;
        mScanning = false;

        //just to be extra safe that gunk doesn't build up
        mBluetoothAdapter = null;
        mBluetoothManager = null;
    }


    /*============================================================================================*/
    /* Support Methods */

    private void sendCommandToParentService(byte actionToRequest, Bundle dataToSend) {
        final String TAGG = "sendCommandToParentService: ";

        // Get our handler's message object so we can populate it with our DB data
        android.os.Message androidMessage = parentHandler.obtainMessage();

        // Send what we're wanting the handler to do
        androidMessage.arg1 = actionToRequest;

        // Supply the data we're going to send
        androidMessage.obj = dataToSend;

        // Actually send the Android-message (with OmniMessage object) back to DeliveryService's handler
        parentHandler.sendMessage(androidMessage);
    }

    /** Method to setup the characteristic for notify.
     * Note: EXPERIMENTAL and unused! See GattCallback subclass below. */
    public boolean setCharacteristicNotification(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic, boolean enable) {
        final String TAGG = "setCharacteristicNotification: ";

        try {
            // Enable notify
            bluetoothGatt.setCharacteristicNotification(characteristic, enable);

            // Now that notify is enabled, it will have descriptor with handle 0x2902, so we need
            // to write BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE to it. First, convert 0x2902
            // to 128 bit UUID (00002902 + BASE-96 BLE UUID).
            UUID notifyDescriptorUUID = UUID.fromString("00002902"+BLE_UUIDSTR_BASE);
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(notifyDescriptorUUID);
            descriptor.setValue(enable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : new byte[]{0x00,0x00});

            return bluetoothGatt.writeDescriptor(descriptor);
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
            return false;
        }
    }

    /** The routine that we run whenever a valid button press has been detected.
     * The button's BluetoothDevice object is passed in, so you can get MAC or anything else needed. */
    private void executeButtonPress(BluetoothDevice device) {
        final String mac = device.getAddress().toLowerCase();
        final String TAGG = "executeButtonPress(" + mac + "): ";

        logV(TAGG + "Running.");

        // Create the URL for the MessageNet server...
        //String url = "http://" + getSharedPrefsServerIPv4(getApplicationContext()) + "/~silentm/bin/smomninotify.cgi";
        final String buttonMacFinal = mac.toLowerCase().replaceAll(":", "");
        String buttonNumber = buttonMacFinal.substring(buttonMacFinal.length() - 8);

        // Setup button data...
        final String buttonNumberFinal;
        StringBuilder sb = new StringBuilder();
        // convert any hex letters to numbers in our own easy way...
        // a = 0; b = 1; c = 2; d = 3; e = 4; f = 5
        for (int i = 0; i < buttonNumber.length(); i++) {
            char c = buttonNumber.charAt(i);
            int i1 = Integer.parseInt(String.valueOf(c), 16) % 10;
            sb.append(i1);
        }
        buttonNumberFinal = sb.toString();

        // Package the data into a bundle for passing to the ButtonService handler
        Bundle dataBundle = new Bundle();
        dataBundle.putString("buttonTypeUUID", DEVICE_CLASS_UUID.toString());
        dataBundle.putString("buttonMAC", buttonMacFinal);
        dataBundle.putString("buttomNumber", buttonNumberFinal);

        // Send button press and data back to ButtonService for handling
        sendCommandToParentService(ButtonService.HANDLER_ACTION_BUTTON_PRESSED, dataBundle);
    }


    /*============================================================================================*/
    /* Subclasses */

    /** Callback class for interacting with GATT on the button device.
     * This was originally envisioned to be for the supposed button press NOTIFY, but just using it for battery level monitoring for now. */
    private class GattCallback extends BluetoothGattCallback {
        final String TAGG = GattCallback.class.getSimpleName()+": ";

        /** Constructor */
        public GattCallback() {
            super();
        }

        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            final String TAGG = this.TAGG+"onConnectionStateChange: ";

            try {
                if (status == GATT_SUCCESS) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        // We're not really bonding in our scheme here, but it's "proper" to handle it in case
                        // Take action depending on the bond state (which, if in play, would dictate how/when we continue)
                        int bondState = mBluetoothDevice.getBondState();
                        if (bondState == BOND_NONE || bondState == BOND_BONDED) {
                            // Connected to device, now proceed to discover its services but delay a bit if needed
                            // With Android versions prior to 8 (7 and lower), if the device has the "Service Changed Characteristic",
                            // the Android stack will still be busy handling it and calling discoverServices() w/out a delay would make
                            // it fail, so you have to add a 1000-1500ms delay. The exact time needed depends on the number of
                            // characteristics of your device. Since at this point, you don't know yet if the device has this characteristic,
                            // it is best to simply always do the delay.
                            int delayWhenBonded = 50;
                            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
                                delayWhenBonded = 1000;
                            }
                            final int delay = bondState == BOND_BONDED ? delayWhenBonded : 50;
                            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    logV(TAGG + "Discovering services with " + delay + "ms delay.");
                                    if (!gatt.discoverServices()) {
                                        logE(TAGG + "discoverServices failed to start.");
                                    }
                                }
                            }, delay);
                        } else if (bondState == BOND_BONDING) {
                            // Bonding process in progress, let it complete
                            // Stack would be busy in this case and service discovery unavailable
                            logW(TAGG + "Waiting for bonding to complete.");
                        } else {
                            // Unhandled case
                            logE(TAGG + "Unhandled bond state.");
                        }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        // We successfully disconnected on our own request
                        gatt.close();
                    } else {
                        // We're CONNECTING or DISCONNECTING, ignore for now as it isn't really implemented anyway
                    }
                } else {
                    // An error happened... figure it out

                    if (status == 19) {
                        // GATT_CONN_TERMINATE_PEER_USER
                        // The device disconnected itself on purpose.
                        // For example, all data has been transferred and there is nothing else to do.
                        logW(TAGG + "Device has disconnected itself on purpose. Closing.");
                        gatt.close();
                    } else if (status == 8) {
                        // GATT_CONN_TIMEOUT
                        // The connection timed out and device disconnected itself.
                        logW(TAGG + "Connection timed-out and device disconnected itself. Closing.");
                        gatt.close();
                    } else if (status == 133) {
                        // GATT_ERROR (this really means nothing, thanks to Android's poor implementation)
                        // There was a low-level error in the communication which led to loss of connection.
                        logE(TAGG + "Status 133 (low-level error / loss of connection / failure to connect). ");
                        gatt.close();
                    } else {
                        logE(TAGG + "An error occurred.");
                        gatt.close();
                    }
                }
            } catch (Exception e) {
                logE(TAGG+"Exception caught: "+e.getMessage());
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            if (status == GATT_SUCCESS) {
                logV(TAGG + "Service discovery responded. Processing...");

                List<BluetoothGattService> services = gatt.getServices();

                for (BluetoothGattService service : services) {
                    /* note: original attempt for button press NOTIFY characteristic
                    if (service.getUuid().toString().equalsIgnoreCase(BLE_UUIDSTR_SERVICE_UNKNOWN)) {
                        //setCharacteristicNotification(gatt, service.getCharacteristic(BLE_UUID_CHARACTERISTIC_UNKNOWN), true);
                    }
                    */
                }
            } else {
                logW(TAGG + "Unhandled status (" + Integer.toString(status) + "), disconnecting GATT...");
                gatt.disconnect();
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
        }
    }

    /** A custom ArrayList for storing recent intervals for a device.
     * Intervals are in milliseconds from Date.getTime, so use long datatype.
     * We simply use ArrayList as an easy way to add values without duplication, manage values, etc. */
    private class IntervalArrayList extends ArrayList<Long> {
        final String TAGG = IntervalArrayList.class.getSimpleName()+": ";

        //TODO Figure out rare error (doesn't seem to cause problems, just annoying): E/BluetoothButton_iTAG: IntervalArrayList: trimSizeTo(3): Exception caught: Index: 4, Size: 4
        public boolean trimSizeTo(int size) {
            final String TAGG = this.TAGG+"trimSizeTo("+size+"): ";
            if (this.size() > size) {
                try {
                    this.remove(this.size()-1);
                    return true;
                } catch (Exception e) {
                    logE(TAGG+"Exception caught: "+e.getMessage());
                }
            }
            return false;
        }

        public long sum() {
            final String TAGG = this.TAGG+"sum: ";
            long sum = 0;
            try {
                if (!isEmpty()) {
                    for (Long item : this) {
                        sum += item;
                    }
                }
            } catch (Exception e) {
                logE(TAGG+"Exception caught: "+e.getMessage());
            }
            return sum;
        }

        public long average() {
            final String TAGG = this.TAGG+"average: ";
            long avg = 0;
            try {
                if (this.size() == 1) avg = this.get(0);
                else if (this.size() > 1) avg = Math.round(sum() / this.size());
            } catch (Exception e) {
                logE(TAGG+"Exception caught: "+e.getMessage());
            }
            return avg;
        }
    }

    /** A custom ArrayList for storing BluetoothDevice objects that we get from the scan-results.
     * We simply use ArrayList as an easy way to add values without duplication, manage values, etc. */
    private class ScannedDevicesArrayList extends ArrayList<BluetoothDevice> {
        private final String TAGG = "ScannedDevicesArrayList: ";

        /*
        @Override
        public boolean add(BluetoothDevice dev) {
            for (int i = 0; i < this.size(); i++) {
                if (this.get(i).getAddress().toLowerCase().equals(dev.getAddress())) {
                    return false;
                }
            }
            return super.add(dev);
        }
        */

        public boolean addIfNotExists(BluetoothDevice dev) {
            final String TAGG = this.TAGG+"addIfNotExists: ";
            for (int i = 0; i < this.size(); i++) {
                if (this.get(i).getAddress().toLowerCase().equals(dev.getAddress())) {
                    return false;
                }
            }
            return super.add(dev);
        }

        public boolean containsDeviceWithMAC(String mac) {
            String TAGG = this.TAGG+"containsDeviceWithMAC("+mac+"): ";
            boolean result = false;
            String thisMAC;
            try {
                for (int i = 0; i < this.size(); i++) {
                    thisMAC = this.get(i).getAddress().toLowerCase();
                    logV(TAGG+"Checking array element \""+thisMAC+"\" for match.");
                    if (thisMAC.equals(mac.toLowerCase())) {
                        logD(TAGG+"Found match, returning true.");
                        result = true;
                        break;
                    }
                }
                logD(TAGG+"No match found, returning false.");
            } catch (Exception e) {
                logE(TAGG+"Exception caught: "+e.getMessage());
            }
            return result;
        }
    }

    /** Define what happens when a scan updates.
     * Note: this should reinstantiate with every scan start and restart. Keep that in mind as you scope stuff. */
    private class BtleScanCallback extends ScanCallback {
        final String TAGG = BtleScanCallback.class.getSimpleName()+": ";

        private long latestAverageScanResultInterval = BLE_ADVERTISE_INTERVAL_BUTTON_PRESS_MIN_MS;          //init with threshold value so we don't inadvertently trigger
        private volatile ScannedDevicesArrayList scannedDevicesArrayList = new ScannedDevicesArrayList();   //it's ok to reinit this when new scan periods start so we're sure to have a fresh list

        final int dataStoreMaxEntries = 256; //NOTE: This is how many different buttons we can support as recent devices. If only one button scanned nearby, the map only contains 1 entry.

        //Just a key/value datastore with String-mac and Date-scanResult...
        private volatile LinkedHashMap<String,Date> scannedDevicesLastResultDate = new LinkedHashMap<String,Date>(dataStoreMaxEntries+1, .75F, false) {
            protected boolean removeEldestEntry(Entry<String,Date> eldest) {return size() > dataStoreMaxEntries;}
        };

        //Just a key/value store with String-mac/Date-lastPress
        private volatile LinkedHashMap<String,Date> scannedDevicesLastPressedDate = new LinkedHashMap<String,Date>(dataStoreMaxEntries+1, .75F, false) {
            protected boolean removeEldestEntry(Entry<String,Date> eldest) {return size() > dataStoreMaxEntries;}
        };

        //Just a key/value store with String-mac/ArrayList-recentIntervals
        private volatile LinkedHashMap<String,IntervalArrayList> scannedDevicesRecentIntervals = new LinkedHashMap<String,IntervalArrayList>(dataStoreMaxEntries+1, .75F, false) {
            protected boolean removeEldestEntry(Entry<String,IntervalArrayList> eldest) {return size() > dataStoreMaxEntries;}
        };

        @Override
        public void onScanResult(final int callbackType, final ScanResult result) {
            /* (note: this scan result could be for any device, so needs to handle multiple devices in multiple scan results and multiple duplicates) */
            super.onScanResult(callbackType, result);
            final String TAGG = this.TAGG+"onScanResult: ";


            ThreadUtils.doStartThread(appContextRef.get(),
                    new Thread(new Runnable() {
                        @Override
                        public void run() {

                            logV(TAGG+"Running for "+result.toString());

                            if (callbackType == ScanSettings.CALLBACK_TYPE_ALL_MATCHES) {   //so we can support multiple buttons

                                /* (following not valid for iTAG buttons since they don't broadcast anything meaningful in their advertisements)
                                // Get Scan Record byte array (Be warned, this can be null)
                                if (result.getScanRecord() != null) {
                                    byte[] scanRecord = result.getScanRecord().getBytes();
                                    Log.v(TAG, TAGG+scanRecord.toString());
                                }*/

                                lastScanResultDate = new Date();

                                BluetoothDevice device = result.getDevice();
                                handleDeviceUpdate(device);
                            }

                        }
                    }),
                    ThreadUtils.SPAWN_NEW_THREAD_FALSE);

        }

        @Override
        public void onBatchScanResults(final List<ScanResult> results) {
            super.onBatchScanResults(results);
            final String TAGG = this.TAGG+"onBatchScanResults: ";

            ThreadUtils.doStartThread(appContextRef.get(),
                    new Thread(new Runnable() {
                        @Override
                        public void run() {

                            logV(TAGG+"Running with "+results.size()+" results.");

                            for (ScanResult result : results) {
                                lastScanResultDate = new Date();

                                handleDeviceUpdate(result.getDevice());
                            }

                        }
                    }),
                    ThreadUtils.SPAWN_NEW_THREAD_TRUE,
                    ThreadUtils.PRIORITY_NORMAL-2);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            final String TAGG = this.TAGG+"onScanFailed: ";
            switch (errorCode) {
                case SCAN_FAILED_INTERNAL_ERROR:
                    logE(TAGG+"BLE Scan Failed with internal error");
                    break;
                default:
                    logE(TAGG+"BLE Scan Failed with code "+errorCode);
                    break;
            }
        }

        private void handleDeviceUpdate(final BluetoothDevice device) {
            final String TAGG = this.TAGG+"handleDeviceUpdate("+android.os.Process.myTid()+"): ";

            final String mac = device.getAddress().toLowerCase();
            long calculatedInterval;
            final long nowTime = new Date().getTime();

            //handle adding the scanned device to our list of found devices
            scannedDevicesArrayList.addIfNotExists(device);

            //calculate some interval value (method will handle all possible extraneous cases automatically for us so we avoid detecting a false positive)
            calculatedInterval = updateLastScanResultDateForDeviceAndReturnInterval(device);
            //logV(TAGG+"calculatedInterval = "+calculatedInterval);

            //update the list of recent-intervals for this device (that we use to calculate recent average)
            //(note: this will also handle initializing it, if needed)
            updateScannedDevicesRecentIntervals(device, calculatedInterval);

            //figure out the most recent interval average for this device
            latestAverageScanResultInterval = scannedDevicesRecentIntervals.get(mac).average();
            //logV(TAGG+"latestAverageScanResultInterval = "+latestAverageScanResultInterval);

            //if we have a short interval average now on our hands, then trigger a button press response
            if (latestAverageScanResultInterval > BLE_ADVERTISE_INTERVAL_BUTTON_PRESS_MIN_MS
                    && latestAverageScanResultInterval < BLE_ADVERTISE_INTERVAL_BUTTON_PRESS_MAX_MS) {
                //at this point, we have a seemingly valid button press...
                //need to take action on it and prevent further triggers for some time

                //first, check if we have just started, as sometimes a low average value can be the result
                //of a lack of sufficient data points, and may cause a false-positive.
                if (nowTime - classInitializationDate.getTime() < IGNORE_PRESS_DETECTION_AFTER_STARTUP_FOR_MS) {
                    logW(TAGG+"Not enough time has elapsed since class startup, ignoring supposed button-press (latestAverageScanResultInterval="+latestAverageScanResultInterval+" from "+scannedDevicesRecentIntervals.size()+" scannedDevicesRecentIntervals).");
                    return;
                }

                ThreadUtils.doStartThread(appContextRef.get(),
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                //for our very first press of this button (as indicated by not yet having a last-pressed Date)
                                //initialize the last-pressed Date and execute the press...
                                if (scannedDevicesLastPressedDate.get(mac) == null) {
                                    scannedDevicesLastPressedDate.put(mac, new Date());                             //never initialized, so do it now
                                    logI(TAGG+"First valid button press detected this scan period for "+mac+".");
                                    executeButtonPress(device);
                                    return;
                                }

                                //scannedDevicesLastPressedDate.get(mac)
                                if (nowTime - scannedDevicesLastPressedDate.get(mac).getTime() < IGNORE_PRESS_DETECTION_AFTER_PRESS_FOR_MS) {
                                    //we're currently in the lockout/wait period after a press, so do nothing right now
                                    logI(TAGG+"Rapid-fire button press detected this scan period for "+mac+".");
                                } else {
                                    scannedDevicesLastPressedDate.put(mac, new Date());                             //reinit with latest valid press time
                                    logI(TAGG+"Another valid button press detected this scan period for "+mac+".");
                                    executeButtonPress(device);
                                }
                            }
                        }),
                        ThreadUtils.SPAWN_NEW_THREAD_TRUE,
                        ThreadUtils.PRIORITY_LOW);

                /*
                //for our very first press of this button (as indicated by not yet having a last-pressed Date)
                //initialize the last-pressed Date and execute the press...
                if (scannedDevicesLastPressedDate.get(mac) == null) {
                    scannedDevicesLastPressedDate.put(mac, new Date());                             //never initialized, so do it now
                    logI(TAGG+"First valid button press detected this scan period for "+mac+".");
                    executeButtonPress(device);
                    return;
                }

                //scannedDevicesLastPressedDate.get(mac)
                if (nowTime - scannedDevicesLastPressedDate.get(mac).getTime() < IGNORE_PRESS_DETECTION_AFTER_PRESS_FOR_MS) {
                    //we're currently in the lockout/wait period after a press, so do nothing right now
                    logI(TAGG+"Rapid-fire button press detected this scan period for "+mac+".");
                } else {
                    scannedDevicesLastPressedDate.put(mac, new Date());                             //reinit with latest valid press time
                    logI(TAGG+"Another valid button press detected this scan period for "+mac+".");
                    executeButtonPress(device);
                }
                */
            }
        }

        /* Update the scan-result Date hashmap as needed, returning the interval calculation.
         * Note: If no existing date, then it will return the current date time value (extremely high current time value) so as to prevent indication of a press event.
         * Note: If something goes wrong, then it will return something below the minimum threshold value so as to prevent indication of a press event. */
        private long updateLastScanResultDateForDeviceAndReturnInterval(BluetoothDevice device) {
            final String TAGG = this.TAGG+"updateLastScanResultDateForDeviceAndReturnInterval("+device.getAddress()+"): ";

            String mac = device.getAddress().toLowerCase();
            long oldTime = 0;
            long result = 0;    //just init with < minimum threshold value in case something goes wrong

            try {
                //first save off the old time (if it doesn't exist, just use initialized value)
                if (scannedDevicesLastResultDate.get(mac) != null) {
                    oldTime = scannedDevicesLastResultDate.get(mac).getTime();
                }

                //next, go ahead and update with the latest time
                scannedDevicesLastResultDate.put(mac, new Date());

                //finally, calculate the interval
                result = scannedDevicesLastResultDate.get(mac).getTime() - oldTime;
            } catch (Exception e) {
                logW(TAGG+"Exception caught with ("+mac+") trying to calculate interval and put new date: "+e.getMessage());
            }

            //if some unexpectedly high value, throw a warning
            if (result > BLE_ADVERTISE_INTERVAL_VERY_HIGH_THRESHOLD && oldTime > 0) {
                logW(TAGG+"Scan result interval is very high ("+String.valueOf(result)+"). There might be problems.");
            } else if (result > BLE_ADVERTISE_INTERVAL_HIGH_THRESHOLD && oldTime > 0) {
                logI(TAGG + "Scan result interval is high (" + String.valueOf(result) + "). There might be problems soon?");
            } else {
                //logV(TAGG + "Returning " + String.valueOf(result) + ".");
            }

            return result;
        }

        private void updateScannedDevicesRecentIntervals(BluetoothDevice device, long interval) {
            final String TAGG = this.TAGG+"updateScannedDevicesRecentIntervals("+device.getAddress()+","+interval+"): ";

            String mac = device.getAddress().toLowerCase();
            IntervalArrayList list;

            try {
                //first get a local ArrayList copy and make changes locally (if not existing yet, initialize one to put back into the main list)
                list = scannedDevicesRecentIntervals.get(mac);

                if (list == null) {
                    list = new IntervalArrayList();
                }

                list.add(0, interval);
                list.trimSizeTo(BLE_ADVERTISE_INTERVAL_BUTTON_PRESS_AVG_VALUES);

                //then just put back up the changed copy
                scannedDevicesRecentIntervals.put(mac, list);
            } catch (Exception e) {
                logW(TAGG+"Exception caught trying to update recent intervals: "+e.getMessage());
            }
        }
    }


    /*============================================================================================*/
    /* Logging Methods */

    public void setMonitorButtonBattery(byte monitorButtonBattery) {
        this.monitorButtonBattery = monitorButtonBattery;
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
