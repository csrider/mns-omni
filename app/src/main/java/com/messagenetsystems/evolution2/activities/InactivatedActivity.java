package com.messagenetsystems.evolution2.activities;

/* InactivatedActivity
 * Just a fullscreen "OFFLINE" indicator intended for use when we want to disable or inactivate Omni.
 * This can be due to non-payment or some other situation where we don't want the Omni to work.
 *
 * Revisions:
 *  2019.04.08      Chris Rider     Created.
 *  2020.04.22      Chris Rider     Migrated from v1; untested.
 */

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.messagenetsystems.evolution2.R;

public class InactivatedActivity extends AppCompatActivity {
    private static final String TAG = InactivatedActivity.class.getSimpleName();

    private final Handler mHideHandler = new Handler();
    private View mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inactivated);

        mContentView = findViewById(R.id.layout_omniInactivated);

        goFullscreen();
    }

    private void goFullscreen() {
        try {
            // Hide UI first
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.hide();
            }

            // Schedule a runnable to remove the status and navigation bar after a delay
            mHideHandler.post(mHidePart2Runnable);
        } catch (Exception e) {
            Log.w(TAG, "hide: Exception caught trying to hide UI elements for full screen: "+e.getMessage());
        }
    }
}
