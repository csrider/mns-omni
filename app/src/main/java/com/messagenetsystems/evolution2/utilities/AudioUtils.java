package com.messagenetsystems.evolution2.utilities;

/* AudioUtils
 * Repository of audio and sound related tasks.
 *
 * There are two ways to use this: work with an instance, or access directly with static methods.
 *
 * To instantiate this in order to use it:
 *  Ex. AudioUtils audioUtils = new AudioUtils(getApplicationContext(), AudioUtils.LOG_METHOD_FILELOGGER);
 *
 * You should also take care to clean this up gracefully when done with it:
 *  Ex. audioUtils.cleanup();
 *
 * Note: This uses weak context reference, so it's safe to initialize and use in long-running processes.
 *
 * Revisions:
 *  2020.04.30      Chris Rider     Created - nowhere near completed... just starting. Focused first on AudioStaticUtils.
 *  2020.05.01      Chris Rider     Began making this our main audio guy.. migrating AudioStaticUtils into it.
 *  2020.05.04      Chris Rider     Additional methods and improvements to get base audio and message gain working.
 */

import android.content.Context;
import android.content.res.Resources;
import android.media.AudioManager;
import android.support.annotation.Nullable;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.R;
import com.messagenetsystems.evolution2.models.ConfigData;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;


public class AudioUtils {
    final private static String TAG = AudioUtils.class.getSimpleName();

    // Constants...
    public final static int UNKNOWN_VALUE = -1;

    public final static int STREAM_DEFAULT = -1;
    public final static int STREAM_VOICE_CALL = AudioManager.STREAM_VOICE_CALL;
    public final static int STREAM_SYSTEM = AudioManager.STREAM_SYSTEM;
    public final static int STREAM_RING = AudioManager.STREAM_RING;
    public final static int STREAM_MUSIC = AudioManager.STREAM_MUSIC;
    public final static int STREAM_ALARM = AudioManager.STREAM_ALARM;
    public final static int STREAM_NOTIFICATION = AudioManager.STREAM_NOTIFICATION;
    public final static int STREAM_DTMF = AudioManager.STREAM_DTMF;

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
    private int defaultBaseVolume;



    /** Constructor
     * @param appContext Application context
     * @param logMethod Logging method to use
     */
    public AudioUtils(Context appContext, int logMethod) {
        this.logMethod = logMethod;
        this.appContextRef = new WeakReference<Context>(appContext);

        instanceCounter.incrementAndGet();

        initialize(appContext);
    }

    /** We can use this to tell if this class has been instantiated or not. */
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


    /*============================================================================================*/
    /* Class Support Methods */

    public void initialize(Context context) {
        final String TAGG = "initialize: ";

        this.defaultBaseVolume = getConfiguredBaseVolume(context);
    }

    public void cleanup() {
        if (appContextRef != null) {
            appContextRef.clear();
            appContextRef = null;
        }
    }


    /*============================================================================================*/
    /* System Methods */

    /** Read from the configuration/shared-prefs to get the saved Volume Default value.
     * Note: There is a static as well as non-static version of this method here. Use accordingly.
     * @return Integer representation of the value saved in the configured shared-prefs.
     */
    public static int getConfiguredBaseVolume(Context context) {
        final String TAGG = "getConfiguredBaseVolume: ";

        int ret;

        try {
            // Get configuration data saved in shared preferences...
            SharedPrefsUtils sharedPrefsUtils = new SharedPrefsUtils(context, Constants.LOG_METHOD_FILELOGGER);
            String configVolumeDefault = sharedPrefsUtils.getStringValueFor(sharedPrefsUtils.spKeyName_volumeDefault, "50");
            sharedPrefsUtils.cleanup();

            // Parse volume...
            try {
                ret = Integer.parseInt(configVolumeDefault);
            } catch (Exception e) {
                logW(TAGG + "Exception caught parsing default base volume from configuration (setting middle ground value of 50): " + e.getMessage());
                ret = 50;
            }

            // Validate and normalize parsed default base volume...
            if (ret >= 0 && ret <= 100) {
                logI(TAGG + "Configured default base volume is: " + Integer.toString(ret));
            } else if (ret < 0) {
                logW(TAGG + "Invalid default base volume of <0, setting to 0.");
                ret = 0;
            } else if (ret > 100) {
                logW(TAGG + "Invalid default base volume of >100, setting to 100.");
                ret = 100;
            } else {
                logW(TAGG + "Invalid default base volume (" + String.valueOf(ret) + "), setting to middle ground value of 50.");
            }
        } catch (Exception e) {
            logW(TAGG + "Exception caught obtaining default base volume from configuration (setting middle ground value of 50): " + e.getMessage());
            ret = 50;
        }

        logV(TAGG + "Returning: "+Integer.toString(ret));
        return ret;
    }
    public int getConfiguredBaseVolume() {
        return getConfiguredBaseVolume(this.appContextRef.get());
    }

