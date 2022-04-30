package com.messagenetsystems.evolution2.utilities;

/* SharedPrefsUtils
 * Shared-Preferences related tasks.
 * Just an easy way to get and set shared-prefs values, and other common tasks.
 * Opted to not go with a model class since it might be overly complicated.
 *
 * You MAY use this in longer running processes.
 * Be mindful to use the cleanup method whenever able.
 *
 * Revisions:
 *  2019.11.12      Chris Rider     Created.
 *  2019.12.06      Chris Rider     Changed Context to use weak reference, in case any instance is long-lived.
 *                                  Fixed many potential data-type related bugs in getters & setters, now supports string and boolean types.
 *                                  Added auto-population of most-basic, already-known fields if necessary.
 *                                  Clarified Context requirements (Preference fragment needs Activity context, specifically).
 *  2020.05.04      Chris Rider     Changed context to WeakReference so it's safer.
 *  2020.07.09      Chris Rider     Made spKeyName values static.
 */

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.R;

import java.lang.ref.WeakReference;


public class SharedPrefsUtils {
    private final String TAG = this.getClass().getSimpleName();

    public final boolean SYNC_SAVE_TRUE = true;
    public final boolean SYNC_SAVE_FALSE = false;
    public final boolean OVERWRITE_EXISTING_TRUE = true;
    public final boolean OVERWRITE_EXISTING_FALSE = false;

    // Logging stuff...
    public static final int LOG_METHOD_LOGCAT = 1;
    public static final int LOG_METHOD_FILELOGGER = 2;
    private final int LOG_SEVERITY_V = 1;
    private final int LOG_SEVERITY_D = 2;
    private final int LOG_SEVERITY_I = 3;
    private final int LOG_SEVERITY_W = 4;
    private final int LOG_SEVERITY_E = 5;
    private int logMethod = LOG_METHOD_LOGCAT;

    // Local stuff...
    private WeakReference<Context> appContextRef;   //since this thread is very long running, we prefer a weak context reference
    private SharedPreferences sharedPreferences;

    public static String spKeyName_tftpConfigFilename;
    public static String spKeyName_applianceMacAddressWifi;
    public static String spKeyName_applianceMacAddressWired;
    public static String spKeyName_applianceMacAddressActive;
    public static String spKeyName_serverAddrIPv4;
    public static String spKeyName_thisDeviceAddrIPv4;
    public static String spKeyName_timezone;
    public static String spKeyName_ntpURL;
    public static String spKeyName_sipPort;
    public static String spKeyName_sipUsername;
    public static String spKeyName_sipPassword;
    public static String spKeyName_sipRegistrationInterval;
    public static String spKeyName_volumeDefault;
    public static String spKeyName_volumeMicrophone;
    public static String spKeyName_wifiSSID;
    public static String spKeyName_wifiPassword;
    public static String spKeyName_wifiSecurityType;
    public static String spKeyName_operationMode;
    public static String spKeyName_thisDeviceID;
    public static String spKeyName_thisDeviceRecno;
    public static String spKeyName_bluetoothSpeaker;
    public static String spKeyName_ipAddressMethod;
    public static String spKeyName_gatewayIPv4;
    public static String spKeyName_dnsServer1IPv4;
    public static String spKeyName_dnsServer2IPv4;
    public static String spKeyName_batteryCapacity_tablet;
    public static String spKeyName_initialRepeats_scrollingMsg;
    public static String spKeyName_flasherMacAddress;
    public static String spKeyName_multicastAddrIPv4;
    public static String spKeyName_inactivationDateTime;
    public static String spKeyName_serialNumber;
    public static String spKeyName_useMessagenetPlatform;
    public static String spKeyName_ecosystemPlatformSelection;

