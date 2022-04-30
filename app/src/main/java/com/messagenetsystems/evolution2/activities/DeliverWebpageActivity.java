package com.messagenetsystems.evolution2.activities;

/* DeliverWebpageActivity
 * Provides webpage message delivery.
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
 *  2020.12.22      Chris Rider     Copied from DeliverScrollingMsgActivity and modified into a blank template useful for creating other delivery activities.
 *  2020.12.22      Chris Ride      Modifying template for webpage-web type.
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.OmniApplication;
import com.messagenetsystems.evolution2.R;
import com.messagenetsystems.evolution2.models.BannerMessage;
import com.messagenetsystems.evolution2.models.FlasherLights;
import com.messagenetsystems.evolution2.models.OmniMessage;
import com.messagenetsystems.evolution2.models.OmniMessages;
import com.messagenetsystems.evolution2.services.DeliveryService;
import com.messagenetsystems.evolution2.services.MainService;
import com.messagenetsystems.evolution2.services.TextToSpeechServicer;
import com.messagenetsystems.evolution2.utilities.DatetimeUtils;
import com.messagenetsystems.evolution2.utilities.SharedPrefsUtils;
import com.messagenetsystems.evolution2.utilities.SystemUtils;
import com.messagenetsystems.evolution2.utilities.ThreadUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;


public class DeliverWebpageActivity extends AppCompatActivity {
    private final String TAG = this.getClass().getSimpleName();

    // Constants...
    private static final String PKG_NAMESPACE = "com.messagenetsystems.evolution2.activities";
    private static final String ACTIVITY_NAME = DeliverWebpageActivity.class.getSimpleName();
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

    public static volatile Date activityLastBecameVisible;

    private OmniMessage omniMessageToDeliver;
    private boolean doTextToSpeech;

    private WebView mWebView;
    WebViewClient mWebViewClient;

    private String msg_URL = null;
    private Boolean interactive;
    private Boolean doSpeakMsg;

    private Date pageLoadCompletedDate;


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
        setContentView(R.layout.activity_deliver_webpage_msg);

        // Initialize more local stuff
        this.mContentView = findViewById(R.id.fullscreen_deliver_webpage);
        this.fullscreen_content = (ConstraintLayout) mContentView;

        // Setup the base webview...
        mWebView = (WebView) findViewById(R.id.fullscreen_webview_webpage);
        mWebView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
        mWebView.setWebChromeClient(new WebChromeClient());                                      //have to use this in order to support HTML5 (e.g. canvas), and possibly other higher-performance stuff
        mWebView.getSettings().setUserAgentString("Mozilla/5.0 (X11; Linux x86_64)");
        mWebView.getSettings().setJavaScriptEnabled(true);                                         //enable javascript support
        mWebView.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
        mWebView.setScrollbarFadingEnabled(false);
        //myWebView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);                                     //needed for HTML5-canvas support, apparently -- must also disable hardwareAccelerated for the webview in the manifest
        mWebView.setInitialScale(100);

        // Setup WebViewClient...
        mWebViewClient = new WebViewClient() {
            final String TAGG = "myWebViewClient: ";

            private boolean tempSkipTTS = false;

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Log.v(TAG, TAGG+"Received error: "+error.getErrorCode()+ " ("+error.getDescription()+").");
                } else {
                    Log.v(TAG, TAGG+"Received error: "+String.valueOf(error));
                }
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);

                Log.v(TAG, TAGG+"Started page loading ("+String.valueOf(url)+").");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                final String TAGGG = "onPageFinished: ";

                Log.v(TAG, TAGG+"Finished page loading ("+String.valueOf(url)+").");
                pageLoadCompletedDate = new Date();

                // Set our delivery-started Datetime
                // Note: We do this in a new thread, so that we minimize any impact on the UI thread!
                ThreadUtils.doStartThread(getBaseContext(),
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    if (omniMessageToDeliver.getMsgFirstDeliveryBeganDate() == null) {
                                        //Date dateBefore = omniMessageToDeliver.getMsgFirstDeliveryBeganDate();
                                        omniMessageToDeliver.setMsgFirstDeliveryBeganDate(pageLoadCompletedDate);
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

                    /*
                    //update this message's last-delivered date in the main active messages list
                    try {
                        SmmonService.activeBannerMessages.updateLastDeliveredByLocalUUID(currentlyDisplayingMessageUUID, new Date());
                    } catch (Exception e) {
                        Log.e(TAG, "deliverTheMessage: Caught exception trying to update dtLastDelivered in activeBannerMessages: " + e.getMessage());
                    }

                    //update this message's delivery-count in the main active messages list
                    try {
                        SmmonService.activeBannerMessages.incrementDeliveryCountByLocalUUID(currentlyDisplayingMessageUUID);
                    } catch (Exception e) {
                        Log.e(TAG, "deliverTheMessage: Caught exception trying to increment deliveryCount in activeBannerMessages: " + e.getMessage());
                    }
                    */

                // Start delivery end timer
                // Note: This is what enables the clock to return for intervals and for message to actually stay down when expired or closed.
                ThreadUtils.doStartThread(getBaseContext(),
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    long mediaPlaytimeMS = omniMessageToDeliver.getMediaPlaytime() * 1000;
                                    if (mediaPlaytimeMS < 1000) mediaPlaytimeMS = 30 * 1000;                //TODO: offer this 30s value to configuration or something?

                                    //wait for media playtime to elapse...
                                    while ( (new Date().getTime() - mediaPlaytimeMS) < pageLoadCompletedDate.getTime() ) {
                                        try {
                                            logV(TAGG+TAGGG+"PLAYTIME: "+(new Date().getTime() - mediaPlaytimeMS)+" < "+pageLoadCompletedDate.getTime());
                                            Thread.sleep(1000);
                                        } catch (InterruptedException e) {
                                            logW(TAGG+TAGGG+"Exception caught trying to sleep while waiting for message's media playtime to finish: " + e.getMessage());
                                        }
                                    }

                                    //if tts, wait up to maximum amount for it to finish...
                                    if (doTextToSpeech) {
                                        int maxSecondsToWait = 30;
                                        while (omniApplication.getIsTextToSpeechOccurring()) {
                                            if (maxSecondsToWait <= 0) {
                                                logE(TAGG+TAGGG+"Waited too long for message's speech synthesis to stop. Stopping synthesis and finishing activity anyway.");
                                                TextToSpeechServicer.stopOngoingTTS(getApplicationContext());
                                                break;  //skip directly to finish() call below...
                                            }

                                            logV(TAGG+TAGGG+"Message is still being spoken by TextToSpeechServicer, waiting up to "+maxSecondsToWait+" more seconds until it's done before finishing activity.");

                                            try {
                                                Thread.sleep(1000);
                                            } catch (InterruptedException e) {
                                                logW(TAGG + TAGGG + "Exception caught trying to sleep while waiting for message's speech synthesis to finish: " + e.getMessage());
                                            }

                                            maxSecondsToWait--;
                                        }
                                    }

                                    //finally, finish the activity and return to clock
                                    finish();
                                } catch (Exception e) {
                                    logE(TAGG + TAGGG + "Exception caught setting delayed delivery end: " + e.getMessage());
                                }
                            }
                        }),
                        ThreadUtils.SPAWN_NEW_THREAD_TRUE,
                        ThreadUtils.PRIORITY_LOW);

                DeliveryService.omniMessageUuidDelivery_currently = omniMessageToDeliver.getMessageUUID();
                DeliveryService.omniMessageUuidDelivery_loading = null;

                //update this activity's public flag
                //msgIsLoaded = true;
            }
        };

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

        msg_URL = this.omniMessageToDeliver.getBannerMessage().getWebpageurl();
        //interactive = getIntent().getBooleanExtra("INTERACTIVE", true);   //TODO
        interactive = true;
        //doSpeakMsg = getIntent().getBooleanExtra("DOSPEAKMSG", false);      //default to false if not provided 2018-02-14,CR  //TODO
        doSpeakMsg = false;

        // Initialize any potential text-to-speech, so it has time to load up and be ready to start on demand
        doTextToSpeech = determineTextToSpeech();
        if (doTextToSpeech && omniApplication.getIsTextToSpeechAvailable()) {
            TextToSpeechServicer.prepareMessageTTS(getApplicationContext(), this.omniMessageToDeliver.getMessageUUID());
        }

        // Set screen elements with message data
        try {
            logV(TAGG + "Preparing to deliver " + this.omniMessageToDeliver.getMessageUUID().toString() + " (\"" + this.omniMessageToDeliver.getMsgText() + "\").");

            //TODO: initializations on screen?
        } catch (Exception e) {
            logE(TAGG + "Exception caught (accessing OmniMessage from DeliveryService?): " + e.getMessage());
            //TODO: some kind of error message to scroll? or just close the activity?
            finish();
            return;
        }

        try {
            //TODO: Customize priority?
            //logE(TAGG+"Thread priority before: "+android.os.Process.getThreadPriority(Process.myTid()));
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            Process.setThreadPriority(-20);  //this is the highest possible priority that can be set
            //logE(TAGG+"Thread priority after: "+android.os.Process.getThreadPriority(Process.myTid()));
        } catch (Throwable throwable) {
            logW(TAGG+"Exception caught adjusting UI thread priority: "+throwable.getMessage());
        }

        deliverTheMessage();
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
        systemUtils.configScreen_hideNavigationBar(findViewById(R.id.fullscreen_deliver_webpage));
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

        // Begin delivery things
        //doMsgTypeEffect();
        //doScrollingAnimation();
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

        mWebView.loadUrl("about:blank");   //make sure nothing sticks around after activity goes away (e.g. youtube audio keeps playing in background, etc.)

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


        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }

        super.onDestroy();
    }


    /*============================================================================================*/
    /* Utility Methods */

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

    /** Deliver the message **/
    private void deliverTheMessage() {
        final String TAGG = "deliverTheMessage: ";

        try {

            //setup whether screen is interactive or not
            if (interactive == false) {
                /*
                logV(TAGG+"Overriding onTouch event so webpage is not interactive.");
                mWebView.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        return true;
                    }
                });
                */
            }

            //provide a way to execute stuff after a page is finished loading (prevents TTS before loading, etc.)
            logV(TAGG+"Setting up webview client.");
            mWebView.setWebViewClient(mWebViewClient);

            //actually start the web page loading, as setup above...
            logD(TAGG+"Now loading URL '" + msg_URL + "'.");
            mWebView.loadUrl(msg_URL);

            //update this message's last-delivered date in the main active messages list


            //update this message's delivery-count in the main active messages list


        } catch (Exception e) {
            logE(TAGG+"Exception caught trying to load URL into fullscreen_webview: " + e.getMessage());
        }
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
