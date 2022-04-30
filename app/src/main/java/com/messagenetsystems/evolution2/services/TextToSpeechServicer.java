package com.messagenetsystems.evolution2.services;

/* TextToSpeechServicer
 * Class for handling Text-to-Speech duties.
 * This is a service that is able to listen for broadcasts that perform TTS duties.
 * There are easy-to-use public-static methods, in case you don't want to do that on your own.
 *
 * There are two ways to use TTS -- manually construct your own broadcast, or use the public static methods (easiest).
 * Either way, the process may two-fold... first, prepare/load message data, and then make it speak after that. You may also just directly speak (preparation will automatically be handled).
 * Preparation may be desired, so that the engine can initialize and have the message ready to speak on command with as little delay as possible when needed on-demand.
 *
 * You may simply call the public-static methods:
 *  TextToSpeechServicer.prepareMessageTTS(Context, UUID);
 *  TextToSpeechServicer.speakMessageTTS(Context, UUID);
 *  TextToSpeechServicer.stopOngoingTTS(Context);
 *
 * OR, manually...
 *  Intent i = new Intent(TextToSpeechServicer.BROADCAST_RECEIVER_NAME);
 *  i.putExtra(TextToSpeechServicer.INTENTEXTRA_TTSPURPOSE, TextToSpeechServicer.TTSPURPOSE_PREPARETOSPEAK);
 *  i.putExtra(TextToSpeechServicer.INTENTEXTRA_MSGUUID, "2ebc607a-ec8c-4a19-bee4-86bde2a616c0");
 *  sendBroadcast(i);
 *
 *  Intent i = new Intent(TextToSpeechServicer.BROADCAST_RECEIVER_NAME);
 *  i.putExtra(TextToSpeechServicer.INTENTEXTRA_TTSPURPOSE, TextToSpeechServicer.TTSPURPOSE_SPEAKMESSAGE);
 *  i.putExtra(TextToSpeechServicer.INTENTEXTRA_MSGUUID, "2ebc607a-ec8c-4a19-bee4-86bde2a616c0");
 *  sendBroadcast(i);
 *
 *  Intent i = new Intent(TextToSpeechServicer.BROADCAST_RECEIVER_NAME);
 *  i.putExtra(TextToSpeechServicer.INTENTEXTRA_TTSPURPOSE, TextToSpeechServicer.TTSPURPOSE_STOPSPEAKING);
 *  sendBroadcast(i);
 *
 * Revisions:
 *  2018.08.15      Chris Rider     Created.
 *  2019.07.26      Chris Rider     Added gender capability.
 *  2019.08.05      Chris Rider     Added static return (just for ease) of SmmonService.isTextToSpeechInProgress value.
 *                                  Logic to now take over responsibility for closing delivery activities if needed (starting with just ScrollMsgWithDetails).
 *  2019.10.25      Chris Rider     Moved TTS duties into a thread, to free up main/UI thread and hopefully fix Choreographer warnings about skipping frames and appliation doing too much work on main thread.
 *  2019.12.05      Chris Rider     Migrated from v1.
 *  2020.04.24-27   Chris Rider     Minor refactoring to add pre-load purpose, and to only require message UUID.
 *  2020.04.29-30   Chris Rider     Utilizing class-wide OmniMessage object that gets initialized for the relevant message on preparation.
 *                                  Implementing speech counting and repeats support.
 *                                  Created some public static methods for easy start-stop from anywhere.
 *                                  Implemented gender selection, based on message data.
 *  2020.05.01      Chris Rider     Implemented audio volume and gain control.
 *  2020.08.07      Chris Rider     Added thread-ID acquisition and output to notification.
 *                                  All the heavier workload (preparing, speaking, etc. -not just .speak() method) is now done on a new thread with lower priority to hopefully assist smoother scrolling.
 *  2020.08.09      Chris Rider     Migrated in v1 textToSpeak normalization routines.
 */

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.OmniApplication;
import com.messagenetsystems.evolution2.R;
import com.messagenetsystems.evolution2.models.ConfigData;
import com.messagenetsystems.evolution2.models.OmniMessage;
import com.messagenetsystems.evolution2.models.OmniMessages;
import com.messagenetsystems.evolution2.utilities.AudioUtils;
import com.messagenetsystems.evolution2.utilities.ThreadUtils;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;


public class TextToSpeechServicer extends Service implements TextToSpeech.OnInitListener {
    public static final String TAG = TextToSpeechServicer.class.getSimpleName();

    // Constants...
    public static final String BROADCAST_RECEIVER_NAME = "com.messagenetsystems.evolution2.TextToSpeechServicer.broadcastReceiver";
    public static final String INTENTEXTRA_TTSPURPOSE = "com.messagenetsystems.evolution2.TextToSpeechServicer.ttsPurpose";
    public static final String INTENTEXTRA_MSGUUID = "com.messagenetsystems.evolution2.TextToSpeechServicer.msgUUID";
    public static final int TTSPURPOSE_PREPARETOSPEAK = 1;
    public static final int TTSPURPOSE_SPEAK = 2;
    public static final int TTSPURPOSE_STOPSPEAKING = 3;

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
    private OmniApplication omniApplication;
    private ConfigData configData;                  //note: be sure to grab what you need from this, initialize data, and then clean it up
    private AudioUtils audioUtils;

    private int tid = 0;

    private Voice voiceDefault;
    private Voice voiceMale;
    private Voice voiceFemale;
    private TextToSpeech tts;
    private float ttsRate;
    private float ttsPitch_male;
    private float ttsPitch_female;
    private int ttsVolumeGain;

    private BroadcastReceiver ttsBroadcastReceiver;

    private volatile boolean ttsIsInitialized;
    private volatile boolean ttsIsOccurring;

