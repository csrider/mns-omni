package com.messagenetsystems.evolution2.receivers;

/* MainServiceStopRequestReceiver
 * Handles receiving MainService stop-requested message, to stop the service.
 *
 * Note:
 *  This should mostly be unnecessary for any class that has an an OmniApplication instance (since there is a public method available to stop MainService).
 *  But some cases seem to need it (e.g. notification action's PendingIntent requires it)
 *
 * To use/invoke this receiver (and thus make it stop the main service)...
 *  Intent broadcastIntent = new Intent(this, MainServiceStopRequestReceiver.class);
 *  sendBroadcast(broadcastIntent);
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


public class MainServiceStopRequestReceiver extends BroadcastReceiver {
    private final String TAG = this.getClass().getSimpleName();

    private OmniApplication omniApplication;

    /** Specify what happens when we receive the broadcast **/
    @Override
    public void onReceive(Context context, Intent intent) {
        final String TAGG = "onReceive: ";

        try {
            this.omniApplication = ((OmniApplication) context.getApplicationContext());
        } catch (Exception e) {
            Log.e(TAG, "Exception caught instantiating "+TAG+": "+e.getMessage());
            return;
        }

        try {
            omniApplication.stopMainService();
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }

        omniApplication = null;
    }
}
