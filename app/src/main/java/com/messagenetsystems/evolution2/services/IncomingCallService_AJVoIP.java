package com.messagenetsystems.evolution2.services;

/*
 * IncomingCallService_AJVoIP service class.
 * Intended to run as a service and listen for any incoming SIP calls from the server.
 * This was created as a fresh/refactor effort for SIP stack version released June 19, 2019.
 *
 * This service hosts the SIP stack, and a thread that monitors what it's doing.
 *
 * Revisions:
 *  2019.06.19-26   Chris Rider     Created.
 *  2019.08.27      Chris Rider     Added flag for turning on/off "... is speaking" scrolling message.
 *  2020.04.23      Chris Rider     Migrated from v1 to here.
 */

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;

import com.messagenetsystems.evolution2.R;
//import com.messagenetsystems.evolution2.SmmonService;
import com.messagenetsystems.evolution2.utilities.NetUtils;
import com.mizuvoip.jvoip.SipStack;

import java.util.ArrayList;

public class IncomingCallService_AJVoIP extends Service {
    private String serviceName = this.getClass().getName();
    private String TAG = this.getClass().getSimpleName();

    private long servicePID = 0;
    private Context appContext = null;
    private SipStack sipStack = null;
    private Thread sipMonitorThread = null;

    final boolean showSpeakingScrollingMessage = false;

    // Some Mizu SIP Stack Values...
    private String activationCode = null;
    private final int SIP_TRANSPORT_UDP = 0;            //(per Mizutech AJVoIP documentation - default)
    private final int SIP_TRANSPORT_TCP = 1;            //(per Mizutech AJVoIP documentation)   TCP signaling; RTP will remain on UDP.
    private final int SIP_TRANSPORT_TLS = 2;            //(per Mizutech AJVoIP documentation)   Encrypted signaling.
    private final String SIP_TRANSPORT_HTTP_TUNNEL = "3";    //(per Mizutech AJVoIP documentation)   HTTP signaling and media (supported ONLY by mizu server or mizu tunnel).
    private final String SIP_TRANSPORT_HTTP_PROXY = "4";     //(per Mizutech AJVoIP documentation)   Proxy connect (requires tunnel server).
    private final String SIP_TRANSPORT_AUTO = "5";           //(per Mizutech AJVoIP documentation)   Auto failover from UDP to HTTP if needed.
    private final String SIP_NOTIF_LINENUM_ALLCHANS = "-2";  //(per Mizutech AJVoIP documentation)   All channels/lines.
    private final String SIP_NOTIF_LINENUM_CURRCHAN = "-1";  //(per Mizutech AJVoIP documentation)   Current channel/line (set by SetLine or other functions). Usually means the first channel.
    private final String SIP_NOTIF_LINENUM_UNDEFINED = "0";  //(per Mizutech AJVoIP documentation)   Undefined (should not be used for endpoints in call, but might be used for registration, etc.).
    private final String SIP_NOTIF_LINENUM_FIRSTCHAN = "1";  //(per Mizutech AJVoIP documentation)   First channel/line.
    //private final String PARAM_PREFCODEC_VALUE = "g.711";    //Set your preferred audio codec. Will accept one of the followings: pcmu, pcma, g.711 (for both PCMU and PCMA), g.719, (g.729 ??), gsm, ilbc, speex, speexwb, speexuwb, opus, opuswb, opusuwb, opussw
    //private final String PARAM_PREFCODEC_VALUE = "opuswb";
    private enum SipNotificationParamsGeneral {                     //(per Mizutech AJVoIP documentation)   The index number (zero-based) of general-status parameters from GetNotifications calls.
        EVENT_TYPE,
        LINE_NUMBER,
        STATUS_TEXT
    }
    private enum SipNotificationParams {                            //(per Mizutech AJVoIP documentation)   The index number (zero-based) of parameters from GetNotifications calls.
        EVENT_TYPE,
        LINE_NUMBER,
        STATUS_TEXT,
        PEER_NAME,
        LOCAL_NAME,
        ENDPOINT_TYPE,
        PEER_DISPLAYNAME,
        CALL_ID,
        ONLINE,
        REGISTERED,
        IN_CALL,
        MUTE,
        HOLD,
        ENCRYPTED,
        IS_VIDEO
    }

    private String sipPrefsUsername;
    private String sipPrefsPassword;
    private String sipPrefsPort;
    private String sipPrefsServer;
    private int sipPrefsRegistrationInterval;

    private String activeLocalIP = null;

    public IncomingCallService_AJVoIP(Context appContext) {
        super();
    }
    public IncomingCallService_AJVoIP() {
    }

    /** Service stuff... **************************************************************************/
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind called.");
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        final String TAGG = "onStartCommand: ";

        servicePID = android.os.Process.myPid();
        appContext = getApplicationContext();

        TAG = TAG+"("+ String.valueOf(servicePID)+")";
        Log.v(TAG, TAGG+"Invoked.");

        initializeData();

        initializeSipStack();
        configureSipStack();
        startSipStack();
        registerSipConnection();

        initializeSipMonitorThread();
        startSipMonitorThread();

        //return flags;
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy called.");

        if (sipStack != null) {
            sipStack.Unregister();
            sipStack.Stop();
        }
        sipStack = null;

