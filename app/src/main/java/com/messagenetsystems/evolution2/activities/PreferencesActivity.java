package com.messagenetsystems.evolution2.activities;

/* PreferencesActivity
 *
 * Revisions:
 *  2019.11.12      Chris Rider     Migrated from v1 series.
 *  2019.12.06      Chris Rider     Added ecosystem section and platform selection.
 *                                  Updated hard-coded strings to match spKeyName stuff in strings XML.
 *  2020.02.20      Chris Rider     Fixed potential null-ref bug.
 */

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.util.Log;
import android.view.WindowManager;
import android.widget.CheckBox;

import com.messagenetsystems.evolution2.R;
import com.messagenetsystems.evolution2.utilities.SharedPrefsUtils;

public class PreferencesActivity extends PreferenceActivity {

    private Context context;
    private Resources resources;

    //What happens when the activity is created
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        context = getApplicationContext();
        resources = context.getResources();

        // Always keep screen turned on for this activity...
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Use a prefs fragment instead of activity (seems to be the latest best practice)
        getFragmentManager().beginTransaction().replace(android.R.id.content, new AvidiaPreferenceFragment()).commit();
    }

    /**
     * A placeholder fragment containing a simple view
     *
     * NOTE: This should be considered the main preferences body of logic?
     *
     */
    public static class AvidiaPreferenceFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
        private String TAG = "AvidiaPreferenceFragment";

        private SharedPrefsUtils sharedPrefsUtils;

        /** EXAMPLES **/
        // To store values
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //SharedPreferences.Editor editor = prefs.edit();
        //editor.putString("Name", "John");
        //editor.apply();

        // To edit stored values
        //SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
        //editor.putString("text", mSaved.getText().toString());
        //editor.putInt("selection-start", mSaved.getSelectionStart());
        //editor.putInt("selection-end", mSaved.getSelectionEnd());
        //editor.apply();

        // To retrieve values
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //String name = prefs.getString("Name", "");
        //if (!name.equalsIgnoreCase("")) {
        //    name = name + " Seth";  //edit the value here
        //}


        /**
         * Main bit that fires when the prefs-fragment is created
         * @param savedInstanceState
         */
        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.app_preferences);
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
            sharedPrefsUtils = new SharedPrefsUtils(getContext().getApplicationContext(), SharedPrefsUtils.LOG_METHOD_FILELOGGER);
        }


        /**
         * Fires whenever the prefs screen shows
         */
        @Override
        public void onResume() {
            super.onResume();

            for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); ++i) {
                Preference pref = getPreferenceScreen().getPreference(i);
                if (pref instanceof PreferenceGroup) {
                    PreferenceGroup prefGroup = (PreferenceGroup) pref;
                    for (int j = 0; j < prefGroup.getPreferenceCount(); ++j) {
                        Preference singlePref = prefGroup.getPreference(j);
                        updatePrefsOnScreen(singlePref, singlePref.getKey());
                    }
                } else {
                    updatePrefsOnScreen(pref, pref.getKey());
                }
            }
        }


        /**
         * Fires whenever the prefs screen ____?
         * NOTE: not necessary?
         */
        //@Override
        //public void onPause() {
        //    super.onPause();
        //    getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        //}


        /**
         * Fires whenever prefs are changed
         * @param sharedPreferences
         * @param key
         */
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

             String TAGG = "onSharedPreferenceChanged: ";

            switch (key) {
                case "serverIPv4":
                    //need to try to download the file with this new IP and then populate all other fields if possible?
                    break;
                default:
                    break;
            }

            updatePrefsOnScreen(findPreference(key), key);

            // Handle IP methods...
            //if IP address method has changed, handle enabling/disabling certain network fields
            //(for instance, for DHCP, we don't need to specify DNS or anything that is got from the lease)
            //if (key.equals("ipAddressMethod")) {
            if (key.equals(getResources().getString(R.string.spKeyName_ipAddressMethod))) {
                Log.d(TAG, TAGG+"key: "+key);
                Log.d(TAG, TAGG+"key getString: "+sharedPreferences.getString(key, "").equals("Static"));

                if (sharedPreferences.getString(key, "").equals("Static")) {
                    findPreference(this.getResources().getString(R.string.spKeyName_thisDeviceAddrIPv4)).setEnabled(true);
                    findPreference(this.getResources().getString(R.string.spKeyName_gatewayIPv4)).setEnabled(true);
                    findPreference(this.getResources().getString(R.string.spKeyName_dnsServer1IPv4)).setEnabled(true);
                    findPreference(this.getResources().getString(R.string.spKeyName_dnsServer2IPv4)).setEnabled(true);
                } else {
                    findPreference(this.getResources().getString(R.string.spKeyName_thisDeviceAddrIPv4)).setEnabled(false);
                    findPreference(this.getResources().getString(R.string.spKeyName_gatewayIPv4)).setEnabled(false);
                    findPreference(this.getResources().getString(R.string.spKeyName_dnsServer1IPv4)).setEnabled(false);
                    findPreference(this.getResources().getString(R.string.spKeyName_dnsServer2IPv4)).setEnabled(false);
                }
            }

            // Handle timezones...
            //if (key.equals("timezones")) {
            //if (key.equals("timezone_name")) {
            if (key.equals(getResources().getString(R.string.spKeyName_timezone))) {
                // get the value of our new timezone.
                // use the alias of that timezone to pass to setTimeZoneFunction
                // call our setTimeZoneFunction
                Log.d(TAG, TAGG+"key: "+key);
                Log.d(TAG, TAGG+"key getString to string: "+sharedPreferences.getString(key, "").toString());

                Log.d(TAG, TAGG+"key getString equals: "+sharedPreferences.getString(key, "").equals("Eastern"));
                Log.d(TAG, TAGG+"key getString equals: "+sharedPreferences.getString(key, "").equals("Central"));

                String[] aliasArray = getResources().getStringArray(R.array.timezones_alias);
                String timezonesAlias;

                if (sharedPreferences.getString(key, "").equals("America/New_York")) {
                    timezonesAlias = "America/New_York";
                    //timezonesAlias = aliasArray[0];
                } else if(sharedPreferences.getString(key, "").equals("America/Chicago")){
                    timezonesAlias = "America/Chicago";
                    //timezonesAlias = aliasArray[1];
                } else if(sharedPreferences.getString(key, "").equals("America/Denver")){
                    timezonesAlias = "America/Denver";
                    //timezonesAlias = aliasArray[2];
                } else if(sharedPreferences.getString(key, "").equals("America/Los_Angeles")){
                    timezonesAlias = "America/Los_Angeles";
                    //timezonesAlias = aliasArray[3];
                }
                else {
                    // default to Eastern
                    Log.d(TAG, TAGG+"Key doesn't match test, go to Eastern as default: "+key.toString());
                    timezonesAlias = "America/New_York";
                }
                Log.d(TAG, TAGG+"Timezone is: "+timezonesAlias);
                sharedPrefsUtils.setSharedPrefsTimeZone(timezonesAlias);
            }

            // Also update the provisioning file on the sdcard if needed...
            //TODO if (key.equals("flasherMacAddress")) {
            //    SettingsUtils.saveLightControllerMacAddressToProvFile(sharedPreferences.getString(key, "NotAvailable"));
            //}
        }


        /**
         * Update the pref value specified, on the screen for the user to see
         * @param pref
         * @param prefKey
         */
        private void updatePrefsOnScreen(Preference pref, String prefKey) {
            final String TAGG = "updatePrefsOnScreen: ";

            if (pref == null) return;

            try {

            /* must specially handle this type or setSummary below will cause fatal exception */
                if (pref instanceof ListPreference) {
                    ListPreference listPref = (ListPreference) pref;
                    listPref.setSummary(listPref.getEntry());

                    // Enable or disable static network fields depending on IP method field's value
                    //if (prefKey.equals("ipAddressMethod")) {
                    if (prefKey.equals(getResources().getString(R.string.spKeyName_ipAddressMethod))) {
                        if (pref.getSummary() != null
                                && pref.getSummary().equals("Static")) {
                            findPreference(this.getResources().getString(R.string.spKeyName_thisDeviceAddrIPv4)).setEnabled(true);
                            findPreference(this.getResources().getString(R.string.spKeyName_gatewayIPv4)).setEnabled(true);
                            findPreference(this.getResources().getString(R.string.spKeyName_dnsServer1IPv4)).setEnabled(true);
                            findPreference(this.getResources().getString(R.string.spKeyName_dnsServer2IPv4)).setEnabled(true);
                        } else {
                            findPreference(this.getResources().getString(R.string.spKeyName_thisDeviceAddrIPv4)).setEnabled(false);
                            findPreference(this.getResources().getString(R.string.spKeyName_gatewayIPv4)).setEnabled(false);
                            findPreference(this.getResources().getString(R.string.spKeyName_dnsServer1IPv4)).setEnabled(false);
                            findPreference(this.getResources().getString(R.string.spKeyName_dnsServer2IPv4)).setEnabled(false);
                        }
                    }

                    // Enable or disable platform fields depending on selected value
                    if (prefKey.equals(getResources().getString(R.string.spKeyName_ecosystemPlatformSelection))) {
                        if (pref.getSummary() == null) {
                            //don't do anything here
                        } else {
                            if (pref.getSummary().equals("PlatformMessageNetV1")) {
                                //old original MessageNet Omni API
                                Log.i(TAG, TAGG + "Ecosystem platform list: Original MessageNet Omni v1 API selected.");
                            } else if (pref.getSummary().equals("PlatformMessageNetV2")) {
                                //newer MessageNet Omni API
                                Log.i(TAG, TAGG + "Ecosystem platform list: MessageNet Omni v2 API selected.");
                            } else if (pref.getSummary().equals("PlatformStandardAPI")) {
                                //standard API
                                Log.i(TAG, TAGG + "Ecosystem platform list: Standard API selected.");
                            } else {
                                //unexpected
                                Log.w(TAG, TAGG + "Ecosystem platform list: Unhandled.");
                            }
                        }
                    }

                    return;
                }

            /* must specially handle this type or setSummary below will cause fatal exception */
                if (pref instanceof CheckBoxPreference) {
                    CheckBoxPreference checkboxPref = (CheckBoxPreference) pref;
                    checkboxPref.setSummary(checkboxPref.getSummary());

                    //do stuff here

                    return;
                }

            /* DEV-NOTE: add any other non-string types here and return */

                if (pref instanceof EditTextPreference) {
                    EditTextPreference textPref = (EditTextPreference) pref;
                    textPref.setSummary(textPref.getText());
                }

                // Update the pref's summary attribute
                SharedPreferences sp = getPreferenceManager().getSharedPreferences();
                pref.setSummary(sp.getString(prefKey, null));

            } catch (Exception e) {
                Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
            }
        }

    }//end class AvidiaPreferenceFragment

}
