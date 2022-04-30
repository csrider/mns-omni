package com.messagenetsystems.evolution2.activities;

/* StartupActivity
  This is the main start activity... the entry point into the app.
  It loads configs, starts other stuff, etc.

  Flow:
    o Entry (activities.StartupActivity via .receivers.BootReceiver, or manual launch of StartupActivity)
    |
    o Initializations and loads
    |
    o Examine run mode (determined by OmniApplication)
    |\
    | o Run mode idle (don't start up stuff, just provide manual options)
    o Run mode normal (start stuff up normally, per available configuration)
    |
    o Start all of our services and stuff

 * Revisions:
 *  2019.10.30      Chris Rider     Created.
 *  2020.02.17      Chris Rider     Begun migrating OmniApplication run-mode stuff over to here (at least set it now upon successful config file download).
 *  2020.02.20      Chris Rider     Fixed potential null-ref bug if OmniApplication is unavailable.
 *  2020.07.24      Chris Rider     Optimized resource scope and cleanup.
 */

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.OmniApplication;
import com.messagenetsystems.evolution2.R;
import com.messagenetsystems.evolution2.fragments.AboutInfoFragment;
import com.messagenetsystems.evolution2.models.ConfigData;
import com.messagenetsystems.evolution2.threads.DownloadAsyncTaskTFTP;
import com.messagenetsystems.evolution2.utilities.SystemUtils;


public class StartupActivity extends AppCompatActivity {
    private final String TAG = this.getClass().getSimpleName();

    private static final int REQ_PERMISSION = 1233;

    private OmniApplication omniApplication;

    private ConfigData configData;

    private ImageView imageView_logo;
    private TextView textView_statusText1;
    private TextView textView_statusText2;
    private LinearLayout linearLayout_leftColumn;
    private LinearLayout linearLayout_rightColumn;
    private Button button_fetchConfig;
    private Button button_sharedPrefs;
    private Button button_setLightController;
    private Button button_startApp;
    private Button button_aboutInfo;

    private BroadcastReceiver downloadStatusReceiver_configuration;


    /*============================================================================================*/
    /* Activity class methods */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String TAGG = "onCreate: ";
        FL.v(TAG, TAGG+"Invoked.");

        SystemUtils systemUtils;

        // Initialize some local stuff that's needed right away or before other stuff below
        try {
            this.omniApplication = ((OmniApplication) getApplicationContext());
        } catch (Exception e) {
            Log.e(TAG, "Exception caught instantiating "+TAG+": "+e.getMessage());
            finish();
            return;
        }
        systemUtils = new SystemUtils(getApplicationContext(), SystemUtils.LOG_METHOD_FILELOGGER);
        configData = new ConfigData(getApplicationContext(), ConfigData.LOG_METHOD_FILELOGGER);

        // Configure screen elements, and then render everything as configured
        systemUtils.configScreen_hideActionBar(this);
        systemUtils.configScreen_keepScreenOn(this);
        setContentView(R.layout.activity_startup);

        // Initialize more local stuff
        imageView_logo = (ImageView) findViewById(R.id.imageview_startup_logo);
        textView_statusText1 = (TextView) findViewById(R.id.textview_startup_status_1);
        textView_statusText2 = (TextView) findViewById(R.id.textview_startup_status_2);
        linearLayout_leftColumn = (LinearLayout) findViewById(R.id.linearlayout_startup_leftcolumn);
        linearLayout_rightColumn = (LinearLayout) findViewById(R.id.linearlayout_startup_rightcolumn);
        button_fetchConfig = (Button) findViewById(R.id.btnAcquireConfig);
        button_sharedPrefs = (Button) findViewById(R.id.btnSettings);
        button_setLightController = (Button) findViewById(R.id.btnAssociateLightController);
        button_startApp = (Button) findViewById(R.id.btnStart);
        button_aboutInfo = (Button) findViewById(R.id.btnAboutInfo);
        downloadStatusReceiver_configuration = new ConfigDownloadStatusReceiver();

