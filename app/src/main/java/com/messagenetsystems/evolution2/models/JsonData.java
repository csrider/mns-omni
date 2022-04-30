package com.messagenetsystems.evolution2.models;

/* JsonData
 * Model class for JSON data that we accept-from and return-to network clients.
 * It can intake raw JSON and initialize it all as an easy-to-use object.
 * It can also generate JSON based on its instance data for return.
 *
 * Revisions:
 *  2019.11.20-21   Chris Rider     Created.
 */

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.OmniApplication;

import org.json.JSONObject;


public class JsonData extends JSONObject {
    final String TAG = this.getClass().getSimpleName();

    /* JSON WHOLE EXAMPLES
        {"cmd":"clear"}
        {"cmd":"msgAdd", "msg":{"id":"001", "type":"text", "pri":200, "durS":60, "payload":{*PAYLOAD*}}}
        {"cmd":"msgDel", "msg":{"id":"001"}}
        {"cmd":""}
     */

    /* JSON PAYLOAD OBJECT EXAMPLES
        TTS (Text-to-Speech):   {"text":"CNN Site", "repeats":0}
        LIGHTS (Lights/LEDs):   {"mode":"on", "color":"white", "bri":"max"}
        Webpage:                {"url":"http://www.cnn.com", "led":{*LIGHTS*}, "tts":{*TTS*}}
        Text/Scrolling:         {"text":"Hello world!", "textcolor":"white", "color":"white", "speed":2, "tts":"false"}
     */

    // Constants..
    // Define the expected third-party-provided JSON keys/values...
    private final String JSON_KEYNAME_CMD = "cmd";                                                  //key-name for command
    private final String JSON_KEYVALUE_CMD_CLEAR = "clear";                                         //key-value for the command to clear the Omni
    private final String JSON_KEYVALUE_CMD_MSGADD = "msgAdd";                                       //key-value for the command to add a message
    private final String JSON_KEYVALUE_CMD_MSGDEL = "msgDel";                                       //key-value for the command to delete a message
    private final String JSON_KEYNAME_MSG = "msg";
    private final String JSON_KEYNAME_MSG_ID = "id";
    private final String JSON_KEYNAME_MSG_TYPE = "type";
    private final String JSON_KEYVALUE_MSG_TYPE_TEXT = "text";
    private final String JSON_KEYVALUE_MSG_TYPE_LEDS = "led";
    private final String JSON_KEYVALUE_MSG_TYPE_WEBPAGE = "webPage";
    private final String JSON_KEYVALUE_MSG_TYPE_IMAGE = "image";
    private final String JSON_KEYVALUE_MSG_TYPE_VIDEOFILE = "videoFile";
    private final String JSON_KEYVALUE_MSG_TYPE_VIDEOSTREAM = "videoStream";
    private final String JSON_KEYVALUE_MSG_TYPE_AUDIOFILE = "audioFile";
    private final String JSON_KEYNAME_MSG_PRI = "pri";
    private final String JSON_KEYNAME_MSG_DUR_S = "durS";
    private final String JSON_KEYNAME_MSG_PAYLOAD = "payload";

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


    /** Constructor
     * @param appContext    Application context
     * @param logMethod     Logging method to use
     */
    public JsonData(Context appContext, int logMethod) {
        Log.v(TAG, "Instantiating. You should initialize with data next!");

        this.logMethod = logMethod;

        try {
            this.omniApplication = ((OmniApplication) appContext);

            Resources resources = appContext.getResources();
            //TODO: get resources as needed

        } catch (Exception e) {
            logE("Exception caught instantiating "+TAG+": "+e.getMessage());
        }
    }


    /*============================================================================================*/
    /* Cleanup Methods */

    /** Cleanup and free up resources. */
    public void cleanup() {
        final String TAGG = "cleanup: ";

        omniApplication = null;
    }


    /*============================================================================================*/
    /* Parsing Methods */

    /** Parse the JSON data contained in the provided string and save to the instance variables.
     * @param jsonStr   JSON data (as a string).
     * @return True if completed without errors, false if error.
     */
    private boolean parseData(String jsonStr) {
        final String TAGG = "parseData: ";
        boolean ret = true;

        try {

        } catch (Exception e) {
            logE(TAGG+"Exception caught parsing JSON: "+e.getMessage());
            ret = false;
        }

        logV(TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }


    /*============================================================================================*/
    /* Getter & Setter Methods */


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
