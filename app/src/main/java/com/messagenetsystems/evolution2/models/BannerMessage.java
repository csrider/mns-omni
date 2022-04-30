package com.messagenetsystems.evolution2.models;

/* BannerMessage
 *
 * Class for a single banner message.
 * Instantiate with a server-generated JSON object representing a message, to create.
 *
 * 2017.07.17   Chris Rider     Created originally as a subclass in SmmonService.
 * 2017.07.25   Chris Rider     Migrated to this dedicated class file. Tested to work OK.
 * 2017.11.22   Chris Rider     Added field: dbb_page_priority_at_launch.
 * 2017.12.12   Chris Rider     Added field for a local-use UUID (just in case a ZX recno is ever re-used to avoid any chance for confusion).
 * 2018.03.05   Chris Rider     Refactored to make sure values are initialized and handled better.
 * 2018.03.08   Chris Rider     Added field: dbb_priority_tolerance.
 * 2018.04.03   Chris Rider     Added field: videoSeekPosition to save (in +seconds) where a video left-off playing.
 * 2019.04.25   Chris Rider     Added field: isReadyToDeliver to initially support delayed-delivery of video message after they download to device.
 * 2019.05.10   Chris Rider     Added field: dsi_audio_group_name to store audio groups this device is a member of (sent during message launch to this device).
 *                              (this also means that dbb_audio_groups contains the message's audio groups now)
 * 2019.07.11   Chris Rider     Added fields: hasAudioComponent and isAudioComponentActive for initially dealing with recorded-PA messages.
 * 2019.07.25   Chris Rider     Added isNumeric method and better handling of non-numeric dbb_audio_repeats value.
 * 2019.07.26   Chris Rider     Added fields for dbb_launch_pin and dss_gender.
 * 2019.12.05   Chris Rider     Migrated to v2 app.
 * 2019.12.17   Chris Rider     Added compatibility methods for returning data that works with OmniMessage.
 *                              Fixed private members not printing to log; restricting certain members from log.
 */

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.R;
import com.messagenetsystems.evolution2.utilities.DatetimeUtils;
import com.messagenetsystems.evolution2.utilities.PlatformUtilsMessageNet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.UUID;

public class BannerMessage {
    private static final String TAG = BannerMessage.class.getSimpleName();

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
    private String BANNMSGFIELDNAME_JSON_ZXRECNO;
    private String BANNMSGFIELDNAME_JSON_RECDTSEC;
    private String BANNMSGFIELDNAME_JSON_DURATION;
    private String BANNMSGFIELDNAME_JSON_MSGTYPE;
    private String BANNMSGFIELDNAME_JSON_MSGTEXT;
    private String BANNMSGFIELDNAME_JSON_MSGDETAILS;
    private String BANNMSGFIELDNAME_JSON_PLAYTIMEDURATION;
    private String BANNMSGFIELDNAME_JSON_FLASHERDURATION;
    private String BANNMSGFIELDNAME_JSON_LIGHTSIGNAL;
    private String BANNMSGFIELDNAME_JSON_LIGHTDURATION;
    private String BANNMSGFIELDNAME_JSON_AUDIOTTSGAIN;
    private String BANNMSGFIELDNAME_JSON_FLASHNEWMESSAGE;
    private String BANNMSGFIELDNAME_JSON_VISIBLETIME;
    private String BANNMSGFIELDNAME_JSON_VISIBLEFREQUENCY;
    private String BANNMSGFIELDNAME_JSON_VISIBLEDURATION;
    private String BANNMSGFIELDNAME_JSON_RECORDVOICEATLAUNCHSELECTION;
    private String BANNMSGFIELDNAME_JSON_RECORDVOICEATLAUNCH;
    private String BANNMSGFIELDNAME_JSON_AUDIORECORDEDGAIN;
    private String BANNMSGFIELDNAME_JSON_PADELIVERYMODE;
    private String BANNMSGFIELDNAME_JSON_AUDIOREPEAT;
    private String BANNMSGFIELDNAME_JSON_SPEED;
    private String BANNMSGFIELDNAME_JSON_PRIORITY;
    private String BANNMSGFIELDNAME_JSON_EXPIREPRIORITY;
    private String BANNMSGFIELDNAME_JSON_PRIORITYDURATION;
    private String BANNMSGFIELDNAME_JSON_PRIORITYATLAUNCH;
    private String BANNMSGFIELDNAME_JSON_PRIORITYTOLERANCE;
    private String BANNMSGFIELDNAME_JSON_MULTIMEDIATYPE;
    private String BANNMSGFIELDNAME_JSON_WEBPAGEURL;
    private String BANNMSGFIELDNAME_JSON_AUDIOGROUPS_HW;
    private String BANNMSGFIELDNAME_JSON_AUDIOGROUPS;
    private String BANNMSGFIELDNAME_JSON_MMAUDIOGAIN;
    //private String BANNMSGFIELDNAME_JSON_MMREPLAYS;
    private String BANNMSGFIELDNAME_JSON_SEQNUM;
    private String BANNMSGFIELDNAME_JSON_LAUNCHPIN;
    private String BANNMSGFIELDNAME_JSON_LAUNCHGENDER;
    private String JSONFIELDNAME_LAUNCHDATETIME;
    private String JSONFIELDNAME_LAUNCHDATETIME_DEVICE;
    private String JSONFIELDNAME_EXPIREDATETIME;
    private String JSONFIELDNAME_EXPIREDATETIME_DEVICE;
    private String JSONFIELDNAME_AUDIOREPEATSREMAINING;
    private String JSONFIELDNAME_UUIDLOCAL;
    private String JSONFIELDNAME_DELIVERYCOUNT;
    private String JSONFIELDNAME_LASTDELIVEREDDATETIME;
    private String JSONFIELDNAME_ISBEINGDELIVERED;
    private String JSONFIELDNAME_SCROLLCOUNT;
    private String JSONFIELDNAME_VIDEOSEEKPOSITION;

    public String MM_TYPE_NONE;
    public String MM_TYPE_MESSAGE;
    public String MM_TYPE_MESSAGEFS;
    public String MM_TYPE_WEBPAGE;
    public String MM_TYPE_VIDEO;
    public String MM_TYPE_PICTURE;
    public String MM_TYPE_PICTURETEXT;
    public String MM_TYPE_WEBMEDIA;
    public String MM_TYPE_LCDLOCATIONMAP;
    public String MM_TYPE_GEOLOCATIONMAP;
    public String MM_TYPE_RTSPSTREAM;

    public String PA_AUDIO_REPEAT_NONSTOP;

    private String SIGNALLIGHT_CMD_NONE;
    private String SIGNALLIGHT_CMD_OFF;
    private String SIGNALLIGHT_CMD_STANDBY;
    private String SIGNALLIGHT_CMD_BLUE_DIM;
    private String SIGNALLIGHT_CMD_BLUE_MED;
    private String SIGNALLIGHT_CMD_BLUE_BRI;
    private String SIGNALLIGHT_CMD_GREEN_DIM;
    private String SIGNALLIGHT_CMD_GREEN_MED;
    private String SIGNALLIGHT_CMD_GREEN_BRI;
    private String SIGNALLIGHT_CMD_ORANGE_DIM;
    private String SIGNALLIGHT_CMD_ORANGE_MED;
    private String SIGNALLIGHT_CMD_ORANGE_BRI;
    private String SIGNALLIGHT_CMD_PINK_DIM;
    private String SIGNALLIGHT_CMD_PINK_MED;
    private String SIGNALLIGHT_CMD_PINK_BRI;
    private String SIGNALLIGHT_CMD_PURPLE_DIM;
    private String SIGNALLIGHT_CMD_PURPLE_MED;
    private String SIGNALLIGHT_CMD_PURPLE_BRI;
    private String SIGNALLIGHT_CMD_RED_DIM;
    private String SIGNALLIGHT_CMD_RED_MED;
    private String SIGNALLIGHT_CMD_RED_BRI;
    private String SIGNALLIGHT_CMD_WHITECOOL_DIM;
    private String SIGNALLIGHT_CMD_WHITECOOL_MED;
    private String SIGNALLIGHT_CMD_WHITECOOL_BRI;
    private String SIGNALLIGHT_CMD_WHITEPURE_DIM;
    private String SIGNALLIGHT_CMD_WHITEPURE_MED;
    private String SIGNALLIGHT_CMD_WHITEPURE_BRI;
    private String SIGNALLIGHT_CMD_WHITEWARM_DIM;
    private String SIGNALLIGHT_CMD_WHITEWARM_MED;
    private String SIGNALLIGHT_CMD_WHITEWARM_BRI;
    private String SIGNALLIGHT_CMD_YELLOW_DIM;
    private String SIGNALLIGHT_CMD_YELLOW_MED;
    private String SIGNALLIGHT_CMD_YELLOW_BRI;
    private String SIGNALLIGHT_CMD_FADING_BLUE;
    private String SIGNALLIGHT_CMD_FADING_GREEN;
    private String SIGNALLIGHT_CMD_FADING_ORANGE;
    private String SIGNALLIGHT_CMD_FADING_PINK;
    private String SIGNALLIGHT_CMD_FADING_PURPLE;
    private String SIGNALLIGHT_CMD_FADING_RED;
    private String SIGNALLIGHT_CMD_FADING_WHITECOOL;
    private String SIGNALLIGHT_CMD_FADING_WHITEPURE;
    private String SIGNALLIGHT_CMD_FADING_WHITEWARM;
    private String SIGNALLIGHT_CMD_FADING_YELLOW;
    private String SIGNALLIGHT_CMD_FLASHING_BLUE;
    private String SIGNALLIGHT_CMD_FLASHING_GREEN;
    private String SIGNALLIGHT_CMD_FLASHING_ORANGE;
    private String SIGNALLIGHT_CMD_FLASHING_PINK;
    private String SIGNALLIGHT_CMD_FLASHING_PURPLE;
    private String SIGNALLIGHT_CMD_FLASHING_RED;
    private String SIGNALLIGHT_CMD_FLASHING_WHITECOOL;
    private String SIGNALLIGHT_CMD_FLASHING_WHITEPURE;
    private String SIGNALLIGHT_CMD_FLASHING_WHITEWARM;
    private String SIGNALLIGHT_CMD_FLASHING_YELLOW;

