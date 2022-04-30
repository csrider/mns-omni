package com.messagenetsystems.evolution2.services;

/* HealthService class.
 * Intended to run as a service and spawn child processes (threads, services, receivers, etc.) to monitor the general health of the device, app and its components.
 * Those child processes get health status in a variety of ways (receipt of system broadcasts, thread-polling, etc.).
 * As data is gathered and analyzed, that data is saved into global static values for anyone to easily use with minimal effort.
 * Then other processes here can do with that data whatever is needed.
 *
 * NOTE: If you're looking for where the on-screen status bar gets updated, refer to OmniStatusBarThread, which
 * is what takes the data we gather here and puts it onto the screen.
 *
 * NOTE: There are some services elsewhere which monitor their own threads and such, so be careful not to do double duty here!
 *
 * Schemas for various monitoring regimes:
 *  - Process monitoring...
 *      - HealthThreadHeartbeat
 *      - HealthProcessStatus
 *  - Energy monitoring...
 *      - HealthReceiverEnergyStates:   Receiver for getting system broadcasts about changes in energy states, and setting data and flags appropriately.
 *      - HealthThreadEnergy:           Thread for getting data not covered by the receiver, and for actually doing stuff with whatever data/flags we have.
 *  - Device storage available / clearing old files
 *      - Receiver for getting system broadcasts about storage states -- DEPRECATED
 *      - Thread for getting data not covered by the receiver, and for actually doing stuff with whatever data/flags we have.
 *  - Display monitoring
 *      - Receiver for getting system broadcasts about display/screen states, and setting flags appropriately.
 *      - Thread for getting data not covered by the receiver, and for actually doing stuff with whatever data/flags we have.
 *  - Network strength / connectivity / repair. TODO
 *  - Uptime and rebooting as needed. TODO
 *
 * Revisions:
 *  2020.05.14      Chris Rider     Created (based on DeliveryService as a template).
 *  2020.05.15      Chris Rider     Added energy stuff.
 *  2020.05.20      Chris Rider     Added storage-space and display-screen receiver stuff.
 *  2020.05.22      Chris Rider     Added Handler case and methods for updating global values.
 *  2020.05.26      Chris Rider     Added storage health stuff.
 *  2020.06.03      Chris Rider     Added heartbeat thread, HealthThreadHeartbeat.
 *  2020.07.26      Chris Rider     Added process status monitoring thread, HealthThreadProcessStatus. Also started migrating logging INT to BYTE.
 *                                  Got rid of MonitorChildProcesses, as we will begin using the centralized HealthThreadProcessStatus instead.
 *  2020.08.04      Chris Rider     Reworked thread start methods to use new ThreadUtils method and simplified them. Added processing-time analysis to help optimization efforts.
 *  2020.08.11      Chris Rider     Implemented (lower) thread priorities
 */

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.OmniApplication;
import com.messagenetsystems.evolution2.models.ProcessStatus;
import com.messagenetsystems.evolution2.receivers.DisplayStateReceiver;
import com.messagenetsystems.evolution2.receivers.HealthReceiverEnergyStates;
import com.messagenetsystems.evolution2.threads.HealthThreadEnergy;
import com.messagenetsystems.evolution2.threads.HealthThreadHeartbeat;
import com.messagenetsystems.evolution2.threads.HealthThreadProcessStatus;
import com.messagenetsystems.evolution2.threads.HealthThreadStorage;
import com.messagenetsystems.evolution2.utilities.DatetimeUtils;
import com.messagenetsystems.evolution2.utilities.EnergyUtils;
import com.messagenetsystems.evolution2.utilities.StorageUtils;
import com.messagenetsystems.evolution2.utilities.SystemUtils;
import com.messagenetsystems.evolution2.utilities.ThreadUtils;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.Locale;


public class HealthService extends Service {
    private final String TAG = this.getClass().getSimpleName();

    // Constants...
    public static final int HANDLER_ACTION_UPDATE_GLOBAL_VALUES_POWER = 1;
    public static final int HANDLER_ACTION_UPDATE_GLOBAL_VALUES_STORAGE = 2;

