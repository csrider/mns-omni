package com.messagenetsystems.evolution2.models;

/* OmniMessage model class
 * This is the processed Omni internal message model.
 * It is how we abstract out data to a standardized construct, no matter what its source may be.
 *
 * Usage example:
 *  OmniMessage omniMessage;
 *  omniMessage = new OmniMessage(OmniMessage.LOG_METHOD_FILELOGGER);
 *  omniMessage.initWithRawData(omniApplication.getEcosystem(), omniRawMessage);
 *
 * Notes:
 *  Whenever you initWithRawData, default will first be assigned (this should clear out anything pre-existing, in case you re-use an instance of this)
 *
 * Parcelable... -- DEPRECATED!! --
 * We implement Parcelable, so that we can pass basic primitives of this class through Intents.
 * Note - Members of this class that are not primitive may not pass through, so use static instance if you must.
 * Here's an example of how you might pass an OmniMessage instance through an Intent to an Activity:
 *  Intent intent = new Intent(this, DeliverScrollingMsgActivity.class);
 *  intent.putExtra(DeliverScrollingMsgActivity.INTENT_EXTRA_KEYNAME_OMNIMESSAGE, omniMessage);
 *  startActivity(intent);
 * And how you might retrieve it in the DeliverScrollingMsgActivity:
 *  OmniMessage omniMessage = getIntent().getParcelableExtra(INTENT_EXTRA_KEYNAME_OMNIMESSAGE);
 *
 * Revisions:
 *  2019.12.10      Chris Rider     Created.
 *  2019.12.17      Chris Rider     Added ecosystem support for legacy Omni (BannerMessage stuff).
 *                                  Adding BannerMessage support.
 *  2019.12.19-20   Chris Rider     Deprecating Parcelable, as it's mostly useless.. use static instance or common venue instead.
 *  2020.01.06      Chris Rider     Added export-as-string methods for cases where we need primitive type data.
 *      -- NOTE: The past two changes were attempts to support primitive data for IPC, but it's too much work for now. Will just use statics for now! --
 *  2020.01.07      Chris Rider     Added latest-delivery date members.
 *  2020.01.14      Chris Rider     Added override for .equals() method to do a deep comparison.
 *  2020.01.16      Chris Rider     Implemented copy constructor for creating copies instead of pass by reference.
 *  2020.01.20      Chris Rider     Added synchronized to possibly competing methods among threads.
 *  2020.01.22      Chris Rider     Added methods to import and export metadata as JSON Object or String.
 *  2020.01.27      Chris Rider     Added OmniRawMessage and ecosystem members.
 *  2020.01.28      Chris Rider     Added created and modified Date members, as well as now updating thisLastModified automatically with every setter method; also made init methods set members directly.
 *  2020.01.29      Chris Rider     Fixed bug where setter for new modified Date member was overwriting provided Date argument with current Date.
 *  2020.02.04      Chris Rider     Added message priority and priority-tolerance members and associated logic.
 *  2020.04.20      Chris Rider     Added field and support for new received-at field, so we know when a message was originally received.
 *  2020.04.30      Chris Rider     Finished implementing gender translation and initialization from BannerMessage.
 *                                  Initializing BannerMessage's dbb_audio_tts_gain field value into ttsVoiceVolumeGain member.
 *  2020.05.07-08   Chris Rider     Fixing and updating expiration calculation, so things like clock-drift don't prematurely end a message or cause issues not finding it.
 *  2020.05.08      Chris Rider     Improved deep-comparison equals() method, massively.
 *  2020.06.27      Chris Rider     Updated metadata for saving flasher light duration.
 *  2020.12.28      Chris Rider     Added media playtime member.
 */

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.OmniApplication;
import com.messagenetsystems.evolution2.utilities.DatetimeUtils;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.UUID;


public class OmniMessage {
    private final String TAG = this.getClass().getSimpleName();

    /*============================================================================================*/
    // Constants...
    public final static int MSG_TYPE_UNKNOWN = 0;
    public final static int MSG_TYPE_TEXT = 1;
    public final static int MSG_TYPE_WEB_PAGE = 2;
    public final static int MSG_TYPE_VIDEO_FILE = 3;
    public final static int MSG_TYPE_VIDEO_STREAM = 4;
    public final static int MSG_TYPE_PICTURE = 5;
    public final static int MSG_TYPE_AUDIO_FILE = 6;
    public final static int MSG_TYPE_AUDIO_STREAM = 7;
    public final static int MSG_TYPE_LOCATIONMAP = 8;

    public final static int MSG_SCROLL_SPEED_UNKNOWN = 0;
    public final static int MSG_SCROLL_SPEED_SLOWEST = 1;
    public final static int MSG_SCROLL_SPEED_SLOWER = 2;
    public final static int MSG_SCROLL_SPEED_SLOW = 3;
    public final static int MSG_SCROLL_SPEED_NORMAL = 4;
    public final static int MSG_SCROLL_SPEED_FAST = 5;
    public final static int MSG_SCROLL_SPEED_FASTER = 6;
    public final static int MSG_SCROLL_SPEED_FASTEST = 7;

    public final static int TTS_VOICE_GENDER_UNKNOWN = 0;
    public final static int TTS_VOICE_GENDER_MALE = 1;
    public final static int TTS_VOICE_GENDER_FEMALE = 2;

    public final static int FLASHER_MODE_UNKNOWN = 0;
    public final static int FLASHER_MODE_OFF = 1;
    public final static int FLASHER_MODE_STEADY = 2;
    public final static int FLASHER_MODE_FADE = 3;
    public final static int FLASHER_MODE_FLASH = 4;
    public final static int FLASHER_BRIGHTNESS_UNKNOWN = 0;
    public final static int FLASHER_BRIGHTNESS_MIN = 1;
    public final static int FLASHER_BRIGHTNESS_MED = 2;
    public final static int FLASHER_BRIGHTNESS_MAX = 3;

    public final static int EXPIRATION_CALC_METHOD_RELATIVE_DURATION_FROM_RECEIPT = 1;
    public final static int EXPIRATION_CALC_METHOD_RELATIVE_DURATION_FROM_DELIVERY = 2;
    public final static int EXPIRATION_CALC_METHOD_ABSOLUTE_FROM_SERVER = 3;
    public final static int EXPIRATION_CALC_METHOD_ABSOLUTE_FROM_DEVICE = 4;                        //obeys dtLaunch

