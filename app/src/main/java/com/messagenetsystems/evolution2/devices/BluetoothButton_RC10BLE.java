package com.messagenetsystems.evolution2.devices;

/* BluetoothButton_RC10BLE
 * White 4-button bluetooth low energy button.
 * Model # RC10-BLE, supplied by Shenzhen Kaipule Technology Co., Ltd.
 *
 * This device sends its status (whether it be sensor, button press, etc.) as part of its beacon advertisement.
 * No connecting required (or possible).
 * Just run an LE scanner continuously.
 *
 * Example raw hex data sent in the advertisement:
 *    Data: 02 01 06 09 08 69 53 65 6E 73 6F 72 20 09 FF 10 A6 E6 1A 39 02 01 F2
 *  (index:  0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22)
 *
 * Per the manufacturer's documentation, here's what each byte in the advertisement represents:
 *      02 AD LENGTH
 *      01 AD TYPE
 *      06 MFG ID
 *      09 ?
 *      08 ?
 *      69 i    53 S    65 e    6E n    73 s    6F o    72 r
 *      20 Device name terminator
 *      09 Data length of sensor
 *      FF Sensor data bit
 *      10 Firmware version
 *      A6 Device ID
 *      E6 Device ID
 *      1A Device ID
 *      39 Type ID
 *    * 02 Event data (which key was pressed: 01-lock, 02-unlock, 04-homelock, 08-SOS... also low battery)
 *      01 Control data
 *      F2 Checksum
 *
 * And here's how all that breaks down into actual key presses:
 *  Button 1 (lock icon - bit1 "away"): 0x02010609086953656E736F722009FF10A6E61A390201F2
 *      Type: 0xFF (mfg data)   Value: 0x10A6E61A390201F2
 *          Data                3 bytes     39 02 01
 *          Checksum            1 byte      F2
 *  Button 2 (unlock icon - bit0 "disarm"): 0x02010609086953656E736F722009FF10A6E61A390101F1
 *      Type: 0xFF (mfg data)   Value: 0x10A6E61A390101F1
 *          Data                3 bytes     39 01 01
 *          Checksum            1 byte      F1
 *  Button 3 (home icon - bit2 "home arm"): 0x02010609086953656E736F722009FF10A6E61A390401F4
 *      Type: 0xFF (mfg data)   Value: 0x10A6E61A390401F4
 *          Data                3 bytes     39 04 01
 *          Checksum            1 byte      F4
 *  Button 4 (sos icon - bit3 "sos"): 0x02010609086953656E736F722009FF10A6E61A390101F1
 *      Type: 0xFF (mfg data)   Value: 0x10A6E61A390801F8
 *          Data                3 bytes     39 08 01
 *          Checksum            1 byte      F8
 *
 * Revisions:
 *  2020.07.07      Chris Rider     Created.
 *  2020.07.10      Chris Rider     More filling out from creation, but not working yet -- device may be Zigbee? Holding development for now.
 *  2020.07.15      Chris Rider     Populating and defining data from documentation received by manufacturer.
 *                                  Got button presses parsing and able to execute. Need to test interoperability of key code with MNS server logic to launch messages, though.
 *  2020.07.16      Chris Rider     Minor refactoring of advertisement processing and parsing, to more closely match protocol documentation provided by manufacturer.
 *  2020.08.12      Chris Rider     Changed logging INT to BYTE.
 */

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.services.ButtonService;
import com.messagenetsystems.evolution2.utilities.ConversionUtils;
import com.messagenetsystems.evolution2.utilities.DatetimeUtils;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class BluetoothButton_RC10BLE {
    private final String TAG = BluetoothButton_RC10BLE.class.getSimpleName();

    // Constants...
    public static final UUID DEVICE_CLASS_UUID = UUID.nameUUIDFromBytes("RC10BLE".getBytes());      // Just a unique UUID for this class/type of button
    private final String BLE_UUIDSTR_BASE = Constants.BLE_UUID_BASE_96;                             // Bring in the standard BLE base UUID (i.e. "-0000-1000-8000-00805F9B34FB")


    // Configuration...
    private final byte ADV_INDEX_EVENT_DATA         = 20;   //What position in the byte array we receive (as the advertisement) to find this data
    private final byte ADV_INDEX_CHECKSUM           = 21;   //What position in the byte array we receive (as the advertisement) to find this data

    private final byte ADV_EVENT_DATA_KEY_A         = 0x01; //Byte value (as hex) that is passed in the advertisement when key is pressed
    private final byte ADV_EVENT_DATA_KEY_B         = 0x02; //Byte value (as hex) that is passed in the advertisement when key is pressed
    private final byte ADV_EVENT_DATA_KEY_C         = 0x04; //Byte value (as hex) that is passed in the advertisement when key is pressed
    private final byte ADV_EVENT_DATA_KEY_D         = 0x08; //Byte value (as hex) that is passed in the advertisement when key is pressed
    //private final byte ADV_EVENT_DATA_LOW_BATTERY   = 0x    //Byte value (as hex) that is passed in the advertisement when low battery occurs

    private final String KEY_NAME_A = "Unlock/Disarm";  //Descriptive name of the key corresponding to A above
    private final String KEY_NAME_B = "Lock/Away";      //Descriptive name of the key corresponding to B above
    private final String KEY_NAME_C = "Home/Arm";       //Descriptive name of the key corresponding to C above
    private final String KEY_NAME_D = "SOS";            //Descriptive name of the key corresponding to D above

    private final int IGNORE_PRESS_DETECTION_AFTER_PRESS_FOR_MS = 5000;                             // Milliseconds to ignore subsequent button-press detection, to avoid duplicate/repeated presses.


    // Locals...
    private WeakReference<Context> appContextRef;
    private Handler parentHandler;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;

    private android.bluetooth.le.ScanFilter mScanFilter;
    private android.bluetooth.le.ScanSettings mScanSettings;
    private android.bluetooth.le.ScanCallback mScanCallback;

    private BluetoothLeScanner mBluetoothLeScanner;

    private byte[] mostRecentAdvertisementBytes;
    private Date mostRecentAdvertisementDate = null;

    private DatetimeUtils datetimeUtils;

    private boolean mScanning;


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
    public BluetoothButton_RC10BLE(Context appContext, Handler parentHandler, byte logMethod) {
        try {
            this.appContextRef = new WeakReference<Context>(appContext);
            this.parentHandler = parentHandler;
            this.logMethod = logMethod;

            if (!initializeBleAdapter()) {
                logE("Failed to get BluetoothAdapter, unable to initialize. Aborting!");
                return;
            }

            this.mScanFilter = new ScanFilter.Builder()
                    .setDeviceName("iSensor ")
                    .build();

            this.mScanSettings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();

            this.mScanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    final String TAGG = "onScanResult: ";
                    processAdvertisement(result);
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    super.onBatchScanResults(results);
                    final String TAGG = "onBatchScanResults: ";
                    logV(TAGG+"Invoked.");
                }

                @Override
                public void onScanFailed(int errorCode) {
                    super.onScanFailed(errorCode);
                    final String TAGG = "onScanFailed: ";
                    logW(TAGG+"Scan failed (error code: "+errorCode+").");
                    mScanning = false;
                }
            };

            this.mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

            this.datetimeUtils = new DatetimeUtils(appContext, logMethod);

            this.mScanning = false;

            logV("Instance created.");
        } catch (Exception e) {
            logE("Exception caught creating an instance, aborting.");
        }
    }

    /** Method to start this instance */
    public boolean start() {
        final String TAGG = "start: ";

        this.mBluetoothLeScanner.startScan(Arrays.asList(mScanFilter), mScanSettings, mScanCallback);
        this.mScanning = true;

        return true;
    }

    /** Method to cleanup the instance */
    public void cleanup() {
        final String TAGG = "cleanup: ";

        this.mBluetoothLeScanner.stopScan(mScanCallback);
        this.mScanning = false;

        this.mBluetoothAdapter = null;
        this.mBluetoothAdapter = null;

        this.mostRecentAdvertisementBytes = null;
        this.mostRecentAdvertisementDate = null;

        this.datetimeUtils = null;

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

        return mScanning;
    }


    /*============================================================================================*/
    /* Bluetooth Methods */

    /** Initialize our BLE adapter */
    private boolean initializeBleAdapter() {
        final String TAGG = "initializeAdapter: ";

        mBluetoothAdapter = null;
        mBluetoothManager = null;

        // Initialize the adapter (note you may need to periodically re-init this to keep things functioning reliably)
        logV(TAGG + "Initializing mBluetoothManager and getting mBluetoothAdapter.");
        mBluetoothManager = (BluetoothManager) appContextRef.get().getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
        try {
            mBluetoothAdapter = mBluetoothManager.getAdapter();
        } catch (Exception e) {
            logE(TAGG+"Exception caught getting adapter from Bluetoothmanager.");
            return false;
        }

        // Ensure Bluetooth is available on the device and it is enabled
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            logE(TAGG + "Bluetooth adapter is either unavailable or not enabled.");
            return false;
        } else {
            logD(TAGG + "Bluetooth initialized.");
            return true;
        }
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

    /** The routine that we run whenever an advertisement is received. */
    private void processAdvertisement(ScanResult scanResult) {
        final String mac = scanResult.getDevice().getAddress().toLowerCase();
        final String TAGG = "processAdvertisement(" + mac + "): ";

        try {
            // Get the raw bytes in the advertisement
            ScanRecord scanRecord = scanResult.getScanRecord();
            byte[] advertisementBytes = scanRecord.getBytes();
            //logV(TAGG+"ADV ("+advertisementBytes.length+" bytes): "+ConversionUtils.byteArrayToHexString(advertisementBytes, " "));

            // Make sure we're not dealing with a rapid-fire or duplicate situation (sometimes an advertisement can rapid-repeat pulse out)
            Date nowDate = new Date();
            if (Arrays.equals(advertisementBytes, mostRecentAdvertisementBytes)
                    && datetimeUtils.datesAreWithinMS(nowDate, mostRecentAdvertisementDate, IGNORE_PRESS_DETECTION_AFTER_PRESS_FOR_MS)) {
                logV(TAGG + "Duplicate advertisement detected, ignoring!");
                return;
            }

            // Since we're continuing with a valid advertisement, let's save its metadata so we can check for duplicates in the future
            this.mostRecentAdvertisementBytes = advertisementBytes;
            this.mostRecentAdvertisementDate = nowDate;

            // DEV-NOTE:
            // At this point, we can go about getting our data in 1 of 2 ways...
            //  1) getManufacturerSpecificData, which returns something like: mManufacturerSpecificData={42512=[-26, 26, 57, 2, 1, -14]}
            //  2) direct reference to byte array with index we want like: advertisementBytes[ADV_INDEX_EVENT_DATA]
            // Since the manufacturer provided the protocol document with index, we will opt for method #2 for now.
            // Note: Original prototype actually used #1, but had to arbitrarily get index 3 of the sparse-array, which has no rhyme or reason.
            // Below is #1 method...
            //  SparseArray<byte[]> mfgSpecificData = scanRecord.getManufacturerSpecificData();             //ex.  mManufacturerSpecificData={42512=[-26, 26, 57, 2, 1, -14]}
            //  byte[] mfgSpecificData_byteArray = mfgSpecificData.valueAt(0);                              //ex.  [-26, 26, 57, 2, 1, -14]
            //  byte keyPressByte = mfgSpecificData_byteArray[3];

            // Advertisement contains following in its DATA frame...
            //  Firmware version (1 byte), Device ID (3 bytes), Data (3 bytes), Checksum (1 byte)
            // In that DATA frame's Data portion, those 3 bytes are...
            //  Type ID (1 byte), Event Data (1 byte), Control Data (1 byte)
            // It's Event-Data we care about!

            // Parse event data (20th byte in the Data portion of the advertisement)
            // (this is for the "remote key fob" Type ID)
            // (bits 0-3 are for function/status, while bits 4-7 are reserved by manufacturer)
            //  0x01 (00000001) Disarm key
            //  0x02 (00000010) Away arm key
            //  0x04 (00000100) Home arm key
            //  0x08 (00001000) SOS key

            //logV(TAGG+"TYPE ID: "+ConversionUtils.byteToHexString(advertisementBytes[19]));
            //logV(TAGG+"EVENT DATA: "+ConversionUtils.byteToHexString(advertisementBytes[20]));

            byte eventDataByte = advertisementBytes[ADV_INDEX_EVENT_DATA];
            switch (eventDataByte) {
                case ADV_EVENT_DATA_KEY_A:
                    logI(TAGG + "Event-Data indicates key pressed: " + KEY_NAME_A);
                    executeButtonPress(mac, ADV_EVENT_DATA_KEY_A);
                    break;
                case ADV_EVENT_DATA_KEY_B:
                    logI(TAGG + "Event-Data indicates key pressed: " + KEY_NAME_B);
                    executeButtonPress(mac, ADV_EVENT_DATA_KEY_B);
                    break;
                case ADV_EVENT_DATA_KEY_C:
                    logI(TAGG + "Event-Data indicates key pressed: " + KEY_NAME_C);
                    executeButtonPress(mac, ADV_EVENT_DATA_KEY_B);
                    break;
                case ADV_EVENT_DATA_KEY_D:
                    logI(TAGG + "Event-Data indicates key pressed: " + KEY_NAME_D);
                    executeButtonPress(mac, ADV_EVENT_DATA_KEY_B);
                    break;
                //TODO: low battery
                default:
                    logW(TAGG + "Unknown Event-Data value (" + ConversionUtils.byteToHexString(eventDataByte) + ")");
                    break;
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }

    /** The routine that we run whenever a valid button press has been detected.
     * The button's keypress event's ScanResult is passed in from the advertisement, so you can get anything needed. */
    private void executeButtonPress(String deviceMac, byte keyPressByte) {
        final String TAGG = "executeButtonPress: ";

        //logV(TAGG + "Running.");

        try {

            // Create the URL for the MessageNet server...
            //String url = "http://" + getSharedPrefsServerIPv4(getApplicationContext()) + "/~silentm/bin/smomninotify.cgi";
            final String buttonMacFinal = deviceMac.toLowerCase().replaceAll(":", "");
            String buttonNumber = buttonMacFinal.substring(buttonMacFinal.length() - 7) + Byte.toString(keyPressByte);

            // Setup button data that we will send...
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
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }


    /*============================================================================================*/
    /* Subclasses */




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
