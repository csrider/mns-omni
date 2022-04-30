package com.messagenetsystems.evolution2.receivers;

/* ClearMessagesRequestReceiver
 * Handles receiving clear-messages broadcast, sent by the notification action button.
 * Will delete all current messages from the Messages database, and finish any delivery activities.
 *
 * Note: This was originally created to deal with no-expire messages not clearing out.
 *
 * Revisions:
 *  2020.06.17      Chris Rider     Creation.
 */

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.WindowManager;
import android.widget.Toast;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.databases.messages.MessageDatabaseClient;
import com.messagenetsystems.evolution2.services.DeliveryService;


public class ClearMessagesRequestReceiver extends BroadcastReceiver {
    private final String TAG = ClearMessagesRequestReceiver.class.getSimpleName();

    /** Specify what happens when we receive the broadcast **/
    @Override
    public void onReceive(final Context context, Intent intent) {
        final String TAGG = "onReceive: ";

        FL.v(TAG, TAGG+"Invoked.");

        try {
            // Hide the notification pulldown so we can see the confirmation that we'll generate next
            try {
                context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
            } catch (Exception e) {
                FL.e(TAG, TAGG+"Exception caught hiding notification pulldown: "+e.getMessage());
            }

            // Confirm the action with the user
            try {
                AlertDialog alertDialog = new AlertDialog.Builder(context)
                        .setTitle("Clear All Messages")
                        .setMessage("Clear all messages?")
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                Toast.makeText(context, "Clearing All Messages!", Toast.LENGTH_LONG).show();
                                doDeleteRecords(context);
                                cancelDeliveryActivities(context);
                            }
                        })
                        .setNegativeButton(android.R.string.no, null)
                        .create();
                alertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                alertDialog.show();
            } catch (NullPointerException npe) {
                FL.e(TAG, TAGG+"Null-pointer exception caught (no getWindow?): "+ npe.getMessage());
            } catch (Exception e) {
                FL.e(TAG, TAGG+"Exception caught initiating confirmation dialog: "+e.getMessage());
            }

        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }
    }

    private void doDeleteRecords(Context context) {
        final String TAGG = "doDeleteRecords: ";

        try {
            MessageDatabaseClient messageDatabaseClient = MessageDatabaseClient.getInstance(context.getApplicationContext());
            if (messageDatabaseClient == null) {
                FL.e(TAG, TAGG + "No available database client instance, aborting.");
                return;
            }

            messageDatabaseClient.deleteAll(context);
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }
    }

    private void cancelDeliveryActivities(Context context) {
        final String TAGG = "cancelDeliveryActivities: ";

        try {
            DeliveryService.finishAllDeliveryActivities(context);
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }
    }
}