    /*============================================================================================*/
    // Logging stuff...
    private final int LOG_SEVERITY_V = 1;
    private final int LOG_SEVERITY_D = 2;
    private final int LOG_SEVERITY_I = 3;
    private final int LOG_SEVERITY_W = 4;
    private final int LOG_SEVERITY_E = 5;
    private int logMethod = Constants.LOG_METHOD_LOGCAT;

    /*============================================================================================*/
    // Local stuff...
    WeakReference<Context> appContextRef = null;

    /*============================================================================================*/
    // Model members...
    private int ecosystem = OmniApplication.ECOSYSTEM_UNKNOWN;
    private OmniRawMessage omniRawMessage = null;                                                   //just for easy reference if needed
    private BannerMessage bannerMessage = null;                                                     //just for legacy Omni support

    private UUID msgUUID = null;                                                                    //the UUID assigned upon first receipt of data
    private JSONObject msgRawJSONObject = null;                                                     //the JSONObject from OmniRawMessage
    private Date msgRawCreated = null;
    private Date msgRawModified = null;
    private Date msgRawReceived = null;
    private Date thisCreatedDate = null;
    private Date thisLastModifiedDate = null;

    private Date msgFirstDeliveryBeganDate = null;
    private Date msgLatestDeliveryBeganDate = null;     //TODO not yet used, just an idea 2020.01.07
    private Date msgLatestDeliveryEndedDate = null;     //TODO not yet used, just an idea 2020.01.07

    private int msgDuration = 0;
    private Date msgExpires = null;
    private int msgType = MSG_TYPE_UNKNOWN;                                                         //NOTE! This "type" is different than legacy MNS v1 type (which was txt-msg header / emergency severity)

    private int msgPriority = 0;
    private int msgPriorityTolerance = 0;

    private String msgHeading_textColorHex = Constants.Colors.BLACK_HEX;
    private String msgHeading_backgroundColorHex = Constants.Colors.WHITE_HEX;
    private String msgText_textColorHex = Constants.Colors.WHITE_HEX;
    private String msgText_backgroundColorHex = Constants.Colors.BLACK_HEX;
    private String msgDetails_textColorHex = Constants.Colors.BLACK_HEX;
    private String msgDetails_backgroundColorHex = Constants.Colors.WHITE_HEX;
    private String msgHeading = null;
    private String msgText = null;
    private String msgDetails = null;
    private int msgTextScrollSpeed = MSG_SCROLL_SPEED_UNKNOWN;
    private long msgTextScrollsToDo = 0;
    private long msgTextScrollsDone = 0;

    private long ttsNumberOfSpeaksToDo = 0;                                                         //if you don't want TTS, then make this 0
    private long ttsNumberOfSpeaksDone = 0;                                                         //for counting number of times TTS speaks the message
    private int ttsVoiceGender = TTS_VOICE_GENDER_UNKNOWN;
    private int ttsVoiceVolumeBase;
    private int ttsVoiceVolumeGain;

    private int flasherMode = FLASHER_MODE_UNKNOWN;
    private int flasherBrightness = FLASHER_BRIGHTNESS_UNKNOWN;
    private int flasherColor;
    private long flasherDurationSecondsToDo = 0;
    private long flasherDurationSecondsDone = 0;

    private long mediaPlaytime = 0;


    /** Constructor
     * @param appContext    Application context
     * @param logMethod     Logging method to use
     */
    public OmniMessage(Context appContext, int logMethod) {
        Log.v(TAG, "Instantiating.");

        this.appContextRef = new WeakReference<Context>(appContext);
        this.logMethod = logMethod;

        try {
            initDefaults();
        } catch (Exception e) {
            logE("Exception caught instantiating "+TAG+": "+e.getMessage());
        }
    }

    /** Copy-constructor
     * DEV-NOTE: Don't forget update/add members here, as modified in this class, for complete deep-copy reliability.
     */
    OmniMessage(OmniMessage om) {
        Log.v(TAG, "Copy-constructor invoked.");

        ecosystem = om.ecosystem;
        omniRawMessage = om.omniRawMessage;
        bannerMessage = om.bannerMessage;

        msgUUID = om.msgUUID;
        msgRawJSONObject = om.msgRawJSONObject;
        msgRawCreated = om.msgRawCreated;
        msgRawModified = om.msgRawModified;
        msgRawReceived = om.msgRawReceived;
        thisCreatedDate = om.thisCreatedDate;
        thisLastModifiedDate = om.thisLastModifiedDate;

        msgFirstDeliveryBeganDate = om.msgFirstDeliveryBeganDate;
        msgLatestDeliveryBeganDate = om.msgLatestDeliveryBeganDate;
        msgLatestDeliveryEndedDate = om.msgLatestDeliveryEndedDate;

        msgDuration = om.msgDuration;
        msgExpires = om.msgExpires;
        msgType = om.msgType;

        msgPriority = om.msgPriority;
        msgPriorityTolerance = om.msgPriorityTolerance;

        msgHeading_textColorHex = om.msgHeading_textColorHex;
        msgHeading_backgroundColorHex = om.getMsgHeading_backgroundColorHex();
        msgText_textColorHex = om.getMsgText_textColorHex();
        msgText_backgroundColorHex = om.getMsgText_backgroundColorHex();
        msgDetails_textColorHex = om.msgDetails_textColorHex;
        msgDetails_backgroundColorHex = om.msgDetails_backgroundColorHex;
        msgHeading = om.msgHeading;
        msgText = om.msgText;
        msgDetails = om.msgDetails;
        msgTextScrollSpeed = om.msgTextScrollSpeed;
        msgTextScrollsToDo = om.msgTextScrollsToDo;
        msgTextScrollsDone = om.msgTextScrollsDone;

        ttsNumberOfSpeaksToDo = om.ttsNumberOfSpeaksToDo;
        ttsNumberOfSpeaksDone = om.ttsNumberOfSpeaksDone;
        ttsVoiceGender = om.ttsVoiceGender;
        ttsVoiceVolumeBase = om.ttsVoiceVolumeBase;
        ttsVoiceVolumeGain = om.ttsVoiceVolumeGain;

        flasherMode = om.flasherMode;
        flasherBrightness = om.flasherBrightness;
        flasherColor = om.flasherColor;
        flasherDurationSecondsToDo = om.flasherDurationSecondsToDo;
        flasherDurationSecondsDone = om.flasherDurationSecondsDone;

        mediaPlaytime = om.mediaPlaytime;
    }

