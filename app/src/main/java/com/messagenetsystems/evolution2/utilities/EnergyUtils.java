package com.messagenetsystems.evolution2.utilities;

/* EnergyUtils class
 * Power, battery, charging, and energy related utilities.
 *
 * Uses a weak reference to context, so it's safe to use in long running processes.
 *
 * Revisions:
 *  2020.05.15      Chris Rider     Created.
 *  2020.05.24-25   Chris Rider     Updated various "getEnglish*" wordings.
 */

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

import com.bosphere.filelogger.FL;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;

import static android.content.Context.BATTERY_SERVICE;

public class EnergyUtils {
    private final static String TAG = EnergyUtils.class.getSimpleName();

    public static final int POWER_SUPPLY_UNKNOWN = -1;
    public static final int POWER_SUPPLY_NONE = 0;
    public static final int POWER_SUPPLY_DC_SOCKET = 1;
    public static final int POWER_SUPPLY_USB_SOCKET = 2;
    public static final int POWER_SUPPLY_ANY = 3;

    public static final int VOLTAGE_UNKNOWN = -1;
    public static final int AMPERAGE_UNKNOWN = Integer.MIN_VALUE;
    public static final int LEVEL_UNKNOWN = -1;

    public static final int BATTERY_HEALTH_UNKNOWN = -1;
    public static final int BATTERY_HEALTH_GOOD = 1;
    public static final int BATTERY_HEALTH_BAD = 2;

    // Logging stuff...
    public static final int LOG_METHOD_LOGCAT = 1;
    public static final int LOG_METHOD_FILELOGGER = 2;
    private static final int LOG_SEVERITY_V = 1;
    private static final int LOG_SEVERITY_D = 2;
    private static final int LOG_SEVERITY_I = 3;
    private static final int LOG_SEVERITY_W = 4;
    private static final int LOG_SEVERITY_E = 5;
    private static int logMethod = LOG_METHOD_LOGCAT;

    // Local stuff...
    private WeakReference<Context> appContextRef;   //since this thread is very long running, we prefer a weak context reference
    private static final AtomicInteger instanceCounter = new AtomicInteger();


    /*============================================================================================*/
    /* Class & Support Methods */

    /** Constructor
     * @param appContext Application context
     * @param logMethod Logging method to use
     */
    public EnergyUtils(Context appContext, int logMethod) {
        this.logMethod = logMethod;
        this.appContextRef = new WeakReference<Context>(appContext);

        instanceCounter.incrementAndGet();

        initialize(appContext);
    }

    /** We can use this to tell if this class has been instantiated or not.
     * This is for determining whether we are using an instance or static.
     */
    public static int getInstanceCount() {
        return instanceCounter.get();
    }
    public static boolean getIsInstantiated() {
        if (getInstanceCount() > 0) {
            return true;
        } else {
            return false;
        }
    }

    /** Initialize
     * Note: Not required for static-use cases.
     */
    public void initialize(Context context) {
        final String TAGG = "initialize: ";


    }

    /** Cleanup
     * Call this whenever you're finished using an instance of this class.
     * Note: Not required for static-use cases.
     */
    public void cleanup() {
        if (appContextRef != null) {
            appContextRef.clear();
            appContextRef = null;
        }
    }


    /*============================================================================================*/
    /* Acquisition Methods */

    /** Figure out which power supply is connected (USB, DC, etc.)
     * Returns the appropriate constant defined at the top of this class.
     * In the case of power source, we more or less just use Android's constants from BatteryManager.
     * You may call it from the instance or statically.
     * @param context Application context
     * @return Integer constant defined in EnergyUtils and closely aligned with BatteryManager constants.
     */
    public static int getCurrentPowerSupply(Context context) {
        final String TAGG = "getCurrentPowerSupply: ";
        int ret = POWER_SUPPLY_UNKNOWN;

        try {
            // This particular intent is sticky, so its state should always be around for us to use
            // Just temporarily register a receiver to get access to it...
            Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

            // Now, just get the data we need
            // We basically reuse whatever BatterManager.EXTRA_PLUGGED provides (adding a few extra ones), so just assign it...
            ret = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, POWER_SUPPLY_UNKNOWN);
        } catch (Exception e) {
            logE(TAGG+"Exception caught (sticky intent method): "+e.getMessage());
        }

