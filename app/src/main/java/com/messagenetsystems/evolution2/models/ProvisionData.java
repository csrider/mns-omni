package com.messagenetsystems.evolution2.models;

/* ProvisionData
 * A model class to outline what is in the provisioning data XML file.
 * It also performs data initialization and parsing capabilities.
 *
 * To use:
 *  1. Create an instance
 *  2. Call one of the init() methods to parse and bring in the data
 *      (will return boolean indicating whether success or not)
 *
 * Dev-Note: To add more fields:
 *  1. Edit strings_provisioning.xml and add line as needed.
 *  2. Add tagName_ variable below.
 *  3. Add tagValue_ variable below.
 *  4. Add initialization of tagName_ in constructor below.
 *  5. Add getter/setter for your value below.
 *
 * Revisions:
 *  2019.11.11      Chris Rider     Created.
 *  2019.11.18      Chris Rider     Added serial number parsing and flushing to prefs.
 *  2019.12.06      Chris Rider     Fixed bug where wifi fields and ip method weren't saving to shared-prefs.
 *                                  Added acquisition/derivation and flushing to shared-prefs of wifi, active-mac, and TFTP config filename values.
 *  2020.02.17      Chris Rider     Added ethernet tag parsing and flushing to shared-prefs (just MAC for now -also method, but doesn't flush to SP).
 */

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.R;
import com.messagenetsystems.evolution2.utilities.FileUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;


public class ProvisionData {
    private final String TAG = this.getClass().getSimpleName();

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
    private String spKeyName_serverIPv4;
    private String spKeyName_wifiSSID, spKeyName_wifiSecurityType, spKeyName_wifiPassword, spKeyName_ipAddressMethod;
    private String spKeyName_applianceMacAddressWifi, spKeyName_applianceMacAddressWired, spKeyName_applianceMacAddressActive;
    private String spKeyName_tftpConfigFilename;
    private String spKeyName_serialNumber;
    private boolean overwriteExistingSharedPrefsValues;
    private String tagName_overwriteExistingSharedPrefsWithThese;
    private String tagName_pdf;
    private String tagName_pdf_createdBy;
    private String tagName_pdf_createdWhen;
    private String tagName_pdf_provStartedWhen;
    private String tagName_pdf_provStartedWhenHuman;
    private String tagName_pdf_devStartedWhen;
    private String tagName_pdf_devStartedWhenHuman;
    private String tagName_ci;
    private String tagName_ci_id;
    private String tagName_di;
    private String tagName_di_mnsSerial;
    private String tagName_di_arbitraryLabel;
    private String tagName_pi;
    private String tagName_pi_lightControllerMacAddress;
    private String tagName_wifi;
    private String tagName_wifi_use;
    private String tagName_wifi_mac;
    private String tagName_wifi_macNoColon;
    private String tagName_wifi_ssid;
    private String tagName_wifi_security;
    private String tagName_wifi_password;
    private String tagName_wifi_method;
    private String tagName_wifi_ip;
    private String tagName_wifi_subnet;
    private String tagName_wifi_gateway;
    private String tagName_wifi_dns1;
    private String tagName_wifi_dns2;
    private String tagName_eth;
    private String tagName_eth_use;
    private String tagName_eth_mac;
    private String tagName_eth_macNoColon;
    private String tagName_eth_method;
    private String tagName_eth_ip;
    private String tagName_eth_subnet;
    private String tagName_eth_gateway;
    private String tagName_eth_dns1;
    private String tagName_eth_dns2;
    private String tagName_srv;
    private String tagName_srv_ipv4;

    // XML node values...
    // These are accessible from this class-instance's getter/setter methods.
    private String tagValue_overwriteExistingSharedPrefsWithThese = "";
    private String tagValue_pdf_createdBy = "";
    private String tagValue_pdf_createdWhen = "";
    private String tagValue_pdf_provStartedWhen = "";
    private String tagValue_pdf_provStartedWhenHuman = "";
    private String tagValue_pdf_devStartedWhen = "";
    private String tagValue_pdf_devStartedWhenHuman = "";
    private String tagValue_ci_id = "";
    private String tagValue_di_mnsSerial = "";
    private String tagValue_di_arbitraryLabel = "";
    private String tagValue_pi_lightControllerMacAddress = "";
    private String tagValue_wifi_use = "";
    private String tagValue_wifi_mac = "";
    private String tagValue_wifi_macNoColon = "";
    private String tagValue_wifi_ssid = "";
    private String tagValue_wifi_security = "";
    private String tagValue_wifi_password = "";
    private String tagValue_wifi_method = "";
    private String tagValue_wifi_ip = "";
    private String tagValue_wifi_subnet = "";
    private String tagValue_wifi_gateway = "";
    private String tagValue_wifi_dns1 = "";
    private String tagValue_wifi_dns2 = "";
    private String tagValue_eth_use = "";
    private String tagValue_eth_mac = "";
    private String tagValue_eth_macNoColon = "";
    private String tagValue_eth_method = "";
    private String tagValue_eth_ip = "";
    private String tagValue_eth_subnet = "";
    private String tagValue_eth_gateway = "";
    private String tagValue_eth_dns1 = "";
    private String tagValue_eth_dns2 = "";
    private String tagValue_srv_ipv4 = "";