    private volatile OmniMessage omniMessageToSpeak;
    private volatile UUID preparedForUUID;
    private volatile String preparedGender;
    private volatile String preparedTextToSpeak;

    /** Constructor */
    public TextToSpeechServicer() {}


    /*============================================================================================*/
    /* Service Stuff */

    @Override
    public void onCreate() {
        // The system invokes this method to perform one-time setup procedures when the service is
        // initially created (before it calls either onStartCommand or onBind). If the service is
        // already running, this method is not invoked.
        super.onCreate();
        final String TAGG = "onCreate: ";
        this.logMethod = LOG_METHOD_FILELOGGER;
        logV(TAGG+"Invoked.");

        this.appContextRef = new WeakReference<Context>(getApplicationContext());

        try {
            this.omniApplication = ((OmniApplication) getApplicationContext());
        } catch (Exception e) {
            logE("Exception caught instantiating "+TAG+": "+e.getMessage());
        }

        Resources resources = getApplicationContext().getResources();

        this.audioUtils = new AudioUtils(getApplicationContext(), Constants.LOG_METHOD_FILELOGGER);

        // Initialize text-to-speech params defined in strings.xml
        this.ttsRate = Float.parseFloat(resources.getString(R.string.tts_rate));
        this.ttsPitch_male = Float.parseFloat(resources.getString(R.string.tts_pitch_male));
        this.ttsPitch_female = Float.parseFloat(resources.getString(R.string.tts_pitch_female));

        // Initialize TTS object
        this.tts = new TextToSpeech(getApplicationContext(), this, resources.getString(R.string.tts_default_engine_package_name));

        // Initialize voices
        this.voiceDefault = new Voice(resources.getString(R.string.tts_voice_name_default), new Locale("en","US"), Voice.QUALITY_NORMAL, Voice.LATENCY_LOW, false, null);
        this.voiceMale = new Voice(resources.getString(R.string.tts_voice_name_male), new Locale("en","US"), Voice.QUALITY_NORMAL, Voice.LATENCY_LOW, false, null);
        this.voiceFemale = new Voice(resources.getString(R.string.tts_voice_name_female), new Locale("en","US"), Voice.QUALITY_NORMAL, Voice.LATENCY_LOW, false, null);

        // Initialize any misc stuff
        this.ttsVolumeGain = 0;

        // Initialize broadcast receiver
        this.ttsBroadcastReceiver = new TextToSpeechBroadcastReceiver();

        // Initialize flags
        this.ttsIsInitialized = false;
        this.ttsIsOccurring = false;
        omniApplication.setIsTextToSpeechAvailable(this.ttsIsInitialized);
        omniApplication.setIsTextToSpeechOccurring(this.ttsIsOccurring);
        this.preparedForUUID = null;
        this.preparedGender = null;
        this.preparedTextToSpeak = null;

        // Register broadcast receiver
        registerReceiver(ttsBroadcastReceiver, new IntentFilter(BROADCAST_RECEIVER_NAME));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // The system invokes this method by calling startService() when another component (such as
        // an activity) requests that the service be started. When this method executes, the service
        // is started and can run in the background indefinitely. If you implement this, it is your
        // responsibility to stop the service by calling stopSelf() or stopService(). If you want to
        // implement binding, then you don't need to implement this method.
        super.onStartCommand(intent, flags, startId);
        final String TAGG = "onStartCommand: ";
        logI(TAGG+"Service started.");

        tid = android.os.Process.myTid();

        // Update notification that everything is started and running
        omniApplication.appendNotificationWithText(TAG+" started. (tid:"+tid+")");

        // Ensure this service is very hard to kill and that it even restarts if needed
        //return START_STICKY;      //NOTE: not necessary since this service lives under MainService (which itself starts sticky)???
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // The system invokes this method by calling bindService() when another component wants to
        // bind with this service (such as to perform RPC). In your implmentation of this method,
        // you must provide an interface that clients use to communicate with the service by
        // returning an IBinder. You must always implement this method; however, if you don't want
        // to allow binding, you should return null;
        final String TAGG = "onBind: ";
        logV(TAGG+"Invoked.");

        return null;
    }

    @Override
    public void onDestroy() {
        // The system invokes this method when the service is no longer used and is being destroyed.
        // Your service should implement this to clean up any resources such as threads, registered
        // listeners, or receivers. This is the last call that the service receives.
        final String TAGG = "onDestroy: ";
        logV(TAGG+"Invoked.");

        // Update notification so we can know something went wrong if it wasn't supposed to
        // (a legit stop will then reset the notification so you don't get a false positive)
        omniApplication.appendNotificationWithText("TextToSpeechServicer died! ("+new Date().toString()+")");

        cleanup();

        super.onDestroy();
    }


    /*============================================================================================*/
    /* TextToSpeech.onInit Stuff */

