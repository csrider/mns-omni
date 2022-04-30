package com.messagenetsystems.evolution2.threads;

/* OmniStatusBarThread
 * Simply periodic/regular broadcasts to update Omni status (e.g. for top of ClockActivity screen).
 * This really should just take already-derived data from somewhere and just update the status bar -that's it.
 *
 * Usage Example (declare, create, configure, and run):
 *  OmniStatusBarThread omniStatusBarThread;
 *  omniStatusBarThread = new OmniStatusBarThread(getApplicationContext(), Constants.LOG_METHOD_FILELOGGER);
 *  omniStatusBarThread.start();
 *
 * Usage Example (stop the thread-loop and free up resources):
 *  omniStatusBarThread.cleanup();
 *
 * Usage Example (pause processing - may be easily resumed later)
 *  omniStatusBarThread.pauseProcessing();
 *
 * Usage Example (resume processing)
 *  omniStatusBarThread.resumeProcessing();
 *
 * Revisions:
 *  2020.05.12      Chris Rider     Created (copied from DeliveryRotator).
 *  2020.05.13      Chris Rider     Added additional status values, improvements, and now supports text colors.
 *  2020.05.14      Chris Rider     Tweaks to further improve status for certain power and battery states.
 *  2020.05.21      Chris Rider     Renamed from OmniStatusUpdater, and clarifying scope of duty to just updating the status bar and its indicators.
 *  2020.05.24      Chris Rider     Added more data (voltage and health) to on-screen battery status if certain debugging flags are set to true; also tweaked orange color.
 *  2020.05.25      Chris Rider     Tweaked status indicator wording.
 *  2020.05.31      Chris Rider     Uptime running status now supports days for >24 hours.
 *  2020.09.28      Chris Rider     Fixed theoretical potential for uncaught overflow in loop counter.
 */

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.OmniApplication;
import com.messagenetsystems.evolution2.activities.ClockActivity;
import com.messagenetsystems.evolution2.models.OmniMessage;
import com.messagenetsystems.evolution2.services.HealthService;
import com.messagenetsystems.evolution2.utilities.EnergyUtils;
import com.messagenetsystems.evolution2.utilities.NetUtils;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;


public class OmniStatusBarThread extends Thread {
    private final String TAG = this.getClass().getSimpleName();

    // Constants..


    // Logging stuff...
    private final int LOG_SEVERITY_V = 1;
    private final int LOG_SEVERITY_D = 2;
    private final int LOG_SEVERITY_I = 3;
    private final int LOG_SEVERITY_W = 4;
    private final int LOG_SEVERITY_E = 5;
    private int logMethod = Constants.LOG_METHOD_LOGCAT;

    // Local stuff...
    private WeakReference<Context> appContextRef;       //since this thread is very long running, we prefer a weak context reference
    private OmniApplication omniApplication;
    private NetUtils netUtils;

    private Handler androidMsgHandler_DeliveryService;  //reference to DeliveryService's OmniMessageRawHandler, so we can send data to there

    private volatile boolean isStopRequested;           //flag to set/check for the thread to interrupt itself
    private volatile boolean isThreadRunning;           //just a status flag
    private volatile boolean pauseProcessing;           //flag to set if you want to pause processing work (may double as a kind of "is paused" flag)

    private int activeProcessingSleepDuration;          //duration (in milliseconds) to sleep during active processing (to help ensure CPU cycles aren't eaten like crazy)
    private int pausedProcessingSleepDuration;          //duration (in milliseconds) to sleep during paused processing (to help ensure CPU cycles aren't eaten like crazy)

    private long loopIterationCounter;

    private int preemptiveShutdownChargePercent;