    /** Constructor
     * @param appContext    Application context
     * @param logMethod     Logging method to use
     */
    public ProvisionData(Context appContext, int logMethod) {
        Log.v(TAG, "Instantiating. You should initialize with data next!");

        this.logMethod = logMethod;

        try {
            Resources resources = appContext.getResources();

            spKeyName_serverIPv4 = resources.getString(R.string.spKeyName_serverAddrIPv4);
            spKeyName_wifiSSID = resources.getString(R.string.spKeyName_wifiSSID);
            spKeyName_wifiSecurityType = resources.getString(R.string.spKeyName_wifiSecurityType);
            spKeyName_wifiPassword = resources.getString(R.string.spKeyName_wifiPassword);
            spKeyName_ipAddressMethod = resources.getString(R.string.spKeyName_ipAddressMethod);
            spKeyName_serialNumber = resources.getString(R.string.spKeyName_serialNumber);
            spKeyName_applianceMacAddressWifi = resources.getString(R.string.spKeyName_applianceMacAddressWifi);
            spKeyName_applianceMacAddressWired = resources.getString(R.string.spKeyName_applianceMacAddressWired);
            spKeyName_applianceMacAddressActive = resources.getString(R.string.spKeyName_applianceMacAddressActive);
            spKeyName_tftpConfigFilename = resources.getString(R.string.spKeyName_tftpConfigFilename);

            overwriteExistingSharedPrefsValues = false;

            tagName_overwriteExistingSharedPrefsWithThese = resources.getString(R.string.provfile_overwriteExistingSharedPrefsWithThese);
            tagName_pdf = resources.getString(R.string.provfile_pdf);
            tagName_pdf_createdBy = resources.getString(R.string.provfile_pdf_createdBy);
            tagName_pdf_createdWhen = resources.getString(R.string.provfile_pdf_createdWhen);
            tagName_pdf_provStartedWhen = resources.getString(R.string.provfile_pdf_provStartedWhen);
            tagName_pdf_provStartedWhenHuman = resources.getString(R.string.provfile_pdf_provStartedWhenHuman);
            tagName_pdf_devStartedWhen = resources.getString(R.string.provfile_pdf_devStartedWhen);
            tagName_pdf_devStartedWhenHuman = resources.getString(R.string.provfile_pdf_devStartedWhenHuman);
            tagName_ci = resources.getString(R.string.provfile_ci);
            tagName_ci_id = resources.getString(R.string.provfile_ci_id);
            tagName_di = resources.getString(R.string.provfile_di);
            tagName_di_mnsSerial = resources.getString(R.string.provfile_di_mnsSerial);
            tagName_di_arbitraryLabel = resources.getString(R.string.provfile_di_arbitraryLabel);
            tagName_pi = resources.getString(R.string.provfile_pi);
            tagName_pi_lightControllerMacAddress = resources.getString(R.string.provfile_pi_lightControllerMacAddress);
            tagName_wifi = resources.getString(R.string.provfile_wifi);
            tagName_wifi_use = resources.getString(R.string.provfile_wifi_use);
            tagName_wifi_mac = resources.getString(R.string.provfile_wifi_mac);
            tagName_wifi_macNoColon = resources.getString(R.string.provfile_wifi_macNoColon);
            tagName_wifi_ssid = resources.getString(R.string.provfile_wifi_ssid);
            tagName_wifi_security = resources.getString(R.string.provfile_wifi_security);
            tagName_wifi_password = resources.getString(R.string.provfile_wifi_password);
            tagName_wifi_method = resources.getString(R.string.provfile_wifi_method);
            tagName_wifi_ip = resources.getString(R.string.provfile_wifi_ip);
            tagName_wifi_subnet = resources.getString(R.string.provfile_wifi_subnet);
            tagName_wifi_gateway = resources.getString(R.string.provfile_wifi_gateway);
            tagName_wifi_dns1 = resources.getString(R.string.provfile_wifi_dns1);
            tagName_wifi_dns2 = resources.getString(R.string.provfile_wifi_dns2);
            tagName_eth = resources.getString(R.string.provfile_eth);
            tagName_eth_use = resources.getString(R.string.provfile_eth_use);
            tagName_eth_mac = resources.getString(R.string.provfile_eth_mac);
            tagName_eth_macNoColon = resources.getString(R.string.provfile_eth_macNoColon);
            tagName_eth_method = resources.getString(R.string.provfile_eth_method);
            tagName_eth_ip = resources.getString(R.string.provfile_eth_ip);
            tagName_eth_subnet = resources.getString(R.string.provfile_eth_subnet);
            tagName_eth_gateway = resources.getString(R.string.provfile_eth_gateway);
            tagName_eth_dns1 = resources.getString(R.string.provfile_eth_dns1);
            tagName_eth_dns2 = resources.getString(R.string.provfile_eth_dns2);
            tagName_srv = resources.getString(R.string.provfile_srv);
            tagName_srv_ipv4 = resources.getString(R.string.provfile_srv_ipv4);
        } catch (Exception e) {
            logE("Exception caught instantiating "+TAG+": "+e.getMessage());
        }
    }


