package com.messagenetsystems.evolution2.receivers;

/* HealthReceiverEnergyStates
 * Handles receiving energy related notifications from the system, basic analysis/translation, and setting HealthService flags as necessary.
 *
 * Listens for system-broadcasted events, as follows...
 *  - Battery voltage / level changes (system fires ACTION_BATTERY_CHANGED)
 *  - Power connected / disconnected (system fires ACTION_POWER_CONNECTED / ACTION_POWER_DISCONNECTED)
 *
 * Revisions:
 *  2020.05.15      Chris Rider     Creation (used BootReciever as a template).
 *  2020.05.18-20   Chris Rider     Refactoring and fine-tuning to make it appear and react right to various power and charge states.
 *  2020.05.21      Chris Rider     Refactoring again to be just for analyzing states and setting HealthService flags. From there, a thread will actually update status bar, etc.
 *                                  Renamed class from "EnergyStateReceiver" to be easier to make a mental connection later with HealthService for code maintainability.
 *  2020.05.22      Chris Rider     Refactored to move global value updates to HealthService's Handler so it can be reused by other stuff without duplication of code.
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.services.HealthService;
import com.messagenetsystems.evolution2.utilities.EnergyUtils;

import java.lang.ref.WeakReference;


public class HealthReceiverEnergyStates extends BroadcastReceiver {
    private static final String TAG = HealthReceiverEnergyStates.class.getSimpleName();

    // Constants...
    public static final String RECEIVER_NAME = "com.messagenetsystems.evolution.receivers.healthReceiverEnergyStates";

    // Logging stuff...
    private final int LOG_SEVERITY_V = 1;
    private final int LOG_SEVERITY_D = 2;
    private final int LOG_SEVERITY_I = 3;
    private final int LOG_SEVERITY_W = 4;
    private final int LOG_SEVERITY_E = 5;
    private int logMethod = Constants.LOG_METHOD_LOGCAT;

    // Local stuff...
    private WeakReference<Context> appContextRef;
    private Handler androidMsgHandler_HealthService;    //reference to HealthService's message-handler, so we can send data to there


    /** Constructor */
    public HealthReceiverEnergyStates(Context context, int logMethod, Handler parentProcessHandler) {
        logV("Instantiating...");

        this.logMethod = logMethod;
        this.appContextRef = new WeakReference<Context>(context);

        // Get our handlers from parents, so we can send Android-Messages back to them
        this.androidMsgHandler_HealthService = parentProcessHandler;                                //get our handler from HealthService

        // Initialize power-connected flag to start with (will update later with any connect/disconnect events)
        HealthService.energy_rawPowerSupplyWhichConnected = EnergyUtils.getCurrentPowerSupply(context);

        // Initialize battery-condition flags to start with (will update later with any battery-change, etc. events)
        //TODO
    }

    public void cleanup() {
        final String TAGG = "cleanup: ";

        if (this.appContextRef != null) {
            this.appContextRef.clear();
            this.appContextRef = null;
        }
    }

    /** Specify what happens when we receive the broadcasts from the OS.
     * Notes:
     *  - Getting amperage requires a BatteryManager instance, and is NOT passed via the intent!
     *  - ACTION_POWER_CONNECTED / ACTION_POWER_DISCONNECTED do NOT pass battery data, or anything useful, really.
     *  - ACTION_BATTERY_CHANGED passes along lots of battery data via the intent (just not amperage).
     *  */
    @Override
    public void onReceive(Context context, Intent intent) {
        final String TAGG = "onReceive: ";

        if (intent.getAction() == null) {
            logW(TAGG+"Intent's getAction returned null, aborting.");
            return;
        }

        try {
            // Figure out why we got this broadcast,
            // and then take appropriate action...
            if (intent.getAction().equals(Intent.ACTION_POWER_CONNECTED)) {
                // At 99% battery full, Android will broadcast an event that shows the power is disconnected
                // and the battery is discharging (even though the phone is still connected to AC power!).
                // Conversely, this connected event may fire when battery begins to charge again.
                logI(TAGG + "Power connected.");

                // Update global values
                HealthService.energy_rawPowerSupplyWhichConnected = EnergyUtils.getCurrentPowerSupply(context);
                logV(TAGG+"HealthService.energy_rawPowerSupplyWhichConnected has been set to: "+Integer.toString(HealthService.energy_rawPowerSupplyWhichConnected));
            }
            else if (intent.getAction().equals(Intent.ACTION_POWER_DISCONNECTED)) {
                // At 99% battery full, Android will broadcast an event that shows the power is disconnected
                // and the battery is discharging (even though the phone is still connected to AC power!).
                logI(TAGG + "Power disconnected.");

                // Update global values
                HealthService.energy_rawPowerSupplyWhichConnected = HealthService.ENERGY_POWER_SUPPLY_NONE;
                logV(TAGG + "HealthService.energy_rawPowerSupplyWhichConnected has been set to: " + Integer.toString(HealthService.energy_rawPowerSupplyWhichConnected));
            }
            else if (intent.getAction().equals(Intent.ACTION_BATTERY_LOW)) {
                // Battery has become very low
                logI(TAGG + "Battery level is low.");

                //TODO
            }
            else if (intent.getAction().equals(Intent.ACTION_BATTERY_OKAY)) {
                // Battery has resumed being OK from a very low state
                logI(TAGG + "Battery level is not low.");

                //TODO
            }
            else if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
                // Dev-note:
                // At 99% battery full, Android will broadcast an event that shows the power is disconnected
                // and the battery is discharging (even though the phone is still connected to AC power!).
                // Instead, use ACTION_POWER_CONNECTED and ACTION_POWER_DISCONNECTED
                logI(TAGG + "Battery changed.");

                // Update global values
                sendCommandToParentService(HealthService.HANDLER_ACTION_UPDATE_GLOBAL_VALUES_POWER, intent);
            } else {
                logW(TAGG+"Unhandled action.");
            }
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }
    }


    /*============================================================================================*/
    /* Utility Methods */

    /** Populate an Android-Message object with data and send it to parent process' Handler.
     */
    private void sendCommandToParentService(int actionToRequest, Object dataToSend) {
        final String TAGG = "sendCommandToParentService: ";

        // Get our handler's message object so we can populate it with our DB data
        android.os.Message androidMessage = androidMsgHandler_HealthService.obtainMessage();

        // Send what we're wanting the handler to do
        androidMessage.arg1 = actionToRequest;

        // Supply the provided data object to the handler
        androidMessage.obj = dataToSend;

        // Actually send the Android-message (with OmniMessage object) back to DeliveryService's handler
        androidMsgHandler_HealthService.sendMessage(androidMessage);
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