    // Initialize fields...
    // Note: these are public so they can be easily logged if necessary.
    public int recno = 0;
    public String dbb_rec_dtsec = String.valueOf(new Date().getTime()/1000);
    public int dbb_duration = 0;
    public String msgType = "";
    public String msgText = "";
    public String msgDetails = "";
    public Long dbb_playtime_duration = Long.getLong("15"); //in seconds
    public int dbb_flasher_duration = 0;
    public String dbb_light_signal = "";
    public int dbb_light_duration = 0;
    public int dbb_audio_tts_gain = 0;
    public String dbb_flash_new_message = "";
    public String dbb_visible_time = "";
    public String dbb_visible_frequency = "";
    public String dbb_visible_duration = "";
    public int dbb_record_voice_at_launch_selection;        //NOTE! This is for speaking message text as help during the phone call --not applicable to Omni !!!!
    public String dbb_record_voice_at_launch = "";          //NOTE! This is the flag for whether the message has recorded voice to play (but only pre-launch.. server won't give us actual value, so IGNORE THIS ON OMNI!
    public int dbb_audio_recorded_gain = 0;
    public String dbb_pa_delivery_mode = "";                //NOTE! Instead of the above, THIS is the field for determining record_voice_at_launch state for a message
    public String dbb_audio_repeat = "0";
    public int dbb_speed = 0;
    public int dbb_priority = 0;                            //this is the defined priority - it might be overridden at launch, so best not to depend on it
    public int dbb_expire_priority = 0;
    public Long dbb_priority_duration = null;
    public int dbb_page_priority_at_launch = 0;             //this is the actual launch-priority - whether defined and left alone or defined and updated at launch
    public int dbb_priority_tolerance = 0;
    public String multimediatype = "";
    public String webpageurl = "";
    public JSONArray dsi_audio_group_name = null;           //this is a list of audio groups that this Omni device is a member of (current as of message launch, so we don't have to reacquire TFTP config file or something)
    public JSONArray dbb_audio_groups = null;               //this is the audio groups selected for the message
    public int dbb_multimedia_audio_gain = 0;
    //public String dbb_replay_media;
    public int sequence_number = 0;
    public String dbb_launch_pin;                           //PIN of the Connections user who launched the message
    public String dss_gender;                               //launcher's gender (from staff database)

    public Date dtLaunch;                           //date-time of the message launch (based on dbb_rec_dtsec, a server-derived time value)
    public Date dtLaunch_device;                    //date-time of the message launch (based on the local device time when we received the launch request from the server)
    public Date dtExpiration;                       //date-time of the message expiration (based on dtLaunch, an ultimately server-derived time value)
    public Date dtExpiration_device;                //date-time of the message expiration (based on dtLaunch_device, an ultimately device-derived time value)
    public int audioRepeatsRemaining;               //number of audio repeats remaining (should start out same as dbb_audio_repeat, then decrement each time spoken)
    public UUID uuid_local;                         //unique identifier used internally to distinguish discrete messages (since ZX recno could theoretically be re-used)
    public int deliveryCount;                       //number of times this message has been delivered so far
    public Date dtLastDelivered;                    //date-time of the most recent delivery
    public boolean isBeingDelivered;                //flag for when a message is trying to get delivered (this is intended to beat any slow activity flags -2018.02.21) NOTE: could be multiple active flags!
    public int scrollCount = 0;                     //for scrolling messages only, counter for scrolls
    public int videoSeekPosition = 0;               //for video messages only, seconds seek position
    public boolean isReadyToDeliver = true;         //initially to allow video messages to finish downloading before they actually deliver on-screen
    public boolean hasAudioComponent = false;       //initially to flag whether recorded-PA exists for the message (determined by whether wav file exists on server for recno/dtsec)
    public boolean isAudioComponentActive = false;  //initially to flag whether recorded-PA is downloading/playing


