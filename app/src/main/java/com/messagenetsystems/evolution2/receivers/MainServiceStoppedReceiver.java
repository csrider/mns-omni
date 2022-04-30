package com.messagenetsystems.evolution2.receivers;

/* MainServiceStoppedReceiver
 * Handles receiving MainService stopped message, and any resultant actions (like restarting it).
 *
 * A common place for this to be used is in the notification item allowing user to stop stuff.
 *
 * Revisions:
 *  2019.11.19      Chris Rider     Creation.
 *  2020.02.20      Chris Rider     Fixed potential null-ref bug if OmniApplication is unavailable.
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.OmniApplication;
import com.messagenetsystems.evolution2.services.MainService;


public class MainServiceStoppedReceiver extends BroadcastReceiver {
    private final String TAG = this.getClass().getSimpleName();

    private OmniApplication omniApplication;


    /** Specify what happens when we receive the broadcast **/
    @Override
    public void onReceive(Context context, Intent intent) {
        final String TAGG = "onReceive: ";

        try {
            omniApplication = ((OmniApplication) context.getApplicationContext());
        } catch (Exception e) {
            Log.e(TAG, "Exception caught instantiating "+TAG+": "+e.getMessage());
            return;
        }

        if (omniApplication.getAllowAppToDie()) {
            FL.i(TAG, TAGG+"MainService stopped, and that's allowed. So, there's nothing to do.");
            omniApplication.replaceNotificationWithText("MainService stopped.");
            omniApplication.setAllowAppToDie(false);    //reinit
        } else {
            FL.i(TAG, TAGG + "MainService stopped, but that's not allowed. Attempting restart...");
            doMainServiceRestart(context);
        }

        omniApplication = null;
    }

    private void doMainServiceRestart(Context context) {
        omniApplication.replaceNotificationWithText("MainService stopped. Attempting restart...");
        context.startService(new Intent(context, MainService.class));
    }

}