    /** Constructor
     * NOTE: If null Handler is provided, we will use Broadcast instead of Message methods. */
    public OmniStatusBarThread(Context appContext, int logMethod, @Nullable Handler deliveryServiceHandler) {
        Log.v(TAG, "Instantiating.");

        this.logMethod = logMethod;

        this.appContextRef = new WeakReference<Context>(appContext);

        try {
            this.omniApplication = ((OmniApplication) appContext);
        } catch (Exception e) {
            logE("Exception caught instantiating "+TAG+": "+e.getMessage());
            return;
        }

        // Get our handlers from parents, so we can send Android-Messages back to them
        if (deliveryServiceHandler != null) {
            this.androidMsgHandler_DeliveryService = deliveryServiceHandler;                            //get our handler from DeliveryService
        } else {
            Log.i(TAG, "Instantiating to send status updates using Broadcast instead of Handler method.");
            this.androidMsgHandler_DeliveryService = null;
        }

        // Initialize values
        this.isStopRequested = false;
        this.isThreadRunning = false;
        this.pauseProcessing = false;
        //this.activeProcessingSleepDuration = 500;
        this.activeProcessingSleepDuration = 1000;
        this.pausedProcessingSleepDuration = 10000;
        this.loopIterationCounter = 0;

        this.preemptiveShutdownChargePercent = 20;

        // Initialize objects
        this.netUtils = new NetUtils(appContext, Constants.LOG_METHOD_FILELOGGER);
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
        logI(TAGG+"Thread starting as process ID #"+ pid);

        boolean showVoltage = false;
        boolean showBatteryHealth = true;

        String batteryStatusString;
        int batteryStatusColor;
        String voltage = "";
        String health = "";

        // As long as our thread is supposed to be running...
        while (!Thread.currentThread().isInterrupted()) {

            // Our thread has started or is still running
            isThreadRunning = true;

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

                    //******************************************************************************
                    // Update battery status
                    if (showVoltage) voltage = " ("+HealthService.energy_hrVoltage+")"; else voltage = "";
                    if (showBatteryHealth) health = " ("+ EnergyUtils.getEnglish_batteryHealthState(HealthService.energy_derivedBatteryHealthCondition)+")"; else health = "";
                    if (HealthService.energy_isBatteryCharging) {
                        if (HealthService.energy_rawBatteryPercent >= 70) {
                            batteryStatusString = "Battery is Charging " + HealthService.energy_hrBatteryPercent + voltage + health;
                            batteryStatusColor = Constants.Colors.GRAY;
                        } else if (HealthService.energy_rawBatteryPercent >= 40) {
                            batteryStatusString = "Battery is Charging " + HealthService.energy_hrBatteryPercent + voltage + health;
                            batteryStatusColor = Constants.Colors.YELLOW;
                        } else if (HealthService.energy_rawBatteryPercent >= 25) {
                            batteryStatusString = "Battery is Charging " + HealthService.energy_hrBatteryPercent + voltage + health;
                            batteryStatusColor = Constants.Colors.ORANGE_BRIGHT;
                        } else {
                            batteryStatusString = "Battery is Charging " + HealthService.energy_hrBatteryPercent + voltage + health;
                            batteryStatusColor = Constants.Colors.RED_BRIGHT;
                        }
                    } else {
                        if (HealthService.energy_rawBatteryPercent >= 80) {
                            batteryStatusString = "Battery Discharging " + HealthService.energy_hrBatteryPercent + voltage + health;
                            batteryStatusColor = Constants.Colors.GRAY;
                        } else if (HealthService.energy_rawBatteryPercent >= 60) {
                            batteryStatusString = "Battery Discharging " + HealthService.energy_hrBatteryPercent + voltage + health;
                            batteryStatusColor = Constants.Colors.YELLOW;
                        } else if (HealthService.energy_rawBatteryPercent >= 40) {
                            batteryStatusString = "Battery Discharging " + HealthService.energy_hrBatteryPercent + voltage + health;
                            batteryStatusColor = Constants.Colors.ORANGE_BRIGHT;
                        } else {
                            batteryStatusString = "Battery Discharging " + HealthService.energy_hrBatteryPercent + voltage + health;
                            batteryStatusColor = Constants.Colors.RED_BRIGHT;
                        }
                    }
                    //if (HealthService.energy_derivedBatteryHealthCondition == HealthService.ENERGY_BATTERY_HEALTH_BAD) batteryStatusColor = Constants.Colors.RED_BRIGHT;  //TODO enable colors for bad battery once battery health is reliable?
                    broadcastStatusBarUpdate_battery(batteryStatusString, batteryStatusColor);

                    //******************************************************************************
                    // Update power supply status
                    // Note: if there is USB and main power connected at same time, any dis/connect event will fire battery-changed (rather than dis/connect events), so we deal with that possibility here
                    // Note: electrical current may be dependent on battery state of charge (most current is consumed between about 25-75% battery SoC?)
                    // Note: at full charge, millamps reported is weird, so assume the best
                    switch (HealthService.energy_rawPowerSupplyWhichConnected) {
                        case HealthService.ENERGY_POWER_SUPPLY_NONE:
                            broadcastStatusBarUpdate_power("Power Lost", Constants.Colors.RED_BRIGHT);
                            break;
                        case HealthService.ENERGY_POWER_SUPPLY_ANY:
                            broadcastStatusBarUpdate_power("Power Connected", Constants.Colors.GRAY);
                            break;
                        case HealthService.ENERGY_POWER_SUPPLY_DC_SOCKET:
                            broadcastStatusBarUpdate_power("Main Power Connected", Constants.Colors.GRAY);
                            break;
                        case HealthService.ENERGY_POWER_SUPPLY_USB_SOCKET:
                            broadcastStatusBarUpdate_power("USB Power Connected", Constants.Colors.GRAY);
                            break;
                        case HealthService.ENERGY_POWER_SUPPLY_UNKNOWN:
                        default:
                            if (HealthService.energy_rawMilliAmpsAtBattery > 200) {
                                broadcastStatusBarUpdate_power("Power Connected", Constants.Colors.GRAY);
                            } else if (HealthService.energy_rawMilliAmpsAtBattery > 0) {
                                broadcastStatusBarUpdate_power("Power is Weak (" + HealthService.energy_hrMilliAmpsAtBattery + ")", Constants.Colors.YELLOW);
                            } else {
                                broadcastStatusBarUpdate_power("Power Insufficient (" + HealthService.energy_hrMilliAmpsAtBattery + ")", Constants.Colors.ORANGE_BRIGHT);
                            }
                            break;
                    }

                    //******************************************************************************
                    // Update network status
                    //TODO: migrate this to its own health thread regime
                    String activeNIC = netUtils.getActiveNIC();
                    if (NetUtils.ACTIVE_NIC_ETH0.equals(activeNIC)) {
                        broadcastStatusBarUpdate_network("Wired Network Connected", Constants.Colors.GRAY);
                    } else if (NetUtils.ACTIVE_NIC_WLAN.equals(activeNIC)) {
                        String wifiStrengthSubjective = netUtils.getWifiStrength_subjective();
                        if (wifiStrengthSubjective.equals(NetUtils.WIFI_STRENGTH_SUBJECTIVE_5_BEST)) {
                            broadcastStatusBarUpdate_network("WiFi Signal " + wifiStrengthSubjective, Constants.Colors.GRAY);
                        } else if (wifiStrengthSubjective.equals(NetUtils.WIFI_STRENGTH_SUBJECTIVE_4_GOOD)) {
                            broadcastStatusBarUpdate_network("WiFi Signal " + wifiStrengthSubjective, Constants.Colors.YELLOW);
                        } else if (wifiStrengthSubjective.equals(NetUtils.WIFI_STRENGTH_SUBJECTIVE_3_FAIR)) {
                            broadcastStatusBarUpdate_network("WiFi Signal " + wifiStrengthSubjective, Constants.Colors.ORANGE);
                        } else if (wifiStrengthSubjective.equals(NetUtils.WIFI_STRENGTH_SUBJECTIVE_2_WEAK)) {
                            broadcastStatusBarUpdate_network("WiFi Signal " + wifiStrengthSubjective, Constants.Colors.RED_BRIGHT);
                        } else if (wifiStrengthSubjective.equals(NetUtils.WIFI_STRENGTH_SUBJECTIVE_1_WORST)) {
                            broadcastStatusBarUpdate_network("WiFi Signal " + wifiStrengthSubjective, Constants.Colors.RED_BRIGHT);
                        } else {
                            broadcastStatusBarUpdate_network("WiFi Signal " + wifiStrengthSubjective, Constants.Colors.RED_BRIGHT);
                        }
                    } else {
                        broadcastStatusBarUpdate_network("Network Unavailable", Constants.Colors.RED_BRIGHT);
                    }

                    //******************************************************************************
                    // Update app-uptime status
                    //TODO: migrate this to its own health thread regime
                    long uptimeAppHrs = omniApplication.getAppRunningHours();
                    if (uptimeAppHrs < 1) {
                        long uptimeAppMins = omniApplication.getAppRunningMinutes();
                        if (uptimeAppMins < 1) {
                            broadcastStatusBarUpdate_uptimeApp("Running for <1 Minute", Constants.Colors.GRAY);
                        } else if (uptimeAppMins == 1) {
                            broadcastStatusBarUpdate_uptimeApp("Running for 1 Minute", Constants.Colors.GRAY);
                        } else {
                            broadcastStatusBarUpdate_uptimeApp("Running for " + String.valueOf(uptimeAppMins) + " Minutes", Constants.Colors.GRAY);
                        }
                    } else if (uptimeAppHrs == 1) {
                        broadcastStatusBarUpdate_uptimeApp("Running for 1 Hour", Constants.Colors.GRAY);
                    } else if (uptimeAppHrs < 24) {
                        broadcastStatusBarUpdate_uptimeApp("Running for "+String.valueOf(uptimeAppHrs)+" Hours", Constants.Colors.GRAY);
                    } else {
                        int days = (int) TimeUnit.HOURS.toDays(uptimeAppHrs);
                        try {
                            int hours = (int) Math.round((((double) uptimeAppHrs / (double) 24) - days) * 24);
                            if (hours > 0) {
                                if (days == 1 && hours == 1) {
                                    broadcastStatusBarUpdate_uptimeApp("Running for " + String.valueOf(days) + " Day, " + hours + " Hour", Constants.Colors.GRAY);
                                } else if (days == 1 && hours > 1) {
                                    broadcastStatusBarUpdate_uptimeApp("Running for " + String.valueOf(days) + " Day, " + hours + " Hours", Constants.Colors.GRAY);
                                } else if (days > 1 && hours == 1) {
                                    broadcastStatusBarUpdate_uptimeApp("Running for " + String.valueOf(days) + " Days, " + hours + " Hour", Constants.Colors.GRAY);
                                } else {
                                    broadcastStatusBarUpdate_uptimeApp("Running for " + String.valueOf(days) + " Days, " + hours + " Hours", Constants.Colors.GRAY);
                                }
                            } else {
                                if (days == 1) {
                                    broadcastStatusBarUpdate_uptimeApp("Running for " + String.valueOf(days) + " Day", Constants.Colors.GRAY);
                                } else {
                                    broadcastStatusBarUpdate_uptimeApp("Running for " + String.valueOf(days) + " Days", Constants.Colors.GRAY);
                                }
                            }
                        } catch (Exception e) {
                            logW(TAGG+"Exception caught (using just days): ");
                            if (days == 1) {
                                broadcastStatusBarUpdate_uptimeApp("Running for " + String.valueOf(days) + " Day", Constants.Colors.GRAY);
                            } else {
                                broadcastStatusBarUpdate_uptimeApp("Running for " + String.valueOf(days) + " Days", Constants.Colors.GRAY);
                            }
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

        if (this.netUtils != null) {
            this.netUtils.cleanup();
            this.netUtils = null;
        }

        this.appContextRef = null;
    }


    /*============================================================================================*/
    /* Processing Methods */

    /** Populate an Android-Message object with our message data from the database and send it to MesssgeService's Handler.
     */
    private void sendCommandToDeliveryService(int actionToRequest, OmniMessage omniMessage) {
        final String TAGG = "sendCommandToDeliveryService: ";

        // Get our handler's message object so we can populate it with our DB data
        android.os.Message androidMessage = androidMsgHandler_DeliveryService.obtainMessage();

        // Send what we're wanting the handler to do
        androidMessage.arg1 = actionToRequest;

        // Supply the provided OmniMessage to the Android-msg we're going to send
        androidMessage.obj = omniMessage;

        // Actually send the Android-message (with OmniMessage object) back to DeliveryService's handler
        androidMsgHandler_DeliveryService.sendMessage(androidMessage);
    }

    private void broadcastStatusBarUpdate_battery(final String text, final int textColor) {
        final String TAGG = "broadcastStatusBarUpdate_battery(\""+text+"\"): ";

        // Do the broadcast in a background worker thread, so we don't hold up this thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent i = new Intent(ClockActivity.OMNI_STATUS_RECEIVER_NAME);

                    i.putExtra(ClockActivity.OMNI_STATUS_RECEIVER_ACTION, ClockActivity.OMNI_STATUS_RECEIVER_ACTION_UPDATE_BATTERY);
                    i.putExtra(ClockActivity.OMNI_STATUS_RECEIVER_KEYNAME_BATTERY_PERCENTAGE, text);
                    i.putExtra(ClockActivity.OMNI_STATUS_RECEIVER_KEYNAME_TEXT_COLOR, textColor);

                    logV(TAGG+"Broadcasting.");
                    appContextRef.get().getApplicationContext().sendBroadcast(i);
                } catch (Exception e) {
                    logE(TAGG+"Exception caught: "+e.getMessage());
                }
            }
        }).start();
    }

    private void broadcastStatusBarUpdate_power(final String text, final int textColor) {
        final String TAGG = "broadcastStatusBarUpdate_power(\""+text+"\"): ";

        // Do the broadcast in a background worker thread, so we don't hold up this thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent i = new Intent(ClockActivity.OMNI_STATUS_RECEIVER_NAME);

                    i.putExtra(ClockActivity.OMNI_STATUS_RECEIVER_ACTION, ClockActivity.OMNI_STATUS_RECEIVER_ACTION_UPDATE_POWER);
                    i.putExtra(ClockActivity.OMNI_STATUS_RECEIVER_KEYNAME_POWER_STATUS, text);
                    i.putExtra(ClockActivity.OMNI_STATUS_RECEIVER_KEYNAME_TEXT_COLOR, textColor);

                    logV(TAGG+"Broadcasting.");
                    appContextRef.get().getApplicationContext().sendBroadcast(i);
                } catch (Exception e) {
                    Log.e(OmniStatusBarThread.class.getSimpleName(), TAGG+"Exception caught: "+e.getMessage());
                }
            }
        }).start();
    }

    private void broadcastStatusBarUpdate_network(final String text, final int textColor) {
        final String TAGG = "broadcastStatusBarUpdate_network(\""+text+"\"): ";

        // Do the broadcast in a background worker thread, so we don't hold up this thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent i = new Intent(ClockActivity.OMNI_STATUS_RECEIVER_NAME);

                    i.putExtra(ClockActivity.OMNI_STATUS_RECEIVER_ACTION, ClockActivity.OMNI_STATUS_RECEIVER_ACTION_UPDATE_NETWORK);
                    i.putExtra(ClockActivity.OMNI_STATUS_RECEIVER_KEYNAME_NETWORK_STATUS, text);
                    i.putExtra(ClockActivity.OMNI_STATUS_RECEIVER_KEYNAME_TEXT_COLOR, textColor);

                    logV(TAGG + "Broadcasting.");
                    appContextRef.get().getApplicationContext().sendBroadcast(i);
                } catch (Exception e) {
                    Log.e(OmniStatusBarThread.class.getSimpleName(), TAGG + "Exception caught: " + e.getMessage());
                }
            }
        }).start();
    }

    private void broadcastStatusBarUpdate_uptimeApp(final String text, final int textColor) {
        final String TAGG = "broadcastStatusBarUpdate_uptimeApp(\""+text+"\"): ";

        // Do the broadcast in a background worker thread, so we don't hold up this thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent i = new Intent(ClockActivity.OMNI_STATUS_RECEIVER_NAME);

                    i.putExtra(ClockActivity.OMNI_STATUS_RECEIVER_ACTION, ClockActivity.OMNI_STATUS_RECEIVER_ACTION_UPDATE_UPTIME_APP);
                    i.putExtra(ClockActivity.OMNI_STATUS_RECEIVER_KEYNAME_UPTIME_APP, text);
                    i.putExtra(ClockActivity.OMNI_STATUS_RECEIVER_KEYNAME_TEXT_COLOR, textColor);

                    logV(TAGG + "Broadcasting.");
                    appContextRef.get().getApplicationContext().sendBroadcast(i);
                } catch (Exception e) {
                    Log.e(OmniStatusBarThread.class.getSimpleName(), TAGG + "Exception caught: " + e.getMessage());
                }
            }
        }).start();
    }

    private void broadcastStatusBarUpdate_uptimeDevice(final String text, final int textColor) {
        final String TAGG = "broadcastStatusBarUpdate_uptimeDevice(\""+text+"\"): ";

        // Do the broadcast in a background worker thread, so we don't hold up this thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent i = new Intent(ClockActivity.OMNI_STATUS_RECEIVER_NAME);

                    i.putExtra(ClockActivity.OMNI_STATUS_RECEIVER_ACTION, ClockActivity.OMNI_STATUS_RECEIVER_ACTION_UPDATE_UPTIME_DEVICE);
                    i.putExtra(ClockActivity.OMNI_STATUS_RECEIVER_KEYNAME_UPTIME_DEVICE, text);
                    i.putExtra(ClockActivity.OMNI_STATUS_RECEIVER_KEYNAME_TEXT_COLOR, textColor);

                    logV(TAGG + "Broadcasting.");
                    appContextRef.get().getApplicationContext().sendBroadcast(i);
                } catch (Exception e) {
                    Log.e(OmniStatusBarThread.class.getSimpleName(), TAGG + "Exception caught: " + e.getMessage());
                }
            }
        }).start();
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