        stopSipMonitorThread();
        sipMonitorThread = null;
    }

    /** Misc stuff.. ******************************************************************************/
    private void initializeData() {
        final String TAGG = "initializeData: ";

        activationCode = appContext.getResources().getString(R.string.sipStack_AJVoIP_activationCode);
        if (activationCode == null || activationCode.isEmpty() || activationCode.equals("")) {
            Log.w(TAG, TAGG+"Unable to retrieve activation code from strings.xml, substituting hard coded value.");
            activationCode = "e9m1bb7az8rh3m8g";
        }

        activeLocalIP = new NetUtils(appContext, NetUtils.LOG_METHOD_FILELOGGER).getDeviceIpAddressAsString_activeInterface();
        if (activeLocalIP == null || activeLocalIP.isEmpty() || activeLocalIP.equals("")) {
            Log.w(TAG, TAGG+"Unable to get device's active NIC's IP address, leaving empty for autodetect.");
            activeLocalIP = "";
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
        try {
            sipPrefsUsername = prefs.getString(String.valueOf(appContext.getResources().getText(R.string.spKeyName_sipUsername)), null);
            sipPrefsPassword = prefs.getString(String.valueOf(appContext.getResources().getText(R.string.spKeyName_sipPassword)), null);
            sipPrefsPort = prefs.getString(String.valueOf(appContext.getResources().getText(R.string.spKeyName_sipPort)), null);
            sipPrefsServer = prefs.getString(String.valueOf(appContext.getResources().getText(R.string.spKeyName_serverAddrIPv4)), null);
            sipPrefsRegistrationInterval = Integer.parseInt(prefs.getString(String.valueOf(appContext.getResources().getText(R.string.spKeyName_sipRegistrationInterval)), null));
            if (sipPrefsUsername == null
                    || sipPrefsPassword == null
                    || sipPrefsPort == null
                    || sipPrefsServer == null) {
                Log.e(TAG, TAGG + "some or all SIP account data unavailable!");
                Log.v(TAG, TAGG + "sipPrefsUsername = '" + sipPrefsUsername + "'.");
                Log.v(TAG, TAGG + "sipPrefsPassword = '" + sipPrefsPassword + "'.");
                Log.v(TAG, TAGG + "sipPrefsPort = '" + sipPrefsPort + "'.");
                Log.v(TAG, TAGG + "sipPrefsServer = '" + sipPrefsServer + "'.");
                Log.v(TAG, TAGG + "sipPrefsRegistrationInterval = '" + sipPrefsRegistrationInterval + "'.");
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }

        prefs = null;
    }

    /** SIP stack stuff... ************************************************************************/
    private void initializeSipStack() {
        final String TAGG = "initializeSipStack: ";

        try {
            sipStack = new SipStack();
            sipStack.Init(appContext);
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }
    }

    private boolean isSipStackInitialized() {
        final String TAGG = "isSipStackInitialized: ";
        boolean ret = true;

        if (sipStack == null) {
            ret = false;
        }

        Log.v(TAG, TAGG+"Returning "+ String.valueOf(ret)+".");
        return ret;
    }

    private void configureSipStack() {
        final String TAGG = "configureSipStack: ";

        if (!isSipStackInitialized()) {
            Log.e(TAG, TAGG+"SIP stack is not initialized. Aborting.");
            return;
        }

        // Stack characteristics...
        sipStack.SetParameter("ssidcode", activationCode);
        sipStack.SetParameter("loglevel", 1);                                                       //TODO: set this to 1 for production
        sipStack.SetParameter("logcat", true);
        //sipStack.SetParameter("cpualwayspartiallock", "true");                                    //always keep a partial lock on the CPU to prevent sleep (default is false)
        sipStack.SetParameter("runservice", 1);                                                     //optimize stack to run from a background service
        sipStack.SetParameter("serviceclassname", serviceName);

        // Network characteristics...
        //sipStack.SetParameter("transport", SIP_TRANSPORT_TCP);                                    //which transport to use
        sipStack.SetParameter("transport", SIP_TRANSPORT_UDP);                                      //which transport to use
        //sipStack.SetParameter("udptos", 10);                                                      //0=disabled, 1=auto/default (10-normal/disabled when tunnel), 2=low-cost, 4=reliable-routing, 8=throughput, 10=low-delay
        //sipStack.SetParameter("codecframecount", "0");                                            //0=2 for g729 1 otherwise (default)
        sipStack.SetParameter("localip", activeLocalIP);

        // Audio and quality characteristics...
        sipStack.SetParameter("plc", true);                                                         //enable packet loss cancellation (true by default)
        sipStack.SetParameter("aec", 2);                                                            //primary algorithm; 0=no, 1=yes but not w/headset (default), 2=yes if supported, 3=forced yes
        sipStack.SetParameter("aectype", "volume");                                                 //auto, none, software, hardware, fast, volume (software horrible, hardware not great, fast is decent, and volume is not bad)
        sipStack.SetParameter("aec2", 2);                                                           //secondary algorithm; 0=no, 1=auto(default), 2=yes, 3=yes w/extra
        sipStack.SetParameter("hardwaremedia", 2);                                                  //0=auto(default), 1=no, 2=yes(by far best working)
        sipStack.SetParameter("jittersize", 3);                                                     //0=no, 1=xsmall, 2=small, 3=normal (default), 4=big, 5=xbig, 6=max     (lower than 3 is BAD quality)
        sipStack.SetParameter("denoise", 1);
        sipStack.SetParameter("increasepriority", "true");                                          //increase priority for whole thread-group
        sipStack.SetParameter("stereomode", false);                                                 //WARNING: true causes RTP packets to drop and sound to stutter
        sipStack.SetParameter("vad", "2");                                                          //0=auto, 1=no, 2=yes for player (default) (helps w/jitter), 3=yes for recorder, 4=yes for both
        sipStack.SetParameter("silencesupress", 0);                                                 //-1=auto, 0=no/disabled, 1=yes     (disable to help with latency/delay) --generally not recommended to be enabled
        //sipStack.SetParameter("use_opusuwb", 3);
        //sipStack.SetParameter("prefcodec", PARAM_PREFCODEC_VALUE);
        //sipStack.SetParameter("codec", "opusuwb");
        sipStack.SetParameter("volumein", 100);
        sipStack.SetParameter("volumeout", 100);
        //sipStack.SetParameter("audiorecorder", 2);                                                //audio recorder stream; 0=default, 1=auto-guess, 2=mic
        sipStack.SetParameter("speakerphoneoutput", 2);
        sipStack.SetParameter("alwaysallowlowcodec", 2);

        // Answering and invite characteristics...
        sipStack.SetParameter("beeponconnect", 4);                                                  //beep to play on client; 1 for auto-accepted incoming calls / 2 for incoming calls / 4 for all calls (default is 0)
        sipStack.SetParameter("autoaccept", true);
        sipStack.SetParameter("playring", 0);       //0=no, 1=incoming, 2=both(default)
        sipStack.SetParameter("aspeakermode", 1);                                                   //speaker device to be used; 0=usually headphone (default), 1=headphone, 2=speakerphone
        //sipStack.SetParameter("autousebluetooth", "2");

        // Ringback and stuff
        sipStack.SetParameter("setfinalcodec", 0);
        //sipStack.SetParameter("use_fast_stun", 2);
        //sipStack.SetParameter("use_fast_ice", 2);
        //sipStack.SetParameter("use_rport", 2);
        //sipStack.SetParameter("changesptoring", 3);
        //sipStack.SetParameter("natopenpackets", 10);
        //sipStack.SetParameter("earlymedia", 3);
        //sipStack.SetParameter("nostopring", 1);
        //sipStack.SetParameter("ringincall", 2);

        // SIP server and registration characteristics...
        sipStack.SetParameter("serveraddress", sipPrefsServer+":"+sipPrefsPort);
        sipStack.SetParameter("signalingport", sipPrefsPort);
        sipStack.SetParameter("username", sipPrefsUsername);
        sipStack.SetParameter("password", sipPrefsPassword);
        sipStack.SetParameter("registerinterval", sipPrefsRegistrationInterval);
        sipStack.SetParameter("register", 0);                                                       //ensure we don't auto-register upon stack start (which can sometimes happen?)
    }

    private void startSipStack() {
        final String TAGG = "startSipStack: ";

        if (!isSipStackInitialized()) {
            Log.e(TAG, TAGG+"SIP stack is not initialized. Aborting.");
            return;
        }

        try {
            sipStack.Start();
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }
    }

    private void registerSipConnection() {
        final String TAGG = "registerSipConnection: ";

        if (!isSipStackInitialized()) {
            Log.e(TAG, TAGG+"SIP stack is not initialized. Aborting.");
            return;
        }

        try {
            sipStack.Register();
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }
    }

    /** Stuff for SIP monitoring process **********************************************************/
    private void initializeSipMonitorThread() {
        final String TAGG = "initializeSipMonitorThread: ";

        sipMonitorThread = new SipMonitorThread();
    }

    private void startSipMonitorThread() {
        final String TAGG = "startSipMonitorThread: ";

        if (sipMonitorThread == null) {
            Log.e(TAG, TAGG+"The sipMonitorThread instance is null. Aborting.");
            return;
        }

        sipMonitorThread.start();
    }

    private void stopSipMonitorThread() {
        final String TAGG = "stopSipMonitorThread: ";

        if (sipMonitorThread == null) {
            Log.e(TAG, TAGG+"The sipMonitorThread instance is null. Aborting.");
            return;
        }

        sipMonitorThread.interrupt();
    }

    class SipMonitorThread extends Thread {
        final String TAG = SipMonitorThread.class.getSimpleName();

        private int initialWaitPeriodMS;
        private int workCycleRestPeriodMS;

        private SipNotifications latestSipNotifications = null;                //for storing the latest batch of SIP notifications from the stack (just the above parsed into a nice organized object)
        private SipNotification latestRegularNotification = null;              //for storing the latest regular-type notification line
        private SipNotification latestGeneralStatusNotification = null;        //for storing the latest general-status notification line

        //private BannerMessage speakLiveMsg = null;    //TODO

        public SipMonitorThread() {
            initialWaitPeriodMS = 5000;
            workCycleRestPeriodMS = 100;
        }

        @Override
        public void run() {
            final String TAGG = "run: ";
            long cycleNumber = 0;

            String sipNotifications = null;

            Log.d(TAG, TAGG + "Invoked.");

            long pid = Thread.currentThread().getId();
            Log.i(TAG, TAGG + "Thread now running with process ID = " + pid);

            // As long as our thread is supposed to be running, start doing work-cycles until it's been flagged to interrupt (rest period happens at the end of the cycle)...
            while (!Thread.currentThread().isInterrupted()) {

                // Take a rest before beginning our first iteration (to give time for the things we're monitoring to potentially come online)...
                if (cycleNumber == 0) {
                    try {
                        //updateCurrentStatus(Thread.THREAD_STATUS_SLEEPING);
                        Thread.sleep(initialWaitPeriodMS);
                    } catch (InterruptedException e) {
                        //Log.e(TAG, TAGG + "Exception caught trying to sleep for first-run (" + workCycleRestPeriodMS + "ms). Broadcasting this error status and stopping.\n" + e.getMessage());
                        //updateCurrentStatus(Thread.THREAD_STATUS_SLEEP_ERROR);
                        Log.e(TAG, TAGG + "Exception caught trying to sleep for first-run (" + workCycleRestPeriodMS + "ms).\n" + e.getMessage());
                        Thread.currentThread().interrupt();
                    }
                }

                // if, for some reason, we don't have our sipStack object available, then no work at all we can do
                if (sipStack == null) {
                    continue;
                }

                // if, for some reason, we're not registered with a sip server, then no work we can really do that's worthwhile
                if (!sipStack.IsRegistered()) {
                    continue;
                }

                /* START MAIN THREAD-WORK
                 * Note: you don't need to exit or break for normal work; instead, only continue (so cleanup and rest can occur at the end of the iteration/cycle) */

                // Indicate that this thread is beginning work tasks (may be useful for outside processes to know this)
                cycleNumber++;
                //Log.v(TAG, TAGG + "=======================================(start)");
                //Log.v(TAG, TAGG + "BEGINNING WORK CYCLE #" + cycleNumber + "...");

                //sipNotifications = sipStack.GetNotificationsSync();
                sipNotifications = sipStack.GetNotifications();

                if (sipNotifications.length() > 0) {
                    //TODO: parse the strings and handle the events... adjust states or screen according to them as you wish (STATUS notification is most relevant)
                    //processNotifications_AJVoIP_method(sipNotifications);
                    processNotifications(sipNotifications);
                }

                /* END MAIN THREAD-WORK */

                // Take a rest before next iteration (to make sure this thread doesn't run full tilt)...
                try {
                    Thread.sleep(workCycleRestPeriodMS);
                } catch (InterruptedException e) {
                    Log.e(TAG, TAGG+"Exception caught trying to sleep for interval ("+workCycleRestPeriodMS+"ms). Thread stopping.\n" + e.getMessage());
                    Thread.currentThread().interrupt();
                }

                // Thread has been flagged to interrupt, so let's end it and clean up...
                if (Thread.currentThread().isInterrupted()) {
                    Log.d(TAG, TAGG+"Stopping.");    //just log it, as the loop's conditional will break out and actually halt the thread
                }

            }//end while
        }//end run()

        void processNotifications(String notifications) {
            final String TAGG = "processNotifications: ";
            Log.v(TAG, TAGG+"Invoked.");

            latestSipNotifications = new SipNotifications(notifications);                           //reinitialize our data store object with that latest notifications data

            if (latestSipNotifications.getNumberOfSipNotifications() > 0) {

                //get the latest GENERAL-STATUS type of notification (since there could be more than one general-status type of notification since we last checked -- unlikely, but possible)...
                //(note: these usually give us just basic news about what's going on with the SIP stack)
                latestGeneralStatusNotification = latestSipNotifications.getSipNotification_latestGeneralStatus();
                if (latestGeneralStatusNotification != null) {
                    if (latestGeneralStatusNotification.param_statusText.contains(SipNotification.GENERAL_STATUS_NOTIF_STATUSTEXT_SPEAKING)) {
                        //NOTE: this will fire every 8 seconds while a call is in-progress!
                        Log.i(TAG, TAGG + "Call is in progress.");
                        if (showSpeakingScrollingMessage) {
                            Log.i(TAG, TAGG + "Call is in progress. Displaying speak-live message.");
                            //SmmonService.showOSA(context, "A Message is Being Spoken", 0);
                            /* TODO
                            if (speakLiveMsg == null) {
                                //since this could repeat, we need to only do it if isn't already doing it
                                speakLiveMsg = MessageNetUtils.generatePredefinedMessage(appContext, MessageNetUtils.PREDEFINEDMSG_SPEAK_LIVE);   //get a speak-live predefined message
                                //SmmonService.activeBannerMessages.addMessage_fromJSON(speakLiveMsg.exportJSONObject());   //TODO - REPLACING WITH BELOW...
                                //MessageNetUtils.deliverPredefinedScrollingMessage(context, speakLiveMsg, false);
                            }
                            MessageNetUtils.deliverPredefinedScrollingMessage(appContext, speakLiveMsg, false);
                            */
                        }
                    } else if (latestGeneralStatusNotification.param_statusText.contains(SipNotification.GENERAL_STATUS_NOTIF_STATUSTEXT_CALLFINISHED)) {
                        Log.i(TAG, TAGG + "Call is finished.");
                        if (showSpeakingScrollingMessage) {
                            Log.i(TAG, TAGG + "Call is finished. Commanding take-down of speak-live message.");
                            //SmmonService.hideOSA(context, true);
                            /* TODO
                            if (speakLiveMsg != null) {
                                //SmmonService.activeBannerMessages.removeMessage_byUUID(speakLiveMsg.uuid_local);  //TODO - replacing with below...
                                MessageNetUtils.finishActivityScrollPredefinedMsg(appContext);
                                SmmonService.hideOSA(appContext, true);
                                speakLiveMsg = null;    //reinit
                            }
                            */
                        }
                    }
                }//end general-status notif processing

                //get the latest REGULAR type of notification
                //(note: these usually give us more details about what's going on with the SIP stack)
                latestRegularNotification = latestSipNotifications.getSipNotification_latest();
                if (latestRegularNotification != null) {
                    if (latestRegularNotification.param_statusText.contains(SipNotification.INDIVIDUAL_LINE_NOTIF_STATUS_TEXT_CALLSETUP)) {
                        Log.i(TAG, TAGG + "Call is beginning (from "+latestRegularNotification.param_peerDisplayname+").");
                        //SmmonService.showOSA(appContext, "Incoming Spoken Message from "+latestRegularNotification.param_peerDisplayname, 2*1000);    TODO
                    } else if (latestRegularNotification.param_statusText.contains(SipNotification.INDIVIDUAL_LINE_NOTIF_STATUS_TEXT_CALLCONNECT)) {
                        Log.i(TAG, TAGG + "Call was just connected.");
                    } else if (latestRegularNotification.param_statusText.contains(SipNotification.INDIVIDUAL_LINE_NOTIF_STATUS_TEXT_CALLDISCONNECT)) {
                        Log.i(TAG, TAGG + "Call was just disconnected.");
                        //SmmonService.hideOSA(appContext, true);   TODO
                    }
                }//end regular notif processing

            }
        }

        void processNotifications_AJVoIP_method(String receivednot) {
            final String TAGG = "processNotifications_AJVoIP_method: ";
            Log.v(TAG, TAGG+"Invoked.");

            if (receivednot == null || receivednot.length() < 1) return;

            // we can receive multiple notifications at once, so we split them by CRLF or with ",NEOL \r\n" and we end up with a String array of notifications
            String[] notarray = receivednot.split(",NEOL \n");
            for (int i = 0; i < notarray.length; i++) {
                String notifywordcontent = notarray[i];

                if (notifywordcontent == null || notifywordcontent.length() < 1) continue;

                notifywordcontent = notifywordcontent.trim();
                notifywordcontent = notifywordcontent.replace("WPNOTIFICATION,", "");

                // now we have a single notification in the "notifywordcontent" String variable
                Log.v(TAG, TAGG+"Received Notification:\n" + notifywordcontent);

                int pos = 0;
                String notifyword1 = ""; //will hold the notification type
                String notifyword2 = ""; //will hold the second most important String in the STATUS notifications, which is the third parameter, right after the "line' parameter

                // First we are checking the first parameter (until the first comma) to determine the event type.
                // Then we will check for the other parameters.
                pos = notifywordcontent.indexOf(",");
                if(pos > 0) {
                    notifyword1 = notifywordcontent.substring(0, pos).trim();
                    notifywordcontent = notifywordcontent.substring(pos+1, notifywordcontent.length()).trim();
                } else {
                    notifyword1 = "EVENT";
                }

                // Notification type, "notifyword1" can have many values, but the most important ones are the STATUS types.
                // After each call, you will receive a CDR (call detail record). We can parse this to get valuable information about the latest call.
                // CDR,line, peername,caller, called,peeraddress,connecttime,duration,discparty,reasontext
                // Example: CDR,1, 112233, 445566, 112233, voip.mizu-voip.com, 5884, 1429, 2, bye received
                if (notifyword1.equals("CDR")) {
                    String[] cdrParams = notifywordcontent.split(",");
                    String line = cdrParams[0];
                    String peername = cdrParams[1];
                    String caller = cdrParams[2];
                    String called = cdrParams[3];
                    String peeraddress = cdrParams[4];
                    String connecttime = cdrParams[5];
                    String duration = cdrParams[6];
                    String discparty = cdrParams[7];
                    String reasontext = cdrParams[8];
                }
                // lets parse a few STATUS notifications
                else if(notifyword1.equals("STATUS")) {
                    //ignore line number. we are not handling it for now
                    pos = notifywordcontent.indexOf(",");
                    if(pos > 0) notifywordcontent = notifywordcontent.substring(pos+1, notifywordcontent.length()).trim();
                    pos = notifywordcontent.indexOf(",");
                    if(pos > 0) {
                        notifyword2 = notifywordcontent.substring(0, pos).trim();
                        notifywordcontent = notifywordcontent.substring(pos+1, notifywordcontent.length()).trim();
                    } else {
                        notifyword2 = notifywordcontent;
                    }

                    if (notifyword2.equals("Registered.")) {
                        // means the SDK is successfully registered to the specified VoIP server
                        Log.i(TAG, TAGG+"Registered.");
                    } else if (notifyword2.equals("CallSetup")) {
                        // a call is in the setup stage
                        Log.i(TAG, TAGG+"Call is setting up.");
                    } else if (notifyword2.equals("Ringing")) {
                        // check the other parameters to see if it an incoming call and display an alert for the user
                        Log.i(TAG, TAGG+"Ringing.");
                    } else if (notifyword2.equals("CallConnect")) {
                        // call was just connected
                        Log.i(TAG, TAGG+"Call just connected.");
                        //SmmonService.showOSA(appContext, "Incoming Spoken Message from "+latestRegularNotification.param_peerDisplayname, 2*1000);    TODO
                    } else if (notifyword2.equals("CallDisconnect")) {
                        // call was just disconnected
                        Log.i(TAG, TAGG+"Call just disconnected.");
                    } else if (notifyword1.equals("CHAT")) {
                        // we received an incoming chat message (parse the other parameters to get the sender name and the text to be displayed)
                    }
                }
                else if(notifyword1.equals("ERROR")) {
                    // we received an error notification; at least log it somewhere
                    Log.e(TAG, TAGG+"ERROR," + notifywordcontent);
                }
                else if(notifyword1.equals("WARNING")) {
                    // we received a warning notification; at least log it somewhere
                    Log.w(TAG, TAGG+"WARNING," + notifywordcontent);
                }
                else if(notifyword1.equals("EVENT")) {
                    // display important event for the user
                    Log.v(TAG, TAGG+notifywordcontent);
                }
            }
        }
    }//end subclass

    /** SipNotification data-storage/organization class for a SINGLE notification returned by SipStack.GetNotifications.
     * "Notifications" is the term used to describe SIP messages/traffic or requests coming into the stack.
     * Note: The stack's GetNotifications method returns a single large string with all notifications delimited by \r\n.
     * The idea here is to containerize an ArrayList (private notificationsList) and use setters/getters on it. */
    class SipNotification {
        private String TAGG = "SipNotification: ";

        //constants
        public static final String EVENT_TYPE_START = "START";
        public static final String EVENT_TYPE_EVENT = "EVENT";
        public static final String EVENT_TYPE_STATUS = "STATUS";
        public static final String EVENT_TYPE_CDR = "CDR";
        public static final String GENERAL_STATUS_NOTIFICATION = "-1";
        public static final String GENERAL_STATUS_NOTIF_STATUSTEXT_READY = "Ready";
        public static final String GENERAL_STATUS_NOTIF_STATUSTEXT_REGISTER = "Register...";
        public static final String GENERAL_STATUS_NOTIF_STATUSTEXT_REGISTERING = "Registering...";  //or "Register..."
        public static final String GENERAL_STATUS_NOTIF_STATUSTEXT_REGISTERFAILED = "Register Failed";
        public static final String GENERAL_STATUS_NOTIF_STATUSTEXT_REGISTERED = "Registered";       //or "Registered."
        public static final String GENERAL_STATUS_NOTIF_STATUSTEXT_UNREGISTERED = "Unregistered";
        public static final String GENERAL_STATUS_NOTIF_STATUSTEXT_ACCEPT = "Accept";
        public static final String GENERAL_STATUS_NOTIF_STATUSTEXT_STARTINGCALL = "Starting Call";
        public static final String GENERAL_STATUS_NOTIF_STATUSTEXT_CALL = "Call";
        public static final String GENERAL_STATUS_NOTIF_STATUSTEXT_CALLINITIATED = "Call Initiated";
        public static final String GENERAL_STATUS_NOTIF_STATUSTEXT_CALLING = "Calling...";
        public static final String GENERAL_STATUS_NOTIF_STATUSTEXT_RINGING = "Ringing...";
        public static final String GENERAL_STATUS_NOTIF_STATUSTEXT_INCOMING = "Incoming...";
        public static final String GENERAL_STATUS_NOTIF_STATUSTEXT_INCALL = "In Call";              //actually... "In Call (xxx sec)"       WARNING: may not actually be in the stack, documentation error?
        public static final String GENERAL_STATUS_NOTIF_STATUSTEXT_SPEAKING = "Speaking";           //actually... "Speaking (xxx sec)"      NOTE: This works, above does not!
        public static final String GENERAL_STATUS_NOTIF_STATUSTEXT_HANGUP = "Hangup";
        public static final String GENERAL_STATUS_NOTIF_STATUSTEXT_CALLFINISHED = "Call Finished";
        public static final String GENERAL_STATUS_NOTIF_STATUSTEXT_CHAT = "Chat";
        public static final String INDIVIDUAL_LINE_NOTIF_STATUS_TEXT_UNKNOWN = "Unknown";           //you should not receive this
        public static final String INDIVIDUAL_LINE_NOTIF_STATUS_TEXT_INIT = "Init";                 //voip library started
        public static final String INDIVIDUAL_LINE_NOTIF_STATUS_TEXT_READY = "Ready";               //sip stack started
        public static final String INDIVIDUAL_LINE_NOTIF_STATUS_TEXT_OUTBAND = "Outband";           //notify/options/etc. you should skip this
        public static final String INDIVIDUAL_LINE_NOTIF_STATUS_TEXT_REGISTER = "Register";         //from register endpoints (or "Register..." or "Registering")
        public static final String INDIVIDUAL_LINE_NOTIF_STATUS_TEXT_UNREGISTER = "Unregister";
        public static final String INDIVIDUAL_LINE_NOTIF_STATUS_TEXT_SUBSCRIBE = "Subscribe";       //presence
        public static final String INDIVIDUAL_LINE_NOTIF_STATUS_TEXT_CHAT = "Chat";                 //IM
        public static final String INDIVIDUAL_LINE_NOTIF_STATUS_TEXT_CALLSETUP = "CallSetup";       //one time event: call begin
        public static final String INDIVIDUAL_LINE_NOTIF_STATUS_TEXT_SETUP = "Setup";               //call init
        public static final String INDIVIDUAL_LINE_NOTIF_STATUS_TEXT_INPROGRESS = "InProgress";     //call init
        public static final String INDIVIDUAL_LINE_NOTIF_STATUS_TEXT_ROUTED = "Routed";             //call init
        public static final String INDIVIDUAL_LINE_NOTIF_STATUS_TEXT_RINGING = "Ringing";           //SIP 180 received or similar
        public static final String INDIVIDUAL_LINE_NOTIF_STATUS_TEXT_CALLCONNECT = "CallConnect";   //one time event: call was just connected
        public static final String INDIVIDUAL_LINE_NOTIF_STATUS_TEXT_INCALL = "InCall";             //call is connected
        public static final String INDIVIDUAL_LINE_NOTIF_STATUS_TEXT_MUTED = "Muted";               //connected call in muted status
        public static final String INDIVIDUAL_LINE_NOTIF_STATUS_TEXT_HOLD = "Hold";                 //connected call in hold status
        public static final String INDIVIDUAL_LINE_NOTIF_STATUS_TEXT_SPEAKING = "Speaking";         //call is connected
        public static final String INDIVIDUAL_LINE_NOTIF_STATUS_TEXT_MIDCALL = "Midcall";           //might be received for transfer, conference, etc. you should treat it like the Speaking status
        public static final String INDIVIDUAL_LINE_NOTIF_STATUS_TEXT_CALLDISCONNECT = "CallDisconnect";//one time event: call was just disconnected
        public static final String INDIVIDUAL_LINE_NOTIF_STATUS_TEXT_FINISHING = "Finishing";       //call is about to be finished. Disconnect message sent: BYE, CANCEL or 400-600 code
        public static final String INDIVIDUAL_LINE_NOTIF_STATUS_TEXT_FINISHED = "Finished";         //call is finished. ACK or 200 OK was received or timeout
        public static final String INDIVIDUAL_LINE_NOTIF_STATUS_TEXT_DELETABLE = "Deletable";       //endpoint is about to be destroyed. You should skip this
        public static final String INDIVIDUAL_LINE_NOTIF_STATUS_TEXT_ERROR = "Error";               //you should not receive this

        //positions and delimiters of the fields in the string
        private static final String PARAM_DELIMITER = ",";
        private static final int PARAM_EVENT_TYPE = 0;
        private static final int PARAM_LINE_NUMBER = 1;
        private static final int PARAM_STATUS_LINE = 2;
        private static final int PARAM_PEER_NAME = 3;
        private static final int PARAM_LOCAL_NAME = 4;
        private static final int PARAM_ENDPOINT_TYPE = 5;
        private static final int PARAM_PEER_DISPLAYNAME = 6;
        private static final int PARAM_CALL_ID = 7;
        private static final int PARAM_ONLINE = 8;
        private static final int PARAM_REGISTERED = 9;
        private static final int PARAM_IN_CALL = 10;
        private static final int PARAM_MUTE = 11;
        private static final int PARAM_HOLD = 12;
        private static final int PARAM_ENCRYPTED = 13;
        private static final int PARAM_IS_VIDEO = 14;

        //data-storage objects
        public String param_eventType = "";
        public String param_lineNumber = "";
        public String param_statusText = "";
        public String param_peerName = "";
        public String param_localName = "";
        public String param_endpointType = "";
        public String param_peerDisplayname = "";
        public String param_callID = "";
        public String param_online = "";
        public String param_registered = "";
        public String param_inCall = "";
        public String param_mute = "";
        public String param_hold = "";
        public String param_encrypted = "";
        public String param_isVideo = "";

        public String[] splitParams = null;

        /** Constructor */
        public SipNotification(String notifLine) {
            Log.d(TAG, TAGG+"Constructing a SipNotification instance with data: "+notifLine);
            initializeLocalData(notifLine);      //initialize instance data
        }

        /** Initialize local instance data. */
        private void initializeLocalData(String notifLine) {
            int i;
            this.splitParams = splitTheParameters(notifLine);

            if (splitParams.length > 0) {
                //there are always at least two parameters, so just blindly populate them now...
                this.param_eventType = this.splitParams[PARAM_EVENT_TYPE];
                this.param_lineNumber = this.splitParams[PARAM_LINE_NUMBER];
                i = 2;

                //populate any additional parameters...
                if (i < splitParams.length) {this.param_statusText = this.splitParams[PARAM_STATUS_LINE]; i++;}
                if (i < splitParams.length) {this.param_peerName = this.splitParams[PARAM_PEER_NAME]; i++;}
                if (i < splitParams.length) {this.param_localName = this.splitParams[PARAM_LOCAL_NAME]; i++;}
                if (i < splitParams.length) {this.param_endpointType = this.splitParams[PARAM_ENDPOINT_TYPE]; i++;}
                if (i < splitParams.length) {this.param_peerDisplayname = this.splitParams[PARAM_PEER_DISPLAYNAME]; i++;}
                if (i < splitParams.length) {this.param_callID = this.splitParams[PARAM_CALL_ID]; i++;}
                if (i < splitParams.length) {this.param_online = this.splitParams[PARAM_ONLINE]; i++;}
                if (i < splitParams.length) {this.param_registered = this.splitParams[PARAM_REGISTERED]; i++;}
                if (i < splitParams.length) {this.param_inCall = this.splitParams[PARAM_IN_CALL]; i++;}
                if (i < splitParams.length) {this.param_mute = this.splitParams[PARAM_MUTE]; i++;}
                if (i < splitParams.length) {this.param_hold = this.splitParams[PARAM_HOLD]; i++;}
                if (i < splitParams.length) {this.param_encrypted = this.splitParams[PARAM_ENCRYPTED]; i++;}
                if (i < splitParams.length) {this.param_isVideo = this.splitParams[PARAM_IS_VIDEO]; i++;}
            } else {
                Log.w(TAG, TAGG+"initializeLocalData: No detectable notification parameters to split in \""+notifLine+"\".");
            }
        }

        /** Splits the long string of possibly many parameters into an array of single-parameter strings. */
        private String[] splitTheParameters(String notifLine) {
            String[] result = null;
            try {
                result = notifLine.split(PARAM_DELIMITER);
            } catch (Exception e) {
                Log.e(TAG, TAGG+"splitTheParameters: Exception caught trying to split the string of notification(s). Retuning null.\n"+e.getMessage());
            }
            return result;
        }
    }

    class SipNotifications extends ArrayList<SipNotification> {
        private String TAGG = "SipNotifications: ";

        private String rawNotifications;
        private ArrayList<SipNotification> notificationsList = new ArrayList<SipNotification>();
        private int numberOfSipNotifications = 0;
        private int indexOfLastSipNotification = 0;

        /** Constructor */
        public SipNotifications(String rawNotifs) {
            if (rawNotifs.length() > 0) {
                Log.d(TAG, TAGG+"Constructing a SipNotifications instance.");
                initializeLocalData(rawNotifs);      //initialize instance data
            } else {
                Log.w(TAG, TAGG+"No data provided, aborting!");
                return;
            }
        }

        /** Initialize local instance data. */
        private void initializeLocalData(String rawNotifs) {
            this.rawNotifications = rawNotifs;     //store aside a local copy of the raw data from the Mizu SipStack.GetNotifications() method
            try {
                parseAndPopulateNotificationLines(rawNotifs);

                numberOfSipNotifications = notificationsList.size();
                Log.v(TAG, TAGG+"numberOfSipNotifications = "+numberOfSipNotifications);

                indexOfLastSipNotification = numberOfSipNotifications - 1;
                Log.v(TAG, TAGG+"indexOfLastSipNotification = "+indexOfLastSipNotification);
            } catch (Exception e) {
                Log.e(TAG, TAGG+"initializeLocalData: Exception caught:\n"+e.getMessage());
            }
        }

        /** Populate the local ArrayList of SipNotification objects from the raw String data. */
        private void parseAndPopulateNotificationLines(String rawNotifs) {
            SipNotification sipNotification;

            //parse all lines (all notifications) into a String array
            //Log.v(TAG, TAGG+"parseAndPopulateNotificationLines: rawNotifs contents:\n"+rawNotifs);
            String[] lines_in = rawNotifs.split("\\r?\\n");

            //for each element in that String array...
            for (String line : lines_in) {
                Log.v(TAG, TAGG+"parseAndPopulateNotificationLines: Instantiating SipNotification object for:\n"+line);

                //create a SipNotification object
                sipNotification = new SipNotification(line);

                //add that object to our private class-local ArrayList
                notificationsList.add(sipNotification);
            }
        }

        /** Getter methods... */
        public int getNumberOfSipNotifications() {
            return numberOfSipNotifications;
        }
        public int getIndexOfLastSipNotification() {
            return indexOfLastSipNotification;
        }
        public SipNotification getSipNotification_latestGeneralStatus() {
            SipNotification thisNotification;
            try {
                for (int i = indexOfLastSipNotification; i >= 0; i--) {
                    thisNotification = notificationsList.get(i);
                    if (thisNotification.param_lineNumber.equals(SipNotification.GENERAL_STATUS_NOTIFICATION)) {
                        return thisNotification;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "getLatestGeneralStatusNotification: Exception caught trying to return SipNotification (returning null):\n"+e.getMessage());
            }
            return null;
        }
        public SipNotification getSipNotification_latest() {
            try {
                return notificationsList.get(getIndexOfLastSipNotification());
            } catch (Exception e) {
                Log.w(TAG, "getSipNotification_latest: Exception caught trying to return SipNotification (returning null):\n"+e.getMessage());
            }
            return null;
        }
    }//end class SipNotifications
}
