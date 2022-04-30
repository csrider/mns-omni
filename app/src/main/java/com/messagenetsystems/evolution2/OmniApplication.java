package com.messagenetsystems.evolution2;

/* OmniApplication
 * Application class for global stuff.
 * This gets executed when the app starts, before anything else.
 * The primary advantage is to have a one-place/one-time initialization of data that all other classes can use, without them needing to init the data.
 * There may also be advantages in memory management and graceful handling of stuff?
 * We can also know which activity is running.
 *
 * Flow (remember this particular class happens implicitly with/before StartupActivity)
 *  o Entry (activities.StartupActivity via .receivers.BootReceiver, or manual launch of StartupActivity)
 *  |
 *  o Initializations and loads
 *
 * Usage:
 *  OmniApplication omniApp = ((OmniApplication) getApplicationContext());
 *
 * Info:
 *  - https://github.com/codepath/android_guides/wiki/Understanding-the-Android-Application-Class
 *
 * Revisions:
 *  2019.11.06      Chris Rider     Created.
 *  2019.12.05      Chris Rider     Added support for TextToSpeechServicer and platform we're running under.
 *  2019.12.06      Chris Rider     Added stuff to support ecosystem.
 *  2019.12.10      Chris Rider     Updated notification methods to add append-as-new-line capability.
 *  2019.12.17      Chris Rider     Updated ecosystem to include legacy Omni stuff and BannerMessage support.
 *  2020.02.17      Chris Rider     Shifted run-mode stuff over to StartupActivity processes, instead only just initializing it here.
 *  2020.02.18      Chris Rider     Added vars to store more app version info and device (typically SIP) password.
 *  2020.02.19      Chris Rider     Now actually generating a unique notification ID, and using it.
 *  2020.05.11      Chris Rider     Added global flag for whether ClockActivity is visible or not.
 *  2020.05.12      Chris Rider     Added Date for tracking when the app started, and accompanying methods, so we can know how long it's been running.
 *  2020.05.26      Chris Rider     Made package-name available statically.
 *  2020.06.04      Chris Rider     Now able to get and save other packages' version numbers.
 *  2020.06.17      Chris Rider     Added grantPermission method to startup.
 */

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.bosphere.filelogger.FLConfig;
import com.bosphere.filelogger.FLConst;
import com.messagenetsystems.evolution2.activities.StartupActivity;
import com.messagenetsystems.evolution2.models.ProcessStatusList;
import com.messagenetsystems.evolution2.models.ProvisionData;
import com.messagenetsystems.evolution2.receivers.ClearMessagesRequestReceiver;
import com.messagenetsystems.evolution2.receivers.MainServiceStopRequestReceiver;
import com.messagenetsystems.evolution2.services.MainService;
import com.messagenetsystems.evolution2.utilities.DatetimeUtils;
import com.messagenetsystems.evolution2.utilities.FileUtils;
import com.messagenetsystems.evolution2.utilities.NetUtils;
import com.messagenetsystems.evolution2.utilities.SharedPrefsUtils;
import com.messagenetsystems.evolution2.utilities.SystemUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class OmniApplication extends Application {
    private final String TAG = this.getClass().getSimpleName();

    // Constants...
    public static final int RUN_MODE_IDLE = 1;          //nothing should really actively be running and doing stuff
    public static final int RUN_MODE_NORMAL = 2;        //should operate as normal (get latest configuration from server)
    //public static final int RUN_MODE_BURN_IN = 3;       //run the Omni through its initial-testing/burn-in procedures
    //public static final int RUN_MODE_IN_THE_SHOP = 4;   //a normal/provisioned Omni is "in the shop" for some reason (repair, troubleshooting, upgrade, etc.)

    public static final int ECOSYSTEM_UNKNOWN = 1;
    public static final int ECOSYSTEM_MESSAGENET_CONNECTIONS_V1 = 2;        //legacy MessageNet Omni (for direct BannerMessage support)
    public static final int ECOSYSTEM_MESSAGENET_CONNECTIONS_V2 = 3;
    public static final int ECOSYSTEM_STANDARD_API = 4;

    public static final int NOTIF_REPLACE = 1;
    public static final int NOTIF_APPEND = 2;

    // Local class stuff...
    private boolean isProvFileAvailable;            //we just save this so we don't have to call expensive I/O every time we want to know
    private NotificationCompat.Builder mNotifBuilder;
    private int mNotifID;
    private Notification mNotification;
    Intent stopMainServiceIntent;
    PendingIntent stopMainServicePendingIntent;
    Intent clearMessagesIntent;
    PendingIntent clearMessagesPendingIntent;

    // Global data variables (available via getter methods)...
    private String appPackageName;
    public static String appPackageNameStatic;
    private String appVersion;
    private String appVersion_watchdog;
    private String appVersion_updater;
    private String appVersion_flashers;
    private String appVersion_watcher;
    private int runMode;
    private int ecosystem;
    private Date appStartedDate;
    private Activity currentVisibleActivity;
    private ProvisionData provisionData;
    private volatile boolean allowAppToDie;
    private String devicePassword;

    private Intent mainServiceIntent;
    private MainService mainService;

    private volatile boolean isClockShowing;

    private volatile boolean isTextToSpeechAvailable;
    private volatile boolean isTextToSpeechOccurring;

    public volatile ProcessStatusList processStatusList;


    /*============================================================================================*/
    /* Application class methods */

    // Called when the application is starting, before any other application objects have been created.
    @Override
    public void onCreate() {
        super.onCreate();
        final String TAGG = "onCreate: ";
        Log.v(TAG, TAGG+"Invoked.");

        // First, before anything, get our logger setup...
        initLoggingUtility();   //logging utility (this needs to be done ASAP before anything else)

        // Initialize stuff...
        initLocalStuff();       //local stuff for the inner workings of this class (do this before globals)
        initGlobalData();       //global (via getters) data variables (requires locals to be initialized first)

        // Grant permissions...
        grantPermission("android.permission.SYSTEM_ALERT_WINDOW");

        // Setup the initial notification and show it
        // We do this here after inits, since it may contain information that needs initialized first
        try {
            String notifText = "Starting";
            mNotifBuilder = createNotificationBuilderObject(notifText);
            showNotif(mNotifBuilder);
        } catch (Exception e) {
            Log.w(TAG, TAGG+"Exception caught trying to setup notification: "+e.getMessage());
        }

        // Register Activity Life Cycle callback, so we can get currently running activity in our app from currentVisibleActivity
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                final String TAGG = "onActivityCreated("+activity.getClass().getSimpleName()+"): ";
                FL.d(TAG, TAGG+"Invoked.");
            }

            @Override
            public void onActivityStarted(Activity activity) {
                final String TAGG = "onActivityStarted("+activity.getClass().getSimpleName()+"): ";
                FL.d(TAG, TAGG+"Invoked.");
            }

            @Override
            public void onActivityResumed(Activity activity) {
                final String TAGG = "onActivityResumed("+activity.getClass().getSimpleName()+"): ";
                FL.i(TAG, TAGG+"Invoked.");

                setCurrentVisibleActivity(activity);
            }

            @Override
            public void onActivityPaused(Activity activity) {
                final String TAGG = "onActivityPaused("+activity.getClass().getSimpleName()+"): ";
                FL.d(TAG, TAGG+"Invoked.");

                setCurrentVisibleActivity(null);
            }

            @Override
            public void onActivityStopped(Activity activity) {
                final String TAGG = "onActivityStopped("+activity.getClass().getSimpleName()+"): ";
                FL.d(TAG, TAGG+"Invoked.");
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                final String TAGG = "onActivitySaveInstanceState("+activity.getClass().getSimpleName()+"): ";
                FL.d(TAG, TAGG+"Invoked.");
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                final String TAGG = "onActivityDestroyed("+activity.getClass().getSimpleName()+"): ";
                FL.d(TAG, TAGG+"Invoked.");
            }
        });
    }

    // Called by the system when the device configuration changes while your app is running.
    // At the time that this function has been called, your Resources object will have been updated to return resource values matching the new configuration.
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        final String TAGG = "onConfigurationChanged: ";
        FL.i(TAG, TAGG+"Invoked.");

        //TODO? Depending on what "configuration" means, you may need to reload/reinit stuff?
    }

    // Called when the overall system is running low on memory, and would like actively running processes to tighten their belts.
    // Actively running processes should trim their memory usage when this is invoked.
    // You should implement this method to release any caches or other unnecessary resources you may be holding onto.
    // The system will perform a garbage collection for you after returning from this method.
    // PREFERABLY, you should implement ComponentCallbacks2 to incrementally unload your resources based on various levels of memory demands (API 14 and higher).
    // This method should, then, be a fallback for older versions (which can be treated same as ComponentCallbacks2#onTrimMemory with the ComponentCallbacks2#TRIM_MEMORY_COMPLETE level)
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        final String TAGG = "onLowMemory: ";
        FL.i(TAG, TAGG+"Invoked.");
    }

    // Called when the OS has determined that it's a good time for a process to trim unneeded memory from its processes.
    // This will happen for example when it goes to background and there's not enough memory to keep as many background processes running as desired.
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        final String TAGG = "onTrimMemory: ";
        FL.i(TAG, TAGG+"Invoked.");
    }

    // This method is for use in emulated process environments.
    // It will never be called on a production Android device!
    @Override
    public void onTerminate() {
        super.onTerminate();
        final String TAGG = "onTerminate: ";
        FL.d(TAG, TAGG+"Invoked.");
    }


    /*============================================================================================*/
    /* Supporting methods */

    // Load up the logging utility for the entire app to use.
    // NOTE: THIS SHOULD BE INVOKED AS EARLY AS POSSIBLE!
    // Note: this will do a short thread sleep before returning!
    private void initLoggingUtility() {
        final String TAGG = "initLoggingUtility: ";
        Log.v(TAG, TAGG+"Invoked.");

        try {
            // Add everything to logcat whitelist to prevent lost logs due to chatty throttling
            // DEV-NOTE: We may no longer need to do this, as it doesn't seem to filter our logfile at all! Better to just let it do its own thing?
            //SystemUtils.whitelistLogcat();

            // Ensure we have storage-access permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, TAGG+"Permission WRITE_EXTERNAL_STORAGE not granted. Logger cannot function!");
                return;
            }

            // Configure prerequisites to feed into logging service configurator
            File logDirectory = new File(Environment.getExternalStorageDirectory(), "logs_"+String.valueOf(getPackageName()));

            // Configure and initialize logging service
            FL.init(new FLConfig.Builder(this)
                    //.logger(...)                                                                    //customize how to hook up with logcat
                    .defaultTag(getResources().getString(R.string.app_name_short))                  //customize default tag
                    .minLevel(FLConst.Level.V)                                                      //customize minimum logging level
                    .logToFile(Constants.Configuration.App.LOG_TO_FILE)                             //enable logging to file
                    .dir(logDirectory)                                                              //customize directory to hold log files
                    //.formatter(...)                                                                 //customize log format and file name
                    .retentionPolicy(FLConst.RetentionPolicy.TOTAL_SIZE)                            //customize retention strategy
                    //.maxFileCount(FLConst.DEFAULT_MAX_FILE_COUNT)                                   //customize how many log files to keep if retention strategy is by file count (defaults to 168)
                    .maxTotalSize(FLConst.DEFAULT_MAX_TOTAL_SIZE)                                   //customize how much space log files can occupy if strategy is by total size (in bytes... defaults to 32MB)
                    .build());

            // Overall toggle to enable/disable logging!
            FL.setEnabled(true);

            // Give a second for things to finish and become ready
            // We do this in case other stuff starts to log right away
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught initializing logging utility: "+e.getMessage());
        }
    }

    // Initialize local resources
    private void initLocalStuff() {
        final String TAGG = "initLocalStuff: ";

        this.isProvFileAvailable = isProvFileAvailable();

        stopMainServiceIntent = new Intent(this, MainServiceStopRequestReceiver.class);
        stopMainServicePendingIntent = PendingIntent.getBroadcast(this, 0, stopMainServiceIntent, 0);

        clearMessagesIntent = new Intent(this, ClearMessagesRequestReceiver.class);
        clearMessagesPendingIntent = PendingIntent.getBroadcast(this, 0, clearMessagesIntent, 0);

        this.mNotifID = Integer.parseInt(new SimpleDateFormat("ddHHmmss", Locale.US).format(new Date()));
    }

    // Initialize "global" data
    // Remember, this gets made available globally by getter methods!
    // WARNING: You must call initLocalStuff first!
    private void initGlobalData() {
        final String TAGG = "initGlobalData: ";

        this.appPackageName = loadAppPackageName(getApplicationContext());
        appPackageNameStatic = appPackageName;
        this.appVersion = loadAppVersion(getApplicationContext());
        this.appVersion_watchdog = "";    //TODO
        this.appVersion_updater = "";     //TODO
        this.appVersion_flashers = loadAppVersion(getApplicationContext(), Constants.PACKAGE_NAME_FLASHERS);
        this.appVersion_watcher = "";     //TODO
        this.runMode = RUN_MODE_IDLE;           //initialize like this for now, and actually set later (StartupActivity)
        this.ecosystem = ECOSYSTEM_UNKNOWN;     //initialize like this for now, and actually set later once we know we're actually starting/running (and have provision/config data)
        this.appStartedDate = new Date();
        this.currentVisibleActivity = null;
        this.provisionData = loadProvData(getApplicationContext());
        this.allowAppToDie = false;
        this.devicePassword = "";         //TODO

        this.mainService = new MainService(getApplicationContext());
        this.mainServiceIntent = new Intent(getApplicationContext(), mainService.getClass());

        this.isClockShowing = false;

        this.isTextToSpeechAvailable = false;
        this.isTextToSpeechOccurring = false;

        this.processStatusList = new ProcessStatusList();
    }

    private void grantPermission(final String permissionName) {
        final String TAGG = "grantPermission("+permissionName+"): ";

        try {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        SystemUtils.grantPermission(getAppPackageName(), permissionName);
                    } catch (Exception e) {
                        FL.e(TAG, TAGG+"Exception caught!", e);
                    }
                }
            }).start();
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught!", e);
        }
    }

    // Load app version and return it
    private String loadAppPackageName(Context appContext) {
        final String TAGG = "loadAppPackageName: ";
        String ret = "";

        try {
            ret = String.valueOf(appContext.getPackageName());
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught!", e);
        }

        FL.d(TAG, TAGG+"Returning \""+ret+"\".");
        return ret;
    }

    // Load app version and return it
    private String loadAppVersion(Context appContext) {
        final String TAGG = "loadAppVersion: ";
        String ret = "";

        try {
            ret = loadAppVersion(appContext, loadAppPackageName(appContext));
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught!", e);
        }

        FL.d(TAG, TAGG+"Returning \""+ret+"\".");
        return ret;
    }

    private String loadAppVersion(Context appContext, String packageName) {
        final String TAGG = "loadAppVersion: ";
        String ret = "";

        try {
            PackageInfo pInfo = appContext.getPackageManager().getPackageInfo(packageName, 0);
            ret = String.valueOf(pInfo.versionName);
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught!", e);
        }

        FL.d(TAG, TAGG+"Returning \""+ret+"\".");
        return ret;
    }

    public void updateAppVersions(Context appContext) {
        final String TAGG = "updateAppVersions: ";

        try {
            this.appVersion_flashers = loadAppVersion(appContext, Constants.PACKAGE_NAME_FLASHERS);
            //this.appVersion_updater = ;       TODO
            //this.appVersion_watchdog = ;      TODO
            //this.appVersion_watcher = ;       TODO
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught!", e);
        }
    }

    // Load running mode and return it
    // This needs to also do the work of figuring out what running mode we need to use
    /*
    private int loadRunMode() {
        final String TAGG = "loadRunMode: ";
        int ret;

        // Check for provisioning file...
        // If none exists, then use RUN_MODE_IDLE; else check Wi-Fi to determine location.
        if (!isProvFileAvailable) {
            FL.d(TAG, TAGG+"Provisioning file is determined to be unavailable. We won't know how to run.");
            ret = RUN_MODE_IDLE;
        }
        // TODO in later version: Check for MessageNet Wi-Fi availability...
        // If available, then use RUN_MODE_IDLE; else check for configuration from server
        /
        else if (isMessageNetWifiAvailable()) {
            FL.d(TAG, TAGG+"MessageNet Wi-Fi is determined to be available. We may need special options available.");
            ret = RUN_MODE_IDLE;
        }
        /
        // Check for available configuration from server...
        // If none, then use RUN_MODE_IDLE; else allow normal startup
        else if (!isServerConfigFileAvailable()) {
            FL.d(TAG, TAGG+"Server configuration file is determined to be unavailable.");
            ret = RUN_MODE_IDLE;
        }
        // If nothing caught above, we should assume normal startup
        else {
            ret = RUN_MODE_NORMAL;
        }

        String runModeForLog;
        switch (ret) {
            case RUN_MODE_IDLE:
                runModeForLog = "RUN_MODE_IDLE";
                break;
            case RUN_MODE_NORMAL:
                runModeForLog = "RUN_MODE_NORMAL";
                break;
            default:
                runModeForLog = "(WARNING: unknown.. this should be OK, just a pain for understanding logs)";
                break;
        }

        FL.i(TAG, TAGG+"Returning "+String.valueOf(ret)+" ("+runModeForLog+").");
        return ret;
    }
    */

    // Load ecosystem and return it
    // (Invoked by MainService, since ecosystem doesn't matter until we're actually running and ready for messages)
    public int loadEcosystem(SharedPrefsUtils sharedPrefsUtils) {
        final String TAGG = "loadEcosystem: ";
        int ret = ECOSYSTEM_UNKNOWN;

        // Try to get shared-prefs value(s) to determine ecosystem/platform we're using...
        try {
            String selectedPlatform = sharedPrefsUtils.getStringValueFor(sharedPrefsUtils.spKeyName_ecosystemPlatformSelection, "");
            if (selectedPlatform.isEmpty()) {
                ret = ECOSYSTEM_UNKNOWN;
            } else if (selectedPlatform.equals("PlatformMessageNetV1")) {
                ret = ECOSYSTEM_MESSAGENET_CONNECTIONS_V1;
            } else if (selectedPlatform.equals("PlatformMessageNetV2")) {
                ret = ECOSYSTEM_MESSAGENET_CONNECTIONS_V2;
            } else if (selectedPlatform.equals("PlatformStandardAPI")) {
                ret = ECOSYSTEM_STANDARD_API;
            } else {
                ret = ECOSYSTEM_UNKNOWN;
            }
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught!", e);
        }

        // Log in a human-readable way...
        String ecosystemForLog;
        switch (ret) {
            case ECOSYSTEM_UNKNOWN:
                ecosystemForLog = "ECOSYSTEM_UNKNOWN";
                break;
            case ECOSYSTEM_MESSAGENET_CONNECTIONS_V1:
                ecosystemForLog = "ECOSYSTEM_MESSAGENET_CONNECTIONS_V1";
                break;
            case ECOSYSTEM_MESSAGENET_CONNECTIONS_V2:
                ecosystemForLog = "ECOSYSTEM_MESSAGENET_CONNECTIONS_V2";
                break;
            case ECOSYSTEM_STANDARD_API:
                ecosystemForLog = "ECOSYSTEM_STANDARD_API";
                break;
            default:
                ecosystemForLog = "(WARNING: unknown.. this should be OK, just a pain for understanding logs)";
                break;
        }

        FL.i(TAG, TAGG+"Returning "+String.valueOf(ret)+" ("+ecosystemForLog+").");
        return ret;
    }

    // Load up and initialize a ProvisionData object and return it
    private ProvisionData loadProvData(Context appContext) {
        final String TAGG = "loadProvData: ";
        ProvisionData provDataObj = new ProvisionData(appContext, ProvisionData.LOG_METHOD_FILELOGGER);

        if (isProvFileAvailable) {
            try {
                if (provDataObj.init(appContext)) {
                    // Also go ahead and update shared prefs...
                    provDataObj.flushToSharedPrefs(appContext);
                }
            } catch (Exception e) {
                FL.e(TAG, TAGG + "Exception caught!", e);
            }
        } else {
            FL.w(TAG, TAGG+"Provisioning file is not available, nothing to load.");
        }

        return provDataObj;
    }

    // Check if provisioning file is available
    private boolean isProvFileAvailable() {
        final String TAGG = "isProvFileAvailable: ";
        boolean ret = false;

        try {
            FileUtils fileUtils = new FileUtils(getApplicationContext(), FileUtils.LOG_METHOD_FILELOGGER);
            ret = fileUtils.doesFileExist(FileUtils.FILE_PATH_EXTERNAL_STORAGE, getResources().getString(R.string.provfile_filename));
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught!", e);
        }

        FL.d(TAG, TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    // TODO in later version: Check if MessageNet Wi-Fi network is available
    private boolean isMessageNetWifiAvailable() {
        final String TAGG = "isMessageNetWifiAvailable: ";
        boolean ret = false;

        try {
            NetUtils netUtils = new NetUtils(getApplicationContext(), NetUtils.LOG_METHOD_FILELOGGER);
            ret = netUtils.isWifiNetworkAvailable("E4200");
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught!", e);
        }

        FL.d(TAG, TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    // Check if configuration file is available from server
    // DEV-NOTE: You may want to leave all of this stuff up to StartupActivity to do....
    /*
    private boolean isServerConfigFileAvailable() {
        final String TAGG = "isServerConfigFileAvailable: ";
        boolean ret = false;

        try {
            //TODO: create a new method in new NetUtilsTftp class
            //TODO: maybe we can also figure out how to just get the file and store it here (so we won't have to hit the server again for it)
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught!", e);
        }

        FL.d(TAG, TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    // Load configuration into shared prefs
    private void loadConfigFileToSharedPrefs() {
        final String TAGG = "loadConfigFileToSharedPrefs: ";

        //TODO: find and read config file and load into shared prefs
    }
    */

    // Check if specified Service is running
    public boolean isServiceRunning(Class<?> serviceClass) {
        final String TAGG = "isServiceRunning("+serviceClass.getSimpleName()+"): ";

        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                FL.i(TAG, TAGG+"Returning true.");
                return true;
            }
        }
        FL.i(TAG, TAGG+"Returning false.");
        return false;
    }

    // Stop the MainService
    // You may call this from anywhere with an OmniApplication instance! Convenient, huh?
    public void stopMainService() {
        final String TAGG = "stopMainService: ";

        try {
            // Stop the MainService
            setAllowAppToDie(true);
            stopService(mainServiceIntent);

            // Since the app is now doing nothing, enter idle run mode
            setRunMode(RUN_MODE_IDLE);
            startActivity(new Intent(this, StartupActivity.class));
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught!", e);
        }
    }


    /*============================================================================================*/
    /* Notification Methods */

    /** Setup and return a Notification Builder object.
     * Remember: After getting what this returns, you'll need to supply to NotificationManager to actually show it. */
    private NotificationCompat.Builder createNotificationBuilderObject(String contentText) {
        final String TAGG = "createNotificationBuilderObject: ";
        Log.v(TAG, TAGG+"Invoked.");

        NotificationCompat.Builder notifBuilder = null;
        final String notifTitle = getApplicationContext().getResources().getString(R.string.notification_title)+" v"+getAppVersion();

        try {
            notifBuilder = new NotificationCompat.Builder(getApplicationContext());
            notifBuilder.setSmallIcon(R.drawable.ic_stat_messagenet_logo_200x200_trans);
            notifBuilder.setContentTitle(notifTitle);
            notifBuilder.setContentText(contentText);
            notifBuilder.setOngoing(true);
            notifBuilder.setPriority(NotificationCompat.PRIORITY_MAX);
            notifBuilder.addAction(0, "Stop Processes", stopMainServicePendingIntent);
            notifBuilder.addAction(0, "Clear All Messages", clearMessagesPendingIntent);
            notifBuilder.setStyle(new NotificationCompat.BigTextStyle());

            // The following is just so we can save and retrieve notification text (in case we want to append later)
            Bundle bundle = new Bundle();
            bundle.putString(NotificationCompat.EXTRA_TEXT, contentText);
            notifBuilder.setExtras(bundle);
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(notifBuilder));
        return notifBuilder;
    }

    /** Update the specified notification object's text.
     * DEV-NOTE: Originally developed with idea to update existing notifcation, but never got it to work. Not using for now, but keeping around just in case. */
    private NotificationCompat.Builder updateNotificationBuilderObjectText(NotificationCompat.Builder notifBuilder, String contentText, int updateMethod) {
        final String TAGG = "updateNotificationBuilderObjectText: ";
        Log.v(TAG, TAGG+"Invoked.");

        try {
            synchronized (notifBuilder) {
                switch (updateMethod) {
                    case NOTIF_REPLACE:
                        notifBuilder.setContentText(contentText);
                        break;
                    case NOTIF_APPEND:
                        String existingText = notifBuilder.getExtras().getString(NotificationCompat.EXTRA_TEXT, "");
                        notifBuilder.setContentText(existingText+"\n"+contentText);
                }
                notifBuilder.notify();
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }

        return notifBuilder;
    }

    /** Finalize and show the provided notification. */
    public void showNotif(NotificationCompat.Builder notifBuilder) {
        final String TAGG = "showNotif: ";
        Log.v(TAG, TAGG + "Invoked.");

        try {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
            mNotification = notifBuilder.build();
            //notificationManager.notify(mNotifID, notifBuilder.build());
            notificationManager.notify(mNotifID, mNotification);
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }
    }

    /** Replace the notification with specified text. */
    public void replaceNotificationWithText(String text) {
        final String TAGG = "replaceNotificationWithText: ";
        Log.v(TAG, TAGG + "Invoked.");

        try {
            mNotifBuilder = createNotificationBuilderObject(text);
            showNotif(mNotifBuilder);
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }
    }

    /** Return the current notification text. */
    public String getCurrentNotificationText() {
        final String TAGG = "getCurrentNotificationText: ";
        Log.v(TAG, TAGG + "Invoked.");
        String ret = "";

        try {
            ret = mNotifBuilder.getExtras().getString(NotificationCompat.EXTRA_TEXT, "");
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }

        Log.v(TAG, TAGG + "Returning...\n"+ret);
        return ret;
    }

    /** Add the specified text as a new line in the existing notification. */
    public void appendNotificationWithText(String text) {
        final String TAGG = "appendNotificationWithText: ";
        Log.v(TAG, TAGG + "Invoked.");

        try {
            text = getCurrentNotificationText() +
                    "\n" +
                    text;
            replaceNotificationWithText(text);
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }
    }


    /*============================================================================================*/
    /* Getter & Setter methods */

    public String getAppPackageName() {
        final String TAGG = "getAppPackageName: ";
        final String ret = this.appPackageName;
        FL.d(TAG, TAGG+"Returning \""+String.valueOf(ret)+"\".");
        return ret;
    }
    public static String getAppPackageNameStatic() {
        return appPackageNameStatic;
    }
    public void setAppPackageName(String packageName) {
        final String TAGG = "setAppPackageName: ";
        FL.d(TAG, TAGG+"Setting \""+String.valueOf(packageName)+"\"...");
        this.appPackageName = packageName;
        appPackageNameStatic = packageName;
    }

    public String getAppVersion() {
        final String TAGG = "getAppVersion: ";
        final String ret = this.appVersion;
        FL.d(TAG, TAGG+"Returning \""+String.valueOf(ret)+"\".");
        return ret;
    }
    public void setAppVersion(String appVersion) {
        final String TAGG = "setAppVersion: ";
        FL.d(TAG, TAGG+"Setting \""+String.valueOf(appVersion)+"\"...");
        this.appVersion = appVersion;
    }

    public String getAppVersion_watchdog() {
        return appVersion_watchdog;
    }
    public void setAppVersion_watchdog(String appVersion_watchdog) {
        this.appVersion_watchdog = appVersion_watchdog;
    }

    public String getAppVersion_updater() {
        return appVersion_updater;
    }
    public void setAppVersion_updater(String appVersion_updater) {
        this.appVersion_updater = appVersion_updater;
    }

    public String getAppVersion_flashers() {
        return appVersion_flashers;
    }
    public void setAppVersion_flashers(String appVersion_flashers) {
        this.appVersion_flashers = appVersion_flashers;
    }

    public String getAppVersion_watcher() {
        return appVersion_watcher;
    }
    public void setAppVersion_watcher(String appVersion_watcher) {
        this.appVersion_watcher = appVersion_watcher;
    }

    public int getRunMode() {
        final String TAGG = "getRunMode: ";
        final int ret = this.runMode;
        FL.d(TAG, TAGG+"Returning \""+String.valueOf(ret)+"\".");
        return ret;
    }
    public void setRunMode(int runMode) {
        final String TAGG = "setRunMode: ";
        FL.d(TAG, TAGG+"Setting \""+String.valueOf(runMode)+"\"...");
        this.runMode = runMode;
    }

    public int getEcosystem() {
        final String TAGG = "getEcosystem: ";
        final int ret = this.ecosystem;
        FL.d(TAG, TAGG+"Returning \""+String.valueOf(ret)+"\".");
        return ret;
    }
    public void setEcosystem(int ecosystem) {
        // (Invoked by MainService, since ecosystem doesn't matter until we're actually running and ready for messages)
        final String TAGG = "setEcosystem: ";
        FL.d(TAG, TAGG+"Setting "+String.valueOf(ecosystem)+"...");
        this.ecosystem = ecosystem;
    }

    public Date getAppStartedDate() {
        final String TAGG = "getAppStartedDate: ";
        final Date ret = this.appStartedDate;
        FL.d(TAG, TAGG+"Returning \""+String.valueOf(ret)+"\".");
        return ret;
    }
    public long getAppRunningHours() {
        final String TAGG = "getAppRunningHours: ";
        long ret = 0;
        try {
            long appStartTime = getAppStartedDate().getTime();
            long currentTime = new Date().getTime();
            long diffMS = currentTime - appStartTime;
            long diffHrs = diffMS / (60 * 60 * 1000);
            ret = diffHrs;
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught calculating app's runtime in hours: "+e.getMessage());
        }
        FL.d(TAG, TAGG+"Returning \""+Long.toString(ret)+"\".");
        return ret;
    }
    public long getAppRunningMinutes() {
        final String TAGG = "getAppRunningMinutes: ";
        long ret = 0;
        try {
            long appStartTime = getAppStartedDate().getTime();
            long currentTime = new Date().getTime();
            long diffMS = currentTime - appStartTime;
            long diffMins = diffMS / (60 * 1000);
            ret = diffMins;
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught calculating app's runtime in hours: "+e.getMessage());
        }
        FL.d(TAG, TAGG+"Returning \""+Long.toString(ret)+"\".");
        return ret;
    }
    public void setAppStartedDate(Date date) {
        final String TAGG = "setAppStartedDate: ";
        FL.d(TAG, TAGG+"Setting "+String.valueOf(date)+"...");
        this.appStartedDate = date;
    }

    public Activity getCurrentVisibleActivity() {
        final String TAGG = "getCurrentVisibleActivity: ";
        String activityName = "";
        try {
            activityName = this.currentVisibleActivity.getClass().getSimpleName();
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught determining activity class name! This is just for logging purposes, so might be alright.", e);
        }
        FL.d(TAG, TAGG+"Returning Activity object for "+String.valueOf(activityName)+".");
        return this.currentVisibleActivity;
    }
    public String getCurrentVisibleActivityName() {
        final String TAGG = "getCurrentVisibleActivityName: ";
        String activityName = "";
        try {
            activityName = this.currentVisibleActivity.getClass().getSimpleName();
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught determining activity class name!", e);
        }
        FL.d(TAG, TAGG+"Returning "+String.valueOf(activityName)+".");
        return activityName;
    }
    public void setCurrentVisibleActivity(Activity activity) {
        final String TAGG = "setCurrentVisibleActivity: ";
        String activityName = "";
        try {
            activityName = activity.getClass().getSimpleName();
        } catch (Exception e) {
            FL.w(TAG, TAGG+"Exception caught determining activity class name! May set to null.", e);
        }
        FL.d(TAG, TAGG+"Setting Activity object for \""+String.valueOf(activityName)+"\"...");
        this.currentVisibleActivity = activity;
    }

    public ProvisionData getProvisionData() {
        final String TAGG = "getProvisionData: ";
        return this.provisionData;
    }

    public boolean getAllowAppToDie() {
        final String TAGG = "getAllowAppToDie: ";
        final boolean ret = this.allowAppToDie;
        FL.d(TAG, TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }
    public void setAllowAppToDie(boolean allowAppToDie) {
        final String TAGG = "setAllowAppToDie: ";
        FL.d(TAG, TAGG+"Setting "+String.valueOf(allowAppToDie)+"...");
        this.allowAppToDie = allowAppToDie;
    }

    public Intent getMainServiceIntent() {
        return mainServiceIntent;
    }
    public void setMainServiceIntent(Intent mainServiceIntent) {
        this.mainServiceIntent = mainServiceIntent;
    }

    public MainService getMainService() {
        return mainService;
    }
    public void setMainService(MainService mainService) {
        this.mainService = mainService;
    }

    public synchronized boolean getIsClockShowing() {
        return this.isClockShowing;
    }
    public synchronized void setIsClockShowing(boolean value) {
        this.isClockShowing = value;
    }

    public boolean getIsTextToSpeechAvailable() {
        return this.isTextToSpeechAvailable;
    }
    public void setIsTextToSpeechAvailable(boolean value) {
        this.isTextToSpeechAvailable = value;
    }

    public boolean getIsTextToSpeechOccurring() {
        return this.isTextToSpeechOccurring;
    }
    public void setIsTextToSpeechOccurring(boolean value) {
        this.isTextToSpeechOccurring = value;
    }

    public String getDevicePassword() {
        final String TAGG = "getDevicePassword: ";
        FL.d(TAG, TAGG+"Returning \""+devicePassword+"\".");
        return devicePassword;
    }

    public void setDevicePassword(String devicePassword) {
        this.devicePassword = devicePassword;
    }

    public int getmNotifID() {
        return mNotifID;
    }

    public void setmNotifID(int mNotifID) {
        this.mNotifID = mNotifID;
    }

    public Notification getmNotification() {
        return mNotification;
    }

    public void setmNotification(Notification mNotification) {
        this.mNotification = mNotification;
    }
}
