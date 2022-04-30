package com.messagenetsystems.evolution2.activities;

/* DeliverScrollingMsgActivity
 * Provides scrolling-text message delivery.
 *
 * Requires standardized OmniMessage object (no matter if source is MNS Banner, API, etc.).
 * That OmniMessage object should be available via static DeliveryService resource, referenced by static UUID there.
 * For now, we're avoiding serializable/parcelable tactics for efficiency and code maintainability.
 *
 * Message-reference Notes:
 *  OmniMessage resource:           DeliveryService.omniMessagesForDelivery
 *  OmniMessage UUID to deliver:    DeliveryService.omniMessageUuidDelivery_loading
 *  How to get OmniMessage:         OmniMessage omniMessageToDeliver = DeliveryService.omniMessagesForDelivery.getOmniMessage(DeliveryService.omniMessageUuidDelivery_loading);
 *
 * NOTE: Defaults should already be included in the OmniMessage object, so no need to init them here?
 *
 * How it works:
 *  1) Activity is started when the message controller needs it to deliver a text message.
 *      (message data is passed in to this via the intent that starts the activity)
 *  2) Class initializes directly with OmniMessage data from the intent upon onCreate invocation.
 *  3) Once loaded, delivery can begin (animate, scroll, TTS, lights flash, etc.)
 *
 * Revisions:
 *  2019.12.03      Chris Rider     Created (created new file from ClockActivity and migrated stuff from v1 scrolling msg activity).
 *  2019.12.10      Chris Rider     Further work to prepare for actually invoking this.
 *  2019.12.16      Chris Rider     Added broadcast receiver subclass (and constants) to help listen for and handle outside commands (like finish activity).
 *  2020.01.13-14   Chris Rider     Implementing actual invocation from DeliveryService for first time.
 *  2020.01.16      Chris Rider     Added check for null OmniMessage; using local COPY of OmniMessage; added delivering-flags nullification to onDestroy.
 *  2020.01.22      Chris Rider     Patched in update of MainService-deliverables list, in addition to DeliveryService list.
 *  2020.01.23      Chris Rider     Changed acquisition of message data from DeliveryService OmniMessages to that in MainService instead.
 *  2020.01.28      Chris Rider     Added ability to obey intent extra of whether to write last-delivered UUID or not.
 *  2020.02.20      Chris Rider     Fixed potential null-ref bug if OmniApplication is unavailable.
 *  2020.04.24-27   Chris Rider     Implemented text-to-speech capability.
 *  2020.04.29      Chris Rider     Made initial delivery of twice-in-a-row scrolling only speak TTS once.
 *  2020.04.30      Chris Rider     Changed TTS from manually creating intents and broadcasting, to utilizing the easier TextToSpeechServicer public-static methods.
 *                                  Activity will now wait for any ongoing TTS to conclude, before allowing finish().
 *  2020.05.07-08   Chris Rider     Setting new first-delivered Date OmniMessage member when animation starts.
 *  2020.05.11      Chris Rider     Now executing more code in new threads to lessen potential impact on UI thread and make sure animation starts out very smooth.
 *  2020.06.21      Chris Rider     Now executing flasher light reset to standby (if needed), just before the end of animation, to give them time to react by time clock shows.
 *  2020.06.27      Chris Rider     Calculating approximate light duration and saving back to main message, so we can support light duration.
 *  2020.07.01      Chris Rider     Removing pre-scroll-end standby light activation, to reduce congestion in BLE stack, but still keeping light duration accrual and saving.
 *  2020.07.29      Chris Rider     No longer need pre-animation end light activation, since newly integrated flasher light driver is immediately responsive; so just ending light mode onAnimationEnd now!
 *  2020.08.05      Chris Rider     Moved onAnimationStart logic to doStartThread so we can have priority control.
 *  2020.08.07      Chris Rider     Fixed potential ANR while waiting for TTS to end onAnimationEnd, by moving loop/sleep into new thread.
 *  2020.08.10      Chris Rider     Now setting this activity's thread priority to highest possible priority. It slightly helps but doesn't solve all jerkiness in scrolling.
 *  2020.08.11      Chris Rider     Disabled MsgTypeEffect; delaying start of animation, to give stuff time to settle so it will look more smooth right at the start; forcing highest possible priority for Android VM as well.
 *  2020.09.29      Chris Rider     Migrated/implemented legacy MNS msg-type based coloring.
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.OmniApplication;
import com.messagenetsystems.evolution2.R;
import com.messagenetsystems.evolution2.models.BannerMessage;
import com.messagenetsystems.evolution2.models.FlasherLights;
import com.messagenetsystems.evolution2.models.OmniMessage;
import com.messagenetsystems.evolution2.models.OmniMessages;
import com.messagenetsystems.evolution2.services.DeliveryService;
import com.messagenetsystems.evolution2.services.FlasherLightService;
import com.messagenetsystems.evolution2.services.MainService;
import com.messagenetsystems.evolution2.services.TextToSpeechServicer;
import com.messagenetsystems.evolution2.utilities.SystemUtils;
import com.messagenetsystems.evolution2.utilities.SharedPrefsUtils;
import com.messagenetsystems.evolution2.utilities.ThreadUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.util.Date;


public class DeliverScrollingMsgActivity extends AppCompatActivity {
    private final String TAG = this.getClass().getSimpleName();

    // Constants...
    private static final String PKG_NAMESPACE = "com.messagenetsystems.evolution2.activities";
    private static final String ACTIVITY_NAME = DeliverScrollingMsgActivity.class.getSimpleName();
    public static final String INTENT_ACTION_CMD_FINISH = PKG_NAMESPACE + "." + ACTIVITY_NAME + ".doFinish";
    public static final String INTENT_EXTRA_KEYNAME_OMNIMESSAGE = PKG_NAMESPACE + ".bundleKeyName.OmniMessage";

    // Logging stuff...
    private final int LOG_SEVERITY_V = 1;
    private final int LOG_SEVERITY_D = 2;
    private final int LOG_SEVERITY_I = 3;
    private final int LOG_SEVERITY_W = 4;
    private final int LOG_SEVERITY_E = 5;
    private int logMethod = Constants.LOG_METHOD_LOGCAT;

    // Local stuff...
    private OmniApplication omniApplication;
    private SystemUtils systemUtils;
    private SharedPrefsUtils sharedPrefsUtils;

    private CommandBroadcastReceiver commandBroadcastReceiver;
    private IntentFilter commandBroadcastReceiverIntentFilterFinish;

    private View mContentView;
    private ConstraintLayout fullscreen_content;
    private LinearLayout layout_msgHeader_container;
    private TextView textView_msgHeader_left;
    private TextView textView_msgHeading;
    private TextView textView_msgHeader_right;
    private TextView textView_message;
    private TextView textView_details;
    private ScrollView scrollView_forMessage;

    private Animation animationMsgType;
    private DecelerateInterpolator decelerateInterpolator;
    private TranslateAnimation translateAnimation;
    private LinearInterpolator linearInterpolator;
    private AnimationEventListener animationEventListener;
    public static volatile Date activityLastBecameVisible;

    private OmniMessage omniMessageToDeliver;
    private boolean doTextToSpeech;


    /*============================================================================================*/
    /* Activity Methods */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String TAGG = "onCreate: ";
        logV(TAGG + "Invoked.");

        // Initialize some local stuff that's needed right away or before other stuff below
        try {
            this.omniApplication = ((OmniApplication) getApplicationContext());
        } catch (Exception e) {
            Log.e(TAG, "Exception caught instantiating " + TAG + ": " + e.getMessage());
            finish();
            return;
        }
        this.systemUtils = new SystemUtils(getApplicationContext(), logMethod);
        this.sharedPrefsUtils = new SharedPrefsUtils(getApplicationContext(), logMethod);

        // Initialize broadcast receivers and associated intent filters
        this.commandBroadcastReceiver = new CommandBroadcastReceiver();
        this.commandBroadcastReceiverIntentFilterFinish = new IntentFilter(INTENT_ACTION_CMD_FINISH);

        // Configure screen elements, and then render everything as configured
        this.systemUtils.configScreen_hideActionBar(this);
        this.systemUtils.configScreen_keepScreenOn(this);
        setContentView(R.layout.activity_deliver_scrolling_msg);

        // Initialize more local stuff
        this.mContentView = findViewById(R.id.fullscreen_deliver_scrolling);
        this.fullscreen_content = (ConstraintLayout) mContentView;
        this.layout_msgHeader_container = (LinearLayout) findViewById(R.id.layout_msgHeader_container);
        this.textView_msgHeading = (TextView) findViewById(R.id.textView_msgHeading);
        this.textView_msgHeader_left = (TextView) findViewById(R.id.textView_msgHeader_left);
        this.textView_msgHeader_right = (TextView) findViewById(R.id.textView_msgHeader_right);
        this.scrollView_forMessage = (ScrollView) findViewById(R.id.scrollView_forMessage);
        this.textView_message = (TextView) findViewById(R.id.textView_message);
        this.textView_details = (TextView) findViewById(R.id.textView_details);
        this.animationMsgType = new AlphaAnimation(0.3f, 1.0f);
        this.decelerateInterpolator = new DecelerateInterpolator();
        this.linearInterpolator = new LinearInterpolator();
        this.animationEventListener = new AnimationEventListener();

        // Initialize message stuff
        try {
            this.omniMessageToDeliver = MainService.omniMessages_deliverable.getOmniMessage(DeliveryService.omniMessageUuidDelivery_loading, OmniMessages.GET_OMNIMESSAGE_AS_COPY);
        } catch (Exception e) {
            logE(TAGG + "Exception caught retrieving OmniMessage from DeliveryService: " + e.getMessage());
            //TODO: some kind of error message to scroll? or just close the activity?
            finish();
            return;
        }
        if (this.omniMessageToDeliver == null) {
            // This sometimes happens when multiple messages are deliverable...
            // Not yet sure why (1/16/2020), but this is probably a good check to have anyway.
            logW(TAGG + "OmniMessage is null! Aborting.");
            finish();
            return;
        }

        // Initialize any potential text-to-speech, so it has time to load up and be ready to start on demand (e.g. as scrolling begins)
        doTextToSpeech = determineTextToSpeech();
        if (doTextToSpeech && omniApplication.getIsTextToSpeechAvailable()) {
            TextToSpeechServicer.prepareMessageTTS(getApplicationContext(), this.omniMessageToDeliver.getMessageUUID());
        }

        // Set screen elements with message data
        try {
            logV(TAGG + "Preparing to deliver " + this.omniMessageToDeliver.getMessageUUID().toString() + " (\"" + this.omniMessageToDeliver.getMsgText() + "\").");

            setColors_legacy(this.omniMessageToDeliver.getMsgHeading());

            this.textView_msgHeading.setText(this.omniMessageToDeliver.getMsgHeading());
            this.textView_message.setText(this.omniMessageToDeliver.getMsgText());
            if (this.omniMessageToDeliver.getMsgDetails() == null || this.omniMessageToDeliver.getMsgDetails().isEmpty()) {
                this.textView_details.setText(this.omniMessageToDeliver.getMsgText());
            } else {
                this.textView_details.setText(this.omniMessageToDeliver.getMsgDetails());
            }

            this.textView_msgHeader_left.setText("");
            this.textView_msgHeader_right.setText("");
        } catch (Exception e) {
            logE(TAGG + "Exception caught (accessing OmniMessage from DeliveryService?): " + e.getMessage());
            //TODO: some kind of error message to scroll? or just close the activity?
            finish();
            return;
        }

        try {
            //logE(TAGG+"Thread priority before: "+android.os.Process.getThreadPriority(Process.myTid()));
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            Process.setThreadPriority(-20);  //this is the highest possible priority that can be set
            //logE(TAGG+"Thread priority after: "+android.os.Process.getThreadPriority(Process.myTid()));
        } catch (Throwable throwable) {
            logW(TAGG+"Exception caught adjusting UI thread priority: "+throwable.getMessage());
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        final String TAGG = "onStart: ";
        logV(TAGG + "Invoked.");
        // Do the stuff here
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        final String TAGG = "onPostCreate: ";
        logV(TAGG + "Invoked.");

        // DEV-NOTE:
        // It is advised not to use this for most things (except for framework stuff),
        // and to use onResume, instead.

        // Load screen elements (these need to be done here --so after the screen completely renders)
        systemUtils.configScreen_hideNavigationBar(findViewById(R.id.fullscreen_deliver_scrolling));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        //updates the activity before onResume

        //Bundle extras = intent.getExtras();
        //threadScrollingMessageCycler.end();
        //this.recreate();

        final String TAGG = "onNewIntent (task ID: " + getTaskId() + "): ";
        Log.d(TAG, TAGG + "Invoked.");
    }

    @Override
    public void onResume() {
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        super.onResume();
        final String TAGG = "onResume: ";
        logV(TAGG + "Invoked.");

        // Update our timestamp for when the activity became visible.
        // (this may be used by cycling routines to ensure clock stays visible at least some amount of time between messages)
        activityLastBecameVisible = new Date();

        // Register appropriate receivers
        registerReceiver(this.commandBroadcastReceiver, commandBroadcastReceiverIntentFilterFinish);

        // Begin scrolling
        //doMsgTypeEffect();
        doScrollingAnimation();
    }

    @Override
    public void onPause() {
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        final String TAGG = "onPause: ";
        logV(TAGG + "Invoked.");

        // Set delivery flags
        DeliveryService.omniMessageUuidDelivery_currently = null;

        // Unregister appropriate receivers
        unregisterReceiver(this.commandBroadcastReceiver);

        super.onPause();
    }

    @Override
    protected void onStop() {
        // The system retains the current state of this.

        final String TAGG = "onStop: ";
        logV(TAGG + "Invoked.");

        // Try to force finish (and thus onDestroy to fire and clean stuff up)
        try {
            finish();
        } catch (Exception e) {
            logE(TAGG + "Exception caught trying to finish activity: " + e.getMessage());
        }

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        final String TAGG = "onDestroy: ";
        logV(TAGG + "Invoked.");
        // NOTE: You should probably never assume that this will always be invoked!
        // But trying to force it (e.g. onStop finish call)

        try {
            DeliveryService.omniMessageUuidDelivery_currently = null;
            DeliveryService.omniMessageUuidDelivery_loading = null;

            if (this.systemUtils != null) {
                this.systemUtils.cleanup();
                this.systemUtils = null;
            }
            this.omniApplication = null;
            this.sharedPrefsUtils = null;

            this.commandBroadcastReceiver = null;
            this.commandBroadcastReceiverIntentFilterFinish = null;

            this.mContentView = null;
            this.fullscreen_content = null;
            this.layout_msgHeader_container = null;
            this.textView_msgHeader_left = null;
            this.textView_msgHeading = null;
            this.textView_msgHeader_right = null;
            this.textView_message = null;
            this.textView_details = null;
            this.scrollView_forMessage = null;

            this.animationMsgType = null;
            this.decelerateInterpolator = null;
            this.translateAnimation = null;
            this.linearInterpolator = null;
            this.animationEventListener = null;
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }

        super.onDestroy();
    }


    /*============================================================================================*/
    /* Utility Methods */

    /** Use legacy method to derive which colors to display and set them on screen (using message type as defined in legacy MNS)
     * @param msgHeading A string containing the legacy MNS message-type (e.g. EMERGENCY, MESSAGE, etc.)
     */
    private void setColors_legacy(String msgHeading) {
        final String TAGG = "setColors_legacy: ";

        int layoutBackgroundColor = Color.WHITE;

        int msgTypeTextColor = Color.BLACK;
        int msgTypeBackgroundColor = Color.WHITE;

        int msgTextTextColor = Color.RED;
        int msgTextBackgroundColor = Color.BLACK;

        int msgDetailsTextColor = Color.BLACK;
        int msgDetailsBackgroundColor = Color.WHITE;

        // Handle and sanitize any unusual data
        if (msgHeading == null || msgHeading.isEmpty()) {
            msgHeading = "MESSAGE";
            Log.w(TAG, TAGG+"msgtype not provided. Substituting '" + msgHeading + "'.");
        }

        // Determine and save all screen colors depending on the message type
        Log.d(TAG, TAGG+"msgType = \"" + msgHeading + "\".");
        switch (msgHeading) {
            case "EMERGENCY":
                int MNS_PURPLE = 0xCCFF00CC;
                layoutBackgroundColor = MNS_PURPLE;
                msgTypeBackgroundColor = Color.RED;
                msgTextTextColor = Color.WHITE;
                msgDetailsBackgroundColor = MNS_PURPLE;

                // Set power-appropriate screen brightness...
                SystemUtils.setScreenBrightness_maximum(SystemUtils.DO_ASYNCHRONOUSLY);

                break;
            case "SHELTER":
                layoutBackgroundColor = Color.YELLOW;
                msgTypeBackgroundColor = Color.RED;
                msgTextTextColor = Color.WHITE;
                msgDetailsBackgroundColor = Color.YELLOW;

                // Set power-appropriate screen brightness...
                SystemUtils.setScreenBrightness_maximum(SystemUtils.DO_ASYNCHRONOUSLY);

                break;
            case "EVACUATE":
                layoutBackgroundColor = Color.RED;
                msgTypeBackgroundColor = Color.RED;
                msgTextTextColor = Color.WHITE;
                msgDetailsBackgroundColor = Color.RED;

                // Set power-appropriate screen brightness...
                SystemUtils.setScreenBrightness_maximum(SystemUtils.DO_ASYNCHRONOUSLY);

                break;
            case "WARNING":
                int MNS_ORANGERED = 0xFFFF3900;
                layoutBackgroundColor = MNS_ORANGERED;
                msgTypeBackgroundColor = MNS_ORANGERED;
                msgTextTextColor = Color.WHITE;
                msgDetailsBackgroundColor = MNS_ORANGERED;

                // Set power-appropriate screen brightness...
                SystemUtils.setScreenBrightness_maximum(SystemUtils.DO_ASYNCHRONOUSLY);

                break;
            case "GRAPHICAL ANNUNCIATOR":
                layoutBackgroundColor = Color.YELLOW;
                msgTypeBackgroundColor = Color.YELLOW;
                msgTextTextColor = Color.WHITE;
                msgDetailsBackgroundColor = Color.YELLOW;

                break;
            case "CLEAR":
                layoutBackgroundColor = Color.GREEN;
                msgTypeBackgroundColor = Color.RED;
                msgTextTextColor = Color.WHITE;
                msgDetailsBackgroundColor = Color.GREEN;

                break;
            case "INFORMATION":
                int MNS_LIGHT_GREEN = 0xFF00FF99;
                layoutBackgroundColor = MNS_LIGHT_GREEN;
                msgTypeBackgroundColor = MNS_LIGHT_GREEN;
                msgTextTextColor = Color.WHITE;
                msgDetailsBackgroundColor = MNS_LIGHT_GREEN;

                break;
            case "ANNOUNCEMENT":
                int MNS_LIGHT_BLUE = 0xFF33CCFF;
                layoutBackgroundColor = MNS_LIGHT_BLUE;
                msgTypeBackgroundColor = MNS_LIGHT_BLUE;
                msgTextTextColor = Color.WHITE;
                msgDetailsBackgroundColor = MNS_LIGHT_BLUE;

                break;
            case "MESSAGE":
                int MNS_LIGHT_PURPLE = 0xFFFF77FF;
                layoutBackgroundColor = MNS_LIGHT_PURPLE;
                msgTypeBackgroundColor = MNS_LIGHT_PURPLE;
                msgTextTextColor = Color.WHITE;
                msgDetailsBackgroundColor = MNS_LIGHT_PURPLE;

                break;
            case "NORMAL":
            default:
                layoutBackgroundColor = Color.WHITE;
                msgTypeBackgroundColor = Color.WHITE;
                msgTextTextColor = Color.WHITE;
                msgDetailsBackgroundColor = Color.WHITE;

                break;
        }

        // Update the main background
        this.fullscreen_content.setBackgroundColor(layoutBackgroundColor);

        // Update the top portion
        this.layout_msgHeader_container.setBackgroundColor(msgTypeBackgroundColor);
        this.textView_msgHeading.setTextColor(msgTypeTextColor);
        this.textView_msgHeader_left.setTextColor(msgTypeTextColor);
        this.textView_msgHeader_right.setTextColor(msgTypeTextColor);

        // Update the message text that will be scrolled
        this.scrollView_forMessage.setBackgroundColor(msgTextBackgroundColor);
        this.textView_message.setTextColor(msgTextTextColor);

        // Update the message details area and text
        this.textView_details.setBackgroundColor(msgDetailsBackgroundColor);
        this.textView_details.setTextColor(msgDetailsTextColor);
    }
    private void setColors_v2(OmniMessage omniMessage) {
        //TODO
    }

    private boolean determineTextToSpeech() {
        final String TAGG = "determineTextToSpeech: ";
        boolean shouldDoTTS = false;

        // How to determine whether to TTS or not...
        //  - For MessageNet Connections v1, if there's an audio group
        //  - For API, ?
        if(omniApplication.getEcosystem()==OmniApplication.ECOSYSTEM_MESSAGENET_CONNECTIONS_V1) {
            Resources r = getApplicationContext().getResources();
            BannerMessage bannerMessage = this.omniMessageToDeliver.getBannerMessage();
            if (bannerMessage == null) {
                logW(TAGG + "BannerMessage is not available. Cannot determine text-to-speech. Defaulting to no text-to-speech.");
                shouldDoTTS = false;
            } else {
                if (bannerMessage.getDbb_pa_delivery_mode().equals(r.getString(R.string.PA_DELIVERY_MODE_TEXTTOSPEECH))
                        /* && TODO: migrate doesMessageSpecifyThisDeviceInAudioGroup() method */) {
                    shouldDoTTS = true;
                /*
                } else if (bannerMessage.getDbb_pa_delivery_mode().equals(r.getString(R.string.PA_DELIVERY_MODE_UNAVAILABLE))
                        && //TODO: migrate doesMessageSpecifyThisDeviceInAudioGroup() method ) {
                    logV(TAGG+"The dbb_pa_delivery_mode is unavailable/blank (\" \"), defaulting to do text-to-speech.");
                    shouldDoTTS = true;
                } else if (bannerMessage.getDbb_pa_delivery_mode().equals(r.getString(R.string.PA_DELIVERY_MODE_NOTSELECTED))
                        && //TODO: migrate doesMessageSpecifyThisDeviceInAudioGroup() method) {
                    logV(TAGG+"The dbb_pa_delivery_mode was not selected in message definition, defaulting to do text-to-speech.");
                    shouldDoTTS = true;
                */
                } else if (bannerMessage.getDbb_pa_delivery_mode().equals(r.getString(R.string.PA_DELIVERY_MODE_RECORDANDPLAY))) {
                    shouldDoTTS = false;
                } else if (bannerMessage.getDbb_pa_delivery_mode().equals(r.getString(R.string.PA_DELIVERY_MODE_SPEAKLIVE))) {
                    shouldDoTTS = false;
                } else {
                    // Maybe the BannerMessage object wasn't constructed? Fall back to raw JSON...
                    try {
                        JSONObject messageAsJSON = this.omniMessageToDeliver.getMessageJSONObject();
                        if (messageAsJSON.has(r.getString(R.string.BANNMSGFIELDNAME_JSON_PADELIVERYMODE))) {
                            if (messageAsJSON.getString(r.getString(R.string.BANNMSGFIELDNAME_JSON_PADELIVERYMODE)).equals(r.getString(R.string.PA_DELIVERY_MODE_TEXTTOSPEECH))
                                /* TODO && BannerMessages.doesMessageSpecifyThisDeviceInAudioGroup(new BannerMessage(getApplicationContext(), messageAsJSON)) */) {
                                shouldDoTTS = true;
                            /*    TODO migrate doesMessageSpecifyThisDeviceInAudioGroup() method
                            } else if (messageAsJSON.getString(r.getString(R.string.BANNMSGFIELDNAME_JSON_PADELIVERYMODE)).equals(r.getString(R.string.PA_DELIVERY_MODE_UNAVAILABLE))
                                    && BannerMessages.doesMessageSpecifyThisDeviceInAudioGroup(new BannerMessage(getApplicationContext(), messageAsJSON))) {
                                Log.v(TAG, TAGG+"The dbb_pa_delivery_mode is unavailable/blank (\" \"), defaulting to do text-to-speech.");
                                this.shouldDoTTS = true;
                            } else if (messageAsJSON.getString(r.getString(R.string.BANNMSGFIELDNAME_JSON_PADELIVERYMODE)).equals(r.getString(R.string.PA_DELIVERY_MODE_NOTSELECTED))
                                    && BannerMessages.doesMessageSpecifyThisDeviceInAudioGroup(new BannerMessage(getApplicationContext(), messageAsJSON))) {
                                Log.v(TAG, TAGG+"The dbb_pa_delivery_mode was not selected in message definition, defaulting to do text-to-speech.");
                                this.shouldDoTTS = true;
                            */
                            } else if (messageAsJSON.getString(r.getString(R.string.BANNMSGFIELDNAME_JSON_PADELIVERYMODE)).equals(r.getString(R.string.PA_DELIVERY_MODE_RECORDANDPLAY))) {
                                shouldDoTTS = false;
                            } else if (messageAsJSON.getString(r.getString(R.string.BANNMSGFIELDNAME_JSON_PADELIVERYMODE)).equals(r.getString(R.string.PA_DELIVERY_MODE_SPEAKLIVE))) {
                                shouldDoTTS = false;
                            } else {
                                shouldDoTTS = false;
                            }
                        } else {
                            logW(TAGG + "Message's JSON object does not contain \"" + R.string.BANNMSGFIELDNAME_JSON_PADELIVERYMODE + "\". Defaulting to no text-to-speech.");
                            shouldDoTTS = false;
                        }
                    } catch (JSONException je) {
                        logE(TAGG + "Exception caught parsing data for determining text-to-speech. Defaulting to no text-to-speech.");
                        shouldDoTTS = false;
                    }
                }
            }
        } else if(omniApplication.getEcosystem()==OmniApplication.ECOSYSTEM_MESSAGENET_CONNECTIONS_V2) {
            //TODO
        } else if(omniApplication.getEcosystem()==OmniApplication.ECOSYSTEM_STANDARD_API) {
            //TODO
        } else {
            logW(TAGG + "Unable to determine ecosystem. Defaulting to no text-to-speech.");
            shouldDoTTS = false;
        }

        logV(TAGG+"Text-to-speech should be set to '"+String.valueOf(shouldDoTTS)+"'.");

        return shouldDoTTS;
    }


    /*============================================================================================*/
    /* Message Methods */

    /** Call this to flush any changes to the local OmniMessage back to the DeliveryService.omniMessagesForDelivery list. */
    private void saveOmniMessage() {
        final String TAGG = "saveOmniMessage: ";

        try {
            //MainService.omniMessages_deliverable.updateOmniMessage(this.omniMessageToDeliver);
            MainService.omniMessages_deliverable.updateOmniMessage(this.omniMessageToDeliver);

            // Verify
            //if (MainService.omniMessages_deliverable.equals(this.omniMessageToDeliver)) {
            if (MainService.omniMessages_deliverable.equals(this.omniMessageToDeliver)) {
                logV(TAGG+"Main deliverable message updated to be equal to this activity's message.");
            } else {
                logW(TAGG+"Main deliverable message does not equal this activity's message after invoking updateOmniMessage (it didn't update successfully).");
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }


    /*============================================================================================*/
    /* Animation Methods */

    /** Start the message-type fade/flash visual effect.
     * NOTE: If scrolling is not smooth enough, consider disabling this effect? **/
    private void doMsgTypeEffect() {
        final String TAGG = "startMsgTypeAnimation: ";
        try {
            if (this.omniMessageToDeliver.getMsgHeading().length() > 0) {
                if (animationMsgType == null) {
                    animationMsgType = new AlphaAnimation(0.3f, 1.0f);
                }
                animationMsgType.setDuration(900);
                animationMsgType.setInterpolator(this.decelerateInterpolator);
                animationMsgType.setRepeatMode(Animation.REVERSE);
                animationMsgType.setRepeatCount(Animation.INFINITE);
                this.textView_msgHeading.startAnimation(animationMsgType);
                logV(TAGG + "Animation started.");
            } else {
                logD(TAGG + "Animation not necessary when there's no msgHeader text.");
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }

    /** Start the message-text scrolling animation
     * 2017.12.21   Chris Rider     Added 800 to display width to position text prior to scrolling, so it doesn't start in the middle of the screen -- seems non-remix Android 5 needs it? **/
    private void doScrollingAnimation() {
        final String TAGG = "doScrollingAnimation: ";

        final Paint textPaint;
        ViewGroup.LayoutParams params;
        DisplayMetrics dm;
        int textWidth, textAndScreenWidth, timescale, animDuration;
        float timescaleFactor, speedFactor;

        // Determine and finalize text and view size parameters
        try {
            textPaint = this.textView_message.getPaint();
            textWidth = Math.round(textPaint.measureText(this.omniMessageToDeliver.getMsgText()));  //measure the text size (WARNING, if this is null, fatal exception and crash will occur)
            params = this.textView_message.getLayoutParams();
            params.width = textWidth;
            this.textView_message.setLayoutParams(params);                                          //refine container based on measured text size
            dm = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught finalizing text and view size parameters (msgText null?). Aborting.");
            return;
        }

        // Calculate a scrolling speed, based on defined value
        //(note: necessary since it could vary depending on length of text, and we want speed normalized across all possible text lengths)
        switch(this.omniMessageToDeliver.getMsgTextScrollSpeed()) {
            case 1:
                // Lower was too fast, so increase to slow it down a bit (per Kevin, 2017.07.20)
                //timescaleFactor = (float) 2.2;                                                      //factor of 1 is baseline, >1 is overall slower, <1 is overall faster
                // Can go slightly faster into the barely uncomfortable-to-read territory (per Kevin, 2018.11.28)
                timescaleFactor = (float) 2.00;
                break;
            case 2:
                //this should be the speed that results in the least blurring but is still fast (per Kevin, 2017.07.20)
                timescaleFactor = (float) 1.40;                                                     //factor of 1 is baseline, >1 is overall slower, <1 is overall faster
                break;
            case 3:
                timescaleFactor = (float) 1.20;                                                     //factor of 1 is baseline, >1 is overall slower, <1 is overall faster
                break;
            case 4:
                timescaleFactor = (float) 1.00;                                                     //factor of 1 is baseline, >1 is overall slower, <1 is overall faster
                break;
            case 5:
                timescaleFactor = (float) 0.90;                                                     //factor of 1 is baseline, >1 is overall slower, <1 is overall faster
                break;
            case 6:
                timescaleFactor = (float) 0.85;                                                     //factor of 1 is baseline, >1 is overall slower, <1 is overall faster
                break;
            case 7:
                timescaleFactor = (float) 0.80;                                                     //factor of 1 is baseline, >1 is overall slower, <1 is overall faster
                break;
            case 8:
                //don't want to go too slow, as it could allow user to deny timely delivery of other messages (per Kevin, 2018.11.28)
                timescaleFactor = (float) 0.80;                                                     //factor of 1 is baseline, >1 is overall slower, <1 is overall faster
                break;
            default:
                timescaleFactor = (float) 1.00;                                                     //factor of 1 is baseline, >1 is overall slower, <1 is overall faster
                break;
        }
        speedFactor = (float) this.omniMessageToDeliver.getMsgTextScrollSpeed()/4;
        textAndScreenWidth = params.width + dm.widthPixels;
        timescale = Math.round(textAndScreenWidth * timescaleFactor);
        animDuration = Math.round(timescale*speedFactor);                                           //calculate time it takes for all characters to clear screen in 2 seconds

        logV(TAGG+"Animation duration will be: "+Integer.toString(animDuration));

        // Setup our command to end the light mode near the end of the scroll, so it can be done by the time the scrolling ends
        // NOTE: You should probably aim to keep the sum of the specified value (and 750ms for clock visibility) greater than the flasher lights app's LIGHT_COMMAND_TIMEOUT_MS value, to ensure GATT gets cleaned up all the way!
        if (DeliveryService.currentMsgHasLightCommand) {
            long accruedLightDurationSoFarUntilNow = this.omniMessageToDeliver.getFlasherDurationSecondsDone();
            int calculatedLightDurationMS = animDuration;

            /*
            int msPreAnimationEndToExecuteAnotherLightCmd = 1800;
            if (setupPreAnimationEndLightCommand(DeliveryService.flasherLightCommandCodes.CMD_LIGHT_STANDBY, animDuration, msPreAnimationEndToExecuteAnotherLightCmd, false)) {
            //if (setupPreAnimationEndLightCommand(DeliveryService.flasherLightCommandCode_nextMessage, animDuration, 250, false)) {
                // At this point, we should know about how long the actual light command should last, so let's go ahead and save it back to the message.
                // This way, we can later know when to no longer do the light command, per any defined light-duration in the message.
                calculatedLightDurationMS = calculatedLightDurationMS - msPreAnimationEndToExecuteAnotherLightCmd;       //this should be about how long the light is active during delivery
            }
            */

            omniMessageToDeliver.setFlasherDurationSecondsDone(accruedLightDurationSoFarUntilNow + calculatedLightDurationMS);
            saveOmniMessage();
        }

        // Define our animation and its characteristics
        this.translateAnimation = new TranslateAnimation(dm.widthPixels+800, -params.width, 0, 0);                    //start at far right of display, then move text to the left as wide as text is rendered
        this.translateAnimation.setDuration(animDuration);
        this.translateAnimation.setRepeatMode(Animation.RESTART);
        this.translateAnimation.setRepeatCount(0);                                                                    //Note: an explicit number setting is required for onAnimationEnd to invoke
        this.translateAnimation.setInterpolator(this.linearInterpolator);
        this.translateAnimation.setAnimationListener(this.animationEventListener);

        // Actually start the animation
        //this.textView_message.startAnimation(this.translateAnimation);
        this.textView_message.setVisibility(View.INVISIBLE);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                textView_message.setVisibility(View.VISIBLE);
                textView_message.startAnimation(translateAnimation);
            }
        },1000);
    }

    /** Considering how long the scroll will take, setup and execute a delayed light command to fire after some time.
     * This may be used to clear the message's light before it actually ends, to execute next message's light command, etc.
     * @param lightCmd Which light command to execute before the animation ends
     * @param animDuration How long (in milliseconds) the animation will take
     * @param msBeforeAnimationEndToExecuteCmd How many ms before animation ends to execute the specified light command
     * @param doForce (undeveloped yet)
     * @return Whether the delayed command was successfully initiated
     */
    private boolean setupPreAnimationEndLightCommand(final byte lightCmd, int animDuration, int msBeforeAnimationEndToExecuteCmd, boolean doForce) {
        final String TAGG = "setupDelayedLightCommand: ";

        if (true) return false; //DISABLE FOR NOW TO PREVENT LIGHT COMMAND MISSES   -2020.07.01

        boolean ret = false;

        try {
            int msDelayToExecuteLightCmd;

            // As long as the specified milliseconds would actually elapse before the end of the animation,
            // and as long as it's a positive value, proceed... Else, we'd be executing the command after
            // the message is done scrolling, or before it even begins (which would not make sense).
            if (msBeforeAnimationEndToExecuteCmd < animDuration
                    && msBeforeAnimationEndToExecuteCmd > 0) {

                // Our first delivery scrolls twice, so in that case, we need to double the calculated execution delay,
                // otherwise we'd be ending the light prematurely on the very first scroll.
                if (omniMessageToDeliver.getMsgTextScrollsDone() <= 1)
                    msDelayToExecuteLightCmd = (animDuration * 2) - msBeforeAnimationEndToExecuteCmd;
                else
                    msDelayToExecuteLightCmd = animDuration - msBeforeAnimationEndToExecuteCmd;

                // If there's only one message, we don't need to execute any additional light commands,
                // as we just keep the message's light mode until it stops delivering (or other messages queue up).
                if (MainService.omniMessages_deliverable.size() == 1) {
                    //no need to do anything, unless our doForce flag is true
                    if (!doForce) {
                        msDelayToExecuteLightCmd = 0;
                    }
                }

                // If we have a valid delay value, setup a Handler to execute the light command on that delay.
                if (msDelayToExecuteLightCmd > 0) {
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                FlasherLights.broadcastLightCommand(getApplicationContext(), lightCmd, Long.MAX_VALUE, null);
                            } catch (Exception e) {
                                logE(TAGG + "Exception caught setting scheduled light command clear: " + e.getMessage());
                            }
                        }
                    }, msDelayToExecuteLightCmd);
                    ret = true;
                }
            } else {
                logW(TAGG+"Specified value ("+msBeforeAnimationEndToExecuteCmd+") must be between 0 and "+animDuration+". Aborting");
            }
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }

        logV(TAGG+"Returning: "+Boolean.toString(ret));
        return ret;
    }


    /*============================================================================================*/
    /* Subclasses */

    private class AnimationEventListener implements Animation.AnimationListener {
        private final String TAGG = AnimationListener.class.getSimpleName() + ": ";

        private boolean tempSkipTTS = false;

        @Override
        public void onAnimationStart(Animation animation) {
            final String TAGGG = "onAnimationStart: ";
            logD(TAGG+TAGGG+"Animation has started.");

            // Set our delivery-started Datetime
            // Note: We do this in a new thread, so that we minimize any impact on the UI thread!
            ThreadUtils.doStartThread(getBaseContext(),
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (omniMessageToDeliver.getMsgFirstDeliveryBeganDate() == null) {
                                    //Date dateBefore = omniMessageToDeliver.getMsgFirstDeliveryBeganDate();
                                    omniMessageToDeliver.setMsgFirstDeliveryBeganDate(new Date());
                                    //Date dateAfter = omniMessageToDeliver.getMsgFirstDeliveryBeganDate();
                                    //logV(TAGG+TAGGG+"First delivery date: Before = \""+String.valueOf(dateBefore)+"\", After = \""+String.valueOf(dateAfter)+"\".");
                                    saveOmniMessage();
                                } else {
                                    logV(TAGG + TAGGG + "First delivery date has already been set (\"" + omniMessageToDeliver.getMsgFirstDeliveryBeganDate().toString() + "\").");
                                }
                            } catch (Exception e) {
                                logE(TAGG+TAGGG+"Exception caught setting delivery-started datetime: "+e.getMessage());
                            }
                        }
                    }),
                    ThreadUtils.SPAWN_NEW_THREAD_TRUE,
                    ThreadUtils.PRIORITY_MINIMUM);

            // Start any text-to-speech
            // Note: We do this in a new thread, so that we minimize any impact on the UI thread!
            ThreadUtils.doStartThread(getBaseContext(),
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (!tempSkipTTS && doTextToSpeech && omniApplication.getIsTextToSpeechAvailable()) {
                                    TextToSpeechServicer.startMessageTTS(getApplicationContext(), omniMessageToDeliver.getMessageUUID());
                                    tempSkipTTS = false;
                                }
                            } catch (Exception e) {
                                logE(TAGG + TAGGG + "Exception caught starting text-to-speech: " + e.getMessage());
                            }
                        }
                    }),
                    ThreadUtils.SPAWN_NEW_THREAD_TRUE,
                    ThreadUtils.PRIORITY_MINIMUM);

            DeliveryService.omniMessageUuidDelivery_currently = omniMessageToDeliver.getMessageUUID();
            DeliveryService.omniMessageUuidDelivery_loading = null;
        }
        @Override
        public void onAnimationEnd(Animation animation) {
            final String TAGGG = "onAnimationEnd: ";
            logD(TAGG+TAGGG+"Animation has ended.");

            if (getIntent() != null) {
                boolean doSkip = getIntent().getBooleanExtra("skipWriteLastDeliveredUUID", false);  //default to false (don't skip) if no extra available
                if (doSkip) {
                    logV(TAGG+TAGGG+"Intent explicitly says to skip writing last-delivered UUID.");
                } else {
                    DeliveryService.omniMessageUuidDelivery_lastCompleted = omniMessageToDeliver.getMessageUUID();
                }
            } else {
                //if no intent available, default to not skipping the write
                DeliveryService.omniMessageUuidDelivery_lastCompleted = omniMessageToDeliver.getMessageUUID();
            }

            omniMessageToDeliver.setMsgTextScrollsDone(omniMessageToDeliver.getMsgTextScrollsDone()+1);
            saveOmniMessage();

            //need to wait until audio is done before continuing
            //WARNING! Animation operates on the main (UI) thread, so sleeping or looping risks ANR

            //if this is the first time this message has been scrolled, repeat the scroll by however many times preferred
            if (omniMessageToDeliver.getMsgTextScrollsDone() <= 1) {
                tempSkipTTS = true;             //ensure our 2nd scroll of an initial delivery (which scrolls twice in a row) doesn't speak twice (per Kevin, 4/28/2020.
                translateAnimation.start();
                return;
            }

            //if we get here, we're really done with delivery, so clear the screen and finish this activity
            //but first need to check any ongoing text-to-speech synthesis (we should wait to finish activity until that's done)
            textView_message.setText("");
            if (doTextToSpeech) {
                final int maxSecondsToWait = 30;
                ThreadUtils.doStartThread(getBaseContext(),
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                int wait = maxSecondsToWait;
                                while (omniApplication.getIsTextToSpeechOccurring()) {
                                    //wait here until it's done

                                    if (wait <= 0) {
                                        logE(TAGG+TAGGG+"Waited too long for message's speech synthesis to stop. Stopping synthesis and finishing activity anyway.");
                                        TextToSpeechServicer.stopOngoingTTS(getApplicationContext());
                                        break;  //skip directly to finish() call below...
                                    }

                                    logV(TAGG+TAGGG+"Message is still being spoken by TextToSpeechServicer, waiting up to "+wait+" more seconds until it's done before finishing activity.");

                                    try {
                                        Thread.sleep(1000);
                                    } catch (InterruptedException e) {
                                        logW(TAGG+TAGGG+"Exception caught trying to sleep while waiting for message's speech synthesis to finish: " + e.getMessage());
                                    }

                                    wait--;
                                }
                            }
                        }),
                        ThreadUtils.SPAWN_NEW_THREAD_TRUE,
                        ThreadUtils.PRIORITY_MINIMUM);
            }

            // Clear any light
            //// Dev-NOTE: letting clock activity onResume handle this or showing next msg's light command
            FlasherLightService.startLight(getApplicationContext(), DeliveryService.flasherLightCommandCodes.CMD_LIGHT_STANDBY);

            finish();
        }
        @Override
        public void onAnimationRepeat(Animation animation) {
            //since we manually do our own "repeat", nothing to do here
        }
    }

    private class CommandBroadcastReceiver extends BroadcastReceiver {
        private final String TAGG = CommandBroadcastReceiver.class.getSimpleName() + ": ";
        
        public CommandBroadcastReceiver() {
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String TAGGG = "onReceive: ";
            
            if (intent.getAction() == null) {
                logW(TAGG+TAGGG+"Intent action is null. Don't know what to do.");
            } else {
                if (intent.getAction().equals(INTENT_ACTION_CMD_FINISH)) {
                    logD(TAGG+TAGGG+"Finishing activity...");
                    finish();
                }
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