        // Assign button tap handlers...
        //TODO: Maybe consider doing this only if buttons are visible?
        button_fetchConfig = (Button) findViewById(R.id.btnAcquireConfig);
        button_fetchConfig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                registerReceiver(downloadStatusReceiver_configuration, new IntentFilter(DownloadAsyncTaskTFTP.INTENTFILTER_DOWNLOAD_CONFIGURATION));
                configData.init(getApplicationContext(), configData.INIT_WITH_REMOTE_CONFIG_DATA);
            }
        });
        button_sharedPrefs = (Button) findViewById(R.id.btnSettings);
        button_sharedPrefs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(StartupActivity.this, PreferencesActivity.class));
            }
        });
        button_setLightController = (Button) findViewById(R.id.btnAssociateLightController);
        button_setLightController.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //TODO
                //BluetoothLightAssociateDialog bluetoothLightAssociateDialog = new BluetoothLightAssociateDialog(getApplicationContext());
                //bluetoothLightAssociateDialog.doInitAdapter();
                //bluetoothLightAssociateDialog.doStartScan();    //when the scan completes, the routines there will auto add the strongest MAC to the shared prefs and show a toast
            }
        });
        button_startApp = (Button) findViewById(R.id.btnStart);
        button_startApp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startStuff(getApplicationContext());
            }
        });
        button_aboutInfo = (Button) findViewById(R.id.btnAboutInfo);
        button_aboutInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                displayAboutInfo();
            }
        });

        systemUtils.cleanup();
        systemUtils = null;
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        final String TAGG = "onPostCreate: ";
        FL.v(TAG, TAGG+"Invoked.");

        textView_statusText2.setVisibility(View.VISIBLE);

        // Render appropriate screen elements
        textView_statusText2.append("Checking run-mode... ");
        if (omniApplication.getRunMode() == OmniApplication.RUN_MODE_NORMAL) {
            //normal startup
            textView_statusText2.append("done (normal)\n");
            linearLayout_leftColumn.setVisibility(View.GONE);
            linearLayout_rightColumn.setVisibility(View.GONE);
        } else if (omniApplication.getRunMode() == OmniApplication.RUN_MODE_IDLE) {
            //show more advanced stuff
            textView_statusText2.append("done (idle)\n");
            linearLayout_leftColumn.setVisibility(View.VISIBLE);
            linearLayout_rightColumn.setVisibility(View.VISIBLE);
        }

        // Ensure permissions are correct
        // NOTE: This requires manual on-screen user intervention, and should only apply to first-time setup by MNS
        textView_statusText2.append("Verifying permissions... ");
        checkAndSetPermissions();
        textView_statusText2.append("done\n");

        // All ready/done preparing stuff, so finally you can now take care of final things,
        // depending on what our run-mode is. This is where all the final stuff gets kicked off!
        if (omniApplication.getRunMode() == OmniApplication.RUN_MODE_IDLE) {
            textView_statusText1.setText("Manual Interaction Mode");
            omniApplication.replaceNotificationWithText("Awaiting manual interaction due to idle run-mode.");
        } else {
            registerReceiver(downloadStatusReceiver_configuration, new IntentFilter(DownloadAsyncTaskTFTP.INTENTFILTER_DOWNLOAD_CONFIGURATION));
            configData.init(getApplicationContext(), configData.INIT_WITH_REMOTE_CONFIG_DATA);
        }

        // TODO - FOR DEBUGGING.....
        configData.init(getApplicationContext(), configData.INIT_WITH_REMOTE_CONFIG_DATA);
        startStuff(getApplicationContext());    //TODO REMOVE!!! Useful for remote development where you can't push the start button on sreen/displayAboutInfo();

        configData.cleanup();
        configData = null;
    }

    @Override
    public void onStart() {
        super.onStart();
        final String TAGG = "onStart: ";
        FL.v(TAG, TAGG+"Invoked.");
        // Do the stuff here
    }

    @Override
    public void onResume() {
        super.onResume();
        final String TAGG = "onResume: ";
        FL.v(TAG, TAGG+"Invoked.");
        // Do the stuff here
    }

    @Override
    public void onPause() {
        final String TAGG = "onPause: ";
        FL.v(TAG, TAGG+"Invoked.");

        stopListeningForDownloadStatus();

        super.onPause();
    }

    @Override
    protected void onStop() {
        final String TAGG = "onStop: ";
        FL.v(TAG, TAGG+"Invoked.");
        // Do the stuff here
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        final String TAGG = "onDestroy: ";
        FL.v(TAG, TAGG+"Invoked.");

        // Explicitly stop our MainService, so its onDestroy is sure to be invoked (which ensures it broadcasts that it stopped so the restarter can work)
        stopService(omniApplication.getMainServiceIntent());

        cleanup();

        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        final String TAGG = "onRequestPermissionsResult: ";
        FL.v(TAG, TAGG+"Invoked.");

        if (requestCode == REQ_PERMISSION) {
            if (grantResults.length > 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                FL.w(TAG, TAGG+"Permission must be granted!");
            }
        }
    }


    /*============================================================================================*/
    /* Supporting methods */

    private void checkAndSetPermissions() {
        final String TAGG = "checkAndSetPermissions: ";
        FL.v(TAG, TAGG+"Invoked.");

        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERMISSION);
            }
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught!", e);
        }
    }

    private void cleanup() {
        final String TAGG = "cleanup: ";
        FL.v(TAG, TAGG+"Invoked.");

        stopListeningForDownloadStatus();

        omniApplication = null;
    }

    public void stopListeningForDownloadStatus() {
        final String TAGG = "stopListeningForDownloadStatus: ";

        try {
            unregisterReceiver(downloadStatusReceiver_configuration);
            downloadStatusReceiver_configuration = null;
        } catch (Exception e) {
            FL.w(TAG, TAGG+"Exception caught!", e);
        }
    }

    private void startStuff(Context appContext) {
        final String TAGG = "startStuff: ";

        try {
            if (!omniApplication.isServiceRunning(omniApplication.getMainService().getClass())) {
                startService(omniApplication.getMainServiceIntent());
            } else {
                FL.w(TAG, TAGG+"MainService already running (should it be?), so not starting it again, so avoid duplicates.");
            }
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught: "+e.getMessage(), e);
        }
    }

    private void displayAboutInfo() {
        final String TAGG = "displayAboutInfo: ";

        FragmentManager fragmentManager = getSupportFragmentManager();
        AboutInfoFragment aboutInfoFragment = AboutInfoFragment.newInstance(AboutInfoFragment.DEFAULT_TITLE, omniApplication.getAppVersion());
        aboutInfoFragment.show(fragmentManager, "fragment_about_info");

        /*
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("Main app version: ").append(omniApplication.getAppVersion()).append("\n");

        stringBuilder.append("\nRelease Notes:\n");
        stringBuilder.append(getReleaseNotesForVersion(omniApplication.getAppVersion()));

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setIcon(R.mipmap.mns_logo_launcher);
        alertDialogBuilder.setTitle("About This Omni");
        alertDialogBuilder.setMessage(stringBuilder);
        alertDialogBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
        */
    }


    /*============================================================================================*/
    /* Subclasses */

    public class ConfigDownloadStatusReceiver extends BroadcastReceiver {
        private final String TAGG = this.getClass().getSimpleName();

        public ConfigDownloadStatusReceiver() {
            FL.v(TAG, TAGG+"Instantiating.");
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String TAGGG = "onReceive: ";
            FL.d(TAG, TAGG+TAGGG+"Broadcast received.");

            // Parse out the intent's status value
            String statusValue = intent.getStringExtra(DownloadAsyncTaskTFTP.INTENTEXTRA_STATUS);
            FL.v(TAG, TAGG+TAGGG+"Status value from intent extra: \""+statusValue+"\"");

            if (statusValue.equals(DownloadAsyncTaskTFTP.INTENTEXTRA_STATUS_STARTED)) {
                textView_statusText2.append("Acquiring configuration file... ");
            }

            if (statusValue.equals(DownloadAsyncTaskTFTP.INTENTEXTRA_STATUS_FINISHED_OK)) {
                textView_statusText2.append("done (ok)\n");
                stopListeningForDownloadStatus();   //unregister this receiver

                omniApplication.setRunMode(OmniApplication.RUN_MODE_NORMAL);    //TODO: may not be the right place ultimately for this?

                // Start things off automatically
                if (omniApplication.getRunMode() == OmniApplication.RUN_MODE_NORMAL) {
                    startStuff(getApplicationContext());
                }
            }

            if (statusValue.equals(DownloadAsyncTaskTFTP.INTENTEXTRA_STATUS_FINISHED_NOTOK)) {
                textView_statusText2.append("done (problem)\n");
                stopListeningForDownloadStatus();   //unregister this receiver

                // Start things off automatically
                if (omniApplication.getRunMode() == OmniApplication.RUN_MODE_NORMAL) {
                    startStuff(getApplicationContext());
                }
            }
        }
    }

}