    /** Override our .equals() method to actually check equality of member field values (deep comparison).
     * If you don't do this, the native method will only check if the provided object is the same instance.
     * @param o Another OmniMessage object to check for equality of member field values.
     * @return Whether member field values equal those of the provided object or not.
     */
    @Override
    public boolean equals(Object o) {
        final String TAGG = "equals: ";

        boolean areStringsEqual, areNumbersEqual, areDatesEqual, areJsonObjectsEqual;

        //The object instances are actually the very same instance, so their values obviously equal
        if (this == o) {
            logW(TAGG+"Provided object is the exact same instance. Returning true.");
            return true;
        }

        // The provided object is null or not the same class, so obviously not equal
        if (o == null || getClass() != o.getClass()) {
            logW(TAGG+"Provided object is null or not an OmniMessage object. Returning false.");
            return false;
        }

        // Now we can go ahead and check member-values for equality
        boolean ret = false;
        OmniMessage omniMessage = (OmniMessage) o;

        try {
            areStringsEqual = areStringsEqual(msgHeading_textColorHex, omniMessage.msgHeading_textColorHex)
                    && areStringsEqual(msgHeading_backgroundColorHex, omniMessage.msgHeading_backgroundColorHex)
                    && areStringsEqual(msgText_textColorHex, omniMessage.msgText_textColorHex)
                    && areStringsEqual(msgText_backgroundColorHex, omniMessage.msgText_backgroundColorHex)
                    && areStringsEqual(msgHeading, omniMessage.msgHeading)
                    && areStringsEqual(msgText, omniMessage.msgText)
                    && areStringsEqual(msgDetails, omniMessage.msgDetails)
                    ;

            areNumbersEqual = areNumbersEqual(msgType, omniMessage.msgType)
                    && areNumbersEqual(msgPriority, omniMessage.msgPriority)
                    && areNumbersEqual(msgPriorityTolerance, omniMessage.msgPriorityTolerance)
                    && areNumbersEqual(msgDuration, omniMessage.msgDuration)
                    && areNumbersEqual(msgTextScrollSpeed, omniMessage.msgTextScrollSpeed)
                    && areNumbersEqual(msgTextScrollsDone, omniMessage.msgTextScrollsDone)
                    && areNumbersEqual(msgTextScrollsToDo, omniMessage.msgTextScrollsToDo)
                    && areNumbersEqual(ttsVoiceGender, omniMessage.ttsVoiceGender)
                    && areNumbersEqual(ttsVoiceVolumeBase, omniMessage.ttsVoiceVolumeBase)
                    && areNumbersEqual(ttsVoiceVolumeGain, omniMessage.ttsVoiceVolumeGain)
                    && areNumbersEqual(ttsNumberOfSpeaksDone, omniMessage.ttsNumberOfSpeaksDone)
                    && areNumbersEqual(ttsNumberOfSpeaksToDo, omniMessage.ttsNumberOfSpeaksToDo)
                    && areNumbersEqual(flasherBrightness, omniMessage.flasherBrightness)
                    && areNumbersEqual(flasherColor, omniMessage.flasherColor)
                    && areNumbersEqual(flasherMode, omniMessage.flasherMode)
                    && areNumbersEqual(flasherDurationSecondsDone, omniMessage.flasherDurationSecondsDone)
                    && areNumbersEqual(flasherDurationSecondsToDo, omniMessage.flasherDurationSecondsToDo)
                    && areNumbersEqual(mediaPlaytime, omniMessage.mediaPlaytime)
                    ;

            areDatesEqual = areDatesEqual(msgRawCreated, omniMessage.msgRawCreated)
                    && areDatesEqual(msgRawModified, omniMessage.msgRawModified)
                    && areDatesEqual(msgRawReceived, omniMessage.msgRawReceived)
                    && areDatesEqual(msgFirstDeliveryBeganDate, omniMessage.msgFirstDeliveryBeganDate)
                    && areDatesEqual(msgLatestDeliveryBeganDate, omniMessage.msgLatestDeliveryBeganDate)
                    && areDatesEqual(msgLatestDeliveryEndedDate, omniMessage.msgLatestDeliveryEndedDate)
                    && areDatesEqual(msgExpires, omniMessage.msgExpires)
                    ;

            areJsonObjectsEqual = areJsonObjectsEqual(msgRawJSONObject, omniMessage.msgRawJSONObject);

            ret = areStringsEqual
                    && areNumbersEqual
                    && areDatesEqual
                    && areJsonObjectsEqual
                    ;
        } catch (Exception e) {
            logW(TAGG+"Exception caught testing equalities: "+e.getMessage());
        }

        logV(TAGG+"Returning "+Boolean.toString(ret)+".");
        return ret;
    }

    private boolean areStringsEqual(String val1, String val2) {
        final String TAGG = "areStringsEqual(\""+String.valueOf(val1)+"\",\""+String.valueOf(val2)+"\"): ";
        boolean ret;

        // Normalize values to actual strings so we don't have any null pointer issues
        val1 = String.valueOf(val1);
        val2 = String.valueOf(val2);

        if (val1.equals(val2)) {
            ret = true;
        } else {
            ret = false;
        }

        logV(TAGG+"Returning "+Boolean.toString(ret)+".");
        return ret;
    }

    private boolean areNumbersEqual(long val1, long val2) {
        final String TAGG = "areNumbersEqual("+String.valueOf(val1)+","+String.valueOf(val2)+"): ";
        boolean ret;

        if (val1 == val2) {
            ret = true;
        } else {
            ret = false;
        }

        logV(TAGG+"Returning "+Boolean.toString(ret)+".");
        return ret;
    }
    private boolean areNumbersEqual(int val1, int val2) {
        return areNumbersEqual((long)val1, (long)val2);
    }

    private boolean areDatesEqual(Date val1, Date val2) {
        final String TAGG = "areDatesEqual("+String.valueOf(val1)+","+String.valueOf(val2)+"): ";
        boolean ret;
        String strVal1, strVal2;

        if (val1 == null) {
            strVal1 = "(null)";
        } else {
            strVal1 = Long.toString(val1.getTime());
        }

        if (val2 == null) {
            strVal2 = "(null)";
        } else {
            strVal2 = Long.toString(val2.getTime());
        }

        ret = areStringsEqual(strVal1, strVal2);

        logV(TAGG+"Returning "+Boolean.toString(ret)+".");
        return ret;
    }

