package com.messagenetsystems.evolution2.models;

/* ConfigData
 *  A model class to outline what is in the configuration data XML file.
 *  It also performs data loading, acquisition (download), initialization, and parsing capabilities.
 *
 * NOTE!!!
 *  This class is centric around getting and parsing the config file itself.
 *  If you want access to config-related stuff, consider SharedPreferencesUtils (since this stuff ultimately gets save THERE).
 *
 *  NOTE: Initialization/Parsing/Saving...
 *      This is intended for initial loading (or reloading) of config data, which is usually performed just on startup, usually.
 *      When you initialize this (with either option), the config data will be automatically parsed and flushed to shared preferences.
 *
 *  NOTE: Asynchronous Downloading and Processing...
 *      Remote file acquisition spawns an AsyncTask to do the actual downloading (.threads.ConfigDownloadAsyncTask).
 *      It also registers a self-unregistering BroadcastReceiver (this.ConfigDownloadStatusReceiver) to handle the parsing and flushing to shared-prefs.
 *      You may elect to use your own BroadcastReceiver to listen for download-completion broadcasts, if you like (e.g. like .activities.StartupActivity does)
 *
 *  To create your ConfigData instance:
 *      ConfigData configData = new ConfigData(getApplicationContext(), ConfigData.LOG_METHOD_FILELOGGER);
 *
 *  To create & initialize with a configuration file obtained from the server:
 *      configData.init(configData.INIT_WITH_REMOTE_CONFIG_DATA);
 *
 *  To create & initialize with a configuration file already acquired / existing locally:
 *      configData.init(configData.INIT_WITH_EXISTING_CONFIG_DATA);
 *
 *  To cleanup with done with your ConfigData instance:
 *      configData.cleanup();
 *      configData = null;      //optional
 *
 *  Dev-Note: To add more fields:
 *      1. Edit strings_config.xml and add line as needed.
 *      2. Add tagName_ variable below.
 *      3. Add tagValue_ variable below.
 *      4. Add initialization of tagName_ in constructor below.
 *      5. Add getter/setter for your value below.
 *
 *  Revisions:
 *      2019.11.12      Chris Rider     Created (based on ProvisionData class).
 *      2019.11.18      Chris Rider     Extended initialization method for already-downloaded data.
 *      2019.12.06      Chris Rider     Added NOTE to clarify what this class does (to alleviate previous confusion from not getting/saving MAC info and thus filename to shared-prefs)
 *      2020.02.18      Chris Rider     The setSipPassword method now also saves to new variable in OmniApplication for easy and cheap access.
 *      2020.02.20      Chris Rider     Fixed potential null-ref bug if OmniApplication is unavailable.
 *      2020.05.01      Chris Rider     Added support for clock-format string parsing (12hr/24hr time).
 *      2020.07.24      Chris Rider     Resource scope and cleanup improvements.
 */

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.OmniApplication;
import com.messagenetsystems.evolution2.R;
import com.messagenetsystems.evolution2.threads.DownloadAsyncTaskTFTP;
import com.messagenetsystems.evolution2.utilities.FileUtils;
import com.messagenetsystems.evolution2.utilities.SharedPrefsUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.StringReader;


public class ConfigData {
    private final String TAG = this.getClass().getSimpleName();

    // Constants..
    public final int INIT_WITH_REMOTE_CONFIG_DATA = 1;
    public final int INIT_WITH_EXISTING_CONFIG_DATA = 2;

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
    private Context appContext;     //TODO: migrate this to WeakReference??
    private OmniApplication omniApplication;

    private SharedPrefsUtils sharedPrefsUtils;
    private BroadcastReceiver downloadStatusReceiver;
    private boolean isConfigurationAcquisitionFinished;
    private boolean usePresentConfigurationData;

    private String cfgFileRawText;

    private String spKeyName_serverIPv4, spKeyValue_serverIPv4;
    private String spKeyName_tftpConfigFilename, spKeyValue_tftpConfigFilename;

    private String tagName_serverAddrIPv4_tagname;
    private String tagName_serverAddrIPv4_attributename;
    private String tagName_sipPort_attributename;
    private String tagName_sipUsername_attributename;
    private String tagName_sipPassword_attributename;
    private String tagName_sipRegistrationInterval_attributename;
    private String tagName_timezone_tagname;
    private String tagName_timezone_attributename;
    private String tagName_ntpURL_tagname;
    private String tagName_ntpURL_attributename;
    private String tagName_volumeDefault_tagname;
    private String tagName_volumeDefault_attributename;
    private String tagName_volumeMicrophone_tagname;
    private String tagName_volumeMicrophone_attributename;
    private String tagName_hardwareData_tagname;
    private String tagName_hardwareRecno_attributename;
    private String tagName_hardwareDeviceID_attributename;
    private String tagName_hardwareClockFormat_attributename;
    private String tagName_licencing_tagname;
    private String tagName_inactivationDate_attributename;

