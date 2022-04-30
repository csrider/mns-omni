package com.messagenetsystems.evolution2.utilities;

/* NetUtils
 * Repository of network related tasks.
 * The focus is more on the network layer/stack/hardware.
 * For tasks that use other layers protocol (e.g. HTTP), use a separate respective utility class for that.
 *
 * You should instantiate this in order to use it.
 *  Ex. NetUtils_fromV1 netUtils = new NetUtils_fromV1(getApplicationContext(), NetUtils.LOG_METHOD_FILELOGGER);
 *
 * Revisions:
 *  2019.10.30      Chris Rider     Created.
 *  2019.12.06      Chris Rider     Migrated MAC retrieval from v1 app.
 *  2020.02.18      Chris Rider     Added cleanup method; migrated in some methods from v1 app.
 *  2020.05.13      Chris Rider     Improvements to WiFi strength methods.
 *  2020.05.14      Chris Rider     Tweaked WiFi subjective wording to err on the side of caution.
 *  2020.06.04      Chris Rider     Reduced Wi-Fi to WiFi.
 */

import android.app.Service;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.bosphere.filelogger.FL;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;

public class NetUtils {
    final private String TAG = this.getClass().getSimpleName();

    private final String MAC_UNAVAILABLE = "02:00:00:00:00:00";                              //define the unavailable or security-restricted MAC address
    public static final String IP_METHOD_STATIC = "STATIC";
    public static final String IP_METHOD_DHCP = "DHCP";
    public static final String ACTIVE_NIC_WLAN = "WiFi";
    public static final String ACTIVE_NIC_ETH0 = "Ethernet";
    public static final int WIFI_DBM_NON_EXISTENT = -127;

    public static final int LOG_METHOD_LOGCAT = 1;
    public static final int LOG_METHOD_FILELOGGER = 2;
    private final int LOG_SEVERITY_V = 1;
    private final int LOG_SEVERITY_D = 2;
    private final int LOG_SEVERITY_I = 3;
    private final int LOG_SEVERITY_W = 4;
    private final int LOG_SEVERITY_E = 5;
    private int logMethod = LOG_METHOD_LOGCAT;

    private Context appContext;     //TODO: migrate this to WeakReference??

    /** Constructor
     * @param appContext Application context
     * @param logMethod Logging method to use
     */
    public NetUtils(Context appContext, int logMethod) {
        this.logMethod = logMethod;
        this.appContext = appContext;
    }

    public void cleanup() {
        appContext = null;
    }


    /*============================================================================================*/
    /* Utility Methods */