    @Override
    public void onInit(int status) {
        final String TAGG = "TextToSpeech.onInit: ";
        logI(TAGG+"Invoked. "+this.getClass().getSimpleName()+" is now ready to use. Simply broadcast a request.");

        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.US);
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {

                // Get available system voices to make sure we have what is specified in strings
                Set<Voice> voicesAvailable_maleEnglishUS = extractSpecifiedVoicesFromAvailable(this.tts, "#male", "en-us");
                Set<Voice> voicesAvailable_femaleEnglishUS = extractSpecifiedVoicesFromAvailable(this.tts, "#female", "en-us");
                //Log.d(TAG, TAGG+"Current voice: \""+tts.getVoice().getName()+"\"");   //debugging
                //Log.v(TAG, TAGG+"Voices available: "+String.valueOf(tts.getVoices()));

                // Check that preferred voices match what is actually available on the system, and set the voice depending on the result
                if (!doesVoiceExistInVoiceSet(voicesAvailable_maleEnglishUS, this.voiceMale)) {
                    this.voiceMale = autoSelectVoiceForMale(voicesAvailable_maleEnglishUS);
                }
                if (!doesVoiceExistInVoiceSet(voicesAvailable_femaleEnglishUS, this.voiceFemale)) {
                    this.voiceFemale = autoSelectVoiceForFemale(voicesAvailable_femaleEnglishUS);
                }

                try {
                    logI(TAGG + "Voices to be used:\nMale: " + this.voiceMale.getName() + "\nFemale: " + this.voiceFemale.getName() + "\nDefault: " + this.voiceDefault.getName());
                } catch (NullPointerException e) {
                    logW(TAGG+"Exception caught: "+e.getMessage());
                }

                // Initial default setup
                this.tts.setVoice(this.voiceMale);
                this.tts.setPitch(this.ttsPitch_male);
                this.tts.setSpeechRate(this.ttsRate);

                // Set our local and global
                this.ttsIsInitialized = true;
                omniApplication.setIsTextToSpeechAvailable(this.ttsIsInitialized);
            }
        } else {
            logE(TAGG+"Did not succeed in initializing TTS. Status = "+status+". Stopping service...");
            stopSelf(); //this should invoke onDestroy (which invokes cleanup)
        }
    }


    /*============================================================================================*/
    /* BroadcastReceiver Stuff */

    public class TextToSpeechBroadcastReceiver extends BroadcastReceiver {
        private final String TAGG = TextToSpeechBroadcastReceiver.class.getSimpleName() + ": ";

        //constructor
        public TextToSpeechBroadcastReceiver(){}

        @Override
        public void onReceive(Context context, Intent intent) {
            final String TAGGG = "onReceive: ";

            Bundle intentExtras;
            int ttsPurpose;
            String uuidMsgString = null;

            try {
                // Get all the extras from the broadcast's intent
                intentExtras = intent.getExtras();
                if (intentExtras == null) {
                    logW(TAGG+TAGGG+"No extras provided by intent, so cannot get data. Aborting any TTS functions for this attempt.");
                    return;
                }

                // Get the purpose of this broadcast from the extras
                ttsPurpose = intentExtras.getInt(INTENTEXTRA_TTSPURPOSE, 0);
                if (ttsPurpose == 0) {
                    logW(TAGG+TAGGG+"No purpose provided by intent, so don't know what to do. Aborting any TTS functions for this attempt.");
                    return;
                }

                // Get message data passed in through intent
                try {
                    uuidMsgString = intentExtras.getString(INTENTEXTRA_MSGUUID);
                } catch (Exception e) {
                    Log.w(TAG, TAGG+"Exception caught getting/parsing message data from intent: "+e.getMessage());
                }

                // Check message data
                if (uuidMsgString == null || uuidMsgString.isEmpty()) {
                    logW(TAGG+TAGGG+"No/invalid message UUID provided by intent. Aborting any TTS functions for this attempt.");
                    return;
                }

                // Take appropriate action, depending on what the purpose is
                final int ttsPurpose_forThread = ttsPurpose;
                final String uuidMsgString_forThread = uuidMsgString;
                ThreadUtils.doStartThread(context,
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                switch (ttsPurpose_forThread) {
                                    case TTSPURPOSE_PREPARETOSPEAK:
                                        handlePurpose_prepareToSpeakMessage(uuidMsgString_forThread);
                                        break;
                                    case TTSPURPOSE_SPEAK:
                                        handlePurpose_stopSpeaking();               //take extra care to stop any previous ongoing text before we continue to speak the next
                                        handlePurpose_speakMessage(uuidMsgString_forThread);
                                        break;
                                    case TTSPURPOSE_STOPSPEAKING:
                                        handlePurpose_stopSpeaking();
                                        break;
                                    default:
                                        logW(TAGG+TAGGG+"Unhandled case (ttsPurpose = "+ttsPurpose_forThread+").");
                                        break;
                                }
                            }
                        }),
                        ThreadUtils.SPAWN_NEW_THREAD_TRUE,
                        ThreadUtils.PRIORITY_MINIMUM);
            } catch (Exception e) {
                logE(TAGG+TAGGG+"Exception caught: "+e.getMessage());
            }
        }

        private void handlePurpose_prepareToSpeakMessage(String uuidMsgString) {
            final String TAGG = "handlePurpose_prepareToSpeakMessage: ";
            logV(TAGG+"Invoked.");

            UUID uuidMsg;
            String textToSpeak = null;
            int msgGender = OmniMessage.TTS_VOICE_GENDER_UNKNOWN;

            // Get the message's data
            if (uuidMsgString == null || uuidMsgString.isEmpty()) {
                logW(TAGG + "No UUID provided for the message to speak. Aborting TTS for this attempt.");
                return;
            } else {
                uuidMsg = UUID.fromString(uuidMsgString);

                if (preparedForUUID != null && preparedForUUID.toString().equals(uuidMsgString)) {
                    //already prepared for specified message, nothing to do
                    logI(TAGG+"Already prepared for specified message, nothing to do. You may tell us to speak now.");
                    return;
                }

                // If we get to here, then we need to prepare TTS with message
                omniMessageToSpeak = MainService.omniMessages_deliverable.getOmniMessage(uuidMsg, OmniMessages.GET_OMNIMESSAGE_AS_COPY);
            }

            // Get any volume gain from the message
            ttsVolumeGain = omniMessageToSpeak.getTtsVoiceVolumeGain();

            // Get text we want to speak from the message
            textToSpeak = omniMessageToSpeak.getMsgText();

            // If no text-to-speak data provided, abort
            if (textToSpeak == null || textToSpeak.isEmpty()) {
                logW(TAGG+"No message text available. Aborting TTS for this attempt.");
                return;
            }

            // Normalize what we actually speak so it sounds right
            textToSpeak = normalizeStringForTTS(textToSpeak);

            // Get gender we want to use for synthesis from the message
            msgGender = omniMessageToSpeak.getTtsVoiceGender();

            // Determine gender to use when synthesizing
            if (msgGender == OmniMessage.TTS_VOICE_GENDER_UNKNOWN) {
                logW(TAGG+"No gender available. Continuing as male.");
                msgGender = OmniMessage.TTS_VOICE_GENDER_MALE;
            }

            prepareGender(msgGender);
            prepareTextToSpeak(textToSpeak);

            //TODO: some double-checks for gender and text?

            preparedForUUID = uuidMsg;
        }

        private void handlePurpose_speakMessage(String uuidMsgString) {
            final String TAGG = "handlePurpose_speakMessage: ";
            logV(TAGG+"Invoked.");

            // See if this message has already been prepared...
            // If not, then prepare it now, else you may proceed with just speaking it.
            if (preparedForUUID != null && preparedForUUID.toString().equals(uuidMsgString)) {
                //message has already been prepared, so just speak it
            } else {
                handlePurpose_prepareToSpeakMessage(uuidMsgString);
            }

            // If TTS engine has already started (or is) speaking the same message, no need to do again and clobber the original
            if ((tts.isSpeaking()
                    || omniApplication.getIsTextToSpeechOccurring())
                && (preparedForUUID != null && preparedForUUID.toString().equals(uuidMsgString))) {
                logW(TAGG+"Synthesis of this message has already started or is occurring, aborting.");
                return;
            }

            // Actually do the TTS
            speakPreparedMessage();
        }

        private void handlePurpose_stopSpeaking() {
            final String TAGG = "handlePurpose_stopSpeaking: ";
            logV(TAGG+"Invoked.");

            stopSpeaking();
        }
    }


    /*============================================================================================*/
    /* Our Internal Stuff */

    private void prepareTextToSpeak(String textToSpeak) {
        preparedTextToSpeak = textToSpeak;
    }

    private void prepareGender(int msgGender) {
        if (msgGender == OmniMessage.TTS_VOICE_GENDER_UNKNOWN) {
            prepareGender("M");
        } else if (msgGender == OmniMessage.TTS_VOICE_GENDER_MALE) {
            prepareGender("M");
        } else if (msgGender == OmniMessage.TTS_VOICE_GENDER_FEMALE) {
            prepareGender("F");
        } else {
            prepareGender("M");
        }
    }
    private void prepareGender(String gender) {
        final String TAGG = "prepareGender: ";

        // If TTS engine is not initialized, then abort
        if (!this.ttsIsInitialized) {
            logW(TAGG+"TTS engine not initialized. Aborting.");
            omniApplication.setIsTextToSpeechAvailable(false);
            return;
        }

        // Set gender
        if (gender.equals("F")) {
            logV(TAGG+"Using female voice.");
            setGenderFemale();
        } else {
            logV(TAGG+"Using male voice.");
            setGenderMale();
        }

        // Set rate
        this.tts.setSpeechRate(this.ttsRate);
    }

    private void speakPreparedMessage() {
        final String TAGG = "speakPreparedMessage: ";
        logV(TAGG+"Invoked.");

        // If TTS engine is not initialized, then abort
        if (!this.ttsIsInitialized) {
            logW(TAGG+"TTS engine not initialized. Aborting.");
            omniApplication.setIsTextToSpeechAvailable(false);
            return;
        }

        // Update the event listeners
        this.tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onBeginSynthesis(String utteranceId, int sampleRateInHz, int audioFormat, int channelCount) {
                final String TAGGG = "UtteranceProgressListener.onBeginSynthesis: ";
                logV(TAGG+TAGGG+"TTS synthesis has begun.");
                ttsIsOccurring = true;
                omniApplication.setIsTextToSpeechOccurring(ttsIsOccurring);
            }

            @Override
            public void onStart(String utteranceId) {
                final String TAGGG = "UtteranceProgressListener.onStart: ";
                logV(TAGG+TAGGG+"TTS has started.");
                ttsIsOccurring = true;
                omniApplication.setIsTextToSpeechOccurring(ttsIsOccurring);
            }

            @Override
            public void onStop(String utteranceId, boolean interrupted) {
                final String TAGGG = "UtteranceProgressListener.onStop: ";
                logV(TAGG+TAGGG+"TTS has been stopped.");

                // Reset our device default volume
                audioUtils.setConfiguredDefaultBaseVolume();

                // Reset our flags
                ttsIsOccurring = false;
                omniApplication.setIsTextToSpeechOccurring(ttsIsOccurring);
            }

            @Override
            public void onDone(String utteranceId) {
                final String TAGGG = "UtteranceProgressListener.onDone: ";
                logV(TAGG+TAGGG+"TTS has finished.");

                // Increment ttsNumberOfSpeaksDone and decrement ttsNumberOfSpeaksToDo
                try {
                    //OmniMessage om = MainService.omniMessages_deliverable.getOmniMessage(preparedForUUID, OmniMessages.GET_OMNIMESSAGE_AS_COPY);
                    long speaksDone = omniMessageToSpeak.getTtsNumberOfSpeaksDone();
                    long speaksToDo = omniMessageToSpeak.getTtsNumberOfSpeaksToDo();
                    omniMessageToSpeak.setTtsNumberOfSpeaksDone(speaksDone + 1);
                    omniMessageToSpeak.setTtsNumberOfSpeaksToDo(speaksToDo - 1);
                    logV(TAGG+TAGGG+"Updating "+preparedForUUID.toString()+", speaks-done "+Long.toString(speaksDone)+"->"+Long.toString(speaksDone+1)+" and speaks-todo "+Long.toString(speaksToDo)+"->"+Long.toString(speaksToDo-1)+".");
                    MainService.omniMessages_deliverable.updateOmniMessage(omniMessageToSpeak);
                } catch (Exception e) {
                    logE(TAGG+TAGGG+"Exception caught (message no longer exists?): "+e.getMessage());
                }

                // Reset our device default volume
                audioUtils.setConfiguredDefaultBaseVolume();

                // Reset our flags
                ttsIsOccurring = false;
                omniApplication.setIsTextToSpeechOccurring(ttsIsOccurring);

                // Just a safety catch to ensure it stops (probably not necessary??)
                stopSpeaking();
            }

            @Override
            public void onError(String utteranceId) {
                final String TAGGG = "UtteranceProgressListener.onError: ";
                logE(TAGG+TAGGG+"TTS has encountered an error.");

                // Reset our device default volume
                audioUtils.setConfiguredDefaultBaseVolume();

                // Reset our flags
                ttsIsOccurring = false;
                omniApplication.setIsTextToSpeechOccurring(ttsIsOccurring);

                // Just be safe
                stopSpeaking();
            }
        });

        // Check gender and set if necessary (sometimes it can be slow to set initially?)
        /*
        if (gender.equals("F")) {
            if (this.tts.getVoice().getName().equals(this.voiceFemale.getName())) {
                //we're good!
            } else {
                logW(TAGG+"setVoice to female did not occur yet, trying again.");
                this.tts.setVoice(this.voiceFemale);
            }
        } else {
            if (this.tts.getVoice().getName().equals(this.voiceMale.getName())) {
                //we're good
            } else {
                logW(TAGG+"voice does not look right, setting to male.");
                this.tts.setVoice(this.voiceMale);
            }
        }
        */

        // Go ahead and set the volume gain now, right before we start speaking //TODO: move this math stuff up to init section?
        int platformGainPercent = 0;
        if (omniApplication.getEcosystem() == OmniApplication.ECOSYSTEM_MESSAGENET_CONNECTIONS_V1) {
            int gainMin = getResources().getInteger(R.integer.volume_gain_tts_minimum);
            int gainMax = getResources().getInteger(R.integer.volume_gain_tts_maximum);
            int gainRange = gainMax - gainMin;
            if (gainRange > 0) {
                platformGainPercent = Math.round((ttsVolumeGain / gainRange) * 100);
            } else {
                platformGainPercent = 100;
            }
        } else if (omniApplication.getEcosystem() == OmniApplication.ECOSYSTEM_MESSAGENET_CONNECTIONS_V2) {
            logE(TAGG+"GAIN CALCULATION FOR PLATFORM NOT DEVELOPED YET"); //TODO
            platformGainPercent = 0;
        } else if (omniApplication.getEcosystem() == OmniApplication.ECOSYSTEM_STANDARD_API) {
            logE(TAGG+"GAIN CALCULATION FOR PLATFORM NOT DEVELOPED YET"); //TODO
            platformGainPercent = 0;
        } else {
            logE(TAGG+"GAIN CALCULATION FOR PLATFORM NOT DEVELOPED YET"); //TODO
            platformGainPercent = 0;
        }
        int androidVolumeGainedAmountToSet = audioUtils.calculateGainedAndroidVolumeAboveCurrent(platformGainPercent, AudioUtils.STREAM_DEFAULT);
        audioUtils.setAudioVolumeForStream(androidVolumeGainedAmountToSet, AudioUtils.STREAM_DEFAULT);

        // Do the TTS in a background worker thread.
        // This hopefully ensures that even if TextToSpeechServicer starts tied to main/UI thread, that TTS doesn't affect it.
        // (Note: Try to have as much other stuff done at this point, as possible, so it's quick as can be to actually start speaking)
        //new Thread(new Runnable() {
        //    @Override
        //    public void run() {
                logI(TAGG+"Attempting to speak from thread #"+ String.valueOf(Thread.currentThread().getId())+" (voice: \""+tts.getVoice().getName()+"\"), \""+preparedTextToSpeak+"\".");
                tts.speak(preparedTextToSpeak, TextToSpeech.QUEUE_FLUSH, null, preparedForUUID.toString());
        //    }
        //}).start();
    }

    private void speakText(final String text, final String utteranceID, String gender) {
        final String TAGG = "speakText: ";
        logV(TAGG+"Invoked.");

        // If no text supplied by argument, then abort
        if (text.isEmpty()) {
            logW(TAGG+"No text provided. Aborting.");
            return;
        }

        // If TTS engine is not initialized, then abort
        if (!this.ttsIsInitialized) {
            logW(TAGG+"TTS engine not initialized. Aborting.");
            omniApplication.setIsTextToSpeechAvailable(false);
            return;
        }

        // Set gender
        if (gender.equals("F")) {
            logV(TAGG+"Using female voice.");
            setGenderFemale();
        } else {
            logV(TAGG+"Using male voice.");
            setGenderMale();
        }

        // Set rate
        this.tts.setSpeechRate(this.ttsRate);

        // Update the event listeners
        this.tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                final String TAGGG = "UtteranceProgressListener.onStart: ";
                logV(TAGG+TAGGG+"TTS has started.");
                omniApplication.setIsTextToSpeechOccurring(true);
            }

            @Override
            public void onDone(String utteranceId) {
                final String TAGGG = "UtteranceProgressListener.onDone: ";
                logV(TAGG+TAGGG+"TTS has finished.");
                omniApplication.setIsTextToSpeechOccurring(false);

                stopSpeaking();

                //TODO: decrement audio repeat count on message??
            }

            @Override
            public void onError(String utteranceId) {
                final String TAGGG = "UtteranceProgressListener.onError: ";
                logE(TAGG+TAGGG+"TTS has encountered an error.");
                omniApplication.setIsTextToSpeechOccurring(false);

                stopSpeaking();
            }
        });

        // Check gender and set if necessary (sometimes it can be slow to set initially?)
        if (gender.equals("F")) {
            if (this.tts.getVoice().getName().equals(this.voiceFemale.getName())) {
                //we're good!
            } else {
                logW(TAGG+"setVoice to female did not occur yet, trying again.");
                this.tts.setVoice(this.voiceFemale);
            }
        } else {
            if (this.tts.getVoice().getName().equals(this.voiceMale.getName())) {
                //we're good
            } else {
                logW(TAGG+"voice does not look right, setting to male.");
                this.tts.setVoice(this.voiceMale);
            }
        }

        // Do the TTS in a background worker thread.
        // This hopefully ensures that even if TextToSpeechServicer starts tied to main/UI thread, that TTS doesn't affect it.
        // (Note: Try to have as much other stuff done at this point, as possible, so it's quick as can be to actually start speaking)
        new Thread(new Runnable() {
            @Override
            public void run() {
                logI(TAGG+"Attempting to speak from thread #"+ String.valueOf(Thread.currentThread().getId())+" (voice: \""+tts.getVoice().getName()+"\"), \""+text+"\".");
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceID);
            }
        }).start();
    }

    private void stopSpeaking() {
        final String TAGG = "stopSpeaking: ";
        logV(TAGG+"Invoked.");

        if (this.tts == null) {
            logW(TAGG+"TTS object is null. Aborting.");
            return;
        }

        if (this.tts.isSpeaking()) {
            this.tts.stop();
        } else {
            logD(TAGG+"TTS is not speaking, so no need to call .stop() method.");
        }
    }


    /*============================================================================================*/
    /* Supporting Methods */

    public void cleanup() {
        final String TAGG = "cleanup: ";
        logV(TAGG+"Invoked.");

        if (this.ttsBroadcastReceiver != null) {
            unregisterReceiver(ttsBroadcastReceiver);
            this.ttsBroadcastReceiver = null;
        }

        if (this.tts != null) {
            this.tts.stop();
            this.tts.shutdown();
            this.tts = null;
        }

        if (omniApplication != null) {
            omniApplication.setIsTextToSpeechOccurring(false);
            omniApplication.setIsTextToSpeechAvailable(false);
            omniApplication = null;
        }

        if (appContextRef != null) {
            appContextRef.clear();
            appContextRef = null;
        }
    }

    /** For now, this just selects the first match */
    private Voice autoSelectVoiceForMale(Set<Voice> voiceSet) {
        String TAGG = "autoSelectVoiceForMale: ";
        Voice ret = voiceDefault;
        String iVoiceName;

        try {
            for (Voice iVoice : voiceSet) {
                iVoiceName = iVoice.getName();
                if (iVoiceName != null) {
                    if (iVoiceName.toLowerCase().contains("#male")) {
                        ret = iVoice;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+ e.getMessage());
        }

        logV(TAGG+"Returning: "+ String.valueOf(ret));
        return ret;
    }

    /** For now, this just selects the first match */
    private Voice autoSelectVoiceForFemale(Set<Voice> voiceSet) {
        String TAGG = "autoSelectVoiceForFemale: ";
        Voice ret = voiceDefault;
        String iVoiceName;

        try {
            for (Voice iVoice : voiceSet) {
                iVoiceName = iVoice.getName();
                if (iVoiceName != null) {
                    if (iVoiceName.toLowerCase().contains("#female")) {
                        ret = iVoice;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+ e.getMessage());
        }

        logV(TAGG+"Returning: "+ String.valueOf(ret));
        return ret;
    }

    private boolean doesVoiceExistInVoiceSet(Set<Voice> voiceSet, Voice voice) {
        String TAGG = "doesVoiceExistInVoiceSet: ";

        if (voiceSet == null || voice == null) {
            logE(TAGG+"Null parameter(s) provided. Aborting and returning false.");
            return false;
        }

        String voiceName = voice.getName();
        if (voiceName == null) {
            logE(TAGG+"Provided Voice has no name. Aborting and returning false.");
            return false;
        }

        boolean ret = false;

        logV(TAGG + "Checking for voice: " + voice.getName());

        voiceName = voiceName.toLowerCase();
        String iVoiceName;

        for (Voice iVoice : voiceSet) {
            iVoiceName = iVoice.getName();
            //logV(TAGG+"Checking: \""+String.valueOf(iVoice)+"\"");  //DEBUGGING ONLY
            if (iVoiceName != null) {
                iVoiceName = iVoiceName.toLowerCase();
                if (iVoiceName.contains(voiceName)) {
                    ret = true;
                    break;
                }
            }
        }

        logV(TAGG+"Returning: "+ String.valueOf(ret));
        return ret;
    }

    /** Given a Set of Voices, extract out and return a Set of specified Voices.
     * Useful for selecting certain gender and language from a larger overall set.
     *  gender      Ex. "male" or "female"
     *  language    Ex. "en-us" */
    private Set<Voice> extractSpecifiedVoicesFromAvailable(TextToSpeech textToSpeech, String gender, String language) {
        String TAGG = "extractSpecifiedVoicesFromAvailable(";

        if (textToSpeech == null || gender == null || language == null) {
            logE(TAGG+"Null parameter(s) provided. Aborting and returning null.");
            return null;
        }

        TAGG = TAGG + "gender="+gender+",language="+language+"): ";
        Set<Voice> ret = textToSpeech.getVoices();
        ret.clear();
        String iVoiceName;
        gender = gender.toLowerCase();
        language = language.toLowerCase();

        for (Voice iVoice : textToSpeech.getVoices()) {
            iVoiceName = iVoice.getName();
            //Log.v(TAG, TAGG+"Checking: \""+String.valueOf(iVoice)+"\"");    //DEBUGGING ONLY
            if (iVoiceName != null) {
                iVoiceName = iVoiceName.toLowerCase();
                if (iVoiceName.contains(gender) && iVoiceName.contains(language)) {
                    if (iVoice.isNetworkConnectionRequired()) {
                        logD(TAGG + "Found matching available voice (\""+ String.valueOf(iVoiceName)+"\"), but network required, so skipping!");
                    } else {
                        logD(TAGG + "Found matching available voice (\""+ String.valueOf(iVoiceName)+"\"). Adding to Set to return.");
                        ret.add(iVoice);
                    }
                }
            }
        }

        logV(TAGG+"Returning: "+ String.valueOf(ret));
        return ret;
    }

    private void setGenderMale() {
        final String TAGG = "setGenderMale: ";

        try {
            this.tts.setVoice(this.voiceMale);
            this.tts.setPitch(this.ttsPitch_male);
            logV(TAGG+"Set voice to male.");
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+ e.getMessage());
        }
    }

    private void setGenderFemale() {
        final String TAGG = "setGenderFemale: ";

        try {
            this.tts.setVoice(this.voiceFemale);
            this.tts.setPitch(this.ttsPitch_female);
            logV(TAGG+"Set voice to female.");
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+ e.getMessage());
        }
    }

    private void setVolumeGain() {
        final String TAGG = "setVolumeGain: ";

        try {

        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+ e.getMessage());
        }
    }

    private void setVolumeToBase() {

    }

    private String normalizeStringForTTS(String preNormalizedText) {
        final String TAGG = "normalizeStringForTTS: ";
        Log.v(TAG, TAGG+"Invoked for \""+preNormalizedText+"\"");

        String postNormalizedText = preNormalizedText;

        // Check for undecoded message variables (data the server didn't resolve as expected)
        // For example #sender or #date, etc. (instead of actual sender or actual date)
        if (undecodedVariablesExist(preNormalizedText)) {
            Log.i(TAG, TAGG+"There appear to be un-decoded message variable(s).");
        } else {
            // Normalize decoded #signature (|X|Sender Name) message variable...
            // Server will always send us this decoded value at the end of the message text, so we can depend on |X| being the delimiter.
            postNormalizedText = normalizeStringForTTS_msgVar_signature(preNormalizedText);
        }

        return postNormalizedText;
    }

    /** Look for a server-decoded #signature (should have a "|X|") and return a suitable string for text-to-speech synthesis.
     * This is challenging, because we have no reliable way to know what words comprise the signature's end, how long signature is, etc.
     * Per Kevin 7/31/19, #signature at beginning and end speaks "from***" but #signature in middle just speaks decoded value only.
     * Also, we enforce both FIRST and LAST name in user record, so we can count on EXACTLY 2 words after |X| being the name!
     *  Ex. "This is a test message |X|Chris Rider"             --> "From: Chris Rider. This is a test message"
     *  Ex. "|X|Chris Rider This is a test message"             --> "From: Chris Rider This is a test message"
     *  Ex. "This is a message so |X|Chris Rider can test it."  --> "This is a message so Chris Rider can test it."
     *  Ex. "This is a message from |X|Chris Rider to test."    --> "This is a message from Chris Rider to test."       */
    private String normalizeStringForTTS_msgVar_signature(String messageText) {
        final String TAGG = "normalizeStringForTTS_msgVar_signature: ";
        Log.v(TAG, TAGG+"Invoked for \""+messageText+"\"");

        final String msgVarSignatureRaw = getResources().getString(R.string.MSGVAR_POUNDSIGNATURE_RAW);
        final String msgVarSignatureDecodedPrepend = getResources().getString(R.string.MSGVAR_POUNDSIGNATURE_DECODEDPREPEND);

        // first, check if messageText is null or effectively nothing and abort if so; else continue
        if (messageText == null || messageText.isEmpty() || messageText.length() <= 1) {
            Log.w(TAG, TAGG+"No valid messageText provided. Aborting and returning messageText.");
            return messageText;
        }

        // if made it to here, now check if there's an un-decoded msg-var; else continue
        if (messageText.contains(msgVarSignatureRaw)) {
            Log.i(TAG, TAGG+"The messageText contains a raw "+msgVarSignatureRaw+". Aborting and returning messageText.");
            return messageText;
        }

        // if made it to here, now check for a decoded #signature (existence of |X|)
        if (messageText.contains(msgVarSignatureDecodedPrepend)) {
            String messageText_normalized = messageText;

            try {

                // split message into an array of words (delimit by spaces)... this allows easier parsing
                final String[] messageTextWordArray = messageText.split(" ");

                // find array index that contains the start of our decoded message variable... this will be the word containing our first name
                int arrayIndexOfDecodedVarStart = 0;
                for (int i = 0; i < messageTextWordArray.length; i++) {
                    String iWord = messageTextWordArray[i];
                    if (iWord == null) {
                        Log.w(TAG, TAGG+"Array element #"+i+" (of "+(messageTextWordArray.length-1)+") is null.");
                    } else if (iWord.contains(msgVarSignatureDecodedPrepend)) {
                        arrayIndexOfDecodedVarStart = i;
                        break;
                    }
                }

                // save first and last names
                String signatureFirstName = messageTextWordArray[arrayIndexOfDecodedVarStart];      //ex. |X|Some Name
                String signatureLastName = messageTextWordArray[arrayIndexOfDecodedVarStart + 1];   //always the single word after first name

                // remove names from message word array, saving to a new string
                String messageText_noSignature;
                StringBuilder messageText_noSignature_sb = new StringBuilder("");
                for (int i = 0; i < messageTextWordArray.length; i++) {
                    if (messageTextWordArray[i].equals(signatureFirstName) || messageTextWordArray[i].equals(signatureLastName)) {
                        //omit adding these
                    } else {
                        //add the word
                        messageText_noSignature_sb.append(messageTextWordArray[i]);

                        //add back in a space if necessary
                        if (i < messageTextWordArray.length - 1) {
                            messageText_noSignature_sb.append(" ");
                        }
                    }
                }
                if (messageText_noSignature_sb.length() < 1) {
                    Log.w(TAG, TAGG+"Problem building out messageText_noSignature_sb. Using messageText for messageText_noSignature.");
                    messageText_noSignature = messageText;
                } else {
                    messageText_noSignature = messageText_noSignature_sb.toString();
                }

                // strip |X| from first name, now that we no longer need its original form to test for while removing name above
                signatureFirstName = signatureFirstName.replace(msgVarSignatureDecodedPrepend, "");

                // construct what we will return, depending on location of decoded value
                if (arrayIndexOfDecodedVarStart == 0) {
                    //signature at very beginning, make it speak "from: ..." and then message text
                    Log.v(TAG, TAGG + "Decoded signature is at beginning of messageText. Moving spoken signature to front.");
                    messageText_normalized = "From: "+signatureFirstName+" "+signatureLastName+". "+messageText_noSignature;
                } else if (arrayIndexOfDecodedVarStart == messageTextWordArray.length - 1 - 1) {
                    //signature is at very end (minus last name), make it speak "from: ..." and then message text
                    Log.v(TAG, TAGG + "Decoded signature is at end of messageText. Moving spoken signature to front.");
                    messageText_normalized = "From: "+signatureFirstName+" "+signatureLastName+". "+messageText_noSignature;
                } else if (arrayIndexOfDecodedVarStart > 0
                        && arrayIndexOfDecodedVarStart < messageTextWordArray.length - 1 - 1) {
                    //signature is somewhere in the middle, so just strip the |X| and allow speak name as part of message text
                    Log.v(TAG, TAGG + "Decoded signature is somewhere in the middle of messageText. Leaving spoken signature in-place but omitting |X|.");
                    messageText_normalized = messageText.replace(msgVarSignatureDecodedPrepend, "");
                } else {
                    //unexpected condition
                    Log.w(TAG, TAGG + "Unexpected condition.");
                }
            } catch (Exception e) {
                Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
            }

            return messageText_normalized;
        } else {
            Log.d(TAG, TAGG+"The messageText does not contain a decoded #signature. Nothing to do.");
            return messageText;
        }
    }

    private boolean undecodedVariablesExist(String messageText) {
        final String TAGG = "undecodedVariablesExist: ";
        Log.v(TAG, TAGG+"Invoked for \""+messageText+"\"");

        boolean ret;
        final String msgVarSymbol = getResources().getString(R.string.MSGVAR_POUND_RAW);

        if (messageText == null) {
            Log.e(TAG, TAGG+"Null messageText. Aborting and returning false.");
            ret = false;
        } else {
            if (messageText.isEmpty() || messageText.length() <= 1) {
                Log.w(TAG, TAGG+"No text in message. Returning false.");
                ret = false;
            } else if (messageText.contains(msgVarSymbol)) {
                Log.d(TAG, TAGG+"Likely message variable found. Returning true.");
                ret = true;
            } else {
                Log.d(TAG, TAGG+"No undecoded variables found. Returning false.");
                ret = false;
            }
        }

        return ret;
    }


    /*============================================================================================*/
    /* Public easy to use methods to affect TTS. */

    /** This method can be invoked from anywhere, to easily prepare speaking TTS of the specified message's text. */
    public static void prepareMessageTTS(Context context, UUID msgUUID) {
        final String TAGG = "startMessageTTS: ";

        try {
            // Get our intent
            Intent i = new Intent(BROADCAST_RECEIVER_NAME);
            i.putExtra(INTENTEXTRA_TTSPURPOSE, TTSPURPOSE_PREPARETOSPEAK);
            i.putExtra(TextToSpeechServicer.INTENTEXTRA_MSGUUID, msgUUID.toString());

            // Broadcast it
            context.sendBroadcast(i);
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }
    }

    /** This method can be invoked from anywhere, to easily start speaking TTS of the specified message's text. */
    public static void startMessageTTS(Context context, UUID msgUUID) {
        final String TAGG = "startMessageTTS: ";

        try {
            // Get our intent
            Intent i = new Intent(BROADCAST_RECEIVER_NAME);
            i.putExtra(INTENTEXTRA_TTSPURPOSE, TTSPURPOSE_SPEAK);
            i.putExtra(TextToSpeechServicer.INTENTEXTRA_MSGUUID, msgUUID.toString());

            // Broadcast it
            context.sendBroadcast(i);
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }
    }

    /** This method can be invoked from anywhere, to easily stop any ongoing TTS. */
    public static void stopOngoingTTS(Context context) {
        final String TAGG = "stopOngoingTTS: ";

        try {
            // Get our intent
            Intent i = new Intent(BROADCAST_RECEIVER_NAME);
            i.putExtra(INTENTEXTRA_TTSPURPOSE, TTSPURPOSE_STOPSPEAKING);

            // Broadcast it
            context.sendBroadcast(i);
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
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
