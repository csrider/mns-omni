package com.messagenetsystems.evolution2.activities;

/* ClockActivity
 *
 * Revisions:
 *  2019.11.15      Chris Rider     Created (migrated from v1).
 *  2020.01.30      Chris Rider     Fixed date not loading.
 *  2020.02.20      Chris Rider     Fixed potential null-ref bug if OmniApplication is unavailable.
 *  2020.05.11      Chris Rider     Checking whether there are deliveries happening, before registering receivers (should further help with stability and smoothness).
 *  2020.05.12      Chris Rider     Adding device status info to top of screen, and accompanying BroadcastReceiver to update it when we get stuff from the new OmniStatusBarThread thread.
 *  2020.05.13      Chris Rider     Added additional status items to top of screen and made it look better.
 *  2020.05.18      Chris Rider     Moved version number from bottom of screen up to status bar area.
 */

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.OmniApplication;
import com.messagenetsystems.evolution2.R;
import com.messagenetsystems.evolution2.services.MainService;
import com.messagenetsystems.evolution2.utilities.SystemUtils;
import com.messagenetsystems.evolution2.utilities.SharedPrefsUtils;

import org.w3c.dom.Text;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class ClockActivity extends AppCompatActivity {
    private final String TAG = this.getClass().getSimpleName();

    // Constants...


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
    private OmniApplication omniApplication;
    private SystemUtils systemUtils;
    private SharedPrefsUtils sharedPrefsUtils;

    private BroadcastReceiver handleOsaReceiver;
    private BroadcastReceiver dateChangedReceiver;
    private BroadcastReceiver omniStatusReceiver;

    private IntentFilter handleOsaIntentFilter;
    private IntentFilter dateChangeIntentFilter;
    private IntentFilter omniStatusIntentFilter;

    private TextClock textClockView;
    private TextView osaTextView;
    private TextView deviceIdTextView;
    private TextView debugInfoTextView;
    private TextView serialNoView;
    private TextView dateTextView;
    private TextView omniStatusAppVersion;
    private LinearLayout omniStatusBar;
    private TextView omniStatusBattery;
    private TextView omniStatusNetwork;
    private TextView omniStatusPower;
    private TextView omniStatusUptimeApp;
    private TextView omniStatusUptimeDevice;

    public static volatile Date activityLastBecameVisible;
    public Date osaLastShownDate;
    private long osaShowForMinMS;


    /*============================================================================================*/
    /* Activity Methods */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String TAGG = "onCreate: ";
        logV(TAGG+"Invoked.");

        // Initialize some local stuff that's needed right away or before other stuff below
        try {
            this.omniApplication = ((OmniApplication) getApplicationContext().getApplicationContext());
        } catch (Exception e) {
            Log.e(TAG, "Exception caught instantiating "+TAG+": "+e.getMessage());
            finish();
            return;
        }
        systemUtils = new SystemUtils(getApplicationContext(), SystemUtils.LOG_METHOD_FILELOGGER);

        // Configure screen elements, and then render everything as configured
        systemUtils.configScreen_hideActionBar(this);
        systemUtils.configScreen_keepScreenOn(this);
        setContentView(R.layout.activity_clock_activity);

        // Initialize more local stuff
        sharedPrefsUtils = new SharedPrefsUtils(getApplicationContext(), SharedPrefsUtils.LOG_METHOD_FILELOGGER);

        textClockView = (TextClock) findViewById(R.id.textClock);
        osaTextView = (TextView) findViewById(R.id.osaTextView);
        deviceIdTextView = (TextView) findViewById(R.id.deviceIdTextView);
        debugInfoTextView = (TextView) findViewById(R.id.debugInfoTextView);
        serialNoView = (TextView) findViewById(R.id.serialNoTextView);
        dateTextView = (TextView) findViewById(R.id.dateTextView);
        omniStatusAppVersion = (TextView) findViewById(R.id.textView_omniStatus_appVersion);
        omniStatusBar = (LinearLayout) findViewById(R.id.linearLayout_omniStatus);
        omniStatusBattery = (TextView) findViewById(R.id.textView_omniStatus_battery);
        omniStatusNetwork = (TextView) findViewById(R.id.textView_omniStatus_network);
        omniStatusPower = (TextView) findViewById(R.id.textView_omniStatus_power);
        omniStatusUptimeApp = (TextView) findViewById(R.id.textView_omniStatus_uptimeApp);
        omniStatusUptimeDevice = (TextView) findViewById(R.id.textView_omniStatus_uptimeDevice);

        handleOsaIntentFilter = new IntentFilter("HANDLE_OSA");
        dateChangeIntentFilter = new IntentFilter();
        dateChangeIntentFilter.addAction(Intent.ACTION_DATE_CHANGED);
        dateChangeIntentFilter.addAction(Intent.ACTION_TIME_CHANGED);
        omniStatusIntentFilter = new IntentFilter(OMNI_STATUS_RECEIVER_NAME);

        handleOsaReceiver = new HandleOsaReceiver();
        dateChangedReceiver = new DateChangedReceiver();
        omniStatusReceiver = new OmniStatusReceiver();
    }

    @Override
    public void onStart() {
        super.onStart();
        final String TAGG = "onStart: ";
        logV(TAGG+"Invoked.");
        // Do the stuff here
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        final String TAGG = "onPostCreate: ";
        logV(TAGG+"Invoked.");

        // Load screen elements (these need to be done here --so after the screen completely renders)
        systemUtils.configScreen_hideNavigationBar(findViewById(R.id.fullscreen_clock));
        loadDeviceInformationOnScreen();
    }

    @Override
    public void onResume() {
        super.onResume();
        final String TAGG = "onResume: ";
        logV(TAGG+"Invoked.");

        systemUtils.configScreen_hideNavigationBar(findViewById(R.id.fullscreen_clock));

        // Update our global flag
        omniApplication.setIsClockShowing(true);

        // Update our timestamp for when the activity became visible.
        // (this may be used by cycling routines to ensure clock stays visible at least some amount of time between messages)
        activityLastBecameVisible = new Date();

        // Initialize the OSA last shown date (also so no null errors can happen anywhere)
        osaLastShownDate = activityLastBecameVisible;

        //TODO: Update debugging info (if it's there)
        //loadDebugInformationOnScreen(getApplicationContext());

        updateDateOnScreen(getDateString());

        // Register listeners
        if (MainService.omniMessages_deliverable.size() > 0) {
            // If there are messages being delivered, then we skip registering the receivers, as
            // this activity will only be visible for a few hundred milliseconds, and frequent
            // registering may not be healthy for the stability of the Omni.
            logI(TAGG+"Not registering receivers, due to deliverable messages existing.");

            // And, just to be safe and avoid any race conditions (where the deliverables-size
            // might still be >0 just because a message JUST finished and the size didn't update yet,
            // let's run another check on a delay, to ensure receivers get registered if they should).
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (omniApplication.getIsClockShowing()) {
                            //we only care to run this if the clock is indeed showing
                            if (MainService.omniMessages_deliverable.size() > 0) {
                                logW(TAGG+"Not registering receivers after delay, due to deliverable messages still existing.");
                            } else {
                                logV(TAGG+"Registering receivers, after short delay has elapsed...");
                                registerReceiver(handleOsaReceiver, new IntentFilter("HANDLE_OSA"));
                                registerReceiver(dateChangedReceiver, dateChangeIntentFilter);
                                registerReceiver(omniStatusReceiver, omniStatusIntentFilter);
                            }
                        }
                    } catch (Exception e) {
                        logE(TAGG+"Exception caught trying to check deliverables and register receivers on a delay.");
                    }
                }
            }, 3*1000);
        } else {
            logV(TAGG+"Registering receivers...");
            registerReceiver(handleOsaReceiver, new IntentFilter("HANDLE_OSA"));
            registerReceiver(dateChangedReceiver, dateChangeIntentFilter);
            registerReceiver(omniStatusReceiver, omniStatusIntentFilter);
        }
    }

    @Override
    public void onPause() {
        final String TAGG = "onPause: ";
        logV(TAGG+"Invoked.");

        // Set our global flag
        omniApplication.setIsClockShowing(false);

        // Unregister listeners
        try {
            //Dev-Note: There is no proper way to determine if receivers are registered or not,
            //so we just rely on the try/catch block (which is a standard and acceptable practice).
            unregisterReceiver(handleOsaReceiver);
            unregisterReceiver(dateChangedReceiver);
            unregisterReceiver(omniStatusReceiver);
        } catch (Exception e) {
            logI(TAGG+"Exception caught unregistering receiver(s) - this should be OK most cases: "+e.getMessage());
        }

        // Clear out status bar so it doesn't maintain old data for long message cycling
        omniStatusBattery.setText("");
        omniStatusPower.setText("");
        omniStatusNetwork.setText("");
        omniStatusUptimeApp.setText("");
        omniStatusBar.setBackgroundColor(Constants.Colors.WHITE);

        super.onPause();
    }

    @Override
    protected void onStop() {
        final String TAGG = "onStop: ";
        logV(TAGG+"Invoked.");
        // Do the stuff here
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        final String TAGG = "onDestroy: ";
        logV(TAGG+"Invoked.");
        // Do the stuff here
        // NOTE: You should probably never assume that this will always be invoked!
        super.onDestroy();
    }


    /*============================================================================================*/
    /* Supporting Methods */

    /** Load device info on screen.
     * This is stuff that doesn't change while app is running, so it's suitable for onPostCreate.
     */
    private void loadDeviceInformationOnScreen() {
        final String TAGG = "loadDeviceInformationOnScreen: ";
        Log.v(TAG, TAGG+"Invoked.");

        try {
            String versionText = "Omni Version " + omniApplication.getAppVersion();
            omniStatusAppVersion.setText(versionText);
            deviceIdTextView.setText(sharedPrefsUtils.getStringValueFor(sharedPrefsUtils.spKeyName_thisDeviceID, ""));
            serialNoView.setText(sharedPrefsUtils.getStringValueFor(sharedPrefsUtils.spKeyName_serialNumber, ""));
        } catch (Exception e) {
            logW(TAGG+"Exception caught: "+e.getMessage());
        }
    }

    /** Load debugging info on screen.
     * This is stuff that likely changes a lot, so it's better suited for onResume or something frequent.
     */
    /*
    private void loadDebugInformationOnScreen(Context context) {
        final String TAGG = "loadDebugInformationOnScreen: ";
        Log.v(TAG, TAGG+"Invoked.");

        try {
            if (MessageNetUtils.isDebuggingRuntimeFlagSet()) {
                debugInfoTextView.setText(SettingsUtils.getDebugInfoText(context, SettingsUtils.DEBUG_INFO_DELINEATE_NEWLINE));
                debugInfoTextView.append("\nClockActivity started: "+this.startTime);
                debugInfoTextView.setVisibility(View.VISIBLE);
            } else {
                debugInfoTextView.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.w(TAG, TAGG+"Exception caught: "+e.getMessage());
        }
    }
    */

    private String getDateString() {
        final String TAGG = "getDateString: ";

        final String simpleDateFormatTemplate = "EEE, MMMM d, yyyy";

        @SuppressLint("SimpleDateFormat")
        final String ret = new SimpleDateFormat(simpleDateFormatTemplate).format(Calendar.getInstance().getTime());

        logV(TAGG+"Returning \""+String.valueOf(ret)+"\".");
        return ret;
    }

    private void updateDateOnScreen(String dateToDisplayOnScreen) {
        final String TAGG = "updateDateOnScreen: ";

        try {
            dateTextView.setText(dateToDisplayOnScreen);
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }

    private void updateStatusOnScreen_battery(String text, int textColor) {
        final String TAGG = "updateStatusOnScreen_battery: ";

        try {
            logV(TAGG+"Setting battery status to: \""+text+"\"...");
            omniStatusBattery.setTextColor(textColor);
            omniStatusBattery.setText(text);
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }

    private void updateStatusOnScreen_power(String text, int textColor) {
        final String TAGG = "updateStatusOnScreen_power: ";

        try {
            logV(TAGG+"Setting power status to: \""+text+"\"...");
            omniStatusPower.setTextColor(textColor);
            omniStatusPower.setText(text);
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }

    private void updateStatusOnScreen_network(String text, int textColor) {
        final String TAGG = "updateStatusOnScreen_network: ";

        try {
            logV(TAGG+"Setting network status to: \""+text+"\"...");
            omniStatusNetwork.setTextColor(textColor);
            omniStatusNetwork.setText(text);
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }

    private void updateStatusOnScreen_uptimeApp(String text, int textColor) {
        final String TAGG = "updateStatusOnScreen_uptimeApp: ";

        try {
            logV(TAGG+"Setting app uptime status to: \""+text+"\"...");
            omniStatusUptimeApp.setTextColor(textColor);
            omniStatusUptimeApp.setText(text);
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }

    private void updateStatusOnScreen_uptimeDevice(String text, int textColor) {
        final String TAGG = "updateStatusOnScreen_uptimeDevice: ";

        try {
            logV(TAGG+"Setting device uptime status to: \""+text+"\"...");
            omniStatusUptimeDevice.setTextColor(textColor);
            omniStatusUptimeDevice.setText(text);
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }


    /*============================================================================================*/
    /* Subclasses */

    /** Take care of showing or hiding an on-screen alert.
     * You may broadcast an intent from other services, threads, activities, etc. as follows:
     *  Intent i = new Intent("HANDLE_OSA");
     *  i.putExtra("showOrHideOSA", "show");
     *  i.putExtra("textForOSA", "My Text");
     *  sendBroadcast(i);
     **/
    public class HandleOsaReceiver extends BroadcastReceiver {
        private final String TAGG = this.getClass().getSimpleName();

        public HandleOsaReceiver() {
            logV(TAGG+"Instantiating.");
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String TAGGG = "onReceive: ";
            logV(TAGG+TAGGG+"Broadcast received.");

            try {
                if (intent.getExtras() != null) {
                    String showOrHide = intent.getExtras().getString("showOrHideOSA");
                    if (showOrHide != null && showOrHide.equals("show")) {
                        doShowOSA(intent.getExtras().getString("textForOSA"));
                        osaShowForMinMS = intent.getExtras().getInt("showForMinMS");
                        osaLastShownDate = new Date();
                    } else if (showOrHide != null && showOrHide.equals("hide")) {
                        long osaShowDate_ms = osaLastShownDate.getTime();
                        long currentDate_ms = new Date().getTime();
                        long diff_ms = currentDate_ms - osaShowDate_ms;
                        logV(TAGG+TAGGG+"osaShowDate_ms = " + osaShowDate_ms + ", currentDate_ms = " + currentDate_ms + ", diff_ms = " + diff_ms);
                        if (diff_ms >= osaShowForMinMS) {
                            //minimum time visible has elapsed, so go ahead and hide it
                            logD(TAGG+TAGGG+"Minimum visible time has elapsed, hiding...");
                            doHideOSA();
                        } else if (intent.getExtras().getBoolean("forceHide")) {
                            //force hide
                            logD(TAGG+TAGGG+"Force hiding...");
                            doHideOSA();
                        } else {
                            //don't hide yet
                            logI(TAGG+TAGGG+"Aborting hide of OSA (min time has not elapsed or force flag not set).");
                        }
                    } else {
                        logW(TAGG+TAGGG+"Unhandled value for showOrHideOSA, or intent-extra(s) do not exist.");
                    }
                } else {
                    logW(TAGG+TAGGG+"No extras provided in intent. Nothing we can do.");
                }
            } catch (Exception e) {
                logE(TAGG+TAGGG+"Exception caught trying to handle an on-screen alert broadcast: "+e.getMessage());
            }
        }

        private void doShowOSA(String text) {
            final String TAGGG = "doShowOSA: ";

            try {
                //TODO: accommodate extras (min show time, etc.)
                logV(TAGG+TAGGG+"Setting and showing textview with \""+text+"\".");
                osaTextView.setText(text);
                osaTextView.setVisibility(TextView.VISIBLE);
            } catch (Exception e) {
                logE(TAGG+TAGGG+"Exception caught trying to show an on-screen alert: "+e.getMessage());
            }
        }

        private void doHideOSA() {
            final String TAGGG = "doHideOSA: ";

            try {
                //TODO: accommodate extras (force hide, etc.)
                logV(TAGG+TAGGG+"Hiding textview.");
                osaTextView.setVisibility(TextView.GONE);
            } catch (Exception e) {
                logE(TAGG+TAGGG+"Exception caught trying to hide an on-screen alert: "+e.getMessage());
            }
        }
    }

    /** Take care of updating the screen when the date or time changes.
     * This is needed particularly to update date value (as time-view automatically does it for us) */
    public class DateChangedReceiver extends BroadcastReceiver {
        private final String TAGG = this.getClass().getSimpleName();

        public DateChangedReceiver() {
            logV(TAGG+"Instantiating.");
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String TAGGG = "onReceive: ";
            logV(TAGG+TAGGG+"Broadcast received.");

            try {
                final String action = intent.getAction();

                if (action == null) {
                    logW(TAGG+TAGGG+"intent.getAction() is null. Aborting.");
                    return;
                }

                if (action.equals(Intent.ACTION_DATE_CHANGED)
                        || action.equals(Intent.ACTION_TIME_CHANGED)) {
                    logV(TAGG+TAGGG+"Action is \"" + String.valueOf(action) + "\".");

                    try {
                        updateDateOnScreen(getDateString());
                    } catch (Exception e) {
                        Log.e(TAG, TAGG + "Exception caught updating screen: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                logE(TAGG+TAGGG+"Exception caught: "+e.getMessage());
            }
        }
    }

    /** Take care of updating our custom status bar at the top of the clock.
     * This handles system actions (Intent.ACTION_*), as well as custom actions you may send (intent extras).
     * You should always send some action to this (setAction), and any extras, as necessary for your custom action.
     * NOTE: Do not instantiate this until you're fairly sure the clock will show for some time (not between message cycles/rotations). */
    public static final String OMNI_STATUS_RECEIVER_NAME = "com.messagenetsystems.evolution.OmniStatusReceiver";
    public static final String OMNI_STATUS_RECEIVER_ACTION = OMNI_STATUS_RECEIVER_NAME+".action";
    public static final String OMNI_STATUS_RECEIVER_ACTION_UPDATE_BATTERY = OMNI_STATUS_RECEIVER_NAME+".action.updateBattery";
    public static final String OMNI_STATUS_RECEIVER_ACTION_UPDATE_NETWORK = OMNI_STATUS_RECEIVER_NAME+".action.updateNetwork";
    public static final String OMNI_STATUS_RECEIVER_ACTION_UPDATE_POWER = OMNI_STATUS_RECEIVER_NAME+".action.updatePower";
    public static final String OMNI_STATUS_RECEIVER_ACTION_UPDATE_UPTIME_APP = OMNI_STATUS_RECEIVER_NAME+".action.uptimeApp";
    public static final String OMNI_STATUS_RECEIVER_ACTION_UPDATE_UPTIME_DEVICE = OMNI_STATUS_RECEIVER_NAME+".action.uptimeDevice";
    public static final String OMNI_STATUS_RECEIVER_KEYNAME_TEXT_COLOR = OMNI_STATUS_RECEIVER_NAME+".keyName.textColor";
    public static final String OMNI_STATUS_RECEIVER_KEYNAME_BATTERY_PERCENTAGE = OMNI_STATUS_RECEIVER_NAME+".keyName.batteryPercentage";
    public static final String OMNI_STATUS_RECEIVER_KEYNAME_NETWORK_STATUS = OMNI_STATUS_RECEIVER_NAME+".keyName.networkStatus";
    public static final String OMNI_STATUS_RECEIVER_KEYNAME_POWER_STATUS = OMNI_STATUS_RECEIVER_NAME+".keyName.powerStatus";
    public static final String OMNI_STATUS_RECEIVER_KEYNAME_UPTIME_APP = OMNI_STATUS_RECEIVER_NAME+".keyName.uptimeApp";
    public static final String OMNI_STATUS_RECEIVER_KEYNAME_UPTIME_DEVICE = OMNI_STATUS_RECEIVER_NAME+".keyName.uptimeDevice";
    public class OmniStatusReceiver extends BroadcastReceiver {
        private final String TAGG = OmniStatusReceiver.class.getSimpleName();

        public OmniStatusReceiver() {
            logV(TAGG+"Instantiating.");
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String TAGGG = "onReceive: ";
            logV(TAGG+TAGGG+"Broadcast received.");

            try {
                // Get info from our intent...
                final Bundle extras = intent.getExtras();

                // Figure out what our desired action to do is...
                String action = null;
                if (extras == null) {
                    logW(TAGG+TAGGG+"intent.getExtras() is null. Aborting.");
                    return;
                } else {
                    action = intent.getExtras().getString(OMNI_STATUS_RECEIVER_ACTION);
                    logV(TAGG+TAGGG+"intent.getExtras() action says: \""+action+"\"");
                }

                // Ensure our status bar is ready...
                omniStatusBar.setBackgroundColor(Constants.Colors.BLACK);

                // Actually update status stuff depending on our desired action...
                if (action.equals(OMNI_STATUS_RECEIVER_ACTION_UPDATE_BATTERY)) {
                    updateStatusOnScreen_battery(intent.getStringExtra(OMNI_STATUS_RECEIVER_KEYNAME_BATTERY_PERCENTAGE), intent.getIntExtra(OMNI_STATUS_RECEIVER_KEYNAME_TEXT_COLOR, Constants.Colors.GRAY));
                    return;
                }
                if (action.equals(OMNI_STATUS_RECEIVER_ACTION_UPDATE_NETWORK)) {
                    updateStatusOnScreen_network(intent.getStringExtra(OMNI_STATUS_RECEIVER_KEYNAME_NETWORK_STATUS), intent.getIntExtra(OMNI_STATUS_RECEIVER_KEYNAME_TEXT_COLOR, Constants.Colors.GRAY));
                    return;
                }
                if (action.equals(OMNI_STATUS_RECEIVER_ACTION_UPDATE_POWER)) {
                    updateStatusOnScreen_power(intent.getStringExtra(OMNI_STATUS_RECEIVER_KEYNAME_POWER_STATUS), intent.getIntExtra(OMNI_STATUS_RECEIVER_KEYNAME_TEXT_COLOR, Constants.Colors.GRAY));
                    return;
                }
                if (action.equals(OMNI_STATUS_RECEIVER_ACTION_UPDATE_UPTIME_APP)) {
                    updateStatusOnScreen_uptimeApp(intent.getStringExtra(OMNI_STATUS_RECEIVER_KEYNAME_UPTIME_APP), intent.getIntExtra(OMNI_STATUS_RECEIVER_KEYNAME_TEXT_COLOR, Constants.Colors.GRAY));
                    return;
                }
                if (action.equals(OMNI_STATUS_RECEIVER_ACTION_UPDATE_UPTIME_DEVICE)) {
                    updateStatusOnScreen_uptimeDevice(intent.getStringExtra(OMNI_STATUS_RECEIVER_KEYNAME_UPTIME_DEVICE), intent.getIntExtra(OMNI_STATUS_RECEIVER_KEYNAME_TEXT_COLOR, Constants.Colors.GRAY));
                    return;
                }
            } catch (Exception e) {
                logE(TAGG+TAGGG+"Exception caught: "+e.getMessage());
            }
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
}