    public static final int ENERGY_BATTERY_PERCENT_UNKNOWN = -1;
    public static final int ENERGY_VOLTAGE_UNKNOWN = EnergyUtils.VOLTAGE_UNKNOWN;
    public static final int ENERGY_AMPERAGE_UNKNOWN = EnergyUtils.AMPERAGE_UNKNOWN;
    public static final int ENERGY_POWER_SUPPLY_UNKNOWN = EnergyUtils.POWER_SUPPLY_UNKNOWN;
    public static final int ENERGY_POWER_SUPPLY_NONE = EnergyUtils.POWER_SUPPLY_NONE;
    public static final int ENERGY_POWER_SUPPLY_DC_SOCKET = EnergyUtils.POWER_SUPPLY_DC_SOCKET;
    public static final int ENERGY_POWER_SUPPLY_USB_SOCKET = EnergyUtils.POWER_SUPPLY_USB_SOCKET;
    public static final int ENERGY_POWER_SUPPLY_ANY = EnergyUtils.POWER_SUPPLY_ANY;
    public static final int ENERGY_BATTERY_HEALTH_UNKNOWN = EnergyUtils.BATTERY_HEALTH_UNKNOWN;
    public static final int ENERGY_BATTERY_HEALTH_GOOD = EnergyUtils.BATTERY_HEALTH_GOOD;
    public static final int ENERGY_BATTERY_HEALTH_BAD = EnergyUtils.BATTERY_HEALTH_BAD;

    public static final int STORAGE_SPACE_STATE_EXTERNAL_UNKNOWN = StorageUtils.SPACE_STATE_EXTERNAL_UNKNOWN;
    public static final int STORAGE_SPACE_STATE_EXTERNAL_OK = StorageUtils.SPACE_STATE_EXTERNAL_OK;
    public static final int STORAGE_SPACE_STATE_EXTERNAL_LOW = StorageUtils.SPACE_STATE_EXTERNAL_LOW;
    public static final int STORAGE_SPACE_STATE_EXTERNAL_FULL = StorageUtils.SPACE_STATE_EXTERNAL_FULL;

    public static final String BUNDLE_KEYNAME_STORAGE_BYTES_FREE_EXTERNAL = "storageBytesFreeExternal";

    public static final int DISPLAY_SCREEN_STATE_UNKNOWN = -1;
    public static final int DISPLAY_SCREEN_STATE_ON = 1;
    public static final int DISPLAY_SCREEN_STATE_OFF = 2;

    // Logging stuff...
    private final byte LOG_SEVERITY_V = 1;
    private final byte LOG_SEVERITY_D = 2;
    private final byte LOG_SEVERITY_I = 3;
    private final byte LOG_SEVERITY_W = 4;
    private final byte LOG_SEVERITY_E = 5;
    private byte logMethod = Constants.LOG_METHOD_LOGCAT;

    // Public/global stuff...
    public static volatile int energy_rawBatteryPercent;
    public static volatile int energy_rawBatteryPercent_prev;
    public static volatile Date energy_batteryPercentLastChangedDate;
    public static volatile String energy_hrBatteryPercent;                                          //intended to store latest data gathered, in human-readable / presentable format
    public static volatile int energy_rawMilliVoltage;                                              //intended to store latest data gathered
    public static volatile int energy_rawMilliVoltage_prev;                                         //intended to store previous data gathered, in case you want to work out trends
    public static volatile Date energy_voltageLastChangedDate;
    public static volatile String energy_hrVoltage;                                                 //intended to store latest data gathered, in human-readable / presentable format
    public static volatile int energy_rawMilliAmpsAtBattery;                                        //intended to store latest data gathered
    public static volatile int energy_rawMilliAmpsAtBattery_prev;                                   //intended to store previous data gathered, in case you want to work out trends
    public static volatile String energy_hrMilliAmpsAtBattery;                                      //intended to store latest data gathered, in human-readable / presentable format
    public static volatile int energy_rawPowerSupplyWhichConnected;                                 //intended to store latest data gathered from BatteryManager.EXTRA_PLUGGED
    public static volatile int energy_rawChargingStatus;                                            //intended to store latest data gathered from BatteryManager.EXTRA_STATUS
    public static volatile boolean energy_isBatteryCharging;                                        //intended to store latest derived (EXTRA_STATUS can show "charging" even if amperage is insufficient to actually charge it)
    public static volatile int energy_derivedBatteryHealthCondition;                                //intended to store latest derived from constants above
    public static volatile int energy_derivedBatteryChargeTrend;                                    //intended to store latest derived from constants above

    public static volatile long storage_rawAvailableBytes_external;
    public static volatile String storage_hrAvailableBytes_external;
    public static volatile int storage_spaceState_external;

    public static volatile int displayScreenState;

    // Local stuff...
    private WeakReference<Context> appContextRef;                                                   //since this thread is very long running, we prefer a weak context reference
    private OmniApplication omniApplication;
    public volatile boolean hasFullyStarted;

    private int tid = 0;

    private Handler healthServiceAndroidMessageHandler;                                             //message handler for passing to child processes, so they know how to talk back to this service

    private HealthThreadProcessStatus healthThreadProcessStatus;
    private IntentFilter healthThreadProcessStatusIntentFilter;
    private int threadFrequencySecs_healthThreadProcessStatus = 3;