    public boolean isWifiEnabled() {
        final String TAGG = "isWifiEnabled: ";
        boolean ret = false;

        try {
            WifiManager wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);

            if (wifiManager == null) {
                logE(TAGG+"Failed to get WifiManager instance.");
            } else {
                ret = wifiManager.isWifiEnabled();
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logD(TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    public void setWifiEnabled() {
        final String TAGG = "setWifiEnabled: ";

        try {
            WifiManager wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
            wifiManager.setWifiEnabled(true);
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }

    //TODO!!!!
    public boolean isWifiNetworkAvailable(@NonNull String wifiSSID) {
        final String TAGG = "isWifiNetworkAvailable(\""+wifiSSID+"\"): ";
        boolean ret = false;

        if (isWifiEnabled()) {
            try {
                WifiManager wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();

                final List<ScanResult> results = wifiManager.getScanResults();

                if (results != null) {
                    for (int i = 0; i < results.size(); i++) {
                        String ssid = results.get(i).SSID;
                        logV(TAGG + "ssid = " + ssid);
                        //if (ssid.startsWith("sv-")) {
                        //    buf.append(ssid+"\n");
                        //}
                    }
                }
            } catch (Exception e) {
                logE(TAGG + "Exception caught: " + e.getMessage());
            }
        } else {
            logW(TAGG+"Wifi not enabled, so cannot determine.");
        }

        logD(TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    /** getMacAddressFromWifiIPv6()
     * This should work on at least Android 6 (M) and Android 7.1.2 (N).
     * Had to develop this due to MAC being locked out after Android 5, from WifiManager.
     *
     * 2017.12.28   Chris Rider     Creation.
     */
    public String getMacAddressFromWifiIPv6() {
        final String TAGG = "getMacAddressFromWifiIPv6: ";

        String ret = MAC_UNAVAILABLE;
        String hexValue;

        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (!nif.getName().equalsIgnoreCase("wlan0")) continue;

                //try to determine hardware address bytes
                byte[] macBytes = nif.getHardwareAddress();

                //if we cannot determine, then break out and return default value
                if (macBytes == null) {
                    break;
                }

                //convert the bytes into a string of hex characters
                StringBuilder res1 = new StringBuilder();
                for (byte b : macBytes) {
                    hexValue = Integer.toHexString(b & 0xFF);
                    if (hexValue.length() == 1) {
                        hexValue = "0"+hexValue;
                    } else if (hexValue.length() == 0) {
                        hexValue = "00";
                    }
                    res1.append(hexValue);
                    res1.append(":");
                }

                //remove trailing ":" character
                if (res1.length() > 0) {
                    res1.deleteCharAt(res1.length() - 1);
                }

                ret = res1.toString();
                Log.d(TAG, TAGG+"MAC from IPv6 determined. Returning \""+ ret +"\".");
                return ret;
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught! Returning '"+ ret +"'.");
            Log.d(TAG, TAGG+"Error message: "+ e.getMessage());
        }

        Log.w(TAG, TAGG+"MAC not available, returning \""+ret+"\".");
        return ret;
    }

    /** M (v6) method for getting MAC address.
     * 2018.04.20   Chris Rider     Creation. */
    public String getWifiMacAddress_forMarshmallow() {
        try {
            String interfaceName = "wlan0";
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                if (!intf.getName().equalsIgnoreCase(interfaceName)){
                    continue;
                }

                byte[] mac = intf.getHardwareAddress();
                if (mac==null){
                    Log.w(TAG, "getWifiMacAddress_forMarshmallow: MAC is null, returning \""+MAC_UNAVAILABLE+"\".");
                    return MAC_UNAVAILABLE;
                }

                StringBuilder buf = new StringBuilder();
                for (byte aMac : mac) {
                    buf.append(String.format("%02X:", aMac));
                }
                if (buf.length()>0) {
                    buf.deleteCharAt(buf.length() - 1);
                }

                Log.v(TAG, "getWifiMacAddress_forMarshmallow: Returning MAC = \""+buf.toString()+"\".");
                return buf.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "getWifiMacAddress_forMarshmallow: Exception caught: "+ e.getMessage());
        }

        Log.w(TAG, "getWifiMacAddress_forMarshmallow: MAC not available, returning \""+MAC_UNAVAILABLE+"\".");
        return MAC_UNAVAILABLE;
    }

    /******  MIGRATED  *******/
    /** Return which interface is currently active.
     * 2019.06.03   Chris Rider     Created.
     * 2019.11.06   Chris Rider     Refactored.
     */
    public String getActiveNIC() {
        final String TAGG = "getActiveNIC: ";
        Log.v(TAG, TAGG+"Invoked.");

        String ret = null;

        try {
            if (nicEthIsUp()) {
                ret = ACTIVE_NIC_ETH0;
            } else if (nicWifiIsUp()) {
                ret = ACTIVE_NIC_WLAN;
            } else {
                Log.w(TAG, TAGG+"No network interface seems to be up.");
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    /** Return whether Wi-Fi interface is UP.
     * 2019.02.05   Chris Rider     Created.
     */
    public boolean nicWifiIsUp() {
        final String TAGG = "nicWifiIsUp: ";
        Log.v(TAG, TAGG+"Invoked.");

        boolean ret = false;

        Process process = null;
        OutputStream stdin;     //used to write commands to shell... using OutputStream type, we can execute commands like writing commands in terminal
        InputStream stdout;     //used to read output of a command we executed... using InputStream type, we can input command's output to our routine here
        InputStream stderr;     //used to read errors of a command we executed... using InputStream type, we can input command's errors to our routine here
        BufferedReader br;
        String line;

        try {
            // Start a super-user process under which to execute our commands as root
            Log.d(TAG, TAGG + "Starting super-user shell session...");
            process = Runtime.getRuntime().exec("su");

            // Get process streams
            stdin = process.getOutputStream();
            stdout = process.getInputStream();
            stderr = process.getErrorStream();

            // Construct and execute command (new-line is like hitting enter)
            Log.d(TAG, TAGG + "Specifying shell command...");
            stdin.write(("/system/bin/ip link show | /system/bin/grep \"state UP\"\n").getBytes());

            // Exit the shell
            stdin.write(("exit\n").getBytes());

            // Flush and close the stdin stream
            stdin.flush();
            stdin.close();

            // Read output of the executed command
            Log.v(TAG, TAGG+"Reading output of executed command...");
            br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                Log.v(TAG, TAGG+"stdout line: "+line);
                if (line.toLowerCase().contains("wlan0")) {
                    ret = true;
                }
            }
            br.close();

            // Read error stream of the executed command
            Log.v(TAG, TAGG+"Reading errors of executed command...");
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                Log.w(TAG, TAGG+"stderr line: "+line);
            }
            br.close();

            // Wait for process to finish
            process.waitFor();
            process.destroy();

            //} catch(SecurityException e) {
            //    Log.e(TAG, TAGG +"Security Exception caught: " + String.valueOf(e));
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }

        // Cleanup
        if (process != null) {
            process.destroy();
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    /** Return whether wired-ethernet interface is UP.
     * 2019.02.05   Chris Rider     Created.
     */
    public boolean nicEthIsUp() {
        final String TAGG = "nicEthIsUp: ";
        Log.v(TAG, TAGG+"Invoked.");

        boolean ret = false;

        Process process = null;
        OutputStream stdin;     //used to write commands to shell... using OutputStream type, we can execute commands like writing commands in terminal
        InputStream stdout;     //used to read output of a command we executed... using InputStream type, we can input command's output to our routine here
        InputStream stderr;     //used to read errors of a command we executed... using InputStream type, we can input command's errors to our routine here
        BufferedReader br;
        String line;

        try {
            // Start a super-user process under which to execute our commands as root
            Log.d(TAG, TAGG + "Starting super-user shell session...");
            process = Runtime.getRuntime().exec("su");

            // Get process streams
            stdin = process.getOutputStream();
            stdout = process.getInputStream();
            stderr = process.getErrorStream();

            // Construct and execute command (new-line is like hitting enter)
            Log.d(TAG, TAGG + "Specifying shell command...");
            stdin.write(("/system/bin/ip link show | /system/bin/grep \"state UP\"\n").getBytes());

            // Exit the shell
            stdin.write(("exit\n").getBytes());

            // Flush and close the stdin stream
            stdin.flush();
            stdin.close();

            // Read output of the executed command
            Log.v(TAG, TAGG+"Reading output of executed command...");
            br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                Log.v(TAG, TAGG+"stdout line: "+line);
                if (line.toLowerCase().contains("eth0")) {
                    ret = true;
                }
            }
            br.close();

            // Read error stream of the executed command
            Log.v(TAG, TAGG+"Reading errors of executed command...");
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                Log.w(TAG, TAGG+"stderr line: "+line);
            }
            br.close();

            // Wait for process to finish
            process.waitFor();
            process.destroy();

            //} catch(SecurityException e) {
            //    Log.e(TAG, TAGG +"Security Exception caught: " + String.valueOf(e));
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }

        // Cleanup
        if (process != null) {
            process.destroy();
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    public static int MAC_INTERFACE_AUTO = 0;
    public static int MAC_INTERFACE_WIFI = 1;
    public static int MAC_INTERFACE_WIRED = 2;
    public static int MAC_COLONS_NO = 0;
    public static int MAC_COLONS_YES = 1;
    /** Return MAC address as a string, of the specified interface.
     * If AUTO is specified, then use the currently-UP interface.
     * 2019.02.05   Chris Rider     Created.
     */
    public String getMacAddress(int networkInterface, final int colonDelimiter) {
        final String TAGG = "getMacAddress: ";
        Log.v(TAG, TAGG+"Invoked (networkInterface="+String.valueOf(networkInterface)+", colonDelimiter="+String.valueOf(colonDelimiter)+").");

        String macAddress = null;   //returned
        String shellCommand = "";

        Process process = null;
        OutputStream stdin;     //used to write commands to shell... using OutputStream type, we can execute commands like writing commands in terminal
        InputStream stdout;     //used to read output of a command we executed... using InputStream type, we can input command's output to our routine here
        InputStream stderr;     //used to read errors of a command we executed... using InputStream type, we can input command's errors to our routine here
        BufferedReader br;
        String line;

        if (networkInterface == MAC_INTERFACE_AUTO) {
            if (nicEthIsUp()) {
                networkInterface = MAC_INTERFACE_WIRED;
            } else if (nicWifiIsUp()) {
                networkInterface = MAC_INTERFACE_WIFI;
            } else {
                Log.w(TAG, TAGG+"It does not appear that any network interface is up. Assuming to use Wi-Fi.");
                networkInterface = MAC_INTERFACE_WIFI;
            }
        }

        try {
            // Start a super-user process under which to execute our commands as root
            Log.d(TAG, TAGG + "Starting super-user shell session...");
            process = Runtime.getRuntime().exec("su");

            // Get process streams
            stdin = process.getOutputStream();
            stdout = process.getInputStream();
            stderr = process.getErrorStream();

            // Construct and execute command (new-line is like hitting enter)
            Log.d(TAG, TAGG + "Specifying shell command...");
            if (networkInterface == MAC_INTERFACE_WIFI) {
                shellCommand = "cat /sys/class/net/wlan0/address";
            } else if (networkInterface == MAC_INTERFACE_WIRED) {
                shellCommand = "cat /sys/class/net/eth0/address";
            } else {
                Log.w(TAG, TAGG+"Unhandled networkInterface value. Assuming to use Wi-Fi.");
                shellCommand = "cat /sys/class/net/wlan0/address";
            }
            stdin.write((shellCommand+"\n").getBytes());

            // Exit the shell
            stdin.write(("exit\n").getBytes());

            // Flush and close the stdin stream
            stdin.flush();
            stdin.close();

            // Read output of the executed command
            Log.v(TAG, TAGG+"Reading output of executed command...");
            br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                Log.v(TAG, TAGG+"stdout line: "+line);
                if (!line.isEmpty() && !line.contains("No such file")) {
                    macAddress = line;
                }
            }
            br.close();

            // Read error stream of the executed command
            Log.v(TAG, TAGG+"Reading errors of executed command...");
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                Log.w(TAG, TAGG+"stderr line: "+line);
            }
            br.close();

            // Wait for process to finish
            process.waitFor();
            process.destroy();
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }

        // Cleanup
        if (process != null) {
            process.destroy();
        }

        if (macAddress != null && macAddress.isEmpty()) {
            macAddress = null;
        }

        if (macAddress != null && colonDelimiter == MAC_COLONS_NO) {
            macAddress = macAddress.replaceAll(":", "");
        }

        Log.v(TAG, TAGG+"Returning \""+String.valueOf(macAddress)+"\".");
        return macAddress;
    }

    /** Figure out and return this device's MAC address as a string.
     * NOTE: requires application context or memory may leak. **/
    public String getMacAddressAsString(Context context) {
        final String TAGG = "getMacAddressAsString: ";
        String macAddress = null;
        WifiManager wifiMgr;
        WifiInfo connInfo;

        try {
            wifiMgr = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            connInfo = wifiMgr.getConnectionInfo();
            macAddress = connInfo.getMacAddress();

            if (macAddress.equals(MAC_UNAVAILABLE)) {
                //attempt to calculate MAC from our IPv6 address...
                Log.i(TAG, TAGG+"Failed to get MAC from WifiManager, attempting to determine from IPv6...");
                macAddress = getMacAddressFromWifiIPv6();
            }

            //if we still don't have an address
            if (macAddress.equals(MAC_UNAVAILABLE)
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Log.i(TAG, TAGG+"Failed to get MAC from IPv6, attempting to determine from Marshmallow...");
                macAddress = getWifiMacAddress_forMarshmallow();
            }

            if (macAddress.equals(MAC_UNAVAILABLE)) {       /* TODO: WORK IN PROGRESS */
                //then we're Android 6+, in which simple hardware info isn't allowed due to security restrictions
                //(https://developer.android.com/about/versions/marshmallow/android-6.0-changes.html#behavior-hardware-id)
                Log.i(TAG, TAGG+"Failed to get MAC from IPv6, attempting to determine from device-admin API...");

                /* Doesn't seem to yield results
                try {
                    Runtime.getRuntime().exec("dpm set-device-owner com.messagenetsystems.evolution/.DevAdminReceiver");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                */

                DeviceAdminReceiver admin = new DeviceAdminReceiver();
                DevicePolicyManager devicepolicymanager = admin.getManager(context);
                ComponentName name1 = admin.getWho(context);
                if (devicepolicymanager.isAdminActive(name1)){
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        macAddress = devicepolicymanager.getWifiMacAddress(name1);
                    }
                    Log.i(TAG, TAGG+"Mac found: "+ macAddress);
                }


                /* This throws an exception
                ComponentName deviceAdmin = new ComponentName(context, DevAdminReceiver.class);
                DevicePolicyManager mDpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    macAddress = mDpm.getWifiMacAddress(deviceAdmin);
                    Log.i(TAG, TAGG+"Mac found: "+ macAddress);
                }
                if (!mDpm.isAdminActive(deviceAdmin)) {
                    Log.w(TAG, TAGG+"This app is not a device admin.");
                }
                if (mDpm.isDeviceOwnerApp(getPackageName())) {
                    mDpm.setLockTaskPackages(deviceAdmin, new String[]{getPackageName()});
                } else {
                    Log.w(TAG, TAGG+"This app is not the device owner.");
                }
                */

                /* This doesn't throw an exception, but doesn't work either
                ComponentName admin = new ComponentName(context, context.getApplicationContext().getPackageName());
                DevicePolicyManager manager = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                if (manager.isDeviceOwnerApp(context.getApplicationContext().getPackageName())) {
                    // This app is set up as the device owner. Show the main features.
                    Log.d(TAG, TAGG + "The app IS the device OWNER");
                    //showFragment(DeviceOwnerFragment.newInstance());
                } else if (manager.isAdminActive(admin)) {
                    Log.d(TAG, TAGG + "The app IS a device ADMIN.");
                } else {
                    // This app is not set up as the device owner. Show instructions.
                    Log.d(TAG, TAGG+"The app is NOT an admin nor the device owner.");
                    //showFragment(InstructionFragment.newInstance());
                }
                */
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught! Returning '"+ macAddress +"'.");
            Log.d(TAG, TAGG+"Error message: "+ e.getMessage());
        }

        return macAddress;
    }

    /** getWifiStrength_dBm
     */
    public int getWifiStrength_dBm() {
        final String TAGG = "getWifiStrength_dBm: ";

        try {
            if (ACTIVE_NIC_WLAN.equals(String.valueOf(getActiveNIC()))) {
                WifiManager wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null) {
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    return wifiInfo.getRssi();
                }
            } else {
                return 0;
            }
        } catch (Exception e) {
            Log.e(TAG, "getWifiStrength_dBm: Exception caught: "+e.getMessage());
            return 0;
        }

        Log.e(TAG, TAGG+"Could not get WifiManager instance.");
        return 0;
    }

    /** getWifiStrength_percent
     */
    public int getWifiStrength_percent() {
        final String TAGG = "getWifiStrength_percent: ";

        try {
            WifiManager wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                int numberOfLevels = 10;
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int level = WifiManager.calculateSignalLevel(wifiInfo.getRssi(), numberOfLevels);
                return level * 10;
            }
        } catch (Exception e) {
            Log.e(TAG, "getWifiStrength_percent: Exception caught: "+e.getMessage());
            return 0;
        }

        Log.e(TAG, TAGG+"Could not get WifiManager instance.");
        return 0;
    }

    /** getWifiStrength_bars
     */
    public int getWifiStrength_bars(int maxBars) {
        final String TAGG = "getWifiStrength_bars: ";

        try {
            WifiManager wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                int numberOfLevels = maxBars;
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int level = WifiManager.calculateSignalLevel(wifiInfo.getRssi(), numberOfLevels);
                logV(TAGG+"Returning: "+String.valueOf(level)+" (out of max "+maxBars+")");
                return level;
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
            return 0;
        }

        logE(TAGG+"Could not get WifiManager instance.");
        return 0;
    }

    /** getWifiStrength_subjective
     */
    public static final String WIFI_STRENGTH_SUBJECTIVE_5_BEST = "Good";
    public static final String WIFI_STRENGTH_SUBJECTIVE_4_GOOD = "Adequate";
    public static final String WIFI_STRENGTH_SUBJECTIVE_3_FAIR = "Fair";
    public static final String WIFI_STRENGTH_SUBJECTIVE_2_WEAK = "Weak";
    public static final String WIFI_STRENGTH_SUBJECTIVE_1_WORST = "Very Weak";
    public String getWifiStrength_subjective() {
        final String TAGG = "getWifiStrength_subjective: ";

        String result = "Unknown";
        int dBm = WIFI_DBM_NON_EXISTENT;

        try {
            WifiManager wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                dBm = wifiInfo.getRssi();
            } else {
                Log.e(TAG, TAGG+"Could not get a WifiManager instance.");
            }

            // These handle extreme values (practically/theoretically impossible, but handling just in case)
            if (dBm > -10) {
                return WIFI_STRENGTH_SUBJECTIVE_5_BEST;
            } else if (dBm < -100) {
                return "No Signal";
            }

            // These handle otherwise normal values
            if (dBm > -50) {
                return WIFI_STRENGTH_SUBJECTIVE_5_BEST;
            } else if (dBm >= -58) {
                return WIFI_STRENGTH_SUBJECTIVE_4_GOOD;
            } else if (dBm >= -66) {
                return WIFI_STRENGTH_SUBJECTIVE_3_FAIR;
            } else if (dBm >= -74) {
                return WIFI_STRENGTH_SUBJECTIVE_2_WEAK;
            } else if (dBm > WIFI_DBM_NON_EXISTENT) {
                return WIFI_STRENGTH_SUBJECTIVE_1_WORST;
            } else if (dBm == WIFI_DBM_NON_EXISTENT) {
                return "Non-Existent";
            } else {
                return result;
            }
        } catch (Exception e) {
            Log.e(TAG, "getWifiStrength_subjective: Exception caught: "+e.getMessage());
            return result;
        }
    }

    /** Figure out and return this device's IPv4 address as a string.
     * NOTE: requires application context or memory may leak.
     * 2019.02.05   Chris Rider     Refactored/overloaded to support multiple possible interfaces. **/
    public String getDeviceIpAddressAsString() {
        Log.v(TAG, "getDeviceIpAddressAsString: Getting IP for active interface...");
        return getDeviceIpAddressAsString_activeInterface();
    }
    public String getDeviceIpAddressAsString_activeInterface() {
        final String TAGG = "getDeviceIpAddressAsString_activeInterface: ";
        String ret = null;

        if (nicEthIsUp()) {
            ret = getDeviceIpAddressAsString_wiredInterface();
            if (ret != null && ret.equals("0.0.0.0")) {
                Log.w(TAG, TAGG+"No valid address from wired interface. Getting IP from Wi-Fi interface.");
                ret = getDeviceIpAddressAsString_wifiInterface(false);
            }
        } else if (nicWifiIsUp()) {
            ret = getDeviceIpAddressAsString_wifiInterface(false);
            if (ret != null && ret.equals("0.0.0.0")) {
                Log.w(TAG, TAGG+"No valid address from Wi-Fi interface. Getting IP from wired interface.");
                ret = getDeviceIpAddressAsString_wifiInterface(false);
            }
        } else {
            Log.w(TAG, TAGG+"No network interface seems to be up.");
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }
    public String getDeviceIpAddressAsString(boolean doUrlEncodedFormat) {
        //DEV-NOTE: this is merely for support of any possible older/original code that expects wifi IP (didn't take time to find-usages and refactor)
        return getDeviceIpAddressAsString_wifiInterface(doUrlEncodedFormat);
    }
    public String getDeviceIpAddressAsString_wifiInterface(boolean doUrlEncodedFormat) {
        final String TAGG = "getDeviceIpAddressAsString: ";

        String ip = null;
        int ipAsInt;
        WifiManager wifiMgr;
        WifiInfo wifiInfo;

        try {
            wifiMgr = (WifiManager) appContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            wifiInfo = wifiMgr.getConnectionInfo();
            ipAsInt = wifiInfo.getIpAddress();
            ip = intToInetAddress(ipAsInt).getHostAddress();

            if (ip.contains("0.0.0.0")) {
                Log.i(TAG, TAGG+"Invalid IP address. Probably using wired Ethernet instead of Wi-Fi.");

                ConnectivityManager connectivityManager = (ConnectivityManager) appContext.getSystemService(Service.CONNECTIVITY_SERVICE);
                if(connectivityManager.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_ETHERNET) {
                    //there is no EthernetManager class, there is only WifiManager. so, I used this below trick to get my IP range, dns, gateway address etc
                    //Log.i("myType ", "Ethernet");
                    //Log.i("routes ", connectivityManager.getLinkProperties(connectivityManager.getActiveNetwork()).getRoutes().toString());
                    //Log.i("domains ", connectivityManager.getLinkProperties(connectivityManager.getActiveNetwork()).getDomains().toString());
                    //Log.i("ip address ", connectivityManager.getLinkProperties(connectivityManager.getActiveNetwork()).getLinkAddresses().toString());
                    //Log.i("dns address ", connectivityManager.getLinkProperties(connectivityManager.getActiveNetwork()).getDnsServers().toString());
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        ip = connectivityManager.getLinkProperties(connectivityManager.getActiveNetwork()).getLinkAddresses().get(0).toString();
                        ip = ip.split("/")[0];
                    } else {
                        Log.w(TAG, TAGG+"Current Android SDK version does not support ConnectivityManager's getActiveNetwork() method.");
                    }
                }
            }

            if (doUrlEncodedFormat) {
                ip = URLEncoder.encode(ip, "UTF-8");
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+ e.getMessage());
        }

        wifiInfo = null;
        wifiMgr = null;

        Log.v(TAG, TAGG+"Returning \""+ ip +"\".");
        return ip;
    }
    public String getDeviceIpAddressAsString_wiredInterface() {
        final String TAGG = "getDeviceIpAddressAsString_wiredInterface: ";
        Log.v(TAG, TAGG+"Invoked.");

        String ret = "0.0.0.0";

        Process process = null;
        OutputStream stdin;     //used to write commands to shell... using OutputStream type, we can execute commands like writing commands in terminal
        InputStream stdout;     //used to read output of a command we executed... using InputStream type, we can input command's output to our routine here
        InputStream stderr;     //used to read errors of a command we executed... using InputStream type, we can input command's errors to our routine here
        BufferedReader br;
        String line;

        try {
            // Start a super-user process under which to execute our commands as root
            Log.d(TAG, TAGG + "Starting super-user shell session...");
            process = Runtime.getRuntime().exec("su");

            // Get process streams
            stdin = process.getOutputStream();
            stdout = process.getInputStream();
            stderr = process.getErrorStream();

            // Construct and execute command (new-line is like hitting enter)
            Log.d(TAG, TAGG + "Specifying shell command...");
            stdin.write(("/system/bin/ifconfig eth0 | /system/bin/grep \"inet addr:\" | /system/bin/busybox awk '{print $2}' | /system/bin/cut -d':' -f2\n").getBytes());

            // Exit the shell
            stdin.write(("exit\n").getBytes());

            // Flush and close the stdin stream
            stdin.flush();
            stdin.close();

            // Read output of the executed command
            Log.v(TAG, TAGG+"Reading output of executed command...");
            br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                Log.v(TAG, TAGG+"stdout line: "+line);
                if (!line.isEmpty()) {
                    ret = line;
                }
            }
            br.close();

            // Read error stream of the executed command
            Log.v(TAG, TAGG+"Reading errors of executed command...");
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                Log.w(TAG, TAGG+"stderr line: "+line);
            }
            br.close();

            // Wait for process to finish
            process.waitFor();
            process.destroy();

            //} catch(SecurityException e) {
            //    Log.e(TAG, TAGG +"Security Exception caught: " + String.valueOf(e));
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }

        // Cleanup
        if (process != null) {
            process.destroy();
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    /** Convert an int style IP address to a human-readable version
     *  Note: If you don't want the leading "/" then apply getHostAddress() method to what this returns. */
    public InetAddress intToInetAddress(int address) {
        byte[] addressBytes = {
                (byte) (0xff & address),
                (byte) (0xff & (address >> 8)),
                (byte) (0xff & (address >> 16)),
                (byte) (0xff & (address >> 24))
        };
        try {
            return InetAddress.getByAddress(addressBytes);
        } catch (UnknownHostException e) {
            throw new AssertionError();
        }//end try-catch
    }

    /** Determines and returns currently active IP method (e.g. "STATIC", "DHCP") */
    public String getCurrentIpMethod_activeInterface(){
        final String TAGG = "getCurrentIpMethod_activeInterface: ";
        String ret = IP_METHOD_DHCP;    //default

        if (nicWifiIsUp()) {
            ret = getCurrentIpMethod_wifi();
        } else {
            Log.w(TAG, TAGG+"Unsupported NIC IP-method detection (for this method), using default.");   //TODO
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }
    public String getCurrentIpMethod_wifi(){
        final String TAGG = "getCurrentIpMethod_wifi: ";
        String ret = IP_METHOD_DHCP;        //just assume DHCP by default

        final int LOLLIPOP = 21;
        final int NOUGAT = 25;
        int currentapiVersion = android.os.Build.VERSION.SDK_INT;
        WifiConfiguration wifiConf;
        Enum ss;
        Field f;
        Object ipConfiguration;

        try {
            wifiConf = getCurrentWiFiConfiguration();
            if (wifiConf == null) {
                Log.e(TAG, TAGG+"Could not get a WifiConfiguration instance.");
                return ret;
            }

            if (currentapiVersion >= LOLLIPOP) {
                ipConfiguration = wifiConf.getClass().getMethod("getIpConfiguration").invoke(wifiConf);
                f = ipConfiguration.getClass().getField("ipAssignment");
                ss = (Enum) f.get(ipConfiguration);
            } else {
                ss = (Enum) WifiConfiguration.class.getField("ipAssignment").get(wifiConf);
            }
            /*
            if (ss.name().equals("STATIC")) {
                return "STATIC";
            } else if (ss.name().equals("DHCP")) {
                return "DHCP";
            }
            */

            wifiConf = null;
            f = null;
            ipConfiguration = null;

            ret = ss.name();
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught. Returning \""+String.valueOf(ret)+"\". ("+e.getMessage()+")");
        }

        return ret;
    }

    public WifiConfiguration getCurrentWiFiConfiguration() {
        final String TAGG = "getCurrentWifiConfiguration: ";
        WifiConfiguration wifiConf = null;
        ConnectivityManager connManager;
        NetworkInfo networkInfo;
        WifiManager wifiManager;
        WifiInfo connectionInfo;

        try {
            connManager = (ConnectivityManager) appContext.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            networkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (networkInfo.isConnected()) {
                wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
                connectionInfo = wifiManager.getConnectionInfo();
                if (connectionInfo != null && !TextUtils.isEmpty(connectionInfo.getSSID())) {
                    List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
                    if (configuredNetworks != null) {
                        for (WifiConfiguration conf : configuredNetworks) {
                            if (conf.networkId == connectionInfo.getNetworkId()) {
                                wifiConf = conf;
                                break;
                            }
                        }
                    }
                }
            }
        } catch (NullPointerException e) {
            Log.e(TAG, TAGG+"NullPointerException caught (something probably failed to instantiate): "+e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }

        connManager = null;
        networkInfo = null;
        wifiManager = null;
        connectionInfo = null;

        if (wifiConf == null) {
            Log.w(TAG, TAGG+"Failed to get a WifiConfiguration instance. Returning null.");
        }

        return wifiConf;
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
