package com.messagenetsystems.evolution2.receivers;

/* BootReceiver
 * Handles receiving boot-up notification from the system, and any resultant actions.
 *
 * Revisions:
 *  2017.01.19      Chris Rider     Creation.
 *  2017.06.01      Chris Rider     Updated to work with starting up the SmonService.
 *  2018.08.22      Chris Rider     Changed to start with StartupActivity instead of SmmonService (so we can get config file and what-not).
 *  2019.11.08      Chris Rider     Migrated to from evolution (v1) to evolution2.
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.messagenetsystems.evolution2.activities.StartupActivity;


public class BootReceiver extends BroadcastReceiver {
    private final String TAG = this.getClass().getSimpleName();

    /** Specify what happens when we receive the boot-up notification from the OS **/
    @Override
    public void onReceive(Context context, Intent intent) {
        final String TAGG = "onReceive: ";

        Intent intentToStart;

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {

            // Define our app's start-up, and actually start it...
            try {
                intentToStart = new Intent(context, StartupActivity.class);
                context.startActivity(intentToStart);
            } catch (Exception e) {
                Log.e(TAG, TAGG+"Exception caught starting StartupActivity: "+e.getMessage());
            }

        }

    }

}