    /*============================================================================================*/
    /* Initialization Methods */

    /** Initialize this instance with data directly from the provisioning file in external storage.
     * @return Whether succeeded or not
     */
    public boolean init(@NonNull Context appContext) {
        final String TAGG = "init: ";
        boolean ret;

        try {
            FileUtils fileUtils;
            String filename;
            File provFile;
            FileInputStream fileInputStream;

            // Get an input stream for the provisioning XML file's contents
            fileUtils = new FileUtils(appContext, this.logMethod);
            filename = appContext.getResources().getString(R.string.provfile_filename);
            provFile = fileUtils.getFileObjectForFile(fileUtils.getFileObjectForExternalStorageDir(), filename);
            fileInputStream = new FileInputStream(provFile);

            // Feed that stream into our init method
            ret = initWithData(fileInputStream);
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
            ret = false;
        }

        logD(TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    /** Take the provided XML file contents and initialize this instance with all its data.
     * @param xmlFileInputStream XML FileInputStream object
     */
    public boolean initWithData(FileInputStream xmlFileInputStream) {
        final String TAGG = "initWithData: ";
        boolean ret;

        try {
            ret = parseData(xmlFileInputStream);

            // Also set any known values...

            // Determine configuration-filename and populate (if needed, since based on mac and likely won't change)
            //String cfgFilename = "Evolution" + getWifiMac().replaceAll(":", "") +".cfg";                            //construct the expected full-filename string from the mac address

        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
            ret = false;
        }

        logD(TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }


    /*============================================================================================*/
    /* Parsing and XML Methods */

    // Go through the provided data and parse values into local variables.
    private boolean parseData(FileInputStream fileInputStream) {
        final String TAGG = "parseData: ";
        boolean ret = true;

        // Parse the XML file and save key values
        try {
            XmlPullParserFactory xmlFactory;
            XmlPullParser xpp;
            int eventType;

            //load data into parsing infrastructure
            xmlFactory = XmlPullParserFactory.newInstance();
            xpp = xmlFactory.newPullParser();
            xpp.setInput(fileInputStream, null);

            //do the actual parsing
            eventType = xpp.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {

                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        logV(TAGG+"Start tag was read: \""+xpp.getName()+"\"");

                        //if "server" tag...
                        if (xpp.getName().equals(tagName_srv)) {
                            //we're at our server info tag, so need to go to next nested tag, which should be our server IP address
                            xpp.nextTag();
                            if (xpp.getName().equals(tagName_srv_ipv4)) {
                                setServerIPv4(xpp.nextText());
                                logD(TAGG+"Found server ipv4 ("+getServerIPv4()+").");
                            }
                        }

                        //if "wifi" tag...
                        if (xpp.getName().equals(tagName_wifi)) {
                            //we're at our wifi info tag, so need to go into nested tags
                            while (true) {
                                if (xpp.getEventType() == XmlPullParser.END_TAG
                                        && xpp.getName().equals(tagName_wifi)) {
                                    break;
                                }

                                xpp.next();

                                if (xpp.getEventType() == XmlPullParser.START_TAG) {
                                    if (xpp.getName().equals(tagName_wifi_ssid)) {
                                        setWifiSsid(xpp.nextText());
                                        logD(TAGG + "Found wifi ssid (" + getWifiSsid() + ").");
                                        xpp.nextTag();
                                    }
                                    if (xpp.getName().equals(tagName_wifi_security)) {
                                        setWifiSecurity(xpp.nextText());
                                        logD(TAGG + "Found wifi security (" + getWifiSecurity() + ").");
                                        xpp.nextTag();
                                    }
                                    if (xpp.getName().equals(tagName_wifi_password)) {
                                        setWifiPassword(xpp.nextText());
                                        logD(TAGG + "Found wifi password (" + getWifiPassword() + ").");
                                    }
                                    if (xpp.getName().equals(tagName_wifi_method)) {
                                        setWifiMethod(xpp.nextText());
                                        logD(TAGG + "Found wifi method (" + getWifiMethod() + ").");
                                    }
                                    if (xpp.getName().equals(tagName_wifi_mac)) {
                                        setWifiMac(xpp.nextText());
                                        logD(TAGG + "Found wifi MAC (" + getWifiMac() + ").");
                                    }
                                    //(other potential nested tags go here)
                                }
                            }
                        }

                        //if "ethernet" tag...
                        if (xpp.getName().equals(tagName_eth)) {
                            //we're at our ethernet info tag, so need to go into nested tags
                            while (true) {
                                if (xpp.getEventType() == XmlPullParser.END_TAG
                                        && xpp.getName().equals(tagName_eth)) {
                                    break;
                                }

                                xpp.next();

                                if (xpp.getEventType() == XmlPullParser.START_TAG) {
                                    if (xpp.getName().equals(tagName_eth_mac)) {
                                        setEthMac(xpp.nextText());
                                        logD(TAGG + "Found ethernet MAC (" + getEthMac() + ").");
                                    }
                                    if (xpp.getName().equals(tagName_eth_method)) {
                                        setEthMethod(xpp.nextText());
                                        logD(TAGG + "Found ethernet method (" + getEthMethod() + ").");
                                    }
                                    //(other potential nested tags go here)
                                }
                            }
                        }

                        //if "deviceInfo" tag...
                        if (xpp.getName().equals(tagName_di)) {
                            //we're at our deviceInfo tag, so need to into nested tags
                            while (true) {
                                if (xpp.getEventType() == XmlPullParser.END_TAG
                                        && xpp.getName().equals(tagName_di)) {
                                    break;
                                }

                                xpp.next();

                                if (xpp.getEventType() == XmlPullParser.START_TAG) {
                                    if (xpp.getName().equals(tagName_di_mnsSerial)) {
                                        setDiMnsSerial(xpp.nextText());
                                        logD(TAGG + "Found serial number ("+getDiMnsSerial()+").");
                                        xpp.nextTag();
                                    }
                                    //(other potential nested tags go here)
                                }
                            }
                        }

                        //(add other tags later here)
                        //(be sure to add a corresponding shared-prefs addition below)

                        //handle in-file override flag
                        //this allows us to effect changes to shared prefs from the file itself
                        if (xpp.getName().equals(tagName_overwriteExistingSharedPrefsWithThese)) {
                            try {
                                setOverwriteExistingSharedPrefsWithThese(xpp.nextText());
                            } catch (Exception e2) {
                                logE(TAGG+"Exception caught parsing override flag in XML file: "+e2.getMessage());
                            }
                        }

                        break;
                }//end switch

                eventType = xpp.next();
            }//end while
        } catch (XmlPullParserException | IOException e) {
            logE(TAGG+"Exception caught parsing XML: "+e.getMessage());
            ret = false;
        }

        logD(TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    // Take current state of values and save to the shared-preferences.
    // NOTE: This will only overwrite existing values if flag is set in prov file (or local var equiv is set to true)!
    // Returns true when DONE, or false if some problem.
    @SuppressLint("ApplySharedPref")
    public boolean flushToSharedPrefs(Context appContext) {
        final String TAGG = "flushToSharedPrefs: ";
        boolean ret;
        String tempSpKeyName, tempProvValue;

        try {
            SharedPreferences spp = PreferenceManager.getDefaultSharedPreferences(appContext);
            SharedPreferences.Editor spe = spp.edit();

            //if existing pref is not populated OR (existing pref is populated AND we can overwrite)
            tempSpKeyName = spKeyName_serverIPv4;           //DEV-NOTE: Only need to edit this! :)
            tempProvValue = this.tagValue_srv_ipv4;         //DEV-NOTE: Only need to edit this! :)
            if (spp.getString(tempSpKeyName, "").equals("") || (!spp.getString(tempSpKeyName, "").equals("") && overwriteExistingSharedPrefsValues)) {
                logV(TAGG+"Existing shared prefs "+tempSpKeyName+" value = \""+spp.getString(tempSpKeyName, "")+"\". Putting \""+tempProvValue+"\".");
                spe.putString(tempSpKeyName, tempProvValue);
            }

            //if existing pref is not populated OR (existing pref is populated AND we can overwrite)
            tempSpKeyName = spKeyName_wifiSSID;             //DEV-NOTE: Only need to edit this! :)
            tempProvValue = this.tagValue_wifi_ssid;        //DEV-NOTE: Only need to edit this! :)
            if (spp.getString(tempSpKeyName, "").equals("") || (!spp.getString(tempSpKeyName, "").equals("") && overwriteExistingSharedPrefsValues)) {
                logV(TAGG+"Existing shared prefs "+tempSpKeyName+" value = \""+spp.getString(tempSpKeyName, "")+"\". Putting \""+tempProvValue+"\".");
                spe.putString(tempSpKeyName, tempProvValue);
            }

            //if existing pref is not populated OR (existing pref is populated AND we can overwrite)
            tempSpKeyName = spKeyName_wifiSecurityType;     //DEV-NOTE: Only need to edit this! :)
            tempProvValue = this.tagValue_wifi_security;    //DEV-NOTE: Only need to edit this! :)
            if (spp.getString(tempSpKeyName, "").equals("") || (!spp.getString(tempSpKeyName, "").equals("") && overwriteExistingSharedPrefsValues)) {
                logV(TAGG+"Existing shared prefs "+tempSpKeyName+" value = \""+spp.getString(tempSpKeyName, "")+"\". Putting \""+tempProvValue+"\".");
                spe.putString(tempSpKeyName, tempProvValue);
            }

            //if existing pref is not populated OR (existing pref is populated AND we can overwrite)
            tempSpKeyName = spKeyName_wifiPassword;         //DEV-NOTE: Only need to edit this! :)
            tempProvValue = this.tagValue_wifi_password;    //DEV-NOTE: Only need to edit this! :)
            if (spp.getString(tempSpKeyName, "").equals("") || (!spp.getString(tempSpKeyName, "").equals("") && overwriteExistingSharedPrefsValues)) {
                logV(TAGG+"Existing shared prefs "+tempSpKeyName+" value = \""+spp.getString(tempSpKeyName, "")+"\". Putting \""+tempProvValue+"\".");
                spe.putString(tempSpKeyName, tempProvValue);
            }

            //if existing pref is not populated OR (existing pref is populated AND we can overwrite)
            tempSpKeyName = spKeyName_applianceMacAddressWifi;  //DEV-NOTE: Only need to edit this! :)
            tempProvValue = this.tagValue_wifi_mac;             //DEV-NOTE: Only need to edit this! :)
            if (spp.getString(tempSpKeyName, "").equals("") || (!spp.getString(tempSpKeyName, "").equals("") && overwriteExistingSharedPrefsValues)) {
                logV(TAGG+"Existing shared prefs "+tempSpKeyName+" value = \""+spp.getString(tempSpKeyName, "")+"\". Putting \""+tempProvValue+"\".");
                spe.putString(tempSpKeyName, tempProvValue);
            }

            //if existing pref is not populated OR (existing pref is populated AND we can overwrite)
            tempSpKeyName = spKeyName_applianceMacAddressWired; //DEV-NOTE: Only need to edit this! :)
            tempProvValue = this.tagValue_eth_mac;              //DEV-NOTE: Only need to edit this! :)
            if (spp.getString(tempSpKeyName, "").equals("") || (!spp.getString(tempSpKeyName, "").equals("") && overwriteExistingSharedPrefsValues)) {
                logV(TAGG+"Existing shared prefs "+tempSpKeyName+" value = \""+spp.getString(tempSpKeyName, "")+"\". Putting \""+tempProvValue+"\".");
                spe.putString(tempSpKeyName, tempProvValue);
            }

            //if existing pref is not populated OR (existing pref is populated AND we can overwrite)
            tempSpKeyName = spKeyName_ipAddressMethod;      //DEV-NOTE: Only need to edit this! :)
            tempProvValue = this.tagValue_wifi_method;      //DEV-NOTE: Only need to edit this! :)
            if (spp.getString(tempSpKeyName, "").equals("") || (!spp.getString(tempSpKeyName, "").equals("") && overwriteExistingSharedPrefsValues)) {
                logV(TAGG+"Existing shared prefs "+tempSpKeyName+" value = \""+spp.getString(tempSpKeyName, "")+"\". Putting \""+tempProvValue+"\".");
                spe.putString(tempSpKeyName, tempProvValue);
            }

            //if existing pref is not populated OR (existing pref is populated AND we can overwrite)
            tempSpKeyName = spKeyName_serialNumber;         //DEV-NOTE: Only need to edit this! :)
            tempProvValue = this.tagValue_di_mnsSerial;     //DEV-NOTE: Only need to edit this! :)
            if (spp.getString(tempSpKeyName, "").equals("") || (!spp.getString(tempSpKeyName, "").equals("") && overwriteExistingSharedPrefsValues)) {
                logV(TAGG+"Existing shared prefs "+tempSpKeyName+" value = \""+spp.getString(tempSpKeyName, "")+"\". Putting \""+tempProvValue+"\".");
                spe.putString(tempSpKeyName, tempProvValue);
            }

            // Take care of any derived data...

            //if existing pref is not populated OR (existing pref is populated AND we can overwrite)
            tempSpKeyName = spKeyName_applianceMacAddressActive;  //DEV-NOTE: Only need to edit this! :)
            tempProvValue = null;
            if (getWifiMac().isEmpty() == false && getEthMac().isEmpty() == false) {
                //we have both MAC values, use wired?
                tempProvValue = this.tagValue_eth_mac;
            } else if (getWifiMac().isEmpty() == true && getEthMac().isEmpty() == false) {
                //we only have wired MAC value
                tempProvValue = this.tagValue_eth_mac;
            } else if (getWifiMac().isEmpty() == false && getEthMac().isEmpty() == true) {
                //we only have wireless MAC value
                tempProvValue = this.tagValue_wifi_mac;
            }
            if (tempProvValue != null) {
                //update the active MAC field...
                if (spp.getString(tempSpKeyName, "").equals("") || (!spp.getString(tempSpKeyName, "").equals("") && overwriteExistingSharedPrefsValues)) {
                    logV(TAGG + "Existing shared prefs " + tempSpKeyName + " value = \"" + spp.getString(tempSpKeyName, "") + "\". Putting \"" + tempProvValue + "\".");
                    spe.putString(tempSpKeyName, tempProvValue);
                }
                //update the TFTP config filename field with active MAC value
                tempSpKeyName = spKeyName_tftpConfigFilename;
                tempProvValue = "Evolution"+tempProvValue.replaceAll(":", "") +".cfg";
                if (spp.getString(tempSpKeyName, "").equals("") || (!spp.getString(tempSpKeyName, "").equals("") && overwriteExistingSharedPrefsValues)) {
                    logV(TAGG + "Existing shared prefs " + tempSpKeyName + " value = \"" + spp.getString(tempSpKeyName, "") + "\". Putting \"" + tempProvValue + "\".");
                    spe.putString(tempSpKeyName, tempProvValue);
                }
            }

            spe.commit();   //we do commit() instead of apply(), so this is synchronous (and we can depend on the validity of the value that we return)
            ret = true;
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
            ret = false;
        }

        logD(TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }


    /*============================================================================================*/
    /* Getter & Setter Methods */

    public String getOverwriteExistingSharedPrefsWithThese() {
        return this.tagValue_overwriteExistingSharedPrefsWithThese;
    }
    public boolean getOverwriteExistingSharedPrefsWithThese(boolean returnThisIfNothing) {
        final String TAGG = "getOverwriteExistingSharedPrefsWithThese("+String.valueOf(returnThisIfNothing)+"): ";
        boolean ret;

        if (String.valueOf(this.tagValue_overwriteExistingSharedPrefsWithThese).equalsIgnoreCase("true")
                || String.valueOf(this.tagValue_overwriteExistingSharedPrefsWithThese).equalsIgnoreCase("false")) {
            ret = Boolean.parseBoolean(this.tagValue_overwriteExistingSharedPrefsWithThese);
        } else {
            logW(TAGG+"Invalid or uninitialized value for tagValue_overwriteExistingSharedPrefsWithThese, will default to provided argument's value.");
            ret = returnThisIfNothing;
        }

        logD(TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }
    public String getPdfCreatedBy() {
        return this.tagValue_pdf_createdBy;
    }
    public String getPdfCreatedWhen() {
        return this.tagValue_pdf_createdWhen;
    }
    public String getPdfProvStartedWhen() {
        return this.tagValue_pdf_provStartedWhen;
    }
    public String getPdfProvStartedWhenHuman() {
        return this.tagValue_pdf_provStartedWhenHuman;
    }
    public String getPdfDevStartedWhen() {
        return this.tagValue_pdf_devStartedWhen;
    }
    public String getPdfDevStartedWhenHuman() {
        return this.tagValue_pdf_devStartedWhenHuman;
    }
    public String getCiId() {
        return this.tagValue_ci_id;
    }
    public String getDiMnsSerial() {
        return this.tagValue_di_mnsSerial;
    }
    public String getDiArbitraryLabel() {
        return this.tagValue_di_arbitraryLabel;
    }
    public String getPiLightControllerMacAddress() {
        return this.tagValue_pi_lightControllerMacAddress;
    }
    public String getWifiUse() {
        return this.tagValue_wifi_use;
    }
    public String getWifiMac() {
        return this.tagValue_wifi_mac;
    }
    public String getWifiMacNoColon() {
        return this.tagValue_wifi_macNoColon;
    }
    public String getWifiSsid() {
        return this.tagValue_wifi_ssid;
    }
    public String getWifiSecurity() {
        return this.tagValue_wifi_security;
    }
    public String getWifiPassword() {
        return this.tagValue_wifi_password;
    }
    public String getWifiMethod() {
        return this.tagValue_wifi_method;
    }
    public String getWifiIp() {
        return this.tagValue_wifi_ip;
    }
    public String getWifiSubnet() {
        return this.tagValue_wifi_subnet;
    }
    public String getWifiGateway() {
        return this.tagValue_wifi_gateway;
    }
    public String getWifiDns1() {
        return this.tagValue_wifi_dns1;
    }
    public String getWifiDns2() {
        return this.tagValue_wifi_dns2;
    }
    public String getEthUse() {
        return this.tagValue_eth_use;
    }
    public String getEthMac() {
        return this.tagValue_eth_mac;
    }
    public String getEthMacNoColon() {
        return this.tagValue_eth_macNoColon;
    }
    public String getEthMethod() {
        return this.tagValue_eth_method;
    }
    public String getEthIp() {
        return this.tagValue_eth_ip;
    }
    public String getEthSubnet() {
        return this.tagValue_eth_subnet;
    }
    public String getEthGateway() {
        return this.tagValue_eth_gateway;
    }
    public String getEthDns1() {
        return this.tagValue_eth_dns1;
    }
    public String getEthDns2() {
        return this.tagValue_eth_dns2;
    }
    public String getServerIPv4() {
        return this.tagValue_srv_ipv4;
    }

    public boolean setOverwriteExistingSharedPrefsWithThese(@NonNull String value) {
        this.tagValue_overwriteExistingSharedPrefsWithThese = value;

        // Also set the boolean variable
        // This prefers the "true" or "false" provided explicitly, but falls back to false if invalid for some reason
        this.overwriteExistingSharedPrefsValues = (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) && Boolean.parseBoolean(this.tagValue_overwriteExistingSharedPrefsWithThese);

        return value.equals(this.tagValue_overwriteExistingSharedPrefsWithThese);
    }
    public boolean setPdfCreatedBy(@NonNull String value) {
        this.tagValue_pdf_createdBy = value;
        return value.equals(this.tagValue_pdf_createdBy);
    }
    public boolean setPdfCreatedWhen(@NonNull String value) {
        this.tagValue_pdf_createdWhen = value;
        return value.equals(this.tagValue_pdf_createdWhen);
    }
    public boolean setPdfProvStartedWhen(@NonNull String value) {
        this.tagValue_pdf_provStartedWhen = value;
        return value.equals(this.tagValue_pdf_provStartedWhen);
    }
    public boolean setPdfProvStartedWhenHuman(@NonNull String value) {
        this.tagValue_pdf_provStartedWhenHuman = value;
        return value.equals(this.tagValue_pdf_provStartedWhenHuman);
    }
    public boolean setPdfDevStartedWhen(@NonNull String value) {
        this.tagValue_pdf_devStartedWhen = value;
        return value.equals(this.tagValue_pdf_devStartedWhenHuman);
    }
    public boolean setPdfDevStartedWhenHuman(@NonNull String value) {
        this.tagValue_pdf_devStartedWhenHuman = value;
        return value.equals(this.tagValue_pdf_devStartedWhenHuman);
    }
    public boolean setCiId(@NonNull String value) {
        this.tagValue_ci_id = value;
        return value.equals(this.tagValue_ci_id);
    }
    public boolean setDiMnsSerial(@NonNull String value) {
        this.tagValue_di_mnsSerial = value;
        return value.equals(this.tagValue_di_mnsSerial);
    }
    public boolean setDiArbitraryLabel(@NonNull String value) {
        this.tagValue_di_arbitraryLabel = value;
        return value.equals(this.tagValue_di_arbitraryLabel);
    }
    public boolean setPiLightControllerMacAddress(@NonNull String value) {
        this.tagValue_pi_lightControllerMacAddress = value;
        return value.equals(this.tagValue_pi_lightControllerMacAddress);
    }
    public boolean setWifiUse(@NonNull String value) {
        this.tagValue_wifi_use = value;
        return value.equals(this.tagValue_wifi_use);
    }
    public boolean setWifiMac(@NonNull String value) {
        this.tagValue_wifi_mac = value;
        return value.equals(this.tagValue_wifi_mac);
    }
    public boolean setWifiMacNoColon(@NonNull String value) {
        this.tagValue_wifi_macNoColon = value;
        return value.equals(this.tagValue_wifi_macNoColon);
    }
    public boolean setWifiSsid(@NonNull String value) {
        this.tagValue_wifi_ssid = value;
        return value.equals(this.tagValue_wifi_ssid);
    }
    public boolean setWifiSecurity(@NonNull String value) {
        this.tagValue_wifi_security = value;
        return value.equals(this.tagValue_wifi_security);
    }
    public boolean setWifiPassword(@NonNull String value) {
        this.tagValue_wifi_password = value;
        return value.equals(this.tagValue_wifi_password);
    }
    public boolean setWifiMethod(@NonNull String value) {
        this.tagValue_wifi_method = value;
        return value.equals(this.tagValue_wifi_method);
    }
    public boolean setWifiIp(@NonNull String value) {
        this.tagValue_wifi_ip = value;
        return value.equals(this.tagValue_wifi_ip);
    }
    public boolean setWifiSubnet(@NonNull String value) {
        this.tagValue_wifi_subnet = value;
        return value.equals(this.tagValue_wifi_subnet);
    }
    public boolean setWifiGateway(@NonNull String value) {
        this.tagValue_wifi_gateway = value;
        return value.equals(this.tagValue_wifi_gateway);
    }
    public boolean setWifiDns1(@NonNull String value) {
        this.tagValue_wifi_dns1 = value;
        return value.equals(this.tagValue_wifi_dns1);
    }
    public boolean setWifiDns2(@NonNull String value) {
        this.tagValue_wifi_dns2 = value;
        return value.equals(this.tagValue_wifi_dns2);
    }
    public boolean setEthUse(@NonNull String value) {
        this.tagValue_eth_use = value;
        return value.equals(this.tagValue_eth_use);
    }
    public boolean setEthMac(@NonNull String value) {
        this.tagValue_eth_mac = value;
        return value.equals(this.tagValue_eth_mac);
    }
    public boolean setEthMacNoColon(@NonNull String value) {
        this.tagValue_eth_macNoColon = value;
        return value.equals(this.tagValue_eth_macNoColon);
    }
    public boolean setEthMethod(@NonNull String value) {
        this.tagValue_eth_method = value;
        return value.equals(this.tagValue_eth_method);
    }
    public boolean setEthIp(@NonNull String value) {
        this.tagValue_eth_ip = value;
        return value.equals(this.tagValue_eth_ip);
    }
    public boolean setEthSubnet(@NonNull String value) {
        this.tagValue_eth_subnet = value;
        return value.equals(this.tagValue_eth_subnet);
    }
    public boolean setEthGateway(@NonNull String value) {
        this.tagValue_eth_gateway = value;
        return value.equals(this.tagValue_eth_gateway);
    }
    public boolean setEthDns1(@NonNull String value) {
        this.tagValue_eth_dns1 = value;
        return value.equals(this.tagValue_eth_dns1);
    }
    public boolean setEthDns2(@NonNull String value) {
        this.tagValue_eth_dns2 = value;
        return value.equals(this.tagValue_eth_dns2);
    }
    public boolean setServerIPv4(@NonNull String value) {
        this.tagValue_srv_ipv4 = value;
        return value.equals(this.tagValue_srv_ipv4);
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
}