        if (ret == POWER_SUPPLY_UNKNOWN) {
            try {
                // In case we could not get a value above, let's try another method
                logW(TAGG + "Unable to get power supply using sticky intent. Trying command line methods...");
                SystemUtils systemUtils = new SystemUtils(context, logMethod);
                if (systemUtils.isDcPowerConnected()) {
                    ret = POWER_SUPPLY_DC_SOCKET;
                } else if (systemUtils.isUsbPowerConnected()) {
                    ret = POWER_SUPPLY_DC_SOCKET;
                } else {
                    ret = POWER_SUPPLY_UNKNOWN;
                }
                systemUtils.cleanup();
            } catch (Exception e) {
                logE(TAGG + "Exception caught (command line methods): " + e.getMessage());
            }
        }

        logV(TAGG+"Returning: "+Integer.toString(ret));
        return ret;
    }
    public int getCurrentPowerSupply() {
        final String TAGG = "getCurrentPowerSupply: ";
        int ret = POWER_SUPPLY_UNKNOWN;

        if (this.appContextRef == null)
            logE(TAGG+"This instance has no context initialized! Aborting and returning default.");
        else
            ret = getCurrentPowerSupply(appContextRef.get().getApplicationContext());

        logV(TAGG+"Returning: "+Integer.toString(ret));
        return ret;
    }

    /** Get the current amperage that's going into (or out of) the battery.
     * You may call it from the instance or statically.
     * @param context Application context
     * @return Integer value representing milliAmp value (negative is draining battery, positive is charging battery)
     */
    public static int getCurrentAmperage_milli(Context context) {
        final String TAGG = "getCurrentAmperage_milli: ";
        int ret = AMPERAGE_UNKNOWN;

        try {
            BatteryManager batteryManager = (BatteryManager) context.getApplicationContext().getSystemService(BATTERY_SERVICE);
            if (batteryManager != null) {
                ret = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000;
            } else {
                logE(TAGG+"Could not get a BatteryManager instance.");
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning: "+Integer.toString(ret));
        return ret;
    }
    public int getCurrentAmperage_milli() {
        final String TAGG = "getCurrentAmperage_milli: ";
        int ret = AMPERAGE_UNKNOWN;

        if (this.appContextRef == null)
            logE(TAGG+"This instance has no context initialized! Aborting and returning default.");
        else
            ret = getCurrentAmperage_milli(appContextRef.get().getApplicationContext());

        logV(TAGG+"Returning: "+Integer.toString(ret));
        return ret;
    }

    /** Get the current voltage that's at the battery.
     * You may call it from the instance or statically.
     * @param context Application context
     * @return Integer value representing milliVolt value
     */
    public static int getCurrentVoltage_milli(Context context) {
        final String TAGG = "getCurrentVoltage_milli: ";
        int ret = VOLTAGE_UNKNOWN;

        try {
            // This particular intent is sticky, so its state should always be around for us to use
            // Just temporarily register a receiver to get access to it...
            Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

            // Now, just get the data we need
            ret = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, VOLTAGE_UNKNOWN);
        } catch (Exception e) {
            logE(TAGG+"Exception caught (sticky intent method): "+e.getMessage());
        }

        if (ret == VOLTAGE_UNKNOWN) {
            try {
                // In case we could not get a value above, let's try another method
                logW(TAGG + "Unable to get voltage using sticky intent. Trying command line methods...");
                SystemUtils systemUtils = new SystemUtils(context, logMethod);
                ret = systemUtils.getBatteryVoltage_milliVolts();
                systemUtils.cleanup();
            } catch (Exception e) {
                logE(TAGG + "Exception caught (command line methods): " + e.getMessage());
            }
        }

        logV(TAGG+"Returning: "+Integer.toString(ret));
        return ret;
    }
    public int getCurrentVoltage_milli() {
        final String TAGG = "getCurrentVoltage_milli: ";
        int ret = VOLTAGE_UNKNOWN;

        if (this.appContextRef == null)
            logE(TAGG+"This instance has no context initialized! Aborting and returning default.");
        else
            ret = getCurrentVoltage_milli(appContextRef.get().getApplicationContext());

        logV(TAGG+"Returning: "+Integer.toString(ret));
        return ret;
    }

    /** Get the current battery charge level (not necessarily percent).
     * You may call it from the instance or statically.
     * @param context Application context
     * @return Integer value representing raw battery level value
     */
    public static int getCurrentBatteryLevel(Context context) {
        final String TAGG = "getCurrentBatteryLevel: ";
        int ret = LEVEL_UNKNOWN;

        try {
            // This particular intent is sticky, so its state should always be around for us to use
            // Just temporarily register a receiver to get access to it...
            Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

            // Now, just get the data we need
            ret = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, LEVEL_UNKNOWN);
        } catch (Exception e) {
            logE(TAGG+"Exception caught (sticky intent method): "+e.getMessage());
        }

        if (ret == LEVEL_UNKNOWN) {
            try {
                // In case we could not get a value above, let's try another method
                logW(TAGG + "Unable to get battery level using sticky intent. Trying other BatteryManager methods...");
                SystemUtils systemUtils = new SystemUtils(context, logMethod);
                ret = systemUtils.getBatteryLevel();
                systemUtils.cleanup();
            } catch (Exception e) {
                logE(TAGG + "Exception caught (command line methods): " + e.getMessage());
            }
        }

        logV(TAGG+"Returning: "+Integer.toString(ret));
        return ret;
    }
    public int getCurrentBatteryLevel() {
        final String TAGG = "getCurrentBatteryLevel: ";
        int ret = LEVEL_UNKNOWN;

        if (this.appContextRef == null)
            logE(TAGG+"This instance has no context initialized! Aborting and returning default.");
        else
            ret = getCurrentBatteryLevel(appContextRef.get().getApplicationContext());

        logV(TAGG+"Returning: "+Integer.toString(ret));
        return ret;
    }

    /** Get the current battery charge level as a percent.
     * You may call it from the instance or statically.
     * @param context Application context
     * @return Integer value representing whole-number rounded battery level percent
     */
    public static int getCurrentBatteryPercent(Context context) {
        final String TAGG = "getCurrentBatteryPercent: ";
        int ret = LEVEL_UNKNOWN;

        try {
            // This particular intent is sticky, so its state should always be around for us to use
            // Just temporarily register a receiver to get access to it...
            Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

            // Now, just get the data we need
            ret = getBatteryPercent_integer(
                    intent.getIntExtra(BatteryManager.EXTRA_LEVEL, LEVEL_UNKNOWN),
                    intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            );
        } catch (Exception e) {
            logE(TAGG+"Exception caught (sticky intent method): "+e.getMessage());
        }

        if (ret == LEVEL_UNKNOWN) {
            try {
                // In case we could not get a value above, let's try another method
                logW(TAGG + "Unable to get battery percent using sticky intent. Trying other BatteryManager methods...");
                SystemUtils systemUtils = new SystemUtils(context, logMethod);
                ret = getBatteryPercent_integer(
                        systemUtils.getBatteryLevel(),
                        100
                );
                systemUtils.cleanup();
            } catch (Exception e) {
                logE(TAGG + "Exception caught (command line methods): " + e.getMessage());
            }
        }

        logV(TAGG+"Returning: "+Integer.toString(ret));
        return ret;
    }
    public int getCurrentBatteryPercent() {
        final String TAGG = "getCurrentBatteryPercent: ";
        int ret = LEVEL_UNKNOWN;

        if (this.appContextRef == null)
            logE(TAGG+"This instance has no context initialized! Aborting and returning default.");
        else
            ret = getCurrentBatteryPercent(appContextRef.get().getApplicationContext());

        logV(TAGG+"Returning: "+Integer.toString(ret));
        return ret;
    }


    /*============================================================================================*/
    /* Analysis Methods */

    /** Try to determine whether the amperage data is unusual or unreliable somehow.
     * This was an early attempt to catch observed weirdness in development and at some customer sites.
     * Not really sure if it's indicative of an unreliable API or actual hardware issues?
     * @param mA Amperage in milliamps to examine
     * @param rawBatteryPercent Battery charge level percentage as a whole number integer
     * @return Whether data is determined to be unusual in some way
     */
    public static boolean isAmperageDataWeird(int mA, int rawBatteryPercent) {
        final String TAGG = "isAmperageDataWeird: ";
        boolean isDataWeird = false;

        try {
            // First, we only care about bad data (and can only detect it) if battery is not full and we have initialized data
            if (rawBatteryPercent < 99 && rawBatteryPercent > -1) {

                // These are the observed weird data values noticed in bench testing...
                if (mA == 0
                        || mA == -128
                        || mA == -3
                        || mA == -1
                        || mA == 1
                        || mA == 3
                        || mA == 128) {
                    isDataWeird = true;
                }
            }
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }

        logV(TAGG+"Returning: "+Boolean.toString(isDataWeird));
        return isDataWeird;
    }


    /*============================================================================================*/
    /* Conversion and Translation Methods */

    /** Calculate battery percentage (as a whole number integer value).
     * @param level Battery level
     * @param scale Battery level scale (maximum value)
     * @return Whole-number rounded percent value, or -1 if something went wrong
     */
    public static int getBatteryPercent_integer(int level, int scale) {
        final String TAGG = "getBatteryPercent_integer: ";
        int ret = -1;

        try {
            ret = Math.round(( (float)level / (float)scale) * 100);
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }

        logV(TAGG+"Returning: "+Integer.toString(ret));
        return ret;
    }

    /** Calculate battery percentage (as a nicely formatted String).
     * @param level Battery level
     * @param scale Battery level scale (maximum value)
     * @return Whole-number rounded percent value, or "-1%" if something went wrong
     */
    public static String getBatteryPercent_human(int level, int scale) {
        final String TAGG = "getBatteryPercent_human: ";
        String ret = "-1%";

        try {
            int batteryPercent = getBatteryPercent_integer(level, scale);
            ret = Integer.toString(batteryPercent)+"%";
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }

        logV(TAGG+"Returning: \""+ret+"\"");
        return ret;
    }

    /** Get a plain-English String for the BatteryManager.EXTRA_STATUS value.
     * @param state Value of BatteryManager.EXTRA_STATUS
     * @return English meaning of the BatteryManager.EXTRA_STATUS value
     */
    public static String getEnglish_batteryChargingState(int state) {
        final String TAGG = "getEnglish_batteryChargingState: ";
        String ret = "Unknown";

        try {
            switch (state) {
                case BatteryManager.BATTERY_STATUS_CHARGING:
                    ret = "Battery Charging";
                    break;
                case BatteryManager.BATTERY_STATUS_DISCHARGING:
                    ret = "Battery Discharging";
                    break;
                case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                    ret = "Battery Not Charging";
                    break;
                case BatteryManager.BATTERY_STATUS_FULL:
                    ret = "Battery Full";
                    break;
                case BatteryManager.BATTERY_STATUS_UNKNOWN:
                default:
                    ret = "Battery Charging State Unknown";
            }
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }

        logV(TAGG+"Returning: \""+ret+"\"");
        return ret;
    }

    /** Get a plain-English String for the BatteryManager.EXTRA_PLUGGED value.
     * @param state Value of BatteryManager.EXTRA_PLUGGED
     * @return English meaning of the BatteryManager.EXTRA_PLUGGED value
     */
    public static String getEnglish_chargePlugState(int state) {
        final String TAGG = "getEnglish_chargePlugState: ";
        String ret = "Unknown";

        try {
            switch (state) {
                case 0:
                    ret = "None";
                    break;
                case BatteryManager.BATTERY_PLUGGED_AC:
                    ret = "Main";
                    break;
                case BatteryManager.BATTERY_PLUGGED_USB:
                    ret = "USB";
                    break;
                case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                    ret = "Wireless";
                    break;
                default:
                    ret = "Unknown";
            }
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }

        logV(TAGG+"Returning: \""+ret+"\"");
        return ret;
    }

    /** Get a plain-English String for the BatteryManager.EXTRA_HEALTH value.
     * Note: This is unlikely to be fully supported or reliable on most OEM devices, probably reporting GOOD for all cases?
     * @param state Value of BatteryManager.EXTRA_HEALTH
     * @return English meaning of the BatteryManager.EXTRA_HEALTH value
     */
    public static String getEnglish_batteryHealthState_raw(int state) {
        final String TAGG = "getEnglish_batteryHealthState_raw: ";
        String ret = "Unknown";

        try {
            switch (state) {
                case BatteryManager.BATTERY_HEALTH_GOOD:
                    ret = "Good";
                    break;
                case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                    ret = "Overheat";
                    break;
                case BatteryManager.BATTERY_HEALTH_DEAD:
                    ret = "Dead";
                    break;
                case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                    ret = "Over Voltage";
                    break;
                case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                    ret = "Unspecified Failure";
                    break;
                case BatteryManager.BATTERY_HEALTH_COLD:
                    ret = "Cold";
                    break;
                case BatteryManager.BATTERY_HEALTH_UNKNOWN:
                default:
                    ret = "Unknown";
            }
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }

        logV(TAGG+"Returning: \""+ret+"\"");
        return ret;
    }

    /** Get a plain-English String for the our custom battery health value.
     * @param state Value of BatteryManager.EXTRA_HEALTH
     * @return English meaning of the BatteryManager.EXTRA_HEALTH value
     */
    public static String getEnglish_batteryHealthState(int state) {
        final String TAGG = "getEnglish_batteryHealthState: ";
        String ret = "Unknown";

        try {
            switch (state) {
                case EnergyUtils.BATTERY_HEALTH_GOOD:
                    ret = "Healthy";
                    break;
                case EnergyUtils.BATTERY_HEALTH_BAD:
                    ret = "Unhealthy";
                    break;
                case EnergyUtils.BATTERY_HEALTH_UNKNOWN:
                default:
                    ret = "Unknown";
            }
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }

        logV(TAGG+"Returning: \""+ret+"\"");
        return ret;
    }


    /*============================================================================================*/
    /* Logging Methods */

    private static void logV(String tagg) {
        if (getIsInstantiated()) {
            log(LOG_SEVERITY_V, tagg);
        } else {
            logStatic(LOG_SEVERITY_V, tagg);
        }
    }

    private static void logD(String tagg) {
        if (getIsInstantiated()) {
            log(LOG_SEVERITY_D, tagg);
        } else {
            logStatic(LOG_SEVERITY_D, tagg);
        }
    }

    private static void logI(String tagg) {
        if (getIsInstantiated()) {
            log(LOG_SEVERITY_I, tagg);
        } else {
            logStatic(LOG_SEVERITY_I, tagg);
        }
    }

    private static void logW(String tagg) {
        if (getIsInstantiated()) {
            log(LOG_SEVERITY_W, tagg);
        } else {
            logStatic(LOG_SEVERITY_W, tagg);
        }
    }

    private static void logE(String tagg) {
        if (getIsInstantiated()) {
            log(LOG_SEVERITY_E, tagg);
        } else {
            logStatic(LOG_SEVERITY_E, tagg);
        }
    }

    private static void log(int logSeverity, String tagg) {
        switch (logMethod) {
            case LOG_METHOD_LOGCAT:
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
            case LOG_METHOD_FILELOGGER:
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
    private static void logStatic(int logSeverity, String tagg) {
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
    }
}