    // XML node values...
    // These are accessible from this class-instance's getter/setter methods.
    private String tagValue_serverAddrIPv4 = "";
    private String tagValue_sipPort = "";
    private String tagValue_sipUsername = "";
    private String tagValue_sipPassword = "";
    private String tagValue_sipRegistrationInterval = "";
    private String tagValue_timezone = "";
    private String tagValue_ntpURL = "";
    private String tagValue_volumeDefault = "";
    private String tagValue_volumeMicrophone = "";
    private String tagValue_hardwareRecno = "";
    private String tagValue_hardwareDeviceID = "";
    private String tagValue_inactivationDate = "";

    /** Constructor
     * @param appContext    Application context
     * @param logMethod     Logging method to use
     */
    public ConfigData(Context appContext, int logMethod) {
        Log.v(TAG, "Instantiating. You should initialize with data next!");

        this.logMethod = logMethod;

        try {
            this.appContext = appContext;
            try {
                this.omniApplication = ((OmniApplication) appContext.getApplicationContext());
            } catch (Exception e) {
                Log.e(TAG, "Exception caught instantiating "+TAG+": "+e.getMessage());
                return;
            }
            this.sharedPrefsUtils = new SharedPrefsUtils(appContext, SharedPrefsUtils.LOG_METHOD_FILELOGGER);
            this.downloadStatusReceiver = new ConfigDownloadStatusReceiver();
            this.isConfigurationAcquisitionFinished = false;
            this.usePresentConfigurationData = false;

            Resources resources = appContext.getResources();

            this.spKeyName_serverIPv4 = resources.getString(R.string.spKeyName_serverAddrIPv4);
            this.spKeyName_tftpConfigFilename = resources.getString(R.string.spKeyName_tftpConfigFilename);

            this.tagName_serverAddrIPv4_tagname = resources.getString(R.string.cfgfile_serverAddrIPv4_tagname);
            this.tagName_serverAddrIPv4_attributename = resources.getString(R.string.cfgfile_serverAddrIPv4_attributename);
            this.tagName_sipPort_attributename = resources.getString(R.string.cfgfile_sipPort_attributename);
            this.tagName_sipUsername_attributename = resources.getString(R.string.cfgfile_sipUsername_attributename);
            this.tagName_sipPassword_attributename = resources.getString(R.string.cfgfile_sipPassword_attributename);
            this.tagName_sipRegistrationInterval_attributename = resources.getString(R.string.cfgfile_sipRegistrationInterval_attributename);
            this.tagName_timezone_tagname = resources.getString(R.string.cfgfile_timezone_tagname);
            this.tagName_timezone_attributename = resources.getString(R.string.cfgfile_timezone_attributename);
            this.tagName_ntpURL_tagname = resources.getString(R.string.cfgfile_ntpURL_tagname);
            this.tagName_ntpURL_attributename = resources.getString(R.string.cfgfile_ntpURL_attributename);
            this.tagName_volumeDefault_tagname = resources.getString(R.string.cfgfile_volumeDefault_tagname);
            this.tagName_volumeDefault_attributename = resources.getString(R.string.cfgfile_volumeDefault_attributename);
            this.tagName_volumeMicrophone_tagname = resources.getString(R.string.cfgfile_volumeMicrophone_tagname);
            this.tagName_volumeMicrophone_attributename = resources.getString(R.string.cfgfile_volumeMicrophone_attributename);
            this.tagName_hardwareData_tagname = resources.getString(R.string.cfgfile_hardwareData_tagname);
            this.tagName_hardwareRecno_attributename = resources.getString(R.string.cfgfile_hardwareRecno_attributename);
            this.tagName_hardwareDeviceID_attributename = resources.getString(R.string.cfgfile_hardwareDeviceID_attributename);
            this.tagName_hardwareClockFormat_attributename = resources.getString(R.string.cfgfile_hardwareClockFormat_attributename);
            this.tagName_licencing_tagname = resources.getString(R.string.cfgfile_licencing_tagname);
            this.tagName_inactivationDate_attributename = resources.getString(R.string.cfgfile_inactivationDate_attributename);
        } catch (Exception e) {
            logE("Exception caught instantiating "+TAG+": "+e.getMessage());
        }

        // Also, go ahead and get some data from shared-prefs that we may need to bootstrap acquisition
        try {
            this.spKeyValue_serverIPv4 = sharedPrefsUtils.getStringValueFor(spKeyName_serverIPv4, null);
            this.spKeyValue_tftpConfigFilename = sharedPrefsUtils.getStringValueFor(spKeyName_tftpConfigFilename, null);

            if (this.spKeyValue_serverIPv4 == null || this.spKeyValue_serverIPv4.isEmpty()) {
                logW("Server IP could not be read from shared-prefs.");
            }
            if (this.spKeyValue_tftpConfigFilename == null || this.spKeyValue_tftpConfigFilename.isEmpty()) {
                logW("TFTP configuration file name could not be read from shared-prefs.");
            }
        } catch (Exception e) {
            logE("Exception caught instantiating "+TAG+": "+e.getMessage());
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


    /*============================================================================================*/
    /* Initialization Methods...
     *
     * NOTE: Initialization (with latest config file) takes place in two steps:
     * 1) Acquire the data from the server
     * 2) Parse the acquired data and load it into this instance (this is automatically done for you by onPostExecute in AsyncTask)
     *
     * Additionally, you should usually update shared-prefs, as well (flushToSharedPrefs method). */

    /** Initialization wrapper to make things more simple.
     * @param appContext Application context
     * @param initMethod Selected init method constant
     */
    public void init(@NonNull Context appContext, int initMethod) {
        final String TAGG = "init: ";

        switch (initMethod) {
            case INIT_WITH_EXISTING_CONFIG_DATA:
                logD(TAGG+"Initializing with remote configuration data.");
                usePresentConfigurationData = true;
                init_load();
                break;
            case INIT_WITH_REMOTE_CONFIG_DATA:
                logD(TAGG+"Initializing with remote configuration data.");
                usePresentConfigurationData = false;
                init_acquire(appContext);
                break;
            default:
                logW(TAGG+"Unhandled initMethod! Initializing with remote configuration data.");
                init(appContext, INIT_WITH_REMOTE_CONFIG_DATA);
        }
    }

    /** Initialize this instance by initiating the download background-task.
     * The subclassed AsyncTask defined below handles post-download procedures.
     * Normally, this is the first initialization you should call! Followed by the second below.
     * @param appContext Application context
     */
    public void init_acquire(@NonNull Context appContext) {
        final String TAGG = "init_acquire: ";

        try {
            // Initialize our flag to indicate whether acquisition is finished
            this.isConfigurationAcquisitionFinished = false;    //remember to set this to true AsyncTask download is done OK!

            // Register broadcast receiver to listen and respond to AsyncTask for download
            appContext.registerReceiver(downloadStatusReceiver, new IntentFilter(DownloadAsyncTaskTFTP.INTENTFILTER_DOWNLOAD_CONFIGURATION));

            // Acquire the configuration file via the TFTP subclass defined further below
            DownloadAsyncTaskTFTP configDownloadAsyncTask = new DownloadAsyncTaskTFTP(appContext);
            configDownloadAsyncTask.execute(appContext, spKeyValue_serverIPv4, spKeyValue_tftpConfigFilename);

            // NOTE: Remember, anything dependent on the file that gets downloaded must be handled in the subclass itself!
            // From here on, things are taken care of asynchronously, so do any subsequent work down in the AsyncTask's event handlers!
            // The AsyncTask will broadcast its status when starting and completing, so you can listen for those if you like.
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }

    /** Initialize this instance by loading in all the present data.
     * Normally, you should have acquired the data first (init_acquire).
     */
    public void init_load() {
        final String TAGG = "init_load: ";

        try {
            if (isConfigurationAcquisitionFinished()) {
                // Read the config file and save its contents to the instance
                FileUtils fileUtils = new FileUtils(this.appContext, FileUtils.LOG_METHOD_FILELOGGER);
                File cfgFile = fileUtils.getFileObjectForFile(fileUtils.getFileObjectForCacheDir(), spKeyValue_tftpConfigFilename);
                setCfgFileRawText(fileUtils.readTextFile(cfgFile));

                // Parse the config file's contents that we just saved above
                if (getCfgFileRawText() == null || getCfgFileRawText().isEmpty()) {
                    logW(TAGG + "Config acquisition yielded no useful data! Unable to load any data.");
                    logD(TAGG + "Data: \"" + String.valueOf(getCfgFileRawText()) + "\"");
                } else {
                    if (parseData(getCfgFileRawText())) {
                        flushToSharedPrefs(sharedPrefsUtils.OVERWRITE_EXISTING_TRUE, sharedPrefsUtils.SYNC_SAVE_FALSE);
                    }
                }
            } else if (usePresentConfigurationData) {
                // Read the config file and save its contents to the instance
                FileUtils fileUtils = new FileUtils(this.appContext, FileUtils.LOG_METHOD_FILELOGGER);
                File cfgFile = fileUtils.getFileObjectForFile(fileUtils.getFileObjectForCacheDir(), spKeyValue_tftpConfigFilename);
                setCfgFileRawText(fileUtils.readTextFile(cfgFile));

                // Parse the config file's contents that we just saved above
                if (getCfgFileRawText() == null || getCfgFileRawText().isEmpty()) {
                    logW(TAGG + "Config file yielded no useful data! Unable to load any data.");
                    logD(TAGG + "Data: \"" + String.valueOf(getCfgFileRawText()) + "\"");
                } else {
                    if (parseData(getCfgFileRawText())) {
                        flushToSharedPrefs(sharedPrefsUtils.OVERWRITE_EXISTING_TRUE, sharedPrefsUtils.SYNC_SAVE_FALSE);
                    }
                }
            } else {
                logW(TAGG + "Configuration file acquisition is not finished / flag not set to use present data. Unable to load any data.");
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }


    /*============================================================================================*/
    /* Cleanup Methods */

    public void cleanup() {
        final String TAGG = "cleanup: ";

        stopListeningForDownloadStatus();

        if (sharedPrefsUtils != null) {
            sharedPrefsUtils.cleanup();
            sharedPrefsUtils = null;
        }

        sharedPrefsUtils = null;
        omniApplication = null;
        appContext = null;
    }

    public void stopListeningForDownloadStatus() {
        final String TAGG = "stopListeningForDownloadStatus: ";

        try {
            appContext.unregisterReceiver(downloadStatusReceiver);
        } catch (Exception e) {
            logW(TAGG+"Exception caught: "+e.getMessage());
        }

        downloadStatusReceiver = null;
    }


    /*============================================================================================*/
    /* Parsing and XML Methods */

    /** Parse the XML data contained in the provided string and save to the instance variables.
     *
     * @param xml XML data (as a string) from the configuration file
     * @return True if completed without errors, false if error
     */
    private boolean parseData(String xml) {
        final String TAGG = "parseData: ";
        boolean ret = true;

        try {
            XmlPullParserFactory xmlFactory;
            XmlPullParser xpp;
            int eventType;

            //load data into parsing infrastructure
            xmlFactory = XmlPullParserFactory.newInstance();
            xpp = xmlFactory.newPullParser();
            xpp.setInput(new StringReader(xml));

            //do the actual parsing
            eventType = xpp.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {

                switch (eventType) {

                    case XmlPullParser.START_TAG:

                        // Server tag
                        if (xpp.getName().equals(this.tagName_serverAddrIPv4_tagname)) {
                            setServerIPv4(xpp.getAttributeValue(null, this.tagName_serverAddrIPv4_attributename));
                            setSipPort(xpp.getAttributeValue(null, this.tagName_sipPort_attributename));
                            setSipUsername(xpp.getAttributeValue(null, this.tagName_sipUsername_attributename));
                            setSipPassword(xpp.getAttributeValue(null, this.tagName_sipPassword_attributename));
                            setSipRegistrationInterval(xpp.getAttributeValue(null, this.tagName_sipRegistrationInterval_attributename));
                        }

                        // Hardware tag
                        if (xpp.getName().equals(this.tagName_hardwareData_tagname)) {
                            setHardwareRecno(xpp.getAttributeValue(null, this.tagName_hardwareRecno_attributename));
                            setHardwareDeviceID(xpp.getAttributeValue(null, this.tagName_hardwareDeviceID_attributename));
                        }

                        // Timezone tag
                        if (xpp.getName().equals(this.tagName_timezone_tagname)) {
                            setTimezone(xpp.getAttributeValue(null, this.tagName_timezone_attributename));
                            xpp.nextTag();  //move to the nested NTP server tag
                            if (xpp.getName().equals(this.tagName_ntpURL_tagname)) {
                                setNtpURL(xpp.getAttributeValue(null, this.tagName_ntpURL_attributename));
                            }
                        }

                        // Audio Config tags
                        if (xpp.getName().equals(this.tagName_volumeDefault_tagname)) {
                            setVolumeDefault(xpp.getAttributeValue(null, this.tagName_volumeDefault_attributename));
                        }
                        if (xpp.getName().equals(this.tagName_volumeMicrophone_tagname)) {
                            setVolumeMicrophone(xpp.getAttributeValue(null, this.tagName_volumeMicrophone_attributename));
                        }

                        // Licensing tag
                        if (xpp.getName().equals(this.tagName_licencing_tagname)) {
                            setInactivationDate(xpp.getAttributeValue(null, this.tagName_inactivationDate_attributename));
                        }

                        break;

                }//end switch

                eventType = xpp.next();
            }//end while
        } catch (Exception e) {
            logE(TAGG+"Exception caught parsing XML: "+e.getMessage());
            ret = false;
        }

        logD(TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    /** Take current state of instance values and save to the shared-preferences.
     * Defaults to asynchronous flush (in which case, return value is not really useful).
     * @param overwriteExistingSharedPrefsValues Whether to overwrite any existing shared-prefs values
     * @param doSynchronously Whether to perform a synchronous flush to shared-prefs (defaults to async apply())
     * @return Whether this routine is complete (true) or had some known problem (false)
     */
    @SuppressLint("ApplySharedPref")
    public boolean flushToSharedPrefs(boolean overwriteExistingSharedPrefsValues, boolean doSynchronously) {
        final String TAGG = "flushToSharedPrefs: ";
        boolean ret;

        try {
            sharedPrefsUtils.setStringValueConditionallyFor(sharedPrefsUtils.spKeyName_serverAddrIPv4, getServerIPv4(), overwriteExistingSharedPrefsValues, doSynchronously);
            sharedPrefsUtils.setStringValueConditionallyFor(sharedPrefsUtils.spKeyName_sipUsername, getSipUsername(), overwriteExistingSharedPrefsValues, doSynchronously);
            sharedPrefsUtils.setStringValueConditionallyFor(sharedPrefsUtils.spKeyName_sipPassword, getSipPassword(), overwriteExistingSharedPrefsValues, doSynchronously);
            sharedPrefsUtils.setStringValueConditionallyFor(sharedPrefsUtils.spKeyName_sipRegistrationInterval, getSipRegistrationInterval(), overwriteExistingSharedPrefsValues, doSynchronously);
            sharedPrefsUtils.setStringValueConditionallyFor(sharedPrefsUtils.spKeyName_ntpURL, getNtpURL(), overwriteExistingSharedPrefsValues, doSynchronously);
            sharedPrefsUtils.setStringValueConditionallyFor(sharedPrefsUtils.spKeyName_timezone, getTimezone(), overwriteExistingSharedPrefsValues, doSynchronously);
            sharedPrefsUtils.setStringValueConditionallyFor(sharedPrefsUtils.spKeyName_volumeDefault, getVolumeDefault(), overwriteExistingSharedPrefsValues, doSynchronously);
            sharedPrefsUtils.setStringValueConditionallyFor(sharedPrefsUtils.spKeyName_volumeMicrophone, getVolumeMicrophone(), overwriteExistingSharedPrefsValues, doSynchronously);
            sharedPrefsUtils.setStringValueConditionallyFor(sharedPrefsUtils.spKeyName_thisDeviceRecno, getHardwareRecno(), overwriteExistingSharedPrefsValues, doSynchronously);
            sharedPrefsUtils.setStringValueConditionallyFor(sharedPrefsUtils.spKeyName_thisDeviceID, getHardwareDeviceID(), overwriteExistingSharedPrefsValues, doSynchronously);
            sharedPrefsUtils.setStringValueConditionallyFor(sharedPrefsUtils.spKeyName_inactivationDateTime, getInactivationDate(), overwriteExistingSharedPrefsValues, doSynchronously);

            ret = true;
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
            ret = false;
        }

        logD(TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }


    /*============================================================================================*/
    /* Getter & Setter Methods */
    //TODO: getter/setter for usePresent

    public boolean isConfigurationAcquisitionFinished() {
        return this.isConfigurationAcquisitionFinished;
    }
    public String getCfgFileRawText() {
        return this.cfgFileRawText;
    }

    public String getServerIPv4() {
        return this.tagValue_serverAddrIPv4;
    }
    public String getSipPort() {
        return this.tagValue_sipPort;
    }
    public String getSipUsername() {
        return this.tagValue_sipUsername;
    }
    public String getSipPassword() {
        return this.tagValue_sipPassword;
    }
    public String getSipRegistrationInterval() {
        return this.tagValue_sipRegistrationInterval;
    }
    public String getTimezone() {
        return this.tagValue_timezone;
    }
    public String getNtpURL() {
        return this.tagValue_ntpURL;
    }
    public String getVolumeDefault() {
        return this.tagValue_volumeDefault;
    }
    public String getVolumeMicrophone() {
        return this.tagValue_volumeMicrophone;
    }
    public String getHardwareRecno() {
        return this.tagValue_hardwareRecno;
    }
    public String getHardwareDeviceID() {
        return this.tagValue_hardwareDeviceID;
    }
    public String getInactivationDate() {
        return this.tagValue_inactivationDate;
    }

    public void setConfigurationAcquisitionFinished(boolean value) {
        this.isConfigurationAcquisitionFinished = value;
    }
    public boolean setCfgFileRawText(@NonNull String value) {
        this.cfgFileRawText = value;
        return value.equals(this.cfgFileRawText);
    }

    public boolean setServerIPv4(@NonNull String value) {
        this.tagValue_serverAddrIPv4 = value;
        return value.equals(this.tagValue_serverAddrIPv4);
    }
    public boolean setSipPort(@NonNull String value) {
        this.tagValue_sipPort = value;
        return value.equals(this.tagValue_sipPort);
    }
    public boolean setSipUsername(@NonNull String value) {
        this.tagValue_sipUsername = value;
        return value.equals(this.tagValue_sipUsername);
    }
    public boolean setSipPassword(@NonNull String value) {
        final String TAGG = "setSipPassword: ";

        this.tagValue_sipPassword = value;

        // Also go ahead and save this to the OmniApplication instance for easy cheap access
        try {
            omniApplication.setDevicePassword(value);
        } catch (Exception e) {
            logE(TAGG+"Exception caught also setting device password in OmniApplication: "+e.getMessage());
        }

        return value.equals(this.tagValue_sipPassword);
    }
    public boolean setSipRegistrationInterval(@NonNull String value) {
        this.tagValue_sipRegistrationInterval = value;
        return value.equals(this.tagValue_sipRegistrationInterval);
    }
    public boolean setTimezone(@NonNull String value) {
        this.tagValue_timezone = value;
        return value.equals(this.tagValue_timezone);
    }
    public boolean setNtpURL(@NonNull String value) {
        this.tagValue_ntpURL = value;
        return value.equals(this.tagValue_ntpURL);
    }
    public boolean setVolumeDefault(@NonNull String value) {
        this.tagValue_volumeDefault = value;
        return value.equals(this.tagValue_volumeDefault);
    }
    public boolean setVolumeMicrophone(@NonNull String value) {
        this.tagValue_volumeMicrophone = value;
        return value.equals(this.tagValue_volumeMicrophone);
    }
    public boolean setHardwareRecno(@NonNull String value) {
        this.tagValue_hardwareRecno = value;
        return value.equals(this.tagValue_hardwareRecno);
    }
    public boolean setHardwareDeviceID(@NonNull String value) {
        this.tagValue_hardwareDeviceID = value;
        return value.equals(this.tagValue_hardwareDeviceID);
    }
    public boolean setInactivationDate(@NonNull String value) {
        this.tagValue_inactivationDate = value;
        return value.equals(this.tagValue_inactivationDate);
    }


    /*============================================================================================*/
    /* Subclasses */

    public class ConfigDownloadStatusReceiver extends BroadcastReceiver {
        private final String TAGG = this.getClass().getSimpleName();

        public ConfigDownloadStatusReceiver() {
            logV(TAGG+"Instantiating.");
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String TAGGG = "onReceive: ";
            logD(TAGG+TAGGG+"Broadcast received.");

            // Parse out the intent's status value
            String statusValue = intent.getStringExtra(DownloadAsyncTaskTFTP.INTENTEXTRA_STATUS);
            logV(TAGG+TAGGG+"Status value from intent extra: \""+statusValue+"\"");

            if (statusValue.equals(DownloadAsyncTaskTFTP.INTENTEXTRA_STATUS_FINISHED_OK)
                    || statusValue.equals(DownloadAsyncTaskTFTP.INTENTEXTRA_STATUS_FINISHED_NOTOK)) {

                // Set the flag indicating we're done acquiring data
                setConfigurationAcquisitionFinished(true);

                // Finish initializing this instance
                init_load();

                // Unregister this receiver
                stopListeningForDownloadStatus();
            }
        }
    }
}
