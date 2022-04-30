package com.messagenetsystems.evolution2.models;

/* ApiData
 * A model class to outline what data is provided for in our API.
 * It also performs data loading, acquisition (download), initialization, and parsing capabilities.
 *
 * NOTE: Initialization/Parsing/Saving...
 *  This is intended for initial loading (or reloading) of config data, which is usually performed just on startup, usually.
 *  When you initialize this (with either option), the config data will be automatically parsed and flushed to shared preferences.
 *
 * To create your ConfigData instance:
 *  ApiData apiDataAdministrative = new ApiData(getApplicationContext(), ConfigData.LOG_METHOD_FILELOGGER);
 *
 * To initialize with data provided by a client connection:
 *  apiDataAdministrative.init(myClientJsonObject);
 *
 * To cleanup with done with your ConfigData instance:
 *  apiDataAdministrative.cleanup();
 *  apiDataAdministrative = null;      //optional
 *
 * Dev-Note: To add more fields:
 *  1. Edit strings_config.xml and add line as needed.
 *  2. Add tagName_ variable below.
 *  3. Add tagValue_ variable below.
 *  4. Add initialization of tagName_ in constructor below.
 *  5. Add getter/setter for your value below.
 *
 * Revisions:
 *  2019.11.20      Chris Rider     Created (based on ConfigData class).
 *  2020.02.20      Chris Rider     Fixed potential null-ref bug if OmniApplication is unavailable.
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

import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.StringReader;


public class ApiData {
    private final String TAG = this.getClass().getSimpleName();

    // Constants..


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
    private Context appContext;
    private OmniApplication omniApplication;



    /** Constructor
     * @param appContext    Application context
     * @param logMethod     Logging method to use
     */
    public ApiData(Context appContext, int logMethod) {
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

            Resources resources = appContext.getResources();


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

    /** Initialize with data provided by the client connection.
     * @param clientDataJSONObject Client-provided JSON (as JSONObject data type).
     */
    public void init(JSONObject clientDataJSONObject) {
        final String TAGG = "init: ";


    }


    /*============================================================================================*/
    /* Cleanup Methods */

    public void cleanup() {
        final String TAGG = "cleanup: ";

        omniApplication = null;
        appContext = null;
    }


    /*============================================================================================*/
    /* Parsing and XML Methods */

    /** Parse the JSON data contained in the provided string and save to the instance variables.
     *
     * @param xml XML data (as a string) from the configuration file
     * @return True if completed without errors, false if error
     */
    private boolean parseData(String xml) {
        final String TAGG = "parseData: ";
        boolean ret = true;

        try {

        } catch (Exception e) {
            logE(TAGG+"Exception caught parsing JSON: "+e.getMessage());
            ret = false;
        }

        logD(TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }


    /*============================================================================================*/
    /* Getter & Setter Methods */




    /*============================================================================================*/
    /* Subclasses */


}