    /** Constructor
     * @param appContext Application context (must be activity-centric)
     * @param logMethod Logging method to use
     */
    public SharedPrefsUtils(Context appContext, int logMethod) {
        this.logMethod = logMethod;

        try {
            this.appContextRef = new WeakReference<Context>(appContext);
            this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext);

            Resources resources = appContext.getResources();

            this.spKeyName_tftpConfigFilename = resources.getString(R.string.spKeyName_tftpConfigFilename);
            this.spKeyName_applianceMacAddressWifi = resources.getString(R.string.spKeyName_applianceMacAddressWifi);
            this.spKeyName_applianceMacAddressWired = resources.getString(R.string.spKeyName_applianceMacAddressWired);
            this.spKeyName_applianceMacAddressActive = resources.getString(R.string.spKeyName_applianceMacAddressActive);
            this.spKeyName_serverAddrIPv4 = resources.getString(R.string.spKeyName_serverAddrIPv4);
            this.spKeyName_thisDeviceAddrIPv4 = resources.getString(R.string.spKeyName_thisDeviceAddrIPv4);
            this.spKeyName_timezone = resources.getString(R.string.spKeyName_timezone);
            this.spKeyName_ntpURL = resources.getString(R.string.spKeyName_ntpURL);
            this.spKeyName_sipPort = resources.getString(R.string.spKeyName_sipPort);
            this.spKeyName_sipUsername = resources.getString(R.string.spKeyName_sipUsername);
            this.spKeyName_sipPassword = resources.getString(R.string.spKeyName_sipPassword);
            this.spKeyName_sipRegistrationInterval = resources.getString(R.string.spKeyName_sipRegistrationInterval);
            this.spKeyName_volumeDefault = resources.getString(R.string.spKeyName_volumeDefault);
            this.spKeyName_volumeMicrophone = resources.getString(R.string.spKeyName_volumeMicrophone);
            this.spKeyName_wifiSSID = resources.getString(R.string.spKeyName_wifiSSID);
            this.spKeyName_wifiPassword = resources.getString(R.string.spKeyName_wifiPassword);
            this.spKeyName_wifiSecurityType = resources.getString(R.string.spKeyName_wifiSecurityType);
            this.spKeyName_operationMode = resources.getString(R.string.spKeyName_operationMode);
            this.spKeyName_thisDeviceID = resources.getString(R.string.spKeyName_thisDeviceID);
            this.spKeyName_thisDeviceRecno = resources.getString(R.string.spKeyName_thisDeviceRecno);
            this.spKeyName_bluetoothSpeaker = resources.getString(R.string.spKeyName_bluetoothSpeaker);
            this.spKeyName_ipAddressMethod = resources.getString(R.string.spKeyName_ipAddressMethod);
            this.spKeyName_gatewayIPv4 = resources.getString(R.string.spKeyName_gatewayIPv4);
            this.spKeyName_dnsServer1IPv4 = resources.getString(R.string.spKeyName_dnsServer1IPv4);
            this.spKeyName_dnsServer2IPv4 = resources.getString(R.string.spKeyName_dnsServer2IPv4);
            this.spKeyName_batteryCapacity_tablet = resources.getString(R.string.spKeyName_batteryCapacity_tablet);
            this.spKeyName_initialRepeats_scrollingMsg = resources.getString(R.string.spKeyName_initialRepeats_scrollingMsg);
            this.spKeyName_flasherMacAddress = resources.getString(R.string.spKeyName_flasherMacAddress);
            this.spKeyName_multicastAddrIPv4 = resources.getString(R.string.spKeyName_multicastAddrIPv4);
            this.spKeyName_inactivationDateTime = resources.getString(R.string.spKeyName_inactivationDateTime);
            this.spKeyName_serialNumber = resources.getString(R.string.spKeyName_serialNumber);
            this.spKeyName_useMessagenetPlatform = resources.getString(R.string.spKeyName_useMnsConnectionsPlatform);
            this.spKeyName_ecosystemPlatformSelection = resources.getString(R.string.spKeyName_ecosystemPlatformSelection);

            // Validate any basic fields that we should already know values for...
            try {
                // If no WiFi MAC address saved in shared-prefs, populate it...
                // NOTE: SP is populated either by user, provisioning file, or config file from server, so it's possible it can be wrongly emptied.
                /*
                if (!stringValueExistsFor(this.spKeyName_applianceMacAddressWifi)) {
                    setStringValueFor(this.spKeyName_applianceMacAddressWifi,
                            new NetUtils(appContext, logMethod).getMacAddress(NetUtils.MAC_INTERFACE_AUTO, NetUtils.MAC_COLONS_NO),
                            false);
                }
                */
                //TODO: Also scan for ethernet dongle MAC (if possible) and auto save it every time app is loaded?!
            } catch (Exception e2) {
                logE("Exception caught validating basic field(s) "+TAG+": "+e2.getMessage());
            }
        } catch (Exception e) {
            logE("Exception caught instantiating "+TAG+": "+e.getMessage());
        }
    }

    public void cleanup() {
        if (this.appContextRef != null) {
            this.appContextRef.clear();
            this.appContextRef = null;
        }
    }


    /*============================================================================================*/
    /* Generalized Getter & Setter Methods */

    public String getStringValueFor(@NonNull String keyName, String defaultValue) {
        final String TAGG = "getStringValueFor(\""+keyName+"\"): ";
        String ret = "";

        try {
            ret = String.valueOf(sharedPreferences.getString(keyName, defaultValue));
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logD(TAGG+"Returning \""+ret+"\".");
        return ret;
    }

    public boolean getBooleanValueFor(@NonNull String keyName, boolean defaultValue) {
        final String TAGG = "getBooleanValueFor(\""+keyName+"\"): ";
        boolean ret = false;

        try {
            ret = sharedPreferences.getBoolean(keyName, defaultValue);
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logD(TAGG+"Returning "+Boolean.toString(ret)+".");
        return ret;
    }

    @SuppressLint("ApplySharedPref")
    public void setStringValueFor(@NonNull String keyName, @NonNull String value, boolean doSynchronously) {
        final String TAGG = "setStringValueFor(\""+keyName+"\",\""+value+"\"): ";

        // Update device with new values as needed for certain fields
        SystemUtils systemUtils = new SystemUtils(appContextRef.get(), SharedPrefsUtils.LOG_METHOD_FILELOGGER);
        if (keyName.equals(spKeyName_timezone) && (!value.equals(getStringValueFor(keyName, "")))) {
            logD(TAGG+"Shared prefs \""+keyName+"\" is different than provided value. Updating device.");
            systemUtils.setTimeZone(value);
        }
        //TODO: Add any more device related stuff right here.
        systemUtils.cleanup();

        // Update shared-prefs' stored values
        try {
            SharedPreferences.Editor spe = this.sharedPreferences.edit();

            spe.putString(keyName, value);

            // Flush the values to shared-prefs
            if (doSynchronously) {
                logD(TAGG+"Synchronously committing changes to shared-prefs.");
                spe.commit();
            } else {
                logD(TAGG+"Asynchronously applying changes to shared-prefs.");
                spe.apply();
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught trying to set value for \""+ keyName +"\": "+ e.getMessage() +".");
        }
    }

    @SuppressLint("ApplySharedPref")
    public void setBooleanValueFor(@NonNull String keyName, boolean value, boolean doSynchronously) {
        final String TAGG = "setBooleanValueFor(\""+keyName+"\",\""+value+"\"): ";

        // Update device with new values as needed for certain fields
        //SystemUtils deviceUtils = new SystemUtils(appContext, SharedPrefsUtils.LOG_METHOD_FILELOGGER);
        //TODO: Add any more device related stuff right here.
        //deviceUtils.cleanup();

        // Update shared-prefs' stored values
        try {
            SharedPreferences.Editor spe = this.sharedPreferences.edit();

            spe.putBoolean(keyName, value);

            // Flush the values to shared-prefs
            if (doSynchronously) {
                logD(TAGG+"Synchronously committing changes to shared-prefs.");
                spe.commit();
            } else {
                logD(TAGG+"Asynchronously applying changes to shared-prefs.");
                spe.apply();
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught trying to set value for \""+ keyName +"\": "+ e.getMessage() +".");
        }
    }

    @SuppressLint("ApplySharedPref")
    public void setStringValueConditionallyFor(@NonNull String keyName, @NonNull String value, boolean overwriteExistingValue, boolean doSynchronously) {
        final String TAGG = "setStringValueConditionallyFor(\""+keyName+"\",\""+value+"\"): ";
        boolean okToSet;

        if (stringValueExistsFor(keyName)) {
            //there's an existing value, so let's see what our argument specifies we should do
            if (overwriteExistingValue) {
                logV(TAGG+"Value already exists, but we can overwrite it.");
            } else {
                logV(TAGG+"Value already exists, but we can NOT overwrite it. Aborting.");
                return;
            }
        } else {
            logV(TAGG+"Value does not already exist, so we can write it.");
        }

        setStringValueFor(keyName, value, doSynchronously);
    }

    @SuppressLint("ApplySharedPref")
    public void setBooleanValueConditionallyFor(@NonNull String keyName, boolean valueToSet, boolean overwriteExistingValue, boolean doSynchronously) {
        final String TAGG = "setBooleanValueConditionallyFor(\""+keyName+"\",\""+valueToSet+"\"): ";
        boolean existingValue;
        boolean okToSet;

        existingValue = sharedPreferences.getBoolean(keyName, false);
        if (!existingValue) {
            //existing value set to false (explicit false), or we hit default value for getBoolean (implicit false)
            if (!valueToSet) {
                //existing value (explicit or implicit) indicates false, and provided with false, so go ahead and explicitly set false (since it could be implicitly set to false)
                logV(TAGG+"Existing value (explicit or implicit) indicates false, and provided with false, so go ahead and explicitly set false (since it could be implicitly set to false)");
                okToSet = true;
            } else {
                //existing value (explicit or implicit) indicates false, but provided with true, so check overwrite-allow argument
                if (overwriteExistingValue) {
                    //overwrite of existing false with provided true is allowed
                    logV(TAGG+"Overwrite of existing "+Boolean.toString(existingValue)+" with provided "+Boolean.toString(valueToSet)+" is allowed");
                    okToSet = true;
                } else {
                    //overwrite of existing false with provided true is not allowed
                    logV(TAGG+"Overwrite of existing "+Boolean.toString(existingValue)+" with provided "+Boolean.toString(valueToSet)+" is not allowed");
                    okToSet = false;
                }
            }
        } else {
            //existing value set to true (explicit true is the only possibility here)
            if (!valueToSet) {
                //existing value is true, and provided value is also true, so no need to set again
                logV(TAGG+"Existing value is true, and provided with true, so no need to set it to the same thing again.");
                okToSet = false;
            } else {
                //existing value is true, but provided value is false, so check overwrite-allow argument
                if (overwriteExistingValue) {
                    //overwrite of existing true with provided false is allowed
                    logV(TAGG+"Overwrite of existing "+Boolean.toString(existingValue)+" with provided "+Boolean.toString(valueToSet)+" is allowed");
                    okToSet = true;
                } else {
                    //overwrite of existing true with provided false is not allowed
                    logV(TAGG+"Overwrite of existing "+Boolean.toString(existingValue)+" with provided "+Boolean.toString(valueToSet)+" is not allowed");
                    okToSet = false;
                }
            }
        }

        if (okToSet) {
            logD(TAGG+"Setting \""+keyName+"\" to "+Boolean.toString(valueToSet)+"...");
            setBooleanValueFor(keyName, valueToSet, doSynchronously);
        } else {
            logD(TAGG+"No need to set \""+keyName+"\" to "+Boolean.toString(valueToSet)+" (it's already set to "+Boolean.toString(existingValue)+").");
        }
    }


    /*============================================================================================*/
    /* Specialized Getter & Setter Methods */

    /** Look in SharedPreferences for an existing Time zone, and set the system time zone to match it.
     * @return String Time Zone or null if not saved in prefs */
    public String getAndSetSharedPrefsTimeZone() {
        final String TAGG = "getAndSetSharedPrefsTimeZone: ";
        String ret = null;

        try {
            // Get the shared-prefs time zone value
            String keyValue = getStringValueFor(spKeyName_timezone, null);                                //get the existing timeZone value from shared prefs

            // Set the device's time zone
            if (keyValue != null) {
                SystemUtils systemUtils = new SystemUtils(appContextRef.get(), SharedPrefsUtils.LOG_METHOD_FILELOGGER);
                systemUtils.setTimeZone(keyValue);
            } else {
                logW(TAGG+"Shared preferences value for \""+spKeyName_timezone+"\" is null, so unable to set.");
            }

            ret = keyValue;
        } catch (Exception e) {
            logE(TAGG+"Exception caught trying to read value for \""+ spKeyName_timezone +"\", or setting system time zone: "+ e.getMessage() +".");
        }

        logD(TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    public void setSharedPrefsTimeZone(String timezone) {
        final String TAGG = "setSharedPrefsTimeZone(\""+timezone+"\"): ";

        try {
            setStringValueFor(spKeyName_timezone, timezone, SYNC_SAVE_FALSE);

            // Set the device's time zone
            SystemUtils systemUtils = new SystemUtils(appContextRef.get(), SharedPrefsUtils.LOG_METHOD_FILELOGGER);
            systemUtils.setTimeZone(timezone);

        } catch (Exception e) {
            logE(TAGG+"Exception caught trying to set value for \""+ spKeyName_timezone +"\", or setting system time zone: "+ e.getMessage() +".");
        }
    }


    /*============================================================================================*/
    /* Utility Methods */

    public boolean stringValueIsBlankFor(@NonNull String keyName) {
        final String TAGG = "stringValueIsBlankFor(\"" + keyName + "\"): ";
        boolean ret;

        String value = getStringValueFor(keyName, "");

        if (value == null || value.isEmpty() || value.equals("")) {
            ret = true;
        } else {
            ret = false;
        }

        logD(TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    public boolean stringValueExistsFor(@NonNull String keyName) {
        final String TAGG = "stringValueExistsFor(\"" + keyName + "\"): ";
        boolean ret;

        String value = getStringValueFor(keyName, "");

        if (value == null || value.isEmpty() || value.equals("")) {
            ret = false;
        } else {
            ret = true;
        }

        logD(TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    public boolean booleanValueExistsFor(@NonNull String keyName) {
        final String TAGG = "booleanValueExistsFor(\"" + keyName + "\"): ";
        boolean ret;

        try {
            getBooleanValueFor(keyName, false);
            ret = true;
        } catch (Exception e) {
            ret = false;
        }

        logD(TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
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
}