    private boolean areJsonObjectsEqual(JSONObject val1, JSONObject val2) {
        final String TAGG = "areJsonObjectsEqual("+String.valueOf(val1)+","+String.valueOf(val2)+"): ";
        boolean ret;
        String strVal1, strVal2;

        if (val1 == null) {
            strVal1 = "(null)";
        } else {
            strVal1 = val1.toString();
        }

        if (val2 == null) {
            strVal2 = "(null)";
        } else {
            strVal2 = val2.toString();
        }

        ret = areStringsEqual(strVal1, strVal2);

        logV(TAGG+"Returning "+Boolean.toString(ret)+".");
        return ret;
    }


    /*============================================================================================*/
    /* Initialization Methods */

    private void initDefaults() {
        final String TAGG = "initDefaults: ";
        logV(TAGG+"Invoked.");

        logW(TAGG+"Not developed yet!");    //TODO
    }

    /** Initialize with raw data (from OmniRawMessage object).
     * Supporting methods included just below this one.
     * @param ecosystem         Ecosystem flag (from OmniApplication) to indicate which platform the raw data originated from, so we know how to parse it.
     * @param omniRawMessage    Actual raw data in the form of an OmniRawMessage object.
     * @return Whether completed without apparent issues or not.
     */
    public boolean initWithRawData(int ecosystem, OmniRawMessage omniRawMessage) {
        final String TAGG = "initWithRawData("+omniRawMessage.getMessageUUID().toString()+"): ";
        logV(TAGG+"Invoked.");

        this.ecosystem = ecosystem;
        //this.omniRawMessage = omniRawMessage;
        this.omniRawMessage = new OmniRawMessage(omniRawMessage);   //embed a copy of the raw message (if we do by reference, might be problematic? -- but maybe we need fresh data readily?

        boolean didCompleteApparentlyOkay = false;

        switch (ecosystem) {
            case OmniApplication.ECOSYSTEM_MESSAGENET_CONNECTIONS_V1:
                // Double-check existence of legacy JSON contents...
                didCompleteApparentlyOkay = init_messagenetConnections_v1(omniRawMessage);
                break;
            case OmniApplication.ECOSYSTEM_MESSAGENET_CONNECTIONS_V2:
                didCompleteApparentlyOkay = init_messagenetConnections(omniRawMessage);
                break;
            case OmniApplication.ECOSYSTEM_STANDARD_API:
                didCompleteApparentlyOkay = init_standardAPI(omniRawMessage);
                break;
            default:
                logW("Unhandled ecosystem, don't know how to parse message data. Aborting.");
                break;
        }

        if (didCompleteApparentlyOkay) {
            logV(TAGG+"OmniMessage initialized for ecosystem "+ecosystem+": "+getMessageUUID()+".");
            Date nowDate = new Date();
            this.thisCreatedDate = nowDate;
            this.thisLastModifiedDate = nowDate;
        }

        logD(TAGG+"Returning "+Boolean.toString(didCompleteApparentlyOkay)+".");
        return didCompleteApparentlyOkay;
    }
    private boolean init_messagenetConnections_v1(OmniRawMessage omniRawMessage) {
        final String TAGG = "init_messagenetConnections_v1("+omniRawMessage.getMessageUUID().toString()+"): ";
        logV(TAGG+"Invoked.");

        boolean valid;
        boolean ret = false;

        // Initialize with default values, first
        initDefaults();

        // Double-check existence of valid legacy JSON content...
        try {
            if (omniRawMessage.getMessageJSONObject() == null
                    || omniRawMessage.getMessageJSONObject().getJSONArray("bannermessages") == null) {
                logW(TAGG+"Message-JSON seems to be invalid!");
                valid = false;
            } else {
                //seems valid
                valid = true;
            }
        } catch (Exception e) {
            logW(TAGG+"JSON seems to be invalid!");
            valid = false;
        }

        DatetimeUtils datetimeUtils = new DatetimeUtils(appContextRef.get(), Constants.LOG_METHOD_FILELOGGER);

        // Now we can initialize this object's members with data parsed from raw object
        if (valid) {
            try {
                logW(TAGG + "Not fully developed yet!");    //TODO parse JSON provided by a MessageNet Connections system (Banner)

                // Set basic and raw members
                this.msgUUID = omniRawMessage.getMessageUUID();
                this.msgRawJSONObject = omniRawMessage.getMessageJSONObject();
                this.msgRawCreated = omniRawMessage.getCreatedAt();
                this.msgRawModified = omniRawMessage.getModifiedAt();
                this.msgRawReceived = omniRawMessage.getReceivedAt();

                // Extract raw JSON and instantiate a BannerMessage object with it (this is what the legacy app did)
                // We just include the legacy object as a member of OmniMessage, for ease of development and to speed along initial testing (not so much backward compatibility focused, but of course it helps that, too)
                JSONObject bannermessageFromJSON = omniRawMessage.getMessageJSONObject().getJSONArray("bannermessages").getJSONObject(0);
                BannerMessage bannerMessage = new BannerMessage(appContextRef.get(), logMethod, bannermessageFromJSON);
                this.bannerMessage = bannerMessage;

                // Now, convert as many BannerMessage members to OmniMessage members as possible (might as well)
                this.msgType = bannerMessage.exportOmniMessage_msgType();
                this.msgPriority = bannerMessage.getDbb_priority();
                this.msgPriorityTolerance = bannerMessage.getDbb_priority_tolerance();
                this.msgDuration = bannerMessage.getDbb_duration();
                this.msgExpires = bannerMessage.getDtExpiration();

                this.msgHeading = bannerMessage.exportOmniMessage_msgHeading();
                this.msgText = bannerMessage.getMsgText();
                this.msgDetails = bannerMessage.getMsgDetails();
                this.msgTextScrollSpeed = bannerMessage.getDbb_speed();

                setTtsVoiceGender(bannerMessage.getDss_gender());
                setTtsVoiceVolumeGain(bannerMessage.getDbb_audio_tts_gain());
                this.ttsNumberOfSpeaksToDo = bannerMessage.getAudioRepeatsRemaining();

                this.flasherMode = bannerMessage.exportOmniMessage_flasherMode();
                //setFlasherColor(bannerMessage.exportOmniMessage_flasherColor());  //TODO
                this.flasherBrightness = bannerMessage.exportOmniMessage_flasherBrightness();
                this.flasherDurationSecondsToDo = bannerMessage.exportOmniMessage_flasherDuration();

                this.mediaPlaytime = bannerMessage.getDbb_playtime_duration();

                ret = true;
            } catch (Exception e) {
                logE(TAGG + "Exception caught: " + e.getMessage());
            }
        } else {
            try {
                logV(TAGG + "Message JSON: " + omniRawMessage.getMessageJSONObject().toString());
            } catch (Exception e) {
                logW(TAGG + "Message JSON: "+String.valueOf(omniRawMessage.getMessageJSONObject()));
            }
        }

        logV(TAGG+"Returning "+Boolean.toString(ret)+".");
        return ret;
    }
    private boolean init_messagenetConnections(OmniRawMessage omniRawMessage) {
        final String TAGG = "init_messagenetConnections("+omniRawMessage.getMessageUUID().toString()+"): ";
        logV(TAGG+"Invoked.");

        boolean ret = false;

        // Initialize with default values, first
        initDefaults();

        try {
            logW(TAGG+"Not developed yet!");    //TODO parse JSON provided by a MessageNet Connections system

            // Set basic and raw members
            this.msgUUID = omniRawMessage.getMessageUUID();
            this.msgRawJSONObject = omniRawMessage.getMessageJSONObject();
            this.msgRawCreated = omniRawMessage.getCreatedAt();
            this.msgRawModified = omniRawMessage.getModifiedAt();
            this.msgRawReceived = omniRawMessage.getReceivedAt();

            //TODO: more parsing/setting

            ret = true;
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning "+Boolean.toString(ret)+".");
        return ret;
    }
    private boolean init_standardAPI(OmniRawMessage omniRawMessage) {
        final String TAGG = "init_standardAPI("+omniRawMessage.getMessageUUID().toString()+"): ";
        logV(TAGG+"Invoked.");

        boolean ret = false;

        // Initialize with default values, first
        initDefaults();

        try {
            logW(TAGG+"Not developed yet!");    //TODO parse JSON provided by the standard/open API

            // Set basic and raw members
            this.msgUUID = omniRawMessage.getMessageUUID();
            this.msgRawJSONObject = omniRawMessage.getMessageJSONObject();
            this.msgRawCreated = omniRawMessage.getCreatedAt();
            this.msgRawModified = omniRawMessage.getModifiedAt();
            this.msgRawReceived = omniRawMessage.getReceivedAt();

            //TODO: parsing/setting

            ret = true;
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning "+Boolean.toString(ret)+".");
        return ret;
    }


    /*============================================================================================*/
    /* Utility Methods */

    /** Checks if this message is expired.
     * Intelligent enough to know whether we're dealing with legacy BannerMessage or not, also.
     * @return Boolean of whether the provided message is expired.
     */
    public boolean isExpired(int expirationCalculationMethod, boolean defaultToReturn, @Nullable Context context) {
        final String TAGG = "isExpired: ";
        boolean ret = defaultToReturn;

        try {
            Date baseDate;
            Date expiryDate = null;
            Date currentDate = new Date();
            DatetimeUtils datetimeUtils = null;

            // Get a DatetimeUtils instance somehow
            // Dev-note: This OmniMessage instance has a null local appContextRef when this method is invoked from MessageDeliverableProcessor... not sure why, just wrote this to deal with it for now...
            if (appContextRef != null) {
                //prefer to use local resource if possible
                datetimeUtils = new DatetimeUtils(appContextRef.get(), Constants.LOG_METHOD_FILELOGGER);
            } else if (context != null) {
                //otherwise fall back to whatever context may be provided
                logW(TAGG+"OmniMessage's context's weak reference is null, using provided context...");
                datetimeUtils = new DatetimeUtils(context, Constants.LOG_METHOD_FILELOGGER);
            } else {
                //else it's not possible to get a DatetimeUtils instance without a context, so abort
                logE(TAGG+"Unable to get DatetimeUtils instance due to null context. Aborting and returning "+Boolean.toString(ret)+".");
                return ret;
            }

            // Calculate the proper expiration date
            switch (expirationCalculationMethod) {
                case EXPIRATION_CALC_METHOD_RELATIVE_DURATION_FROM_RECEIPT:
                    //calculate expiration relative to first-receipt of the message (received + duration)
                    logV(TAGG+"Calculating expiration based on duration from first receipt.");
                    baseDate = this.getOmniRawMessage().getReceivedAt();
                    expiryDate = datetimeUtils.calculateExpirationDate_fromDurationInSecs(baseDate, this.msgDuration);
                    logD(TAGG+"Using first-received date ("+baseDate.toString()+") to calculate expiration ("+expiryDate.toString()+").");
                    break;
                case EXPIRATION_CALC_METHOD_RELATIVE_DURATION_FROM_DELIVERY:
                    //calculate expiration relative to first-delivery of the message (first-delivered + duration)
                    logV(TAGG+"Calculating expiration based on duration from first delivery.");
                    if (this.msgFirstDeliveryBeganDate == null) {
                        //message hasn't yet been delivered, so use a long-out date (implicitly not-yet-expired)
                        baseDate = datetimeUtils.getDateFromString_mmddyyyy("12-31-2499");
                        expiryDate = datetimeUtils.calculateExpirationDate_fromDurationInSecs(baseDate, this.msgDuration);
                        logD(TAGG+"Using first-delivered date (not delivered yet) to calculate expiration ("+expiryDate.toString()+").");
                    } else {
                        baseDate = this.msgFirstDeliveryBeganDate;
                        expiryDate = datetimeUtils.calculateExpirationDate_fromDurationInSecs(this.msgFirstDeliveryBeganDate, this.msgDuration);
                        logD(TAGG+"Using first-delivered date ("+baseDate.toString()+") to calculate expiration ("+expiryDate.toString()+").");
                    }
                    break;
                case EXPIRATION_CALC_METHOD_ABSOLUTE_FROM_SERVER:
                    //use absolute expiration given to us in the original message data from the server
                    //note: this is an old relic from the legacy MessageNet Connections system, likely won't use any longer?
                    logV(TAGG+"Calculating expiration based on explicit expiration value from message originator.");
                    logW(TAGG+"Absolute expiration from the server is no longer supported!");
                    break;
                //case EXPIRATION_CALC_METHOD_ABSOLUTE_FROM_DEVICE:
                //use absolute expiration given to us in the original message data as calculated
                //break;
                default:
                    logE(TAGG+"Unhandled case!");
                    break;
            }

            // Determine whether we are expired or not
            try {
                if (currentDate.getTime() >= expiryDate.getTime()) {
                    //expired
                    ret = true;
                } else if (currentDate.getTime() <= expiryDate.getTime()) {
                    //not yet expired
                    ret = false;
                }
            } catch (Exception e) {
                logW(TAGG+"Exception caught determining expiration state: "+e.getMessage());
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        /* OLD... before moving to just using device delivery-start + duration...
        try {
            Date currentDate = new Date();
            if (this.getMsgExpires() != null) {
                logD(TAGG+"Current time ("+currentDate.toString()+"). Message expiration time("+this.getMsgExpires().toString()+").");
                if (currentDate.getTime() > this.getMsgExpires().getTime()) {
                    ret = true;
                }
            } else {
                //we might be using legacy msg data, so check that...
                BannerMessage bannerMessage = this.getBannerMessage();
                if (bannerMessage != null) {
                    //TODO: possible future intelligence/revision around platform-defined-absolute vs on-device-calculated expiration?
                    if (bannerMessage.getDtExpiration() != null && currentDate.getTime() > bannerMessage.getDtExpiration().getTime()) {
                        logD(TAGG+"getDtExpiration (absolute) has occurred.");
                        ret = true;
                    } else if (bannerMessage.getDtExpiration_device() != null && currentDate.getTime() > bannerMessage.getDtExpiration_device().getTime()) {
                        logD(TAGG+"getDtExpiration_device (relative) has occurred.");
                        ret = true;
                    } else {
                        logW(TAGG + "Expiration Date is null (for both OmniMessage and legacy BannerMessage), cannot determine.");
                    }
                } else {
                    logW(TAGG + "Expiration Date is null, cannot determine.");
                }
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
        */

        logV(TAGG+"Returning "+Boolean.toString(ret)+".");
        return ret;
    }


    /*============================================================================================*/
    /* Export Methods */

    /** This exports the message data in this model to a JSONObject.
     * You can use that JSONObject to feed back into Message-RoomDB or OmniRawMessage, for instance.
     * @param ecosystem
     * @return JSONObject with the data in this instance.
     */
    public JSONObject exportMsgToJSONObject(int ecosystem) {
        final String TAGG = "exportMsgToJSONObject: ";
        logV(TAGG+"Invoked.");

        JSONObject ret = null;

        try {
            switch (ecosystem) {
                case OmniApplication.ECOSYSTEM_MESSAGENET_CONNECTIONS_V1:
                    //TODO!
                    break;
                case OmniApplication.ECOSYSTEM_MESSAGENET_CONNECTIONS_V2:
                    ret = exportMsgToJSONObject_messagenetConnections();
                    break;
                case OmniApplication.ECOSYSTEM_STANDARD_API:
                    ret = exportMsgToJSONObject_standardAPI();
                    break;
                default:
                    logW("Unhandled ecosystem, don't know how to structure message data. Aborting.");
                    break;
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logD(TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }
    private JSONObject exportMsgToJSONObject_messagenetConnections() {
        final String TAGG = "exportMsgToJSONObject_messagenetConnections: ";
        logV(TAGG+"Invoked.");

        JSONObject ret = null;

        try {
            ret = new JSONObject();
            //ret.put("msgTextScrollsDone", this.msgTextScrollsDone);
            //TODO: add more message types of fields
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }
    private JSONObject exportMsgToJSONObject_standardAPI() {
        final String TAGG = "exportMsgToJSONObject_standardAPI: ";
        logV(TAGG+"Invoked.");

        JSONObject ret = null;

        try {
            logW(TAGG+"Not developed yet!");    //TODO create JSON object
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    /** This exports the message data in this model to a JSON string.
     * You can use that string as a primitive (for passing between processes, etc.).
     * @param ecosystem
     * @return String of JSON with the data in this instance.
     */
    public String exportMsgToJSONString(int ecosystem) {
        final String TAGG = "exportMsgToJSONString: ";
        logV(TAGG+"Invoked.");

        String ret = null;

        try {
            switch (ecosystem) {
                case OmniApplication.ECOSYSTEM_MESSAGENET_CONNECTIONS_V1:
                    //TODO!
                    break;
                case OmniApplication.ECOSYSTEM_MESSAGENET_CONNECTIONS_V2:
                    ret = exportMsgToJSONString_messagenetConnections();
                    break;
                case OmniApplication.ECOSYSTEM_STANDARD_API:
                    ret = exportMsgToJSONString_standardAPI();
                    break;
                default:
                    logW("Unhandled ecosystem, don't know how to structure message data. Aborting.");
                    break;
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logD(TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }
    private String exportMsgToJSONString_messagenetConnections() {
        final String TAGG = "exportMsgToJSONString_messagenetConnections: ";
        logV(TAGG+"Invoked.");

        String ret = null;

        try {
            logW(TAGG+"Not developed yet!");    //TODO create JSON object
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }
    private String exportMsgToJSONString_standardAPI() {
        final String TAGG = "exportMsgToJSONString_standardAPI: ";
        logV(TAGG+"Invoked.");

        String ret = null;

        try {
            logW(TAGG+"Not developed yet!");    //TODO create JSON object
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    /** This imports metadata into this model. */
    public void importMetadata(JSONObject jsonObject) {
        final String TAGG = "importMetadata: ";
        logV(TAGG+"Invoked.");

        try {
            this.msgTextScrollsDone = jsonObject.getLong("msgTextScrollsDone");
            this.flasherDurationSecondsDone = jsonObject.getLong("flasherDurationSecondsDone");
            //TODO: add mroe metadata types of fields

            this.thisLastModifiedDate = new Date();
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }
    public void importMetadata(String jsonString) {
        final String TAGG = "importMetadata: ";
        logV(TAGG+"Invoked.");

        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            importMetadata(jsonObject);
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }

    /** This exports the metadata in this model as a JSONObject. */
    public JSONObject exportMetaToJSONObject() {
        final String TAGG = "exportMetaToJSONObject: ";
        logV(TAGG+"Invoked.");

        JSONObject ret = null;

        try {
            ret = new JSONObject();
            ret.put("msgTextScrollsDone", this.msgTextScrollsDone);
            ret.put("flasherDurationSecondsDone", this.flasherDurationSecondsDone);
            //TODO: add more metadata types of fields
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    /** This exports the metadata in this model as a JSON string.
     * You can use that string as a primitive (for passing between processes, etc.). */
    public String exportMetaToJSONString() {
        final String TAGG = "exportMetaToJSONString: ";
        logV(TAGG+"Invoked.");

        String ret = null;

        try {
            ret = exportMetaToJSONObject().toString();
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }


    /*============================================================================================*/
    /* Subclasses */




    /*============================================================================================*/
    /* Getter & Setter Methods */

    public int getEcosystem() {
        return ecosystem;
    }

    public void setEcosystem(int ecosystem) {
        this.ecosystem = ecosystem;
        this.thisLastModifiedDate = new Date();
    }

    public OmniRawMessage getOmniRawMessage() {
        return omniRawMessage;
    }

    public void setOmniRawMessage(OmniRawMessage omniRawMessage) {
        this.omniRawMessage = omniRawMessage;
        this.thisLastModifiedDate = new Date();
    }

    public BannerMessage getBannerMessage() {
        return bannerMessage;
    }

    public void setBannerMessage(BannerMessage bannerMessage) {
        this.bannerMessage = bannerMessage;
        this.thisLastModifiedDate = new Date();
    }

    public UUID getMessageUUID() {
        return msgUUID;
    }

    public void setMessageUUID(UUID msgUUID) {
        this.msgUUID = msgUUID;
        this.thisLastModifiedDate = new Date();
    }

    public JSONObject getMessageJSONObject() {
        return msgRawJSONObject;
    }

    public void setMessageJSONObject(JSONObject msgRawJSONObject) {
        this.msgRawJSONObject = msgRawJSONObject;
        this.thisLastModifiedDate = new Date();
    }

    public Date getMsgRawCreated() {
        return msgRawCreated;
    }

    public void setMsgRawCreated(Date msgRawCreated) {
        this.msgRawCreated = msgRawCreated;
        this.thisLastModifiedDate = new Date();
    }

    public Date getMsgRawModified() {
        return msgRawModified;
    }

    public void setMsgRawModified(Date msgRawModified) {
        this.msgRawModified = msgRawModified;
        this.thisLastModifiedDate = new Date();
    }

    public Date getMsgRawReceived() {
        return msgRawReceived;
    }

    public void setMsgRawReceived(Date msgRawReceived) {
        this.msgRawReceived = msgRawReceived;
        this.thisLastModifiedDate = new Date();
    }

    public Date getThisCreatedDate() {
        return thisCreatedDate;
    }

    public void setThisCreatedDate(Date thisCreatedDate) {
        this.thisCreatedDate = thisCreatedDate;
        this.thisLastModifiedDate = new Date();
    }

    public Date getThisLastModifiedDate() {
        return thisLastModifiedDate;
    }

    public void setThisLastModifiedDate(Date thisLastModifiedDate) {
        this.thisLastModifiedDate = thisLastModifiedDate;
    }

    public Date getMsgLatestDeliveryBeganDate() {
        return msgLatestDeliveryBeganDate;
    }

    public void setMsgLatestDeliveryBeganDate(Date msgLatestDeliveryBeganDate) {
        this.msgLatestDeliveryBeganDate = msgLatestDeliveryBeganDate;
        this.thisLastModifiedDate = new Date();
    }

    public Date getMsgLatestDeliveryEndedDate() {
        return msgLatestDeliveryEndedDate;
    }

    public void setMsgLatestDeliveryEndedDate(Date msgLatestDeliveryEndedDate) {
        this.msgLatestDeliveryEndedDate = msgLatestDeliveryEndedDate;
        this.thisLastModifiedDate = new Date();
    }

    public Date getMsgExpires() {
        return msgExpires;
    }

    public void setMsgExpires(Date msgExpires) {
        this.msgExpires = msgExpires;
        this.thisLastModifiedDate = new Date();
    }

    public int getMsgType() {
        return msgType;
    }

    public void setMsgType(int msgType) {
        this.msgType = msgType;
        this.thisLastModifiedDate = new Date();
    }

    public int getMsgPriority() {
        return msgPriority;
    }

    public void setMsgPriority(int msgPriority) {
        this.msgPriority = msgPriority;
        this.thisLastModifiedDate = new Date();
    }

    public int getMsgPriorityTolerance() {
        return msgPriorityTolerance;
    }

    public void setMsgPriorityTolerance(int msgPriorityTolerance) {
        this.msgPriorityTolerance = msgPriorityTolerance;
        this.thisLastModifiedDate = new Date();
    }

    public String getMsgHeading_textColorHex() {
        return msgHeading_textColorHex;
    }

    public void setMsgHeading_textColorHex(String msgHeading_textColorHex) {
        this.msgHeading_textColorHex = msgHeading_textColorHex;
        this.thisLastModifiedDate = new Date();
    }

    public String getMsgHeading_backgroundColorHex() {
        return msgHeading_backgroundColorHex;
    }

    public void setMsgHeading_backgroundColorHex(String msgHeading_backgroundColorHex) {
        this.msgHeading_backgroundColorHex = msgHeading_backgroundColorHex;
        this.thisLastModifiedDate = new Date();
    }

    public String getMsgText_textColorHex() {
        return msgText_textColorHex;
    }

    public void setMsgText_textColorHex(String msgText_textColorHex) {
        this.msgText_textColorHex = msgText_textColorHex;
        this.thisLastModifiedDate = new Date();
    }

    public String getMsgText_backgroundColorHex() {
        return msgText_backgroundColorHex;
    }

    public void setMsgText_backgroundColorHex(String msgText_backgroundColorHex) {
        this.msgText_backgroundColorHex = msgText_backgroundColorHex;
        this.thisLastModifiedDate = new Date();
    }

    public String getMsgDetails_textColorHex() {
        return msgDetails_textColorHex;
    }

    public void setMsgDetails_textColorHex(String msgDetails_textColorHex) {
        this.msgDetails_textColorHex = msgDetails_textColorHex;
        this.thisLastModifiedDate = new Date();
    }

    public String getMsgDetails_backgroundColorHex() {
        return msgDetails_backgroundColorHex;
    }

    public void setMsgDetails_backgroundColorHex(String msgDetails_backgroundColorHex) {
        this.msgDetails_backgroundColorHex = msgDetails_backgroundColorHex;
        this.thisLastModifiedDate = new Date();
    }

    public String getMsgHeading() {
        return msgHeading;
    }

    public void setMsgHeading(String msgHeading) {
        this.msgHeading = msgHeading;
        this.thisLastModifiedDate = new Date();
    }

    public String getMsgText() {
        return msgText;
    }

    public void setMsgText(String msgText) {
        this.msgText = msgText;
        this.thisLastModifiedDate = new Date();
    }

    public String getMsgDetails() {
        return msgDetails;
    }

    public void setMsgDetails(String msgDetails) {
        this.msgDetails = msgDetails;
        this.thisLastModifiedDate = new Date();
    }

    public int getMsgTextScrollSpeed() {
        return msgTextScrollSpeed;
    }

    public void setMsgTextScrollSpeed(int msgTextScrollSpeed) {
        this.msgTextScrollSpeed = msgTextScrollSpeed;
        this.thisLastModifiedDate = new Date();
    }

    public long getMsgTextScrollsToDo() {
        return msgTextScrollsToDo;
    }

    public void setMsgTextScrollsToDo(long msgTextScrollsToDo) {
        this.msgTextScrollsToDo = msgTextScrollsToDo;
        this.thisLastModifiedDate = new Date();
    }

    public long getMsgTextScrollsDone() {
        return msgTextScrollsDone;
    }

    public synchronized void setMsgTextScrollsDone(long msgTextScrollsDone) {
        logV("setMsgTextScrollsDone: "+this.msgTextScrollsDone+" -> "+msgTextScrollsDone+"...");
        this.msgTextScrollsDone = msgTextScrollsDone;
        this.thisLastModifiedDate = new Date();
    }

    public long getTtsNumberOfSpeaksToDo() {
        return ttsNumberOfSpeaksToDo;
    }

    public void setTtsNumberOfSpeaksToDo(long ttsNumberOfSpeaksToDo) {
        this.ttsNumberOfSpeaksToDo = ttsNumberOfSpeaksToDo;
        this.thisLastModifiedDate = new Date();
    }

    public long getTtsNumberOfSpeaksDone() {
        return ttsNumberOfSpeaksDone;
    }

    public synchronized void setTtsNumberOfSpeaksDone(long ttsNumberOfSpeaksDone) {
        this.ttsNumberOfSpeaksDone = ttsNumberOfSpeaksDone;
        this.thisLastModifiedDate = new Date();
    }

    public int getTtsVoiceGender() {
        return ttsVoiceGender;
    }

    public void setTtsVoiceGender(String ttsVoiceGender) {
        if (ttsVoiceGender.equals("M")) {
            setTtsVoiceGender(TTS_VOICE_GENDER_MALE);
        } else if (ttsVoiceGender.equals("F")) {
            setTtsVoiceGender(TTS_VOICE_GENDER_FEMALE);
        } else {
            setTtsVoiceGender(TTS_VOICE_GENDER_MALE);
        }
    }
    public void setTtsVoiceGender(int ttsVoiceGender) {
        this.ttsVoiceGender = ttsVoiceGender;
        this.thisLastModifiedDate = new Date();
    }

    public int getTtsVoiceVolumeBase() {
        return ttsVoiceVolumeBase;
    }

    public void setTtsVoiceVolumeBase(int ttsVoiceVolumeBase) {
        this.ttsVoiceVolumeBase = ttsVoiceVolumeBase;
        this.thisLastModifiedDate = new Date();
    }

    public int getTtsVoiceVolumeGain() {
        return ttsVoiceVolumeGain;
    }

    public void setTtsVoiceVolumeGain(int ttsVoiceVolumeGain) {
        this.ttsVoiceVolumeGain = ttsVoiceVolumeGain;
        this.thisLastModifiedDate = new Date();
    }

    public int getFlasherMode() {
        return flasherMode;
    }

    public void setFlasherMode(int flasherMode) {
        this.flasherMode = flasherMode;
        this.thisLastModifiedDate = new Date();
    }

    public int getFlasherBrightness() {
        return flasherBrightness;
    }

    public void setFlasherBrightness(int flasherBrightness) {
        this.flasherBrightness = flasherBrightness;
        this.thisLastModifiedDate = new Date();
    }

    public int getFlasherColor() {
        return flasherColor;
    }

    public void setFlasherColor(int flasherColor) {
        this.flasherColor = flasherColor;
        this.thisLastModifiedDate = new Date();
    }

    public long getFlasherDurationSecondsToDo() {
        return flasherDurationSecondsToDo;
    }

    public void setFlasherDurationSecondsToDo(long flasherDurationSecondsToDo) {
        this.flasherDurationSecondsToDo = flasherDurationSecondsToDo;
        this.thisLastModifiedDate = new Date();
    }

    public long getFlasherDurationSecondsDone() {
        return flasherDurationSecondsDone;
    }

    public void setFlasherDurationSecondsDone(long flasherDurationSecondsDone) {
        this.flasherDurationSecondsDone = flasherDurationSecondsDone;
        this.thisLastModifiedDate = new Date();
    }

    public Date getMsgFirstDeliveryBeganDate() {
        logV("getMsgFirstDeliveryBeganDate: returning: "+String.valueOf(msgFirstDeliveryBeganDate));
        return msgFirstDeliveryBeganDate;
    }

    public void setMsgFirstDeliveryBeganDate(Date msgFirstDeliveryBeganDate) {
        this.msgFirstDeliveryBeganDate = msgFirstDeliveryBeganDate;
        this.thisLastModifiedDate = new Date();
    }

    public int getMsgDuration() {
        return msgDuration;
    }

    public void setMsgDuration(int msgDuration) {
        this.msgDuration = msgDuration;
        this.thisLastModifiedDate = new Date();
    }

    public long getMediaPlaytime() {
        return mediaPlaytime;
    }

    public void setMediaPlaytime(long mediaPlaytime) {
        this.mediaPlaytime = mediaPlaytime;
        this.thisLastModifiedDate = new Date();
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
