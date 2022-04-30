package com.messagenetsystems.evolution2.receivers;

/* DisplayStateReceiver
 * Handles receiving display related notifications from the system, and any resultant actions.
 *
 * What it does / How it works:
 * Listens for system-broadcasted events, as follows...
 *  - Device screen state (system fires ACTION_SCREEN_OFF / ACTION_SCREEN_ON)
 *      Updates global variables.
 *
 * Revisions:
 *  2020.05.20      Chris Rider     Creation (used HealthReceiverStorageStates as a template).
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.services.HealthService;

import java.lang.ref.WeakReference;


public class DisplayStateReceiver extends BroadcastReceiver {
    private final String TAG = this.getClass().getSimpleName();

    // Constants...
    public static final String RECEIVER_NAME = "com.messagenetsystems.evolution.receivers.displayStateReceiver";

    // Logging stuff...
    private final int LOG_SEVERITY_V = 1;
    private final int LOG_SEVERITY_D = 2;
    private final int LOG_SEVERITY_I = 3;
    private final int LOG_SEVERITY_W = 4;
    private final int LOG_SEVERITY_E = 5;
    private int logMethod = Constants.LOG_METHOD_LOGCAT;

    // Local stuff...
    private WeakReference<Context> appContextRef;


    /** Constructor */
    public DisplayStateReceiver(Context context, int logMethod) {
        logV("Instantiating...");

        this.logMethod = logMethod;
        this.appContextRef = new WeakReference<Context>(context);
    }

    public void cleanup() {
        final String TAGG = "cleanup: ";

        if (this.appContextRef != null) {
            this.appContextRef.clear();
            this.appContextRef = null;
        }
    }

    /** Specify what happens when we receive the broadcasts from the OS. */
    @Override
    public void onReceive(Context context, Intent intent) {
        final String TAGG = "onReceive: ";

        if (intent.getAction() == null) {
            logW(TAGG+"Intent's getAction returned null, aborting.");
            return;
        }

        try {
            // Figure out why we got this broadcast,
            // and then take appropriate action...
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                logI(TAGG + "Screen has turned off.");
                HealthService.displayScreenState = HealthService.DISPLAY_SCREEN_STATE_OFF;
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                logI(TAGG + "Screen has turned on.");
                HealthService.displayScreenState = HealthService.DISPLAY_SCREEN_STATE_ON;
            } else {
                logW(TAGG+"Unhandled action.");
            }
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }
    }


    /*============================================================================================*/
    /* Utility Methods */




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