    /** Constructor */
    public BannerMessage(Context context, int logMethod, JSONObject msg) {
        Log.v(TAG, "Creating a BannerMessage instance for JSON: "+msg.toString());

        this.logMethod = logMethod;

        Resources resources = context.getResources();
        DatetimeUtils datetimeUtils = new DatetimeUtils(context, DatetimeUtils.LOG_METHOD_FILELOGGER);
        PlatformUtilsMessageNet platformUtilsMessageNet = new PlatformUtilsMessageNet(context, PlatformUtilsMessageNet.LOG_METHOD_FILELOGGER);

        try {
            // initialize some resources constants (just so they're easy to access from an instance without having to go get them from resources)
            this.BANNMSGFIELDNAME_JSON_ZXRECNO = resources.getString(R.string.BANNMSGFIELDNAME_JSON_ZXRECNO);
            this.BANNMSGFIELDNAME_JSON_RECDTSEC = resources.getString(R.string.BANNMSGFIELDNAME_JSON_RECDTSEC);
            this.BANNMSGFIELDNAME_JSON_DURATION = resources.getString(R.string.BANNMSGFIELDNAME_JSON_DURATION);
            this.BANNMSGFIELDNAME_JSON_MSGTYPE = resources.getString(R.string.BANNMSGFIELDNAME_JSON_MSGTYPE);
            this.BANNMSGFIELDNAME_JSON_MSGTEXT = resources.getString(R.string.BANNMSGFIELDNAME_JSON_MSGTEXT);
            this.BANNMSGFIELDNAME_JSON_MSGDETAILS = resources.getString(R.string.BANNMSGFIELDNAME_JSON_MSGDETAILS);
            this.BANNMSGFIELDNAME_JSON_PLAYTIMEDURATION = resources.getString(R.string.BANNMSGFIELDNAME_JSON_PLAYTIMEDURATION);
            this.BANNMSGFIELDNAME_JSON_FLASHERDURATION = resources.getString(R.string.BANNMSGFIELDNAME_JSON_FLASHERDURATION);
            this.BANNMSGFIELDNAME_JSON_LIGHTSIGNAL = resources.getString(R.string.BANNMSGFIELDNAME_JSON_LIGHTSIGNAL);
            this.BANNMSGFIELDNAME_JSON_LIGHTDURATION = resources.getString(R.string.BANNMSGFIELDNAME_JSON_LIGHTDURATION);
            this.BANNMSGFIELDNAME_JSON_AUDIOTTSGAIN = resources.getString(R.string.BANNMSGFIELDNAME_JSON_AUDIOTTSGAIN);
            this.BANNMSGFIELDNAME_JSON_FLASHNEWMESSAGE = resources.getString(R.string.BANNMSGFIELDNAME_JSON_FLASHNEWMESSAGE);
            this.BANNMSGFIELDNAME_JSON_VISIBLETIME = resources.getString(R.string.BANNMSGFIELDNAME_JSON_VISIBLETIME);
            this.BANNMSGFIELDNAME_JSON_VISIBLEFREQUENCY = resources.getString(R.string.BANNMSGFIELDNAME_JSON_VISIBLEFREQUENCY);
            this.BANNMSGFIELDNAME_JSON_VISIBLEDURATION = resources.getString(R.string.BANNMSGFIELDNAME_JSON_VISIBLEDURATION);
            this.BANNMSGFIELDNAME_JSON_RECORDVOICEATLAUNCHSELECTION = resources.getString(R.string.BANNMSGFIELDNAME_JSON_RECORDVOICEATLAUNCHSELECTION);
            this.BANNMSGFIELDNAME_JSON_RECORDVOICEATLAUNCH = resources.getString(R.string.BANNMSGFIELDNAME_JSON_RECORDVOICEATLAUNCH);
            this.BANNMSGFIELDNAME_JSON_AUDIORECORDEDGAIN = resources.getString(R.string.BANNMSGFIELDNAME_JSON_AUDIORECORDEDGAIN);
            this.BANNMSGFIELDNAME_JSON_PADELIVERYMODE = resources.getString(R.string.BANNMSGFIELDNAME_JSON_PADELIVERYMODE);
            this.BANNMSGFIELDNAME_JSON_AUDIOREPEAT = resources.getString(R.string.BANNMSGFIELDNAME_JSON_AUDIOREPEAT);
            this.BANNMSGFIELDNAME_JSON_SPEED = resources.getString(R.string.BANNMSGFIELDNAME_JSON_SPEED);
            this.BANNMSGFIELDNAME_JSON_PRIORITY = resources.getString(R.string.BANNMSGFIELDNAME_JSON_PRIORITY);
            this.BANNMSGFIELDNAME_JSON_EXPIREPRIORITY = resources.getString(R.string.BANNMSGFIELDNAME_JSON_EXPIREPRIORITY);
            this.BANNMSGFIELDNAME_JSON_PRIORITYDURATION = resources.getString(R.string.BANNMSGFIELDNAME_JSON_PRIORITYDURATION);
            this.BANNMSGFIELDNAME_JSON_PRIORITYATLAUNCH = resources.getString(R.string.BANNMSGFIELDNAME_JSON_PRIORITYATLAUNCH);
            this.BANNMSGFIELDNAME_JSON_PRIORITYTOLERANCE = resources.getString(R.string.BANNMSGFIELDNAME_JSON_PRIORITYTOLERANCE);
            this.BANNMSGFIELDNAME_JSON_MULTIMEDIATYPE = resources.getString(R.string.BANNMSGFIELDNAME_JSON_MULTIMEDIATYPE);
            this.BANNMSGFIELDNAME_JSON_WEBPAGEURL = resources.getString(R.string.BANNMSGFIELDNAME_JSON_WEBPAGEURL);
            this.BANNMSGFIELDNAME_JSON_AUDIOGROUPS_HW = resources.getString(R.string.BANNMSGFIELDNAME_JSON_AUDIOGROUPS_HW);
            this.BANNMSGFIELDNAME_JSON_AUDIOGROUPS = resources.getString(R.string.BANNMSGFIELDNAME_JSON_AUDIOGROUPS);
            this.BANNMSGFIELDNAME_JSON_MMAUDIOGAIN = resources.getString(R.string.BANNMSGFIELDNAME_JSON_MMAUDIOGAIN);
            //this.BANNMSGFIELDNAME_JSON_MMREPLAYS = resources.getString(R.string.BANNMSGFIELDNAME_JSON_MMREPLAYS);
            this.BANNMSGFIELDNAME_JSON_SEQNUM = resources.getString(R.string.BANNMSGFIELDNAME_JSON_SEQNUM);
            this.BANNMSGFIELDNAME_JSON_LAUNCHPIN = resources.getString(R.string.BANNMSGFIELDNAME_JSON_LAUNCHPIN);
            this.BANNMSGFIELDNAME_JSON_LAUNCHGENDER = resources.getString(R.string.BANNMSGFIELDNAME_JSON_LAUNCHGENDER);
            this.JSONFIELDNAME_LAUNCHDATETIME = resources.getString(R.string.JSONFIELDNAME_LAUNCHDATETIME);
            this.JSONFIELDNAME_LAUNCHDATETIME_DEVICE = resources.getString(R.string.JSONFIELDNAME_LAUNCHDATETIME_DEVICE);
            this.JSONFIELDNAME_EXPIREDATETIME = resources.getString(R.string.JSONFIELDNAME_EXPIREDATETIME);
            this.JSONFIELDNAME_EXPIREDATETIME_DEVICE = resources.getString(R.string.JSONFIELDNAME_EXPIREDATETIME_DEVICE);
            this.JSONFIELDNAME_AUDIOREPEATSREMAINING = resources.getString(R.string.JSONFIELDNAME_AUDIOREPEATSREMAINING);
            this.JSONFIELDNAME_UUIDLOCAL = resources.getString(R.string.JSONFIELDNAME_UUIDLOCAL);
            this.JSONFIELDNAME_DELIVERYCOUNT = resources.getString(R.string.JSONFIELDNAME_DELIVERYCOUNT);
            this.JSONFIELDNAME_LASTDELIVEREDDATETIME = resources.getString(R.string.JSONFIELDNAME_LASTDELIVEREDDATETIME);
            this.JSONFIELDNAME_ISBEINGDELIVERED = resources.getString(R.string.JSONFIELDNAME_ISBEINGDELIVERED);
            this.JSONFIELDNAME_SCROLLCOUNT = resources.getString(R.string.JSONFIELDNAME_SCROLLCOUNT);
            this.JSONFIELDNAME_VIDEOSEEKPOSITION = resources.getString(R.string.JSONFIELDNAME_VIDEOSEEKPOSITION);

            this.MM_TYPE_NONE = resources.getString(R.string.MM_TYPE_NONE);
            this.MM_TYPE_MESSAGE = resources.getString(R.string.MM_TYPE_MESSAGE);
            this.MM_TYPE_MESSAGEFS = resources.getString(R.string.MM_TYPE_MESSAGEFS);
            this.MM_TYPE_WEBPAGE = resources.getString(R.string.MM_TYPE_WEBPAGE);
            this.MM_TYPE_VIDEO = resources.getString(R.string.MM_TYPE_VIDEO);
            this.MM_TYPE_PICTURE = resources.getString(R.string.MM_TYPE_PICTURE);
            this.MM_TYPE_PICTURETEXT = resources.getString(R.string.MM_TYPE_PICTURETEXT);
            this.MM_TYPE_WEBMEDIA = resources.getString(R.string.MM_TYPE_WEBMEDIA);
            this.MM_TYPE_LCDLOCATIONMAP = resources.getString(R.string.MM_TYPE_LCDLOCATIONMAP);
            this.MM_TYPE_GEOLOCATIONMAP = resources.getString(R.string.MM_TYPE_GEOLOCATIONMAP);
            this.MM_TYPE_RTSPSTREAM = resources.getString(R.string.MM_TYPE_RTSPSTREAM);

            this.PA_AUDIO_REPEAT_NONSTOP = resources.getString(R.string.PA_AUDIO_REPEAT_NONSTOP);

            this.SIGNALLIGHT_CMD_NONE = resources.getString(R.string.SIGNALLIGHT_CMD_NONE);
            this.SIGNALLIGHT_CMD_OFF = resources.getString(R.string.SIGNALLIGHT_CMD_OFF);
            this.SIGNALLIGHT_CMD_STANDBY = resources.getString(R.string.SIGNALLIGHT_CMD_STANDBY);
            this.SIGNALLIGHT_CMD_BLUE_DIM = resources.getString(R.string.SIGNALLIGHT_CMD_BLUE_DIM);
            this.SIGNALLIGHT_CMD_BLUE_MED = resources.getString(R.string.SIGNALLIGHT_CMD_BLUE_MED);
            this.SIGNALLIGHT_CMD_BLUE_BRI = resources.getString(R.string.SIGNALLIGHT_CMD_BLUE_BRI);
            this.SIGNALLIGHT_CMD_GREEN_DIM = resources.getString(R.string.SIGNALLIGHT_CMD_GREEN_DIM);
            this.SIGNALLIGHT_CMD_GREEN_MED = resources.getString(R.string.SIGNALLIGHT_CMD_GREEN_MED);
            this.SIGNALLIGHT_CMD_GREEN_BRI = resources.getString(R.string.SIGNALLIGHT_CMD_GREEN_BRI);
            this.SIGNALLIGHT_CMD_ORANGE_DIM = resources.getString(R.string.SIGNALLIGHT_CMD_ORANGE_DIM);
            this.SIGNALLIGHT_CMD_ORANGE_MED = resources.getString(R.string.SIGNALLIGHT_CMD_ORANGE_MED);
            this.SIGNALLIGHT_CMD_ORANGE_BRI = resources.getString(R.string.SIGNALLIGHT_CMD_ORANGE_BRI);
            this.SIGNALLIGHT_CMD_PINK_DIM = resources.getString(R.string.SIGNALLIGHT_CMD_PINK_DIM);
            this.SIGNALLIGHT_CMD_PINK_MED = resources.getString(R.string.SIGNALLIGHT_CMD_PINK_MED);
            this.SIGNALLIGHT_CMD_PINK_BRI = resources.getString(R.string.SIGNALLIGHT_CMD_PINK_BRI);
            this.SIGNALLIGHT_CMD_PURPLE_DIM = resources.getString(R.string.SIGNALLIGHT_CMD_PURPLE_DIM);
            this.SIGNALLIGHT_CMD_PURPLE_MED = resources.getString(R.string.SIGNALLIGHT_CMD_PURPLE_MED);
            this.SIGNALLIGHT_CMD_PURPLE_BRI = resources.getString(R.string.SIGNALLIGHT_CMD_PURPLE_BRI);
            this.SIGNALLIGHT_CMD_RED_DIM = resources.getString(R.string.SIGNALLIGHT_CMD_RED_DIM);
            this.SIGNALLIGHT_CMD_RED_MED = resources.getString(R.string.SIGNALLIGHT_CMD_RED_MED);
            this.SIGNALLIGHT_CMD_RED_BRI = resources.getString(R.string.SIGNALLIGHT_CMD_RED_BRI);
            this.SIGNALLIGHT_CMD_WHITECOOL_DIM = resources.getString(R.string.SIGNALLIGHT_CMD_WHITECOOL_DIM);
            this.SIGNALLIGHT_CMD_WHITECOOL_MED = resources.getString(R.string.SIGNALLIGHT_CMD_WHITECOOL_MED);
            this.SIGNALLIGHT_CMD_WHITECOOL_BRI = resources.getString(R.string.SIGNALLIGHT_CMD_WHITECOOL_BRI);
            this.SIGNALLIGHT_CMD_WHITEPURE_DIM = resources.getString(R.string.SIGNALLIGHT_CMD_WHITEPURE_DIM);
            this.SIGNALLIGHT_CMD_WHITEPURE_MED = resources.getString(R.string.SIGNALLIGHT_CMD_WHITEPURE_MED);
            this.SIGNALLIGHT_CMD_WHITEPURE_BRI = resources.getString(R.string.SIGNALLIGHT_CMD_WHITEPURE_BRI);
            this.SIGNALLIGHT_CMD_WHITEWARM_DIM = resources.getString(R.string.SIGNALLIGHT_CMD_WHITEWARM_DIM);
            this.SIGNALLIGHT_CMD_WHITEWARM_MED = resources.getString(R.string.SIGNALLIGHT_CMD_WHITEWARM_MED);
            this.SIGNALLIGHT_CMD_WHITEWARM_BRI = resources.getString(R.string.SIGNALLIGHT_CMD_WHITEWARM_BRI);
            this.SIGNALLIGHT_CMD_YELLOW_DIM = resources.getString(R.string.SIGNALLIGHT_CMD_YELLOW_DIM);
            this.SIGNALLIGHT_CMD_YELLOW_MED = resources.getString(R.string.SIGNALLIGHT_CMD_YELLOW_MED);
            this.SIGNALLIGHT_CMD_YELLOW_BRI = resources.getString(R.string.SIGNALLIGHT_CMD_YELLOW_BRI);
            this.SIGNALLIGHT_CMD_FADING_BLUE = resources.getString(R.string.SIGNALLIGHT_CMD_FADING_BLUE);
            this.SIGNALLIGHT_CMD_FADING_GREEN = resources.getString(R.string.SIGNALLIGHT_CMD_FADING_GREEN);
            this.SIGNALLIGHT_CMD_FADING_ORANGE = resources.getString(R.string.SIGNALLIGHT_CMD_FADING_ORANGE);
            this.SIGNALLIGHT_CMD_FADING_PINK = resources.getString(R.string.SIGNALLIGHT_CMD_FADING_PINK);
            this.SIGNALLIGHT_CMD_FADING_PURPLE = resources.getString(R.string.SIGNALLIGHT_CMD_FADING_PURPLE);
            this.SIGNALLIGHT_CMD_FADING_RED = resources.getString(R.string.SIGNALLIGHT_CMD_FADING_RED);
            this.SIGNALLIGHT_CMD_FADING_WHITECOOL = resources.getString(R.string.SIGNALLIGHT_CMD_FADING_WHITECOOL);
            this.SIGNALLIGHT_CMD_FADING_WHITEPURE = resources.getString(R.string.SIGNALLIGHT_CMD_FADING_WHITEPURE);
            this.SIGNALLIGHT_CMD_FADING_WHITEWARM = resources.getString(R.string.SIGNALLIGHT_CMD_FADING_WHITEWARM);
            this.SIGNALLIGHT_CMD_FADING_YELLOW = resources.getString(R.string.SIGNALLIGHT_CMD_FADING_YELLOW);
            this.SIGNALLIGHT_CMD_FLASHING_BLUE = resources.getString(R.string.SIGNALLIGHT_CMD_FLASHING_BLUE);
            this.SIGNALLIGHT_CMD_FLASHING_GREEN = resources.getString(R.string.SIGNALLIGHT_CMD_FLASHING_GREEN);
            this.SIGNALLIGHT_CMD_FLASHING_ORANGE = resources.getString(R.string.SIGNALLIGHT_CMD_FLASHING_ORANGE);
            this.SIGNALLIGHT_CMD_FLASHING_PINK = resources.getString(R.string.SIGNALLIGHT_CMD_FLASHING_PINK);
            this.SIGNALLIGHT_CMD_FLASHING_PURPLE = resources.getString(R.string.SIGNALLIGHT_CMD_FLASHING_PURPLE);
            this.SIGNALLIGHT_CMD_FLASHING_RED = resources.getString(R.string.SIGNALLIGHT_CMD_FLASHING_RED);
            this.SIGNALLIGHT_CMD_FLASHING_WHITECOOL = resources.getString(R.string.SIGNALLIGHT_CMD_FLASHING_WHITECOOL);
            this.SIGNALLIGHT_CMD_FLASHING_WHITEPURE = resources.getString(R.string.SIGNALLIGHT_CMD_FLASHING_WHITEPURE);
            this.SIGNALLIGHT_CMD_FLASHING_WHITEWARM = resources.getString(R.string.SIGNALLIGHT_CMD_FLASHING_WHITEWARM);
            this.SIGNALLIGHT_CMD_FLASHING_YELLOW = resources.getString(R.string.SIGNALLIGHT_CMD_FLASHING_YELLOW);

            // grab all the raw data from the provided JSON object
            try {this.recno = msg.getInt(this.BANNMSGFIELDNAME_JSON_ZXRECNO);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.dbb_rec_dtsec = msg.getString(this.BANNMSGFIELDNAME_JSON_RECDTSEC);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.dbb_duration = msg.getInt(this.BANNMSGFIELDNAME_JSON_DURATION);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.msgType = msg.getString(this.BANNMSGFIELDNAME_JSON_MSGTYPE);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.msgText = msg.getString(this.BANNMSGFIELDNAME_JSON_MSGTEXT);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.msgDetails = msg.getString(this.BANNMSGFIELDNAME_JSON_MSGDETAILS);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.dbb_playtime_duration = msg.getLong(this.BANNMSGFIELDNAME_JSON_PLAYTIMEDURATION);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.dbb_flasher_duration = msg.getInt(this.BANNMSGFIELDNAME_JSON_FLASHERDURATION);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.dbb_light_signal = msg.getString(this.BANNMSGFIELDNAME_JSON_LIGHTSIGNAL);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.dbb_light_duration = msg.getInt(this.BANNMSGFIELDNAME_JSON_LIGHTDURATION);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.dbb_audio_tts_gain = msg.getInt(this.BANNMSGFIELDNAME_JSON_AUDIOTTSGAIN);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.dbb_flash_new_message = msg.getString(this.BANNMSGFIELDNAME_JSON_FLASHNEWMESSAGE);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.dbb_visible_time = msg.getString(this.BANNMSGFIELDNAME_JSON_VISIBLETIME);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.dbb_visible_frequency = msg.getString(this.BANNMSGFIELDNAME_JSON_VISIBLEFREQUENCY);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.dbb_visible_duration = msg.getString(this.BANNMSGFIELDNAME_JSON_VISIBLEDURATION);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.dbb_record_voice_at_launch_selection = msg.getInt(this.BANNMSGFIELDNAME_JSON_RECORDVOICEATLAUNCHSELECTION);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.dbb_record_voice_at_launch = msg.getString(this.BANNMSGFIELDNAME_JSON_RECORDVOICEATLAUNCH);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.dbb_audio_recorded_gain = msg.getInt(this.BANNMSGFIELDNAME_JSON_AUDIORECORDEDGAIN);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.dbb_pa_delivery_mode = msg.getString(this.BANNMSGFIELDNAME_JSON_PADELIVERYMODE);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.dbb_audio_repeat = msg.getString(this.BANNMSGFIELDNAME_JSON_AUDIOREPEAT);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.dbb_speed = msg.getInt(this.BANNMSGFIELDNAME_JSON_SPEED);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.dbb_priority = msg.getInt(this.BANNMSGFIELDNAME_JSON_PRIORITY);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.dbb_expire_priority = msg.getInt(this.BANNMSGFIELDNAME_JSON_EXPIREPRIORITY);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.dbb_priority_duration = msg.getLong(this.BANNMSGFIELDNAME_JSON_PRIORITYDURATION);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.dbb_page_priority_at_launch = msg.getInt(this.BANNMSGFIELDNAME_JSON_PRIORITYATLAUNCH);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.dbb_priority_tolerance = msg.getInt(this.BANNMSGFIELDNAME_JSON_PRIORITYTOLERANCE);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.multimediatype = msg.getString(this.BANNMSGFIELDNAME_JSON_MULTIMEDIATYPE);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.webpageurl = msg.getString(this.BANNMSGFIELDNAME_JSON_WEBPAGEURL);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.dsi_audio_group_name = msg.getJSONArray(this.BANNMSGFIELDNAME_JSON_AUDIOGROUPS_HW);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.dbb_audio_groups = msg.getJSONArray(this.BANNMSGFIELDNAME_JSON_AUDIOGROUPS);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.dbb_multimedia_audio_gain = msg.getInt(this.BANNMSGFIELDNAME_JSON_MMAUDIOGAIN);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            //this.dbb_replay_media = msg.getString(this.BANNMSGFIELDNAME_JSON_MMREPLAYS);
            try {this.sequence_number = msg.getInt(this.BANNMSGFIELDNAME_JSON_SEQNUM);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.dbb_launch_pin = msg.getString(this.BANNMSGFIELDNAME_JSON_LAUNCHPIN);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}
            try {this.dss_gender = msg.getString(this.BANNMSGFIELDNAME_JSON_LAUNCHGENDER);} catch (JSONException e) {
                logW("Exception caught trying to parse JSON value. Using initialized value instead. ("+e.getMessage()+")");}

            // validate and fix any raw data necessary
            //if (this.dbb_audio_repeat.equals(this.PA_AUDIO_REPEAT_NONSTOP)) this.dbb_audio_repeat
            if (this.dss_gender == null || this.dss_gender.isEmpty() || this.dss_gender.length() == 0 || this.dss_gender.equals(" ")) {
                Log.i(TAG, "INFO: No gender available from server. Defaulting to male.");           //we only do this because studies have shown a male voice to be authoritative.. not our judgement, just hard data shows it
                this.dss_gender = "M";
            }

            // calculate anything else needed, from the data acquired above
            this.dtLaunch = platformUtilsMessageNet.calculateDate_fromServerDTSEC(Long.parseLong(this.dbb_rec_dtsec));
            try {
                this.dtLaunch_device = datetimeUtils.getDateFromString(msg.get(this.JSONFIELDNAME_LAUNCHDATETIME_DEVICE).toString());
            } catch (Exception e) {
                logW("WARNING: JSONFIELDNAME_LAUNCHDATETIME_DEVICE not provided. Provide it as close to receipt of launch data as possible. Adding here and now, automatically.");
                logV("Ex.)  bannMsg_json_newest.put(getApplicationContext().getResources().getString(R.string.JSONFIELDNAME_LAUNCHDATETIME_DEVICE), new Date().toString());");
                this.dtLaunch_device = new Date();
            }
            try {
                this.dtExpiration = datetimeUtils.calculateExpirationDate_fromDurationInSecs(this.dtLaunch, this.dbb_duration);
            } catch (Exception e) {
                logW("WARNING: Invalid dtLaunch or dbb_duration value, so could not calculate valid dtExpiration value. Using current date.");
                this.dtExpiration = new Date(); //just some valid value so nothing crashes, even though this is wrong
            }
            this.dtExpiration_device = datetimeUtils.calculateExpirationDate_fromDurationInSecs(this.dtLaunch_device, this.dbb_duration);
            if (this.dbb_audio_repeat.equals(this.PA_AUDIO_REPEAT_NONSTOP)) {
                this.audioRepeatsRemaining = Integer.MAX_VALUE - 1;     //so we don't overflow audioRepeatsRemaining and have it become negative
                if (this.audioRepeatsRemaining == 0) this.audioRepeatsRemaining = 32766;    //maybe above isn't right so fall back to 2 byte max (minus 1)
            } else if (isNumeric(this.dbb_audio_repeat)) {
                this.audioRepeatsRemaining = Integer.parseInt(this.dbb_audio_repeat);   //initialize with the message-defined audio repeats value (we can decrement later in logic as needed)
            } else {
                logW("WARNING: Unhandled dbb_audio_repeat value (\""+ String.valueOf(this.dbb_audio_repeat)+"\"). Substituting 0 for audioRepeatsRemaining value.");
                this.audioRepeatsRemaining = 0;
            }

            // override the server-provided multimediatype for if we can infer an RTSP-stream message
            if (String.valueOf(this.webpageurl).toLowerCase().contains("rtsp:")) {
                this.multimediatype = MM_TYPE_RTSPSTREAM;
            }
            // override the server-provided (deprecated form of) webpageurl for Omni-originated RTSP-stream message
            if (this.multimediatype.equals(MM_TYPE_RTSPSTREAM)
                    && this.webpageurl.contains("/evolution")) {
                this.webpageurl = this.webpageurl.replace("/evolution", "/video/h264");
            }

            // anything else necessary or useful
            this.uuid_local = UUID.randomUUID();        //associate a unique identifier with the instance
            this.deliveryCount = 0;                     //initialize with a count of zero to begin with
            this.dtLastDelivered = null;                //don't initialize with an actual date, yet
            this.isBeingDelivered = false;
            this.scrollCount = 0;
            this.videoSeekPosition = 0;
            this.isReadyToDeliver = true;               //default to true, so we don't risk impacting legacy behaviors (this was introduced to support videos, initially)
            this.hasAudioComponent = false;
            this.isAudioComponentActive = false;

            logD("Instantiated a BannerMessage object (UUID="+ String.valueOf(this.uuid_local)+" / recno="+ String.valueOf(this.recno)+" / \""+this.msgText+"\").");
            logV(assembleAllPublicFieldsAndValues()); //log all fields and values in an easy-to-read manner
        } catch (Exception e) {
            logE("Exception caught instantiating "+TAG+": "+e.getMessage());
        }
    }//end constructor

    private boolean isNumeric(String strNum) {
        try {
            double d = Double.parseDouble(strNum);
        } catch (NumberFormatException | NullPointerException nfe) {
            return false;
        }
        return true;
    }

    private String assembleAllPublicFieldsAndValues() {
        final String TAGG = "assembleAllPublicFieldsAndValues: ";
        StringBuilder out = new StringBuilder("Logging all public values...");
        for(Field field : this.getClass().getFields()) {
            try {
                if (field.getName().contains("MM_TYPE")
                        || field.getName().contains("LOG_METHOD")
                        || field.getName().contains("PA_AUDIO")) {
                    //don't print these
                } else {
                    out.append("\n").append(field.getName()).append(" = ").append(field.get(this));
                }
            } catch (IllegalAccessException e) {
                logE(TAGG+"Exception caught: "+ e.getMessage());
            }
        }
        return out.toString();
    }

    public JSONObject exportJSONObject() {
        final String TAGG = "exportJSONObject: ";
        JSONObject oj = new JSONObject();
        try {
            oj.put(this.BANNMSGFIELDNAME_JSON_ZXRECNO, this.recno);
            oj.put(this.BANNMSGFIELDNAME_JSON_RECDTSEC, this.dbb_rec_dtsec);
            oj.put(this.BANNMSGFIELDNAME_JSON_DURATION, this.dbb_duration);
            oj.put(this.BANNMSGFIELDNAME_JSON_MSGTYPE, this.msgType);
            oj.put(this.BANNMSGFIELDNAME_JSON_MSGTEXT, this.msgText);
            oj.put(this.BANNMSGFIELDNAME_JSON_MSGDETAILS, this.msgDetails);
            oj.put(this.BANNMSGFIELDNAME_JSON_PLAYTIMEDURATION, this.dbb_playtime_duration);
            oj.put(this.BANNMSGFIELDNAME_JSON_FLASHERDURATION, this.dbb_flasher_duration);
            oj.put(this.BANNMSGFIELDNAME_JSON_LIGHTSIGNAL, this.dbb_light_signal);
            oj.put(this.BANNMSGFIELDNAME_JSON_LIGHTDURATION, this.dbb_light_duration);
            oj.put(this.BANNMSGFIELDNAME_JSON_AUDIOTTSGAIN, this.dbb_audio_tts_gain);
            oj.put(this.BANNMSGFIELDNAME_JSON_FLASHNEWMESSAGE, this.dbb_flash_new_message);
            oj.put(this.BANNMSGFIELDNAME_JSON_VISIBLETIME, this.dbb_visible_time);
            oj.put(this.BANNMSGFIELDNAME_JSON_VISIBLEFREQUENCY, this.dbb_visible_frequency);
            oj.put(this.BANNMSGFIELDNAME_JSON_VISIBLEDURATION, this.dbb_visible_duration);
            oj.put(this.BANNMSGFIELDNAME_JSON_RECORDVOICEATLAUNCHSELECTION, this.dbb_record_voice_at_launch_selection);
            oj.put(this.BANNMSGFIELDNAME_JSON_RECORDVOICEATLAUNCH, this.dbb_record_voice_at_launch);
            oj.put(this.BANNMSGFIELDNAME_JSON_AUDIORECORDEDGAIN, this.dbb_audio_recorded_gain);
            oj.put(this.BANNMSGFIELDNAME_JSON_PADELIVERYMODE, this.dbb_pa_delivery_mode);
            oj.put(this.BANNMSGFIELDNAME_JSON_AUDIOREPEAT, this.dbb_audio_repeat);
            oj.put(this.BANNMSGFIELDNAME_JSON_SPEED, this.dbb_speed);
            oj.put(this.BANNMSGFIELDNAME_JSON_PRIORITY, this.dbb_priority);
            oj.put(this.BANNMSGFIELDNAME_JSON_EXPIREPRIORITY, this.dbb_expire_priority);
            oj.put(this.BANNMSGFIELDNAME_JSON_PRIORITYDURATION, this.dbb_priority_duration);
            oj.put(this.BANNMSGFIELDNAME_JSON_PRIORITYATLAUNCH, this.dbb_page_priority_at_launch);
            oj.put(this.BANNMSGFIELDNAME_JSON_PRIORITYTOLERANCE, this.dbb_priority_tolerance);
            oj.put(this.BANNMSGFIELDNAME_JSON_MULTIMEDIATYPE, this.multimediatype);
            oj.put(this.BANNMSGFIELDNAME_JSON_WEBPAGEURL, this.webpageurl);
            oj.put(this.BANNMSGFIELDNAME_JSON_AUDIOGROUPS_HW, this.dsi_audio_group_name);
            oj.put(this.BANNMSGFIELDNAME_JSON_AUDIOGROUPS, this.dbb_audio_groups);
            oj.put(this.BANNMSGFIELDNAME_JSON_MMAUDIOGAIN, this.dbb_multimedia_audio_gain);
            //oj.put(this.BANNMSGFIELDNAME_JSON_MMREPLAYS, this.dbb_replay_media);
            oj.put(this.BANNMSGFIELDNAME_JSON_LAUNCHPIN, this.dbb_launch_pin);
            oj.put(this.BANNMSGFIELDNAME_JSON_LAUNCHGENDER, this.dss_gender);

            oj.put(this.JSONFIELDNAME_LAUNCHDATETIME, this.dtLaunch);
            oj.put(this.JSONFIELDNAME_LAUNCHDATETIME_DEVICE, this.dtLaunch_device);
            oj.put(this.JSONFIELDNAME_EXPIREDATETIME, this.dtExpiration);
            oj.put(this.JSONFIELDNAME_EXPIREDATETIME_DEVICE, this.dtExpiration_device);
            oj.put(this.JSONFIELDNAME_AUDIOREPEATSREMAINING, this.audioRepeatsRemaining);
            oj.put(this.JSONFIELDNAME_UUIDLOCAL, this.uuid_local);
            oj.put(this.JSONFIELDNAME_DELIVERYCOUNT, this.deliveryCount);
            oj.put(this.JSONFIELDNAME_LASTDELIVEREDDATETIME, this.dtLastDelivered);
            oj.put(this.JSONFIELDNAME_ISBEINGDELIVERED, this.isBeingDelivered);
            oj.put(this.JSONFIELDNAME_SCROLLCOUNT, this.scrollCount);
            oj.put(this.JSONFIELDNAME_VIDEOSEEKPOSITION, this.videoSeekPosition);

        } catch (JSONException e) {
            logE(TAGG+"Exception caught trying to create JSONObject for export: "+e.getMessage());
        }

        return oj;
    }

    public JSONObject createDummyData_JSON(Context context) {
        final String TAGG = "createDummyData_JSON: ";
        JSONObject dummyMessage = new JSONObject();

        try {
            dummyMessage.put(this.BANNMSGFIELDNAME_JSON_MSGTYPE, "MESSAGE");
            dummyMessage.put(this.BANNMSGFIELDNAME_JSON_MSGTEXT, "No Messages");
            dummyMessage.put(this.BANNMSGFIELDNAME_JSON_MSGDETAILS, "No Messages");

            dummyMessage.put(this.BANNMSGFIELDNAME_JSON_ZXRECNO, "0");
            dummyMessage.put(this.BANNMSGFIELDNAME_JSON_RECDTSEC, String.valueOf(new Date().getTime()/1000));
            dummyMessage.put(this.BANNMSGFIELDNAME_JSON_DURATION, "0");
            dummyMessage.put(this.BANNMSGFIELDNAME_JSON_PLAYTIMEDURATION, "0");
        } catch (JSONException e) {
            logE(TAGG+"Caught exception during creation of dummy JSON message object: " + e.getMessage());
        }

        return dummyMessage;
    }


    /*============================================================================================*/
    /* Compatibility Methods */

    //TODO -- Make sure you didn't confuse this routine's logic with JSON's "bannerpurpose" value?!?!
    //TODO -- Think I initially did.. old BannerMessage field msgType was "normal" or "emergency" etc.... NOT type of delivery!
    public int exportOmniMessage_msgType() {
        final String TAGG = "exportOmniMessage_msgType: ";
        int ret = OmniMessage.MSG_TYPE_UNKNOWN;

        try {
            //String nativeValue = this.msgType;
            String nativeValue = this.multimediatype;
            logV(TAGG+"multimediatype = \""+String.valueOf(nativeValue)+"\"");

            if (nativeValue.equals(this.MM_TYPE_NONE)
                    || nativeValue.equals(this.MM_TYPE_MESSAGE)
                    || nativeValue.equals(this.MM_TYPE_MESSAGEFS)) {
                ret = OmniMessage.MSG_TYPE_TEXT;
            } else if (nativeValue.equals(this.MM_TYPE_WEBPAGE)) {
                ret = OmniMessage.MSG_TYPE_WEB_PAGE;
            } else if (nativeValue.equals(this.MM_TYPE_VIDEO)) {
                ret = OmniMessage.MSG_TYPE_VIDEO_FILE;
            } else if (nativeValue.equals(this.MM_TYPE_PICTURE)
                    || nativeValue.equals(this.MM_TYPE_PICTURETEXT)) {
                ret = OmniMessage.MSG_TYPE_PICTURE;
            } else if (nativeValue.equals(this.MM_TYPE_WEBMEDIA)
                    || nativeValue.equals(this.MM_TYPE_RTSPSTREAM)) {
                ret = OmniMessage.MSG_TYPE_VIDEO_STREAM;
            } else if (nativeValue.equals(this.MM_TYPE_LCDLOCATIONMAP)
                    || nativeValue.equals(this.MM_TYPE_GEOLOCATIONMAP)) {
                ret = OmniMessage.MSG_TYPE_LOCATIONMAP;
            } else {
                logW(TAGG+"Unhandled legacy message-type value ("+nativeValue+").");
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning \""+ret+"\".");
        return ret;
    }

    public String exportOmniMessage_msgHeading() {
        final String TAGG = "exportOmniMessage_msgHeading: ";
        String ret = "";

        try {
            String nativeValue = this.msgType;
            ret = nativeValue;
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning \""+ret+"\".");
        return ret;
    }

    public int exportOmniMessage_flasherMode() {
        final String TAGG = "exportOmniMessage_flasherMode: ";
        int ret = OmniMessage.FLASHER_MODE_UNKNOWN;

        try {
            String nativeValue = this.dbb_light_signal;

            if (nativeValue.equals(this.SIGNALLIGHT_CMD_NONE)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_STANDBY)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_BLUE_DIM)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_BLUE_MED)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_BLUE_BRI)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_GREEN_DIM)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_GREEN_MED)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_GREEN_BRI)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_ORANGE_DIM)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_ORANGE_MED)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_ORANGE_BRI)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_PINK_DIM)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_PINK_MED)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_PINK_BRI)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_PURPLE_DIM)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_PURPLE_MED)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_PURPLE_BRI)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_RED_DIM)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_RED_MED)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_RED_BRI)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_WHITECOOL_DIM)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_WHITECOOL_MED)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_WHITECOOL_BRI)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_WHITEPURE_DIM)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_WHITEPURE_MED)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_WHITEPURE_BRI)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_WHITEWARM_DIM)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_WHITEWARM_MED)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_WHITEWARM_BRI)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_YELLOW_DIM)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_YELLOW_MED)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_YELLOW_BRI)) {
                ret = OmniMessage.FLASHER_MODE_STEADY;
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_OFF)) {
                ret = OmniMessage.FLASHER_MODE_OFF;
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_BLUE)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_GREEN)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_ORANGE)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_PINK)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_PURPLE)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_RED)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_WHITECOOL)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_WHITEPURE)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_WHITEWARM)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_YELLOW)) {
                ret = OmniMessage.FLASHER_MODE_FADE;
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_BLUE)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_GREEN)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_ORANGE)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_PINK)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_PURPLE)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_RED)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_WHITECOOL)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_WHITEPURE)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_WHITEWARM)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_YELLOW)) {
                ret = OmniMessage.FLASHER_MODE_FLASH;
            } else {
                logW(TAGG+"Unhandled legacy flasher value ("+nativeValue+").");
            }

        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning "+Integer.toString(ret)+".");
        return ret;
    }

    /* TODO
    public int exportOmniMessage_flasherColor() {
        final String TAGG = "exportOmniMessage_flasherColor: ";
        int ret = OmniMessage.FLASHER_COLOR_UNKNOWN;

        try {
            String nativeValue = this.dbb_light_signal;

            if (nativeValue.equals(this.SIGNALLIGHT_CMD_NONE)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_OFF)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_STANDBY)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_BLUE_DIM)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_BLUE_MED)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_BLUE_BRI)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_GREEN_DIM)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_GREEN_MED)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_GREEN_BRI)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_ORANGE_DIM)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_ORANGE_MED)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_ORANGE_BRI)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_PINK_DIM)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_PINK_MED)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_PINK_BRI)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_PURPLE_DIM)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_PURPLE_MED)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_PURPLE_BRI)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_RED_DIM)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_RED_MED)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_RED_BRI)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_WHITECOOL_DIM)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_WHITECOOL_MED)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_WHITECOOL_BRI)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_WHITEPURE_DIM)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_WHITEPURE_MED)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_WHITEPURE_BRI)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_WHITEWARM_DIM)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_WHITEWARM_MED)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_WHITEWARM_BRI)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_YELLOW_DIM)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_YELLOW_MED)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_YELLOW_BRI)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_BLUE)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_GREEN)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_ORANGE)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_PINK)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_PURPLE)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_RED)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_WHITECOOL)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_WHITEPURE)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_WHITEWARM)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_YELLOW)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_BLUE)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_GREEN)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_ORANGE)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_PINK)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_PURPLE)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_RED)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_WHITECOOL)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_WHITEPURE)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_WHITEWARM)) {
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_YELLOW)) {
            } else {
                logW(TAGG+"Unhandled legacy flasher value ("+nativeValue+").");
            }

        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning "+Integer.toString(ret)+".");
        return ret;
    }
    */

    public int exportOmniMessage_flasherBrightness() {
        final String TAGG = "exportOmniMessage_flasherBrightness: ";
        int ret = OmniMessage.FLASHER_BRIGHTNESS_UNKNOWN;

        try {
            String nativeValue = this.dbb_light_signal;

            if (nativeValue.equals(this.SIGNALLIGHT_CMD_NONE)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_OFF)) {
                //TODO
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_STANDBY)) {
                //TODO
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_BLUE_DIM)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_GREEN_DIM)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_ORANGE_DIM)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_PINK_DIM)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_PURPLE_DIM)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_RED_DIM)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_WHITECOOL_DIM)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_WHITEPURE_DIM)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_WHITEWARM_DIM)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_YELLOW_DIM)) {
                ret = OmniMessage.FLASHER_BRIGHTNESS_MIN;
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_BLUE_MED)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_GREEN_MED)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_ORANGE_MED)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_PINK_MED)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_PURPLE_MED)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_RED_MED)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_WHITECOOL_MED)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_WHITEWARM_MED)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_WHITEPURE_MED)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_YELLOW_MED)) {
                ret = OmniMessage.FLASHER_BRIGHTNESS_MED;
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_BLUE_BRI)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_GREEN_BRI)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_ORANGE_BRI)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_PINK_BRI)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_PURPLE_BRI)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_RED_BRI)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_WHITECOOL_BRI)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_WHITEPURE_BRI)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_WHITEWARM_BRI)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_YELLOW_BRI)) {
                ret = OmniMessage.FLASHER_BRIGHTNESS_MAX;
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_BLUE)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_GREEN)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_ORANGE)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_PINK)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_PURPLE)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_RED)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_WHITECOOL)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_WHITEPURE)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_WHITEWARM)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FADING_YELLOW)) {
                ret = OmniMessage.FLASHER_BRIGHTNESS_MAX;
            } else if (nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_BLUE)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_GREEN)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_ORANGE)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_PINK)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_PURPLE)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_RED)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_WHITECOOL)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_WHITEPURE)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_WHITEWARM)
                    || nativeValue.equals(this.SIGNALLIGHT_CMD_FLASHING_YELLOW)) {
                ret = OmniMessage.FLASHER_BRIGHTNESS_MAX;
            } else {
                logW(TAGG+"Unhandled legacy flasher value ("+nativeValue+").");
            }

        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning "+Integer.toString(ret)+".");
        return ret;
    }

    public long exportOmniMessage_flasherDuration() {
        final String TAGG = "exportOmniMessage_flasherDuration: ";
        long ret = 0;

        try {
            ret = (long) this.dbb_flasher_duration;
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning "+Long.toString(ret)+".");
        return ret;
    }


    /*============================================================================================*/
    /* Getter & Setter Methods */

    public int getRecno() {
        return recno;
    }

    public void setRecno(int recno) {
        this.recno = recno;
    }

    public String getDbb_rec_dtsec() {
        return dbb_rec_dtsec;
    }

    public void setDbb_rec_dtsec(String dbb_rec_dtsec) {
        this.dbb_rec_dtsec = dbb_rec_dtsec;
    }

    public int getDbb_duration() {
        return dbb_duration;
    }

    public void setDbb_duration(int dbb_duration) {
        this.dbb_duration = dbb_duration;
    }

    public String getMsgType() {
        return msgType;
    }

    public void setMsgType(String msgType) {
        this.msgType = msgType;
    }

    public String getMsgText() {
        return msgText;
    }

    public void setMsgText(String msgText) {
        this.msgText = msgText;
    }

    public String getMsgDetails() {
        return msgDetails;
    }

    public void setMsgDetails(String msgDetails) {
        this.msgDetails = msgDetails;
    }

    public Long getDbb_playtime_duration() {
        return dbb_playtime_duration;
    }

    public void setDbb_playtime_duration(Long dbb_playtime_duration) {
        this.dbb_playtime_duration = dbb_playtime_duration;
    }

    public int getDbb_flasher_duration() {
        return dbb_flasher_duration;
    }

    public void setDbb_flasher_duration(int dbb_flasher_duration) {
        this.dbb_flasher_duration = dbb_flasher_duration;
    }

    public String getDbb_light_signal() {
        return dbb_light_signal;
    }

    public void setDbb_light_signal(String dbb_light_signal) {
        this.dbb_light_signal = dbb_light_signal;
    }

    public int getDbb_light_duration() {
        return dbb_light_duration;
    }

    public void setDbb_light_duration(int dbb_light_duration) {
        this.dbb_light_duration = dbb_light_duration;
    }

    public int getDbb_audio_tts_gain() {
        return dbb_audio_tts_gain;
    }

    public void setDbb_audio_tts_gain(int dbb_audio_tts_gain) {
        this.dbb_audio_tts_gain = dbb_audio_tts_gain;
    }

    public String getDbb_flash_new_message() {
        return dbb_flash_new_message;
    }

    public void setDbb_flash_new_message(String dbb_flash_new_message) {
        this.dbb_flash_new_message = dbb_flash_new_message;
    }

    public String getDbb_visible_time() {
        return dbb_visible_time;
    }

    public void setDbb_visible_time(String dbb_visible_time) {
        this.dbb_visible_time = dbb_visible_time;
    }

    public String getDbb_visible_frequency() {
        return dbb_visible_frequency;
    }

    public void setDbb_visible_frequency(String dbb_visible_frequency) {
        this.dbb_visible_frequency = dbb_visible_frequency;
    }

    public String getDbb_visible_duration() {
        return dbb_visible_duration;
    }

    public void setDbb_visible_duration(String dbb_visible_duration) {
        this.dbb_visible_duration = dbb_visible_duration;
    }

    public int getDbb_record_voice_at_launch_selection() {
        return dbb_record_voice_at_launch_selection;
    }

    public void setDbb_record_voice_at_launch_selection(int dbb_record_voice_at_launch_selection) {
        this.dbb_record_voice_at_launch_selection = dbb_record_voice_at_launch_selection;
    }

    public String getDbb_record_voice_at_launch() {
        return dbb_record_voice_at_launch;
    }

    public void setDbb_record_voice_at_launch(String dbb_record_voice_at_launch) {
        this.dbb_record_voice_at_launch = dbb_record_voice_at_launch;
    }

    public int getDbb_audio_recorded_gain() {
        return dbb_audio_recorded_gain;
    }

    public void setDbb_audio_recorded_gain(int dbb_audio_recorded_gain) {
        this.dbb_audio_recorded_gain = dbb_audio_recorded_gain;
    }

    public String getDbb_pa_delivery_mode() {
        return dbb_pa_delivery_mode;
    }

    public void setDbb_pa_delivery_mode(String dbb_pa_delivery_mode) {
        this.dbb_pa_delivery_mode = dbb_pa_delivery_mode;
    }

    public String getDbb_audio_repeat() {
        return dbb_audio_repeat;
    }

    public void setDbb_audio_repeat(String dbb_audio_repeat) {
        this.dbb_audio_repeat = dbb_audio_repeat;
    }

    public int getDbb_speed() {
        return dbb_speed;
    }

    public void setDbb_speed(int dbb_speed) {
        this.dbb_speed = dbb_speed;
    }

    public int getDbb_priority() {
        return dbb_priority;
    }

    public void setDbb_priority(int dbb_priority) {
        this.dbb_priority = dbb_priority;
    }

    public int getDbb_expire_priority() {
        return dbb_expire_priority;
    }

    public void setDbb_expire_priority(int dbb_expire_priority) {
        this.dbb_expire_priority = dbb_expire_priority;
    }

    public Long getDbb_priority_duration() {
        return dbb_priority_duration;
    }

    public void setDbb_priority_duration(Long dbb_priority_duration) {
        this.dbb_priority_duration = dbb_priority_duration;
    }

    public int getDbb_page_priority_at_launch() {
        return dbb_page_priority_at_launch;
    }

    public void setDbb_page_priority_at_launch(int dbb_page_priority_at_launch) {
        this.dbb_page_priority_at_launch = dbb_page_priority_at_launch;
    }

    public int getDbb_priority_tolerance() {
        return dbb_priority_tolerance;
    }

    public void setDbb_priority_tolerance(int dbb_priority_tolerance) {
        this.dbb_priority_tolerance = dbb_priority_tolerance;
    }

    public String getMultimediatype() {
        return multimediatype;
    }

    public void setMultimediatype(String multimediatype) {
        this.multimediatype = multimediatype;
    }

    public String getWebpageurl() {
        return webpageurl;
    }

    public void setWebpageurl(String webpageurl) {
        this.webpageurl = webpageurl;
    }

    public JSONArray getDsi_audio_group_name() {
        return dsi_audio_group_name;
    }

    public void setDsi_audio_group_name(JSONArray dsi_audio_group_name) {
        this.dsi_audio_group_name = dsi_audio_group_name;
    }

    public JSONArray getDbb_audio_groups() {
        return dbb_audio_groups;
    }

    public void setDbb_audio_groups(JSONArray dbb_audio_groups) {
        this.dbb_audio_groups = dbb_audio_groups;
    }

    public int getDbb_multimedia_audio_gain() {
        return dbb_multimedia_audio_gain;
    }

    public void setDbb_multimedia_audio_gain(int dbb_multimedia_audio_gain) {
        this.dbb_multimedia_audio_gain = dbb_multimedia_audio_gain;
    }

    public int getSequence_number() {
        return sequence_number;
    }

    public void setSequence_number(int sequence_number) {
        this.sequence_number = sequence_number;
    }

    public String getDbb_launch_pin() {
        return dbb_launch_pin;
    }

    public void setDbb_launch_pin(String dbb_launch_pin) {
        this.dbb_launch_pin = dbb_launch_pin;
    }

    public String getDss_gender() {
        return dss_gender;
    }

    public void setDss_gender(String dss_gender) {
        this.dss_gender = dss_gender;
    }

    public Date getDtLaunch() {
        return dtLaunch;
    }

    public void setDtLaunch(Date dtLaunch) {
        this.dtLaunch = dtLaunch;
    }

    public Date getDtLaunch_device() {
        return dtLaunch_device;
    }

    public void setDtLaunch_device(Date dtLaunch_device) {
        this.dtLaunch_device = dtLaunch_device;
    }

    public Date getDtExpiration() {
        return dtExpiration;
    }

    public void setDtExpiration(Date dtExpiration) {
        this.dtExpiration = dtExpiration;
    }

    public Date getDtExpiration_device() {
        return dtExpiration_device;
    }

    public void setDtExpiration_device(Date dtExpiration_device) {
        this.dtExpiration_device = dtExpiration_device;
    }

    public int getAudioRepeatsRemaining() {
        return audioRepeatsRemaining;
    }

    public void setAudioRepeatsRemaining(int audioRepeatsRemaining) {
        this.audioRepeatsRemaining = audioRepeatsRemaining;
    }

    public UUID getUuid_local() {
        return uuid_local;
    }

    public void setUuid_local(UUID uuid_local) {
        this.uuid_local = uuid_local;
    }

    public int getDeliveryCount() {
        return deliveryCount;
    }

    public void setDeliveryCount(int deliveryCount) {
        this.deliveryCount = deliveryCount;
    }

    public Date getDtLastDelivered() {
        return dtLastDelivered;
    }

    public void setDtLastDelivered(Date dtLastDelivered) {
        this.dtLastDelivered = dtLastDelivered;
    }

    public boolean isBeingDelivered() {
        return isBeingDelivered;
    }

    public void setBeingDelivered(boolean beingDelivered) {
        isBeingDelivered = beingDelivered;
    }

    public int getScrollCount() {
        return scrollCount;
    }

    public void setScrollCount(int scrollCount) {
        this.scrollCount = scrollCount;
    }

    public int getVideoSeekPosition() {
        return videoSeekPosition;
    }

    public void setVideoSeekPosition(int videoSeekPosition) {
        this.videoSeekPosition = videoSeekPosition;
    }

    public boolean isReadyToDeliver() {
        return isReadyToDeliver;
    }

    public void setReadyToDeliver(boolean readyToDeliver) {
        isReadyToDeliver = readyToDeliver;
    }

    public boolean isHasAudioComponent() {
        return hasAudioComponent;
    }

    public void setHasAudioComponent(boolean hasAudioComponent) {
        this.hasAudioComponent = hasAudioComponent;
    }

    public boolean isAudioComponentActive() {
        return isAudioComponentActive;
    }

    public void setAudioComponentActive(boolean audioComponentActive) {
        isAudioComponentActive = audioComponentActive;
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
