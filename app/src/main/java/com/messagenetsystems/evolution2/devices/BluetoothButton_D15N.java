package com.messagenetsystems.evolution2.devices;

/* BluetoothButton_D15N
 * White circular 1-button bluetooth low energy button.
 *
 * Model # D15N
 *
 * Manufacturer: Minew Tech (mfg model: Beacon Plus)
 * Software: Nordic nRF52
 *
 * Revisions:
 *  2020.07.10      Chris Rider     Created.
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
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.services.ButtonService;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.UUID;

import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static android.bluetooth.BluetoothDevice.BOND_BONDING;
import static android.bluetooth.BluetoothDevice.BOND_NONE;
import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;

public class BluetoothButton_D15N {
    private final String TAG = BluetoothButton_D15N.class.getSimpleName();

    // Constants...
    public static final UUID DEVICE_CLASS_UUID = UUID.nameUUIDFromBytes("D15N".getBytes());         //Just a unique UUID for this class/type of button
    private final String BLE_UUIDSTR_BASE = Constants.BLE_UUID_BASE_96;                             //Bring in the standard BLE base UUID (i.e. "-0000-1000-8000-00805F9B34FB")


    // Configuration...
    private final String BLE_UUIDSTR_SVC_GENERIC_ACCESS = "00001800" + BLE_UUIDSTR_BASE;            //(Primary Service) Generic Access Service
    private final String BLE_UUIDSTR_SVC_GENERIC_ATTR   = "00001801" + BLE_UUIDSTR_BASE;            //(Primary Service) Generic Attribute Service
    private final String BLE_UUIDSTR_SVC_DEVICE_INFO    = "0000180A" + BLE_UUIDSTR_BASE;            //(Primary Service) Device Information Service
    private final String BLE_UUIDSTR_SVC_UNKNOWN        = "7f280001-8204-f393-e0a9-e50e24dcca9e";   //(Primary Service) Unknown Service
    private final String BLE_UUIDSTR_SVC_EDDYSTONE_CONF = "a3c87500-8ed3-4bdf-8a39-a01bebede295";   //(Primary Service) Eddystone Configuration Service

    private final String BLE_UUIDSTR_GENACC_CHR_DEVICE_NAME        = "00002A00" + BLE_UUIDSTR_BASE; //Device Name                                   READ                Ex. nRF5x
    private final String BLE_UUIDSTR_GENACC_CHR_APPEARANCE         = "00002A01" + BLE_UUIDSTR_BASE; //Appearance                                    READ                Ex. [0] Unknown
    private final String BLE_UUIDSTR_GENACC_CHR_PERIPH_CONN_PARAMS = "00002A04" + BLE_UUIDSTR_BASE; //Peripheral Preferred Connection Parameters    READ                Ex. Connection Interval: 20.00ms - 75.00ms, Slave Latency: 0, Supervision Timeout Multiplier: 400
    private final String BLE_UUIDSTR_GENACC_CHR_CENT_ADDR_RESOLVE  = "00002AA6" + BLE_UUIDSTR_BASE; //Central Address Resolution                    READ                Ex. Address resolution supported

    private final String BLE_UUIDSTR_GENATTR_CHR_SVC_CHANGED       = "00002A05" + BLE_UUIDSTR_BASE; //Service Changed                               INDICATE
    private final String BLE_UUIDSTR_GENATTR_CHR_SVS_CHANGED_DESC  = "00002902" + BLE_UUIDSTR_BASE; //Service Changed's descriptor

    private final String BLE_UUIDSTR_DEVINFO_CHR_MFG_NAME_STR      = "00002A29" + BLE_UUIDSTR_BASE; //Manufacturer Name String                      READ                Ex. Minew Tech
    private final String BLE_UUIDSTR_DEVINFO_CHR_MODEL_NUMBER_STR  = "00002A24" + BLE_UUIDSTR_BASE; //Model Number String                           READ                Ex. Beacon Plus
    private final String BLE_UUIDSTR_DEVINFO_CHR_SERIAL_NUMBER_STR = "00002A25" + BLE_UUIDSTR_BASE; //Serial Number String (same as MAC)            READ                Ex. AC233F50519F
    private final String BLE_UUIDSTR_DEVINFO_CHR_HW_REVISION_STR   = "00002A27" + BLE_UUIDSTR_BASE; //Hardware Revision String                      READ                Ex. MS71SF6_V1.0.0
    private final String BLE_UUIDSTR_DEVINFO_CHR_FW_REVISION_STR   = "00002A26" + BLE_UUIDSTR_BASE; //Firmware Revision String                      READ                Ex. 1.3.04
    private final String BLE_UUIDSTR_DEVINFO_CHR_SW_REVISION_STR   = "00002A28" + BLE_UUIDSTR_BASE; //Software Revision String                      READ                Ex. nRF52-SDK13.0

    private final String BLE_UUIDSTR_UNKNOWN_CHR_UNKNOWN  = "7f280002-8204-f393-e0a9-e50e24dcca9e"; //Unknown Characteristic                        NOTIFY,READ,WRITE   Ex. (0x) E2-80-81-07-73-2F-48-71-00-BB-95-F5-2F-1A-61-11
    private final String BLE_UUIDSTR_UNKNOWN_CHR_UNKNOWN_DESC = "00002902" + BLE_UUIDSTR_BASE;      //Unknown Characteristic's descriptor

    private final String BLE_UUIDSTR_ES_CHR_CAPABILITIES  = "a3c87501-8ed3-4bdf-8a39-a01bebede295"; //Capabilities                                  READ
    private final String BLE_UUIDSTR_ES_CHR_ACTIVE_SLOT   = "a3c87502-8ed3-4bdf-8a39-a01bebede295"; //Active Slot                                   READ,WRITE
    private final String BLE_UUIDSTR_ES_CHR_ADV_INTERVAL  = "a3c87503-8ed3-4bdf-8a39-a01bebede295"; //Advertising Interval                          READ,WRITE
    private final String BLE_UUIDSTR_ES_CHR_TX_PWR        = "a3c87504-8ed3-4bdf-8a39-a01bebede295"; //Radio Tx Power                                READ,WRITE
    private final String BLE_UUIDSTR_ES_CHR_ADV_TX_PWR    = "a3c87505-8ed3-4bdf-8a39-a01bebede295"; //(Advanced) Advertised Tx Power                READ,WRITE
    private final String BLE_UUIDSTR_ES_CHR_SLOT_DATA     = "a3c8750a-8ed3-4bdf-8a39-a01bebede295"; //ADV Slot Data                                 READ,WRITE
    private final String BLE_UUIDSTR_ES_CHR_FACTORY_RESET = "a3c8750b-8ed3-4bdf-8a39-a01bebede295"; //(Advanced) Factory Reset                      WRITE


    // Locals...
    private WeakReference<Context> appContextRef;
    private Handler parentHandler;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mBluetoothDevice;
    private BluetoothGatt mBluetoothGatt;

    private GattCallback gattCallback;


    // Logging stuff...
    private final int LOG_SEVERITY_V = 1;
    private final int LOG_SEVERITY_D = 2;
    private final int LOG_SEVERITY_I = 3;
    private final int LOG_SEVERITY_W = 4;
    private final int LOG_SEVERITY_E = 5;
    private int logMethod = Constants.LOG_METHOD_LOGCAT;


    /*============================================================================================*/
    /* Class Methods */

    /** Constructor */
    public BluetoothButton_D15N(Context appContext, Handler parentHandler, int logMethod) {
        try {
            this.appContextRef = new WeakReference<Context>(appContext);
            this.parentHandler = parentHandler;
            this.logMethod = logMethod;

            this.gattCallback = new GattCallback();

            logV("Instance created.");
        } catch (Exception e) {
            logE("Exception caught creating an instance, aborting.");
        }
    }

    /** Method to start this instance */
    public boolean start() {
        final String TAGG = "start: ";

        if (!initializeBleAdapter()) {
            logE(TAGG+"Failed to get BluetoothAdapter, unable to start device. Aborting!");
            return false;
        }

//        if (!initializeBluetoothDevice(deviceMacAddress)) {
//            logE(TAGG+"Failed to get BluetoothDevice. Aborting!");  //TODO: abort or keep trying? perhaps device is out of range or battery is dead?
//            return false;
//        }

        mBluetoothGatt = this.mBluetoothDevice.connectGatt(appContextRef.get().getApplicationContext(),
                true,
                gattCallback,
                BluetoothDevice.TRANSPORT_AUTO);
        mBluetoothGatt.connect();

        switch (mBluetoothManager.getConnectionState(mBluetoothDevice, BluetoothProfile.GATT)) {
            case BluetoothProfile.STATE_CONNECTED:
                logD(TAGG+"GATT connected.");
                break;
            case BluetoothProfile.STATE_DISCONNECTED:
                logW(TAGG+"GATT disconnected.");
                break;
            default:
                logW(TAGG+"GATT status unknown.");
        }

        return true;
    }

    /** Method to cleanup the instance */
    public void cleanup() {
        final String TAGG = "cleanup: ";

        gattCallback = null;

        mBluetoothDevice = null;
        mBluetoothAdapter = null;
        mBluetoothAdapter = null;

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

        return true;    //TODO
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
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        // Ensure Bluetooth is available on the device and it is enabled
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            logE(TAGG + "Bluetooth adapter is either unavailable or not enabled.");
            return false;
        } else {
            logD(TAGG + "Bluetooth initialized.");
            return true;
        }
    }

    /** Initialize our bluetooth device */
    private boolean initializeBluetoothDevice(String macAddress) {
        final String TAGG = "initializeBluetoothDevice: ";

        if (mBluetoothAdapter == null) {
            logE(TAGG+"No BluetoothAdapter instance, aborting.");
            return false;
        }

        mBluetoothDevice = null;

        mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(macAddress.toUpperCase());

        if (mBluetoothDevice == null) {
            logE(TAGG+"Failed to get BluetoothDevice.");
            return false;
        } else {
            logD(TAGG+"Got BluetoothDevice: "+mBluetoothDevice.getAddress()+" ("+mBluetoothDevice.getName()+")");
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

    /** Callback class for scanning for our button device.
     * Include in scan invocation like: mBluetoothAdapter.startLeScan(...) */
    private class LeScanCallback implements BluetoothAdapter.LeScanCallback {
        final String TAGG = LeScanCallback.class.getSimpleName()+": ";

        /** Constructor */
        public LeScanCallback() {
            logV(this.TAGG+"Callback instance created.");
        }

        @Override
        public void onLeScan(BluetoothDevice bluetoothDevice, int i, byte[] bytes) {
            final String TAGG = this.TAGG+"onLeScan: ";

            if (bluetoothDevice.getName() != null) {
                logV(TAGG+"BluetoothDevice: "+bluetoothDevice.getName());
            }
        }
    }

    /** Callback class for interacting with GATT on the button device.
     * Include in connect invocation like: mBluetoothDevice.connectGatt(...) */
    private class GattCallback extends BluetoothGattCallback {
        final String TAGG = GattCallback.class.getSimpleName()+": ";

        /** Constructor */
        public GattCallback() {
            logV(this.TAGG+"Callback instance created.");
        }

        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            final String TAGG = this.TAGG+"onConnectionStateChange: ";

            logV(TAGG+"Invoked.");

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
            final String TAGG = this.TAGG+"onServicesDiscovered: ";

            logV(TAGG+"Invoked.");

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
            final String TAGG = this.TAGG+"onCharacteristicRead: ";

            logV(TAGG+"Invoked.");
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            final String TAGG = this.TAGG+"onCharacteristicWrite: ";

            logV(TAGG+"Invoked.");
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            final String TAGG = this.TAGG+"onCharacteristicChanged: ";

            logV(TAGG+"Invoked.");
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
            final String TAGG = this.TAGG+"onReadRemoteRssi: ";

            logV(TAGG+"Invoked.");
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