    /** Return what we consider to be the main primary audio stream, since Android has many that may be irrelevant to us.
     * You could do this other ways, but this is just to help avoid confusion since we primarily use just the media stream.
     * @return Integer ID of the .
     */
    public static int getPrimaryStream() {
        return STREAM_MUSIC;
    }


    /*============================================================================================*/
    /* Audio System Methods */

    /** Create and return an AudioManager instance.
     * Note: There is a static as well as non-static version of this method here. Use accordingly.
     * @return An AudioManager instance, or null if could not create.
     */
    public static AudioManager getAudioManagerInstance(Context context) {
        final String TAGG = "getAudioManagerInstance: ";

        AudioManager audioManager = null;

        try {
            audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+ e.getMessage());
        }

        if (audioManager == null) {
            logW(TAGG+"Could not get an AudioManager instance, returning null!");
        }

        return audioManager;
    }
    public AudioManager getAudioManagerInstance() {
        return getAudioManagerInstance(this.appContextRef.get());
    }


    /*============================================================================================*/
    /* Volume Methods */

    /** Get the maximum raw Android level for the specified stream.
     * Note: There is a static as well as non-static version of this method here. Use accordingly.
     * @return Integer representing the internal Android maximum volume level for the specified stream.
     */
    public static int getMaximumLevelForStream(Context context, int specifiedStream) {
        final String TAGG = "getMaximumLevelForStream("+Integer.toString(specifiedStream)+"): ";
        int maxStreamLevel;

        if (specifiedStream == STREAM_DEFAULT) {
            specifiedStream = getPrimaryStream();
        }

        try {
            AudioManager audioManager = getAudioManagerInstance(context);                           // Get an audio controller instance
            maxStreamLevel = audioManager.getStreamMaxVolume(specifiedStream);                      // Get the specified stream's maximum value
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
            maxStreamLevel = UNKNOWN_VALUE;
        }

        logV(TAGG+"Returning: "+Integer.toString(maxStreamLevel));
        return maxStreamLevel;
    }
    public int getMaximumLevelForStream(int specifiedStream) {
        return getMaximumLevelForStream(this.appContextRef.get(), specifiedStream);
    }

    /** Get the current raw Android level for the specified stream.
     * Note: There is a static as well as non-static version of this method here. Use accordingly.
     * @return Integer representing the internal Android current volume level for the specified stream.
     */
    public static int getCurrentLevelForStream(Context context, int specifiedStream) {
        final String TAGG = "getCurrentLevelForStream("+Integer.toString(specifiedStream)+"): ";
        int currentStreamLevel;

        if (specifiedStream == STREAM_DEFAULT) {
            specifiedStream = getPrimaryStream();
        }

        try {
            AudioManager audioManager = getAudioManagerInstance(context);                           // Get an audio controller instance
            currentStreamLevel = audioManager.getStreamVolume(specifiedStream);                     // Get the specified stream's current raw value
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
            currentStreamLevel = UNKNOWN_VALUE;
        }

        logV(TAGG+"Returning: "+Integer.toString(currentStreamLevel));
        return currentStreamLevel;
    }
    public int getCurrentLevelForStream(int specifiedStream) {
        return getCurrentLevelForStream(this.appContextRef.get(), specifiedStream);
    }

    /** Get the current percentage equivalent for the specified stream.
     * Note: There is a static as well as non-static version of this method here. Use accordingly.
     * @return Integer representation of the percentage value for the current level of the specified volume stream.
     */
    public static int getCurrentVolumePercentForStream(Context context, int specifiedStream) {
        final String TAGG = "getCurrentVolumePercentForStream("+Integer.toString(specifiedStream)+"): ";
        int currentStreamLevel_percent;

        if (specifiedStream == STREAM_DEFAULT) {
            specifiedStream = getPrimaryStream();
        }

        try {
            AudioManager audioManager = getAudioManagerInstance(context);                           // Get an audio controller instance
            int currentStreamLevel_raw = audioManager.getStreamVolume(specifiedStream);             // Get the specified stream's current raw value
            int maxStreamLevel = getMaximumLevelForStream(context, specifiedStream);                // Get the specified stream's maximum value
            currentStreamLevel_percent = ConversionUtils.calculatePercentOfMax_nearestInteger(maxStreamLevel, currentStreamLevel_raw);
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
            currentStreamLevel_percent = UNKNOWN_VALUE;
        }

        logV(TAGG+"Returning: "+Integer.toString(currentStreamLevel_percent));
        return currentStreamLevel_percent;
    }
    public int getCurrentVolumePercentForStream(int specifiedStream) {
        return getCurrentVolumePercentForStream(this.appContextRef.get(), specifiedStream);
    }

    /** Calculate the raw Android volume level for the specified percent and stream.
     * Note: There is a static as well as non-static version of this method here. Use accordingly.
     * @param percentVolume     Integer representation of a whole-number percent to calculate raw Android value for.
     * @param specifiedStream   Stream ID to calculate for.
     * @return Integer value of calculated raw Android volume level.
     */
    public static int calculateAndroidVolumeLevelFromPercentForStream(Context context, int percentVolume, int specifiedStream) {
        final String TAGG = "calculateAndroidVolumeLevelFromPercentForStream("+Integer.toString(percentVolume)+","+Integer.toString(specifiedStream)+"): ";
        int calculatedRawAndroidVolumeLevel;

        if (specifiedStream == STREAM_DEFAULT) {
            specifiedStream = getPrimaryStream();
        }

        try {
            // Apply percentage to stream's maximum value and round
            calculatedRawAndroidVolumeLevel = ConversionUtils.calculateValueOfPercentMax_nearestInteger(getMaximumLevelForStream(context, specifiedStream), percentVolume);
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
            calculatedRawAndroidVolumeLevel = UNKNOWN_VALUE;
        }

        logV(TAGG+"Returning: "+Integer.toString(calculatedRawAndroidVolumeLevel));
        return calculatedRawAndroidVolumeLevel;
    }
    public int calculateAndroidVolumeLevelFromPercentForStream(int percentVolume, int specifiedStream) {
        return calculateAndroidVolumeLevelFromPercentForStream(this.appContextRef.get(), percentVolume, specifiedStream);
    }

    /** Set the Android volume for the stream directly.
     * @param specifiedStream           Stream ID to set volume for.
     * @param androidVolumeLevelToSet   Integer Android raw volume level to set.
     * @return The Android volume level for the stream after setting it, or UNKNOWN_VALUE if not available.
     */
    public static int setAudioVolumeForStream(Context context, int androidVolumeLevelToSet, int specifiedStream) {
        final String TAGG = "setAudioVolumeForStream("+Integer.toString(androidVolumeLevelToSet)+","+Integer.toString(specifiedStream)+"): ";
        int volumeAfterSetting = UNKNOWN_VALUE;

        if (specifiedStream == STREAM_DEFAULT) {
            specifiedStream = getPrimaryStream();
        }

        try {
            int amFlag = 0;

            // Get Android volume level bounds
            int streamMinimumValue = 0;
            int streamMaximumValue = getMaximumLevelForStream(context, specifiedStream);

            // Normalize the specified level, if outside of possible values
            if (androidVolumeLevelToSet < streamMinimumValue) {
                logW(TAGG+"Specified volume is below stream minimum. Setting to minimum ("+Integer.toString(streamMinimumValue)+").");
                androidVolumeLevelToSet = streamMinimumValue;
            } else if (androidVolumeLevelToSet > streamMaximumValue) {
                logW(TAGG+"Specified volume is above stream maximum. Setting to maximum ("+Integer.toString(streamMaximumValue)+").");
                androidVolumeLevelToSet = streamMaximumValue;
            }

            // Set the stream level
            AudioManager audioManager = getAudioManagerInstance(context);
            audioManager.setStreamVolume(specifiedStream, androidVolumeLevelToSet, amFlag);
            audioManager = null;

            // Get the current stream level
            volumeAfterSetting = getCurrentLevelForStream(context, specifiedStream);
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        if (volumeAfterSetting != androidVolumeLevelToSet) {
            logW(TAGG+"Specified volume could not be set. Perhaps safe-volume limitation is in effect?\nMake sure build.prop file is updated to include \"audio.safemedia.bypass=true\"");
        }

        logD(TAGG+"Stream is now: "+Integer.toString(volumeAfterSetting));
        return volumeAfterSetting;
    }
    public int setAudioVolumeForStream(int androidVolumeLevelToSet, int specifiedStream) {
        return setAudioVolumeForStream(this.appContextRef.get(), androidVolumeLevelToSet, specifiedStream);
    }

    /** Set the configured default device base volume for all streams.
     * Note: There is a static as well as non-static version of this method here. Use accordingly.
     * @return Boolean representing whether volume streams were reset or not.
     */
    public static boolean setConfiguredDefaultBaseVolume(Context context) {
        final String TAGG = "setConfiguredDefaultBaseVolume: ";
        boolean ret = false;

        try {
            // Get our Android-equivalent default volume to set
            int configuredBaseVolume = getConfiguredBaseVolume(context);                            //This is a percent, since the range is 0-100
            int androidMaxVolume = getMaximumLevelForStream(context, getPrimaryStream());
            int calculatedAndroidVolume = ConversionUtils.calculateValueOfPercentMax_nearestInteger(androidMaxVolume, configuredBaseVolume);

            // Set the volume
            setAudioVolumeForStream(context, calculatedAndroidVolume, getPrimaryStream());

            // Validate the setting so we know what result to return
            if (getCurrentLevelForStream(context, getPrimaryStream()) == calculatedAndroidVolume) {
                ret = true;
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
            ret = false;
        }

        logV(TAGG+"Returning \""+Boolean.toString(ret)+"\".");
        return ret;
    }
    public boolean setConfiguredDefaultBaseVolume() {
        return setConfiguredDefaultBaseVolume(this.appContextRef.get());
    }

    /** Calculate the Android volume level that is the specified percent above the current level.
     * For example, current level is 6 (max 10), percent gain is 50, then result will be 8.
     * Note: There is a static as well as non-static version of this method here. Use accordingly.
     * @param percentToGain     Integer representation of a whole-number percent of gain to calculate for.
     * @param specifiedStream   Stream ID to calculate volume gain for.
     * @return Android raw volume after gain has been added.
     */
    public static int calculateGainedAndroidVolumeAboveCurrent(Context context, int percentToGain, int specifiedStream) {
        final String TAGG = "calculateGainedAndroidVolumeAboveCurrent("+Integer.toString(percentToGain)+"%,"+Integer.toString(specifiedStream)+"): ";
        int gainedAndroidVolume = UNKNOWN_VALUE;

        if (specifiedStream == STREAM_DEFAULT) {
            specifiedStream = getPrimaryStream();
        }

        try {
            // Get current Android volume for the stream
            int currentVolume = getCurrentLevelForStream(context, specifiedStream);

            // Get the maximum amount that the current volume can still increase to
            int maxLevelForStream = getMaximumLevelForStream(context, specifiedStream);
            int actualMaxGainPossible = maxLevelForStream - currentVolume;

            // Apply the percent gain to the max gain possible, so we know what we can increase by
            int amountToGain = Math.round((float)percentToGain/100 * actualMaxGainPossible);

            // Add the gain amount to the current, so we know what to actually return
            gainedAndroidVolume = currentVolume + amountToGain;

            // Validate result before returning it
            if (gainedAndroidVolume > maxLevelForStream) {
                gainedAndroidVolume = maxLevelForStream;
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning \""+Integer.toString(gainedAndroidVolume)+"\".");
        return gainedAndroidVolume;
    }
    public int calculateGainedAndroidVolumeAboveCurrent(int percentToGain, int specifiedStream) {
        return calculateGainedAndroidVolumeAboveCurrent(this.appContextRef.get(), percentToGain, specifiedStream);
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