    private HealthReceiverEnergyStates healthReceiverEnergyStates;
    private IntentFilter healthReceiverEnergyStatesIntentFilter;

    public volatile boolean isThreadAlive_healthThreadEnergy;
    private HealthThreadEnergy healthThreadEnergy;
    private int threadFrequencySecs_healthThreadEnergy = 10;            //TODO: move to strings or constants

    public volatile boolean isThreadAlive_healthyStorage;
    private HealthThreadStorage healthThreadStorage;
    private int threadFrequencySecs_healthyStorage = 30;                //TODO: move to strings or constants

    public volatile boolean isThreadAlive_healthThreadHeartbeat;
    private HealthThreadHeartbeat healthThreadHeartbeat;
    private int threadFrequencySecs_healthThreadHeartbeat = 10;         //TODO: move to strings or constants

    private DisplayStateReceiver displayStateReceiver;
    private IntentFilter displayStateReceiverIntentFilter;


    /** Constructors (singleton pattern) */
    public HealthService(Context appContext) {
        super();
    }
    public HealthService() {
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

        this.logMethod = (byte) Constants.LOG_METHOD_FILELOGGER;

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
        this.isThreadAlive_healthyStorage = false;

        energy_rawBatteryPercent = ENERGY_BATTERY_PERCENT_UNKNOWN;
        energy_rawBatteryPercent_prev = ENERGY_BATTERY_PERCENT_UNKNOWN;
        energy_batteryPercentLastChangedDate = new Date();
        energy_hrBatteryPercent = "";
        energy_rawMilliVoltage = ENERGY_VOLTAGE_UNKNOWN;
        energy_rawMilliVoltage_prev = ENERGY_VOLTAGE_UNKNOWN;
        energy_voltageLastChangedDate = new Date();
        energy_hrVoltage = "";
        energy_rawMilliAmpsAtBattery = ENERGY_AMPERAGE_UNKNOWN;
        energy_rawMilliAmpsAtBattery_prev = ENERGY_AMPERAGE_UNKNOWN;
        energy_hrMilliAmpsAtBattery = "";
        energy_rawPowerSupplyWhichConnected = SystemUtils.getWhichPowerPlugged(getApplicationContext(), ENERGY_POWER_SUPPLY_NONE);
        energy_rawChargingStatus = BatteryManager.BATTERY_STATUS_UNKNOWN;
        energy_isBatteryCharging = false;
        energy_derivedBatteryHealthCondition = ENERGY_BATTERY_HEALTH_UNKNOWN;

        storage_rawAvailableBytes_external = -1;
        storage_hrAvailableBytes_external = "";
        storage_spaceState_external = STORAGE_SPACE_STATE_EXTERNAL_UNKNOWN;

        displayScreenState = DISPLAY_SCREEN_STATE_UNKNOWN;

        this.healthServiceAndroidMessageHandler = new HealthServiceHandler();

        this.healthThreadProcessStatus = new HealthThreadProcessStatus(getApplicationContext(), logMethod, healthServiceAndroidMessageHandler, threadFrequencySecs_healthThreadProcessStatus);
        this.healthThreadProcessStatusIntentFilter = new IntentFilter();

        this.healthThreadStorage = new HealthThreadStorage(getApplicationContext(), logMethod, healthServiceAndroidMessageHandler, threadFrequencySecs_healthyStorage);

        this.healthThreadEnergy = new HealthThreadEnergy(getApplicationContext(), logMethod, healthServiceAndroidMessageHandler, threadFrequencySecs_healthThreadEnergy);
        this.healthReceiverEnergyStates = new HealthReceiverEnergyStates(getApplicationContext(), logMethod, healthServiceAndroidMessageHandler);
        this.healthReceiverEnergyStatesIntentFilter = new IntentFilter();
        this.healthReceiverEnergyStatesIntentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);              //when something about the battery changes (voltage, level, temp, health, etc.)
        this.healthReceiverEnergyStatesIntentFilter.addAction(Intent.ACTION_BATTERY_LOW);                  //when battery level becomes low
        this.healthReceiverEnergyStatesIntentFilter.addAction(Intent.ACTION_BATTERY_OKAY);                 //when battery level recovers from being low
        this.healthReceiverEnergyStatesIntentFilter.addAction(Intent.ACTION_POWER_CONNECTED);              //when any power sources are connected
        this.healthReceiverEnergyStatesIntentFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);           //when all power sources are disconnected

        this.displayStateReceiver = new DisplayStateReceiver(getApplicationContext(), logMethod);
        this.displayStateReceiverIntentFilter = new IntentFilter();
        this.displayStateReceiverIntentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        this.displayStateReceiverIntentFilter.addAction(Intent.ACTION_SCREEN_ON);

        this.healthThreadHeartbeat = new HealthThreadHeartbeat(getApplicationContext(), logMethod, healthServiceAndroidMessageHandler, threadFrequencySecs_healthThreadHeartbeat);

        // Inform processStatus about how many children processes there should be here to account for
        // Count: healthThreadProcessStatus, healthThreadStorage, healthThreadEnergy, healthThreadHeartbeat
        omniApplication.processStatusList.setNumberOfExpectedChildrenForProcess(this.getClass(), 4);
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

        ////////////////////////////////////////////////////////////////////////////////////////////
        // Register receivers...
        registerReceiver(healthReceiverEnergyStates, healthReceiverEnergyStatesIntentFilter);
        registerReceiver(displayStateReceiver, displayStateReceiverIntentFilter);

        ////////////////////////////////////////////////////////////////////////////////////////////
        // Start services...
        // Remember that any Service you start here (startService) will exist in the same thread as this one (and whichever is upstream -likely the main thread)!

        ////////////////////////////////////////////////////////////////////////////////////////////
        // Start threads...
        //startThread_processStatus(true);  //TODO: Need to figure out why this produces random ANRs
        startThread_heartbeat(true);
        startThread_energy(true);
        startThread_storage(true);

        // Finish service startup...
        this.hasFullyStarted = true;    //note: this is assumed, as threads above are asynchronous
        omniApplication.processStatusList.recordProcessStart(this.getClass(), tid);
        logI(TAGG+"Service started.");
        omniApplication.appendNotificationWithText(TAG+" started. (tid:"+tid+")");    // Update notification that everything is started and running

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
        omniApplication.appendNotificationWithText("HealthService died! ("+new Date().toString()+")");

        // Unregister any receivers
        unregisterReceiver(healthReceiverEnergyStates);
        this.healthReceiverEnergyStates = null;

        unregisterReceiver(displayStateReceiver);
        this.displayStateReceiver = null;

        // Stop any stuff we started
        if (this.healthThreadProcessStatus != null) {
            this.healthThreadProcessStatus.cleanup();
            this.healthThreadProcessStatus = null;
        }
        if (this.healthThreadStorage != null) {
            this.healthThreadStorage.cleanup();
            this.healthThreadStorage = null;
        }
        if (this.healthThreadEnergy != null) {
            this.healthThreadEnergy.cleanup();
            this.healthThreadEnergy = null;
        }
        if (this.healthThreadHeartbeat != null) {
            this.healthThreadHeartbeat.cleanup();
            this.healthThreadHeartbeat = null;
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

    private void startThread_processStatus(boolean restartIfRunning) {
        final String TAGG = "startThread_processStatus: ";
        logV(TAGG+"Processing start of HealthThreadProcessStatus.");

        try {
            if (healthThreadProcessStatus != null && healthThreadProcessStatus.isThreadRunning()) {
                //thread is running...
                if (restartIfRunning) {
                    logI(TAGG + "Thread is running, so we will stop/reinitialize/re-start it now...");
                    healthThreadProcessStatus.cleanup();
                } else {
                    logI(TAGG + "Thread is running, so we will leave it be and do nothing.");
                    return;
                }
            } else {
                //thread is not running...
                logI(TAGG+"Thread is not running, so we will initialize and start it anew now...");
            }

            healthThreadProcessStatus = new HealthThreadProcessStatus(appContextRef.get(), logMethod, healthServiceAndroidMessageHandler, threadFrequencySecs_healthThreadProcessStatus);
            ThreadUtils.doStartThread(this, healthThreadProcessStatus, ThreadUtils.SPAWN_NEW_THREAD_TRUE, ThreadUtils.PRIORITY_MINIMUM);
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }
    }

    private void startThread_energy(boolean restartIfRunning) {
        final String TAGG = "startThread_energy: ";
        logV(TAGG+"Processing start of HealthThreadEnergy.");

        try {
            if (healthThreadEnergy != null && healthThreadEnergy.isThreadRunning()) {
                //thread is running...
                if (restartIfRunning) {
                    logI(TAGG + "Thread is running, so we will stop/reinitialize/re-start it now...");
                    healthThreadEnergy.cleanup();
                } else {
                    logI(TAGG + "Thread is running, so we will leave it be and do nothing.");
                    return;
                }
            } else {
                //thread is not running...
                logI(TAGG+"Thread is not running, so we will initialize and start it anew now...");
            }

            healthThreadEnergy = new HealthThreadEnergy(appContextRef.get(), logMethod, healthServiceAndroidMessageHandler, threadFrequencySecs_healthThreadEnergy);
            ThreadUtils.doStartThread(this, healthThreadEnergy, ThreadUtils.SPAWN_NEW_THREAD_TRUE, ThreadUtils.PRIORITY_MINIMUM);
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }
    }

    private void startThread_storage(boolean restartIfRunning) {
        final String TAGG = "startThread_storage: ";
        logV(TAGG+"Processing start of HealthThreadStorage.");

        try {
            if (healthThreadStorage != null && healthThreadStorage.isThreadRunning()) {
                //thread is running...
                if (restartIfRunning) {
                    logI(TAGG + "Thread is running, so we will stop/reinitialize/re-start it now...");
                    healthThreadStorage.cleanup();
                } else {
                    logI(TAGG + "Thread is running, so we will leave it be and do nothing.");
                    return;
                }
            } else {
                //thread is not running...
                logI(TAGG+"Thread is not running, so we will initialize and start it anew now...");
            }

            healthThreadStorage = new HealthThreadStorage(appContextRef.get(), logMethod, healthServiceAndroidMessageHandler, threadFrequencySecs_healthyStorage);
            ThreadUtils.doStartThread(this, healthThreadStorage, ThreadUtils.SPAWN_NEW_THREAD_TRUE, ThreadUtils.PRIORITY_MINIMUM);
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }
    }

    private void startThread_heartbeat(boolean restartIfRunning) {
        final String TAGG = "startThread_heartbeat: ";
        logV(TAGG+"Processing start of HealthThreadHeartbeat.");

        try {
            if (healthThreadHeartbeat != null && healthThreadHeartbeat.isThreadRunning()) {
                //thread is running...
                if (restartIfRunning) {
                    logI(TAGG + "Thread is running, so we will stop/reinitialize/re-start it now...");
                    healthThreadHeartbeat.cleanup();
                } else {
                    logI(TAGG + "Thread is running, so we will leave it be and do nothing.");
                    return;
                }
            } else {
                //thread is not running...
                logI(TAGG+"Thread is not running, so we will initialize and start it anew now...");
            }

            healthThreadHeartbeat = new HealthThreadHeartbeat(appContextRef.get(), logMethod, healthServiceAndroidMessageHandler, threadFrequencySecs_healthThreadEnergy);
            ThreadUtils.doStartThread(this, healthThreadHeartbeat, ThreadUtils.SPAWN_NEW_THREAD_TRUE, ThreadUtils.PRIORITY_LOW);
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }
    }

    /** Update the global power-related values with whatever data we have.
     * NOTE: This should be the main mechanism for updating our globals!
     * Intent arg is intended to be the ACTION_POWER* and ACTION_BATTERY* intent.
     * If null arg is provided, data will be sourced from other means.
     * @param intent If provided, tries to get data from Intent, otherwise uses other means to get data
     */
    private void updateGlobalValues_power(@Nullable Intent intent) {
        final String TAGG = "updateGlobalValues_power: ";
        long startedTime = new Date().getTime();

        if (intent == null) {
            // The needed intent is sticky, so its state should always be around for us to use.
            // Just temporarily register a receiver to get access to it...
            logV(TAGG+"Provided intent is null, attempting to get sticky intent.");
            intent = appContextRef.get().getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        }

        if (intent == null) {
            //TODO: EnergyUtils methods?
            logE(TAGG+"Failed to get an intent, so no way to get data, aborting!");
        } else {
            try {
                if (intent.getAction() != null) {
                    if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
                        // Preserve latest data as previous values...
                        energy_rawBatteryPercent_prev = energy_rawBatteryPercent;
                        energy_rawMilliVoltage_prev = energy_rawMilliVoltage;
                        energy_rawMilliAmpsAtBattery_prev = energy_rawMilliAmpsAtBattery;

                        // Gather all the data...
                        int rawMilliAmpsAtBattery = EnergyUtils.getCurrentAmperage_milli(appContextRef.get().getApplicationContext());
                        int rawBatteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                        int rawBatteryScale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                        int rawBatteryPercent = EnergyUtils.getBatteryPercent_integer(rawBatteryLevel, rawBatteryScale);
                        int rawBatteryTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);                                                      //Probably not reliable, so we just don't use it
                        int rawMilliVoltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                        int rawBattHealth = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN);                         //Probably not reliable, so we use our own derivedBatteryHealth instead
                        int rawChargingStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                        int rawChargePlugState = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, HealthService.ENERGY_POWER_SUPPLY_UNKNOWN);

                        // If battery percent has now changed, update its last-changed date directly into globals (just to be simple)...
                        if (rawBatteryPercent != energy_rawBatteryPercent) {
                            energy_batteryPercentLastChangedDate = new Date();
                        }

                        // If battery voltage has now changed, update its last-changed date directly into globals (just to be simple)...
                        if (rawMilliVoltage != energy_rawMilliVoltage_prev) {
                            energy_voltageLastChangedDate = new Date();
                        }

                        // Derive whatever data we need to from what we've gathered...
                        boolean derivedIsBatteryCharging = deriveIsBatteryCharging(rawChargingStatus, rawMilliAmpsAtBattery, rawChargePlugState, rawMilliVoltage);
                        int derivedBatteryHealth = deriveBatteryHealth(derivedIsBatteryCharging, rawMilliAmpsAtBattery, rawMilliVoltage, rawBatteryPercent, energy_batteryPercentLastChangedDate);

                        // Save all that data to globals...
                        energy_rawMilliAmpsAtBattery = rawMilliAmpsAtBattery;
                        energy_rawBatteryPercent = rawBatteryPercent;
                        energy_rawMilliVoltage = rawMilliVoltage;
                        energy_rawChargingStatus = rawChargingStatus;
                        energy_isBatteryCharging = derivedIsBatteryCharging;
                        energy_derivedBatteryHealthCondition = derivedBatteryHealth;
                        energy_rawPowerSupplyWhichConnected = rawChargePlugState;

                        energy_hrMilliAmpsAtBattery = Integer.toString(rawMilliAmpsAtBattery) + "mA";
                        energy_hrBatteryPercent = EnergyUtils.getBatteryPercent_human(rawBatteryLevel, rawBatteryScale);
                        energy_hrVoltage = String.format(Locale.US, "%.2f", ((float) rawMilliVoltage / 1000)) + "v";

                        // Debugging/testing log of all our data...
                        logV(TAGG+"Information available...\n" +
                                "Level: "+Integer.toString(rawBatteryLevel)+" out of "+Integer.toString(rawBatteryScale)+" ("+energy_hrBatteryPercent+") (last changed "+energy_batteryPercentLastChangedDate.toString()+")\n" +
                                "Temp: "+Integer.toString(rawBatteryTemp)+" (likely unsupported in most Omni tablets)\n" +
                                "Voltage: "+Integer.toString(rawMilliVoltage)+"mv ("+energy_hrVoltage+") (last changed "+energy_voltageLastChangedDate.toString()+")\n" +
                                "Amperage: "+energy_hrMilliAmpsAtBattery+"\n" +
                                "Charging: "+Boolean.toString(energy_isBatteryCharging)+" (our own derived value)\n" +
                                "Status: "+Integer.toString(rawChargingStatus)+" ("+EnergyUtils.getEnglish_batteryChargingState(rawChargingStatus)+") (may be unreliable)\n" +
                                "ChargePlug: "+Integer.toString(rawChargePlugState)+" ("+EnergyUtils.getEnglish_chargePlugState(rawChargePlugState)+")\n" +
                                "Raw Health: "+Integer.toString(rawBattHealth)+" ("+EnergyUtils.getEnglish_batteryHealthState_raw(rawBattHealth)+") (likely unsupported in most Omni tablets)\n" +
                                "Health: "+Integer.toString(derivedBatteryHealth)+" ("+EnergyUtils.getEnglish_batteryHealthState(derivedBatteryHealth)+") (our own derived value)\n");
                    }
                    //TODO other intent actions?
                } else {
                    logW(TAGG+"Failed to get action from provided intent. Recursing back in to use alternative data source.");
                    updateGlobalValues_power(null);
                }
            } catch (Exception e) {
                logE(TAGG + "Exception caught: " + e.getMessage());
            }
        }

        ThreadUtils.analyzeProcessingTime(this.getClass().getSimpleName(), TAGG, startedTime);
    }

    /** Analyze provided data and determine whether battery is actually charging.
     * This is intended to supplement the native Android value provided by BatteryManager.EXTRA_STATUS, as it's not always what we want.
     * @param rawChargingStatus Native Android value provided by BatteryManager.EXTRA_STATUS
     * @param rawMilliAmpsAtBattery Milliamps
     * @param rawChargePlugState Native Android value provided by BatteryManager.EXTRA_PLUGGED
     * @param rawMilliVoltage Voltage provided by BatteryManager.EXTRA_VOLTAGE
     * @return Whether we determined battery is actually charging or not
     */
    private boolean deriveIsBatteryCharging(int rawChargingStatus, int rawMilliAmpsAtBattery, int rawChargePlugState, int rawMilliVoltage) {
        final String TAGG = "deriveIsBatteryCharging: ";
        long startedTime = new Date().getTime();
        boolean ret;

        // Derive actual charging state
        switch (rawChargingStatus) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                // "Charging" can be the reported case even if insufficient power is connected to actually charge the battery, so need to check that...
                if (rawMilliAmpsAtBattery > 0) {
                    ret = true;
                } else {
                    ret = false;
                }

                // Sometimes amperage reporting is weird or unreliable, so if that seems to be the case, look at voltage as a backup-plan until amperage reporting returns to normal
                if (!ret) {
                    if (rawMilliVoltage >= 3750) {
                        ret = true;
                    } else {
                        ret = false;
                    }
                }
                break;
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                ret = false;
                break;
            case BatteryManager.BATTERY_STATUS_FULL:
                // Could be full but just unplugged and thus discharging, so check power connection (amperage may be unreliable at full charge)
                switch (rawChargePlugState) {
                    case BatteryManager.BATTERY_PLUGGED_AC:
                    case BatteryManager.BATTERY_PLUGGED_USB:
                    case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                        ret = true;
                        break;
                    default:
                        ret = false;
                        break;
                }
            case BatteryManager.BATTERY_STATUS_UNKNOWN:
            default:
                logW(TAGG+"Unknown battery charge status, assuming not charging.");
                ret = false;
                break;
        }

        ThreadUtils.analyzeProcessingTime(this.getClass().getSimpleName(), TAGG, startedTime);

        logV(TAGG+"Returning: "+Boolean.toString(ret));
        return ret;
    }

    /** Analyze provided data and determine battery health.
     * This is intended to supplement or replace native Android value provided by BatteryManager.EXTRA_HEALTH, as it hasn't proven to be reliable yet.
     * @param energy_isBatteryCharging Whether battery is deemed to be charging or not
     * @param rawMilliAmpsAtBattery Milliamps
     * @param rawMilliVoltage Voltage provided by BatteryManager.EXTRA_VOLTAGE
     * @param rawBatteryPercent Integer representation of battery charge percentage
     * @param percentLastChangedDate The last time battery charge percentage changed
     * @return BATTERY_HEALTH constant (defined in EnergyUtils and duplicated here locally for ease-of-use)
     */
    private int deriveBatteryHealth(boolean energy_isBatteryCharging, int rawMilliAmpsAtBattery, int rawMilliVoltage, int rawBatteryPercent, Date percentLastChangedDate) {
        final String TAGG = "deriveBatteryHealth: ";
        long startedTime = new Date().getTime();
        int ret = ENERGY_BATTERY_HEALTH_UNKNOWN;

        // Notes:
        // Many devices don't report actual health reliably via EXTRA_HEALTH, so we need to derive somehow...
        // This seems to be possible by looking at voltage, amperage, and percentage...

        // First of all, if the Android API reports actual bad health, let's just trust that
        //if () TODO


        // If battery charge level has not changed in a long time, something is not right
        int timeDiffWithinOK = 15;
        String timeDiffUnits = "minutes";
        DatetimeUtils datetimeUtils = new DatetimeUtils(appContextRef.get().getApplicationContext(), logMethod);
        if (energy_isBatteryCharging
                && rawBatteryPercent < 99
                && !datetimeUtils.datesAreWithinMins(percentLastChangedDate, new Date(), timeDiffWithinOK)) {
            logI(TAGG + "EXPERIMENTAL: Battery charging but not full, and charge level ("+rawBatteryPercent+"%) hasn't increased in over "+timeDiffWithinOK+" "+timeDiffUnits+". Something wrong?");
            ret = ENERGY_BATTERY_HEALTH_BAD;
        }

        // If battery is charging and not full, but voltage is high and amps are low, then battery capacity has degraded
        else if (energy_isBatteryCharging
                && rawBatteryPercent < 80
                && rawMilliVoltage > 4000
                && rawMilliAmpsAtBattery < 100) {
            logI(TAGG + "EXPERIMENTAL: Battery charging but not full, even though voltage is high and amps are low. Capacity has degraded?");
            ret = ENERGY_BATTERY_HEALTH_BAD;
        }

        // If battery is discharging and it has unusually low voltage for the charge level, then battery is possibly failing
        else if (!energy_isBatteryCharging
                && rawBatteryPercent > 10
                && rawMilliVoltage < 3475) {
            logI(TAGG+"EXPERIMENTAL: Battery discharging and voltage is lower than expected for charge level. Failing battery?");
            ret = ENERGY_BATTERY_HEALTH_BAD;
        }

        else {
            logI(TAGG+"EXPERIMENTAL: Battery health undetermined, assuming good.");
            ret = ENERGY_BATTERY_HEALTH_GOOD;
        }

        ThreadUtils.analyzeProcessingTime(this.getClass().getSimpleName(), TAGG, startedTime);

        logV(TAGG+"Returning: "+Integer.toString(ret));
        return ret;
    }

    /** Update the global storage-related values with whatever data we have.
     * NOTE: This should be the main mechanism for updating our globals!
     * Intent arg is intended to be the ACTION_POWER* and ACTION_BATTERY* intent.
     * If null arg is provided, data will be sourced from other means.
     * @param storageDataBundle If provided, tries to get data from Intent, otherwise uses other means to get data
     */
    private void updateGlobalValues_storage(@Nullable Bundle storageDataBundle) {
        final String TAGG = "updateGlobalValues_storage: ";
        long startedTime = new Date().getTime();
        long availableBytesExternal;

        // Get all the data we need...
        if (storageDataBundle == null) {
            logE(TAGG+"This method requires a Bundle be provided for now, aborting.");
            return;

            //TODO: acquire missing data via other means
        } else {
            //extract data from bundle
            availableBytesExternal = storageDataBundle.getLong(BUNDLE_KEYNAME_STORAGE_BYTES_FREE_EXTERNAL);
        }

        try {
            // Save values to globals
            storage_rawAvailableBytes_external = availableBytesExternal;
            storage_hrAvailableBytes_external = StorageUtils.getBytesWithHumanUnit(availableBytesExternal, 0);

            // Derive global values
            storage_spaceState_external = deriveStorageSpaceStateExternal(availableBytesExternal);

        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }

        ThreadUtils.analyzeProcessingTime(this.getClass().getSimpleName(), TAGG, startedTime);
    }

    /** Analyze provided data and determine storage space health.
     * @param bytesFree Available free bytes
     * @return An appropriate STORAGE_SPACE_STATE constant defined in HealthService
     */
    private int deriveStorageSpaceStateExternal(long bytesFree) {
        final String TAGG = "deriveStorageSpaceStateExternal: ";
        long startedTime = new Date().getTime();
        int ret = STORAGE_SPACE_STATE_EXTERNAL_UNKNOWN;

        int spaceLowThreshold = Constants.Health.Storage.EXTERNAL_LOW_THRESHOLD_MB * 1024 * 1024;
        int spaceFullThreshold = Constants.Health.Storage.EXTERNAL_FULL_THRESHOLD_MB * 1024 * 1024;

        try {
            if (bytesFree >= spaceLowThreshold) {
                ret = STORAGE_SPACE_STATE_EXTERNAL_OK;
            } else if (bytesFree >= spaceFullThreshold) {
                ret = STORAGE_SPACE_STATE_EXTERNAL_LOW;
            } else if (bytesFree >= 0){
                ret = STORAGE_SPACE_STATE_EXTERNAL_FULL;
            } else {
                ret = STORAGE_SPACE_STATE_EXTERNAL_UNKNOWN;
            }
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }

        ThreadUtils.analyzeProcessingTime(this.getClass().getSimpleName(), TAGG, startedTime);

        logV(TAGG+"Returning: "+Integer.toString(ret)+" ("+StorageUtils.getEnglish_storageSpaceState(ret)+")");
        return ret;
    }


    /*============================================================================================*/
    /* Subclasses */

    /** Handler for working with Android-Messages from child processes. */
    private class HealthServiceHandler extends Handler {
        final String TAGG = this.getClass().getSimpleName() + ": ";

        // Constructor
        public HealthServiceHandler() {
        }

        @Override
        public void handleMessage(Message androidMessage) {
            final String TAGGG = "handleMessage: ";
            long startedTime = new Date().getTime();
            //super.handleMessage(androidMessage);  //TODO: needed??

            // First, check if this service is still running before we do anything with its resources (to avoid null pointer exceptions)
            if (omniApplication == null) {
                logI(TAGG + TAGGG + "Host service has been destroyed, aborting.");
                return;
            }

            // See what our command-request is and handle accordingly
            try {
                switch (androidMessage.arg1) {
                    case HANDLER_ACTION_UPDATE_GLOBAL_VALUES_POWER:
                        logI(TAGG+TAGGG+"Updating global power values.");
                        updateGlobalValues_power( (Intent)androidMessage.obj );
                        break;
                    case HANDLER_ACTION_UPDATE_GLOBAL_VALUES_STORAGE:
                        logI(TAGG+TAGGG+"Updating global storage values.");
                        updateGlobalValues_storage( (Bundle)androidMessage.obj );
                        break;
                    default:
                        logW(TAGG + TAGGG + "Unhandled case (" + String.valueOf(androidMessage.arg1) + "). Aborting.");
                        return;
                }
            } catch (Exception e) {
                logE(TAGG + "Exception caught: " + e.getMessage());
            }

            ThreadUtils.analyzeProcessingTime(this.getClass().getSimpleName(), TAGG, startedTime);
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
