package com.messagenetsystems.evolution2.utilities;

/* NetUtils_fromV1
  Repository of network related tasks.
  The focus is more on the network layer/stack/hardware.
  For tasks that use other layers protocol (e.g. HTTP), use a separate respective utility class for that.

  You should instantiate this in order to use it.
   Ex. NetUtils_fromV1 netUtils = new NetUtils_fromV1(getApplicationContext());

  Revisions:
   2019.10.30      Chris Rider     Created.
 */

import android.app.Service;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.messagenetsystems.evolution2.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class NetUtils_fromV1 {
    private final String TAG = this.getClass().getSimpleName();

    private final String MAC_UNAVAILABLE = "02:00:00:00:00:00";                              //define the unavailable or security-restricted MAC address
    public static final String IP_METHOD_STATIC = "STATIC";
    public static final String IP_METHOD_DHCP = "DHCP";
    public static final String ACTIVE_NIC_WLAN = "Wi-Fi";
    public static final String ACTIVE_NIC_ETH0 = "Ethernet";
    public static final int WIFI_DBM_NON_EXISTENT = -127;

    private Context appContext;

    /** Constructor
     * @param appContext
     */
    public NetUtils_fromV1(Context appContext) {
        Log.v(TAG, "Instantiating.");

        this.appContext = appContext;
    }

    /*============================================================================================*/
    /* Hardware & Stack Control Methods */



    /*============================================================================================*/
    /* Information Methods */

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




    /*============================================================================================*/
    /* Conversion Methods */



    /******  MIGRATED  *******/
    /** Return which interface is currently active.
     * 2019.06.03   Chris Rider     Created.
     * 2019.11.06   Chris Rider     Refactored.
     */
    public String getActiveNIC(Context context) {
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

    /** Figure out and return this device's IPv4 address as a string.
     * NOTE: requires application context or memory may leak.
     * 2019.02.05   Chris Rider     Refactored/overloaded to support multiple possible interfaces. **/
    public String getDeviceIpAddressAsString(Context context) {
        Log.v(TAG, "getDeviceIpAddressAsString: Getting IP for active interface...");
        return getDeviceIpAddressAsString_activeInterface(context);
    }
    public String getDeviceIpAddressAsString_activeInterface(Context context) {
        final String TAGG = "getDeviceIpAddressAsString_activeInterface: ";
        String ret = null;

        if (nicEthIsUp()) {
            ret = getDeviceIpAddressAsString_wiredInterface();
            if (ret != null && ret.equals("0.0.0.0")) {
                Log.w(TAG, TAGG+"No valid address from wired interface. Getting IP from Wi-Fi interface.");
                ret = getDeviceIpAddressAsString_wifiInterface(context, false);
            }
        } else if (nicWifiIsUp()) {
            ret = getDeviceIpAddressAsString_wifiInterface(context, false);
            if (ret != null && ret.equals("0.0.0.0")) {
                Log.w(TAG, TAGG+"No valid address from Wi-Fi interface. Getting IP from wired interface.");
                ret = getDeviceIpAddressAsString_wifiInterface(context, false);
            }
        } else {
            Log.w(TAG, TAGG+"No network interface seems to be up.");
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }
    public String getDeviceIpAddressAsString(Context context, boolean doUrlEncodedFormat) {
        //DEV-NOTE: this is merely for support of any possible older/original code that expects wifi IP (didn't take time to find-usages and refactor)
        return getDeviceIpAddressAsString_wifiInterface(context, doUrlEncodedFormat);
    }
    public String getDeviceIpAddressAsString_wifiInterface(Context context, boolean doUrlEncodedFormat) {
        final String TAGG = "getDeviceIpAddressAsString: ";

        String ip = null;
        int ipAsInt;
        WifiManager wifiMgr;
        WifiInfo wifiInfo;

        try {
            wifiMgr = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            wifiInfo = wifiMgr.getConnectionInfo();
            ipAsInt = wifiInfo.getIpAddress();
            ip = intToInetAddress(ipAsInt).getHostAddress();

            if (ip.contains("0.0.0.0")) {
                Log.i(TAG, TAGG+"Invalid IP address. Probably using wired Ethernet instead of Wi-Fi.");

                ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Service.CONNECTIVITY_SERVICE);
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

    /** Figure out and return this device's IPv6 address as a string. **/
    public String getDeviceIpv6AddressAsString() {
        final String TAGG = "getDeviceIpv6AddressAsString: ";

        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();

                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet6Address) {
                        String ipaddress = inetAddress.getHostAddress();
                        Log.v(TAG, TAGG+"Returning "+ ipaddress +".");
                        return ipaddress;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught! Returning null.");
            Log.d(TAG, TAGG+"Error message: "+ e.getMessage());
        }
        return null;
    }

    /** Return the currently-configured WiFi SSID (may not actually be connected)
     * NOTE: requires application context or memory may leak. */
    public String getCurrentSSID_configuredOnly(Context context) {
        final String TAGG = "getCurrentSSID_configuredOnly: ";

        String ssid = null;
        WifiManager wifiMgr;
        WifiInfo wifiInfo;

        try {
            wifiMgr = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            wifiInfo = wifiMgr.getConnectionInfo();
            ssid = wifiInfo.getSSID();
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught! Returning '"+ ssid +"'.");
            Log.d(TAG, TAGG+"Error message: "+ e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning "+ ssid +".");
        return ssid;
    }

    /** Return the currently-connected WiFi SSID.
     * NOTE: requires application context or memory may leak. */
    public String getCurrentSSID(Context context) {
        final String TAGG = "getCurrentSSID: ";

        String ssid = "";
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        if (networkInfo == null) {
            Log.i(TAG, TAGG+"No active network info found.");
            return ssid;
        }

        if (networkInfo.isConnected()) {
            final WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
            if (connectionInfo != null) {
                ssid = connectionInfo.getSSID();
            } else {
                Log.i(TAG, TAGG+"No connection info available.");
            }
        }
        return ssid;
    }

    /** Figure out and return the DHCP server's IP address as a string.
     * NOTE: requires application context or memory may leak. **/
    public String getDhcpServerIpAddressAsString(Context context) {
        final String TAGG = "getDhcpServerIpAddressAsString: ";

        String ip = null;
        int ipAsInt;
        WifiManager wifiMgr;
        DhcpInfo dhcpInfo;

        try {
            wifiMgr = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            dhcpInfo = wifiMgr.getDhcpInfo();
            ipAsInt = dhcpInfo.serverAddress;
            ip = intToInetAddress(ipAsInt).getHostAddress();
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught! Returning '"+ ip +"'.");
            Log.d(TAG, TAGG+"Error message: "+ e.getMessage());
        }

        return ip;
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

    /** Returns the type of current network connection.
     * ConnectivityManager.TYPE_... */
    public int getTypeOfNetworkConnection(Context context) {
        final String TAGG = "getTypeOfNetworkConnection: ";
        int ret = -1;
        ConnectivityManager cm;

        try {
            cm = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                if (cm.getActiveNetworkInfo() != null) {
                    ret = cm.getActiveNetworkInfo().getType();
                } else {
                    Log.e(TAG, TAGG+"Could not get a ConnectivityManager-getActiveNetworkInfo instance.");
                }
            } else {
                Log.e(TAG, TAGG+"Could not get a ConnectivityManager instance.");
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }

        cm = null;

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    /** Test network connectivity using ConnectivityManager.
     * (taken from developer.android.com/training/monitoring-device-state/connectivity-monitoring) */
    public boolean thereIsAnInternetConnection_CM(Context context) {
        final String TAGG = "thereIsAnInternetConnection_CM: ";
        boolean ret;
        ConnectivityManager cm;
        NetworkInfo activeNetwork;

        try {
            cm = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                activeNetwork = cm.getActiveNetworkInfo();
                ret = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            } else {
                Log.e(TAG, TAGG+"Could not get a ConnectivityManager instance.");
                ret = false;
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
            ret = false;
        }

        cm = null;
        activeNetwork = null;

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }
    public boolean currentInternetConnectionIsWifi_CM(Context context) {
        final String TAGG = "currentInternetConnectionIsWifi_CM: ";
        boolean ret;
        ConnectivityManager cm;
        NetworkInfo activeNetwork;

        try {
            cm = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                activeNetwork = cm.getActiveNetworkInfo();
                ret = activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;
            } else {
                Log.e(TAG, TAGG+"Could not get a ConnectivityManager instance.");
                ret = false;
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
            ret = false;
        }

        cm = null;
        activeNetwork = null;

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    /** Figure out latency in ms to specified host
     *  Returns the latency to a given host IP in milli-seconds by issuing a ping command.
     *  System will issue NUMBER_OF_PACKTETS ICMP Echo Request packet each having size of 56 bytes every second, and returns the avg latency of them.
     *  Returns 0 when there is no connection */
    public double getLatency(String ipAddress){
        final String TAGG = "getLatency: ";

        Process process;
        final int NUMBER_OF_PACKETS = 2;
        final int RESPONSE_DEADLINE = 2;    //seconds to wait at most for a response
        String pingCommand = "/system/bin/ping -c " + NUMBER_OF_PACKETS + " -w "+RESPONSE_DEADLINE+" " + ipAddress;
        String inputLine = "";
        double avgRtt = 0;

        try {
            // execute the command on the environment interface
            process = Runtime.getRuntime().exec(pingCommand);

            // get the input stream to get the output of the executed command
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
            inputLine = stdInput.readLine();
            while ((inputLine != null)) {
                // when we get to the interesting line of executed ping command
                if (inputLine.length() > 0 && inputLine.contains("avg")) {
                    break;
                }
                // get next line for next iteration
                inputLine = stdInput.readLine();
            }

            process.destroy();
        } catch (IOException e){
            Log.e(TAG, TAGG+"Exception caught: "+ e.getMessage() +".");
            e.printStackTrace();
        }

        if (inputLine == null) {
            Log.e(TAG, TAGG+"Result of ping command is null. Returning 0.");
            return 0;
        }

        // Extracting the average round trip time from the inputLine string
        String afterEqual = inputLine.substring(inputLine.indexOf("="), inputLine.length()).trim();
        String afterFirstSlash = afterEqual.substring(afterEqual.indexOf('/') + 1, afterEqual.length()).trim();
        String strAvgRtt = afterFirstSlash.substring(0, afterFirstSlash.indexOf('/'));
        avgRtt = Double.valueOf(strAvgRtt);

        return avgRtt;
    }

    /** Keep the Wifi alive and return a handle for the lock-hold.
     *  NOTE: Must have WAKE_LOCK permission in manifest!
     *  NOTE: requires application context or memory may leak.*/
    public WifiManager.WifiLock disableWifiSleep(Context context) {
        final String TAGG = "disableWifiSleep: ";

        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiManager.WifiLock lock;
            if (wifiManager != null) {
                lock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "LockTag");
            } else {
                Log.e(TAG, TAGG+"No WifiManager instance available. Cannot createWifiLock.");
                return null;
            }

            lock.setReferenceCounted(false);        //don't count and keep track of calls.. just use one reference for potential releases
            lock.acquire();

            if (lock.isHeld()) {
                Log.i(TAG, TAGG+"WifiLock acquired.");
                return lock;
            } else {
                Log.w(TAG, TAGG+"WifiLock hold failed.");
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught during WifiLock acquisition.");
            return null;
        }
    }

    /** Check if device is connected to a network. Does not work for WiFi connectivity. */
    public boolean isNetworkConnected(Context context) {
        final String TAGG = "isNetworkConnected: ";

        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                return cm.getActiveNetworkInfo() != null;
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught. Returning false. ("+e.getMessage()+")");
        }

        Log.w(TAG, TAGG+"Could not execute (perhaps ConnectivityManager did not instantiate?)");
        return false;
    }

    /** Check if device is connected to a WiFi network. */
    public boolean isWifiNetworkConnected(Context context) {
        final String TAGG = "isWifiNetworkConnected: ";

        try {
            ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connManager != null) {
                NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                if (mWifi.isConnected()) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught. Returning false. ("+e.getMessage()+")");
        }

        Log.w(TAG, TAGG+"Could not execute (perhaps ConnectivityManager did not instantiate?)");
        return false;
    }

    /** Get SSID of connected WiFi network.
     * NOTE: requires application context or memory may leak. */
    public String getWifiSSID(Context context) {
        final String TAGG = "getWifiSSID: ";

        try {
            WifiManager manager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (manager != null) {
                if (manager.isWifiEnabled()) {
                    WifiInfo wifiInfo = manager.getConnectionInfo();
                    if (wifiInfo != null) {
                        NetworkInfo.DetailedState state = WifiInfo.getDetailedStateOf(wifiInfo.getSupplicantState());
                        if (state == NetworkInfo.DetailedState.CONNECTED || state == NetworkInfo.DetailedState.OBTAINING_IPADDR) {
                            Log.v(TAG, TAGG + "Connected to SSID, \"" + wifiInfo.getSSID() + "\".");
                            return wifiInfo.getSSID();
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught. Returning false. ("+e.getMessage()+")");
        }

        Log.w(TAG, TAGG+"Could not execute (perhaps WifiManager did not instantiate?)");
        return null;
    }

    /** Check if WiFi is on
     * NOTE: requires application context or memory may leak. */
    public boolean isWifiOn(Context context) {
        final String TAGG = "isWifiOn: ";

        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                return wifiManager.isWifiEnabled();
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught trying to check WiFi. Returning false.");
            Log.d(TAG, TAGG+"Error message: "+ e.getMessage());
            return false;
        }

        Log.w(TAG, TAGG+"Could not execute (perhaps WifiManager did not instantiate?)");
        return false;
    }

    /** Turn on WiFi
     *  NOTE: Must have CHANGE_WIFI_STATE permission in manifest!
     *  NOTE: requires application context or memory may leak.*/
    public boolean turnOnWifi(Context context) {
        final String TAGG = "turnOnWifi: ";
        Log.v(TAG, TAGG+"Running.");

        boolean ret;
        WifiManager wifiManager;

        // Try to get a WifiManager instance, first.
        try {
            wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

            if (wifiManager == null) {
                throw new Exception();
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught trying to get WifiManager instance. Returning false.");
            return false;
        }

        // If we got to this point, then we should have a valid WifiManager instance to work with.
        try {
            if (wifiManager.isWifiEnabled()) {
                Log.d(TAG, TAGG+"Wi-Fi already on, returning true.");
                ret = true;
            } else {
                //turn on wifi
                Log.i(TAG, TAGG+"Enabling WiFi...");
                wifiManager.setWifiEnabled(true);

                //wait a little bit to give time for wifi to come online
                try {
                    Thread.sleep(1000 * 5);   //4s is enough, so do 5s to be safe
                } catch (Exception e) {
                    Log.w(TAG, TAGG+"Exception caught trying to wait for WiFi to enable. (NOTE: failure could be falsely reported below)");
                }

                //test whether we had success
                if (wifiManager.isWifiEnabled()) {
                    Log.i(TAG, TAGG+"SUCCESS! WiFi is now on.");
                    ret = true;
                } else {
                    Log.w(TAG, TAGG+"FAILURE? WiFi is off, or taking a long time.");
                    ret = false;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught trying to enable WiFi. Returning false.");
            Log.d(TAG, TAGG+"Error message: "+ e.getMessage());
            ret = false;
        }

        // Explicit cleanup to be extra safe against memory leaks
        wifiManager = null;

        return ret;
    }

    /** Turn off WiFi
     *  NOTE: Must have CHANGE_WIFI_STATE permission in manifest!
     *  NOTE: requires application context or memory may leak. */
    public boolean turnOffWifi(Context context) {
        final String TAGG = "turnOffWifi: ";
        Log.v(TAG, TAGG+"Running.");

        WifiManager wifiManager;

        try {
            wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                wifiManager.setWifiEnabled(false);
                Thread.sleep(1000*2);   //give time for wifi to go offline
            } else {
                Log.e(TAG, TAGG+"Could not execute because WifiManager did not instantiate.");
                return false;
            }

            //test whether we had success
            if (!isWifiOn(context)) {
                Log.i(TAG, TAGG+"SUCCESS! isWifiOn reports WiFi is off.");
                wifiManager = null;
                return true;
            } else {
                Log.w(TAG, TAGG+"FAILURE! isWifiOn reports WiFi is on.");
                wifiManager = null;
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught trying to disable WiFi. Returning false.");
            Log.d(TAG, TAGG+"Error message: "+ e.getMessage());
            wifiManager = null;
            return false;
        }
    }

    /** Cycle WiFi (turn off then on again) */
    public void cycleWifi(Context context) {
        Log.v(TAG, "cycleWifi: Running.");
        try {
            Log.i(TAG, "cycleWifi: Restarting WiFi...");
            if (turnOffWifi(context)) {
                Log.d(TAG, "cycleWifi: WiFi is now off.");
            }
            if (turnOnWifi(context)) {
                Log.d(TAG, "cycleWifi: WiFi is now on.");
            }
        } catch (Exception e) {
            Log.e(TAG, "cycleWifi: Exception caught trying to cycle WiFi: "+ e.getMessage() +".");
        }
    }

    /** Force connect to the WiFi SSID saved in shared prefs.
     *  Will also re-add the saved SSID, and cleanup any duplicates, to be safe.
     *  You may run this routine any time you want to make sure we're connected to the right SSID (there will be momentary drop-out).
     *  NOTE: requires application context or memory may leak.*/
    public boolean flag_justConnectedToSavedWifiSSID = false;    //sometimes connecting can take a few seconds, so flag that we attempted to connect (you must reset this by whatever uses it)
    public void connectToSavedWifiSSID(Context context) {
        final String TAGG = "connectToSavedWifiSSID: ";
        Log.v(TAG, TAGG+"Running.");
        String networkSSID = "";
        try {
            String currentSSID = getCurrentSSID(context);
            networkSSID = PreferenceManager.getDefaultSharedPreferences(context).getString(context.getResources().getString(R.string.spKeyName_wifiSSID), "");
            String networkPass = PreferenceManager.getDefaultSharedPreferences(context).getString(context.getResources().getString(R.string.spKeyName_wifiPassword), "");
            String networkSecurity = PreferenceManager.getDefaultSharedPreferences(context).getString(context.getResources().getString(R.string.spKeyName_wifiSecurityType), "");

            if (networkSSID.isEmpty() || networkSSID.equals("")) {
                Log.i(TAG, TAGG+"WiFi SSID not specified. Nothing to do. (currently connected to '"+ currentSSID +"').");
                return;
            }
            if (currentSSID.equals(networkSSID) || currentSSID.equals("\""+networkSSID+"\"")) {
                Log.i(TAG, TAGG+"Already connected ("+currentSSID+") to the specified WiFi SSID ("+networkSSID+"). Nothing to do.");
                return;
            }
            if ((networkSecurity.equals("WEP") || networkSecurity.equals("WPA")) && (networkPass.isEmpty() || networkPass.equals(""))) {
                Log.w(TAG, TAGG+"Secured network requires a password, but none specified. Nothing we can do.");
                return;
            }

            WifiConfiguration conf = new WifiConfiguration();
            conf.SSID = "\"" + networkSSID + "\"";   // Please note the quotes. String should contain ssid in quotes

            switch (networkSecurity) {
                case "WEP":
                    conf.wepKeys[0] = "\"" + networkPass + "\"";        //note: if p/w is hex, then no need for quotes around it
                    conf.wepTxKeyIndex = 0;
                    conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                    conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
                    break;
                case "WPA":
                    conf.preSharedKey = "\""+ networkPass +"\"";
                    break;
                case "Open":
                    conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                    break;
                default:
                    break;
            }

            WifiManager wifiManager = (WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null) {
                Log.e(TAG, TAGG+"Could not get WifiManager instance, so cannot setup network.");
                return;
            }

            //check existing saved networks... clean up any duplicates
            String prevSSID = "";
            int networkIDofMatch = -1;
            List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();
            for( WifiConfiguration i : list ) {
                Log.v(TAG, TAGG+"WifiManager saved network before cleanup: "+ i.SSID);
                if(i.SSID == null) {
                    break;
                }
                //check for our saved SSID already in the manager and save its ID if found (so we can flag it for removal later)
                if(i.SSID.equals("\""+ networkSSID +"\"")) {
                    Log.d(TAG, TAGG+"This SSID ("+i.SSID+") is a match for what we're trying to add and will be flagged for removal.");
                    networkIDofMatch = i.networkId;
                }
                //remove this one if it's a duplicate of the previous (which can happen if logic adds without first checking - they're usually in order, so prev is fine to check)
                if(i.SSID.equals("\"" + prevSSID + "\"")) {
                    Log.d(TAG, TAGG+"This SSID ("+i.SSID+") is a duplicate and will be removed.");
                    wifiManager.removeNetwork(i.networkId);
                    continue;   //don't update prevSSID, keep checking against the last one since we just removed this one
                }
                prevSSID = i.SSID;
            }

            //if we have a network flagged for removal, remove it before adding it
            if(networkIDofMatch > -1) {
                Log.i(TAG, TAGG + "Removing and re-adding the " + networkSSID + " network (to prevent duplicates being saved).");
                wifiManager.removeNetwork(networkIDofMatch);
            } else {
                Log.i(TAG, TAGG + "Adding the " + networkSSID + " network.");
            }
            wifiManager.addNetwork(conf);

            //before bringing anything up, disable all networks
            Log.i(TAG, TAGG+"Disabling all saved networks...");
            for( WifiConfiguration i : list ) {
                Log.v(TAG, TAGG+"Disabling "+ i.SSID +"("+ i.networkId +").");
                wifiManager.disableNetwork(i.networkId);
            }

            //enable our desired network so Android connects to it
            Log.i(TAG, TAGG+"Specifically enabling the connection to "+ networkSSID +"...");
            for( WifiConfiguration i : list ) {
                if(i.SSID != null && (i.SSID.equals("\"" + networkSSID + "\"") || i.SSID.equals(networkSSID))) {
                    Log.v(TAG, TAGG+"Enabling and connecting to "+ i.SSID +"("+ i.networkId +").");
                    wifiManager.disconnect();
                    wifiManager.enableNetwork(i.networkId, true);
                    wifiManager.reconnect();
                }
            }

            flag_justConnectedToSavedWifiSSID = true;
            //Thread.sleep(4000); //give time for network to connect before allowing to continue    //TODO: doesn't seem to really work... if needed, try SystemClock.sleep?
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught connecting to saved WiFi "+ networkSSID +": "+ e.getMessage() +".");
        }
    }

    /** WORK IN PROGRESS */
    /*
    public void bindProcessToSpecifiedNetwork(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        //connectivityManager.bindProcessToNetwork()
    }
    */

    /** Force connect to the IPv4 address saved in shared prefs.
     * NOTE: requires application context or memory may leak. */
    public void connectAsDefinedIPv4(Context context) {
        final String TAGG = "connectAsDefinedIPv4: ";
        try {

            //String deviceIP = "192.168.1.43";   //omni 2 on mndemo & demobox
            //String deviceIP = "192.168.1.86";   //chris omni on mndemo
            final String deviceIP = PreferenceManager.getDefaultSharedPreferences(context).getString(String.valueOf(context.getResources().getText(R.string.spKeyName_thisDeviceAddrIPv4)), null);
            final String gatewayIP = PreferenceManager.getDefaultSharedPreferences(context).getString(String.valueOf(context.getResources().getText(R.string.spKeyName_gatewayIPv4)), null);
            final String dns1 = PreferenceManager.getDefaultSharedPreferences(context).getString(String.valueOf(context.getResources().getText(R.string.spKeyName_dnsServer1IPv4)), null);
            final String dns2 = PreferenceManager.getDefaultSharedPreferences(context).getString(String.valueOf(context.getResources().getText(R.string.spKeyName_dnsServer2IPv4)), null);

            //format the list of dns servers so that the below method can accept them
            final InetAddress[] dnsServers = new InetAddress[]{
                    InetAddress.getByName(dns1),
                    InetAddress.getByName(dns2)
            };

            //setStaticIpConfiguration(context, null, null, InetAddress.getByName(deviceIP), 24, InetAddress.getByName(gatewayIP), new InetAddress[]{InetAddress.getByName("8.8.4.4"), InetAddress.getByName("9.9.9.9")});
            setStaticIpConfiguration(context, null, null, InetAddress.getByName(deviceIP), 24, InetAddress.getByName(gatewayIP), dnsServers);

        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught trying to set current connection to what's configured: "+ e.getMessage() +".");
        }
    }

    /** Determines and returns shared-prefs saved configuration for static/DHCP IP address */
    public boolean isSupposedToBeStaticIP(Context context) {
        final String TAGG = "isSupposedToBeStaticIP: ";
        String spValue;
        try {
            spValue = PreferenceManager.getDefaultSharedPreferences(context).getString(context.getResources().getString(R.string.spKeyName_ipAddressMethod), "");
            if (spValue.toUpperCase().equals("STATIC")) {
                Log.v(TAG, TAGG+"Returning true.");
                return true;
            } else {
                Log.v(TAG, TAGG+"Returning false.");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught trying to read and examine IP method, returning false: "+ e.getMessage() +".");
            return false;
        }
    }

    /** Determines and returns currently active IP method (e.g. "STATIC", "DHCP") */
    public String getCurrentIpMethod_activeInterface(Context context){
        final String TAGG = "getCurrentIpMethod_activeInterface: ";
        String ret = IP_METHOD_DHCP;    //default

        if (nicWifiIsUp()) {
            ret = getCurrentIpMethod_wifi(context);
        } else {
            Log.w(TAG, TAGG+"Unsupported NIC IP-method detection (for this method), using default.");   //TODO
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }
    public String getCurrentIpMethod_wifi(Context context){
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
            wifiConf = getCurrentWiFiConfiguration(context);
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

    /** setDhcpIpConfiguration
     *
     * Sample usage (Wifi object are optional):
     WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
     WifiConfiguration wifiConf = WifiHelper.getCurrentWiFiConfiguration(getApplicationContext());
     setDhcpIpConfiguration(context, [wifiManager|null], [wifiConf|null]);
     */
    public void setDhcpIpConfiguration(Context context, WifiManager manager, WifiConfiguration config) {
        final String TAGG = "setDhcpIpConfiguration: ";

        if (manager == null) {
            manager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        }
        if (config == null) {
            config = getCurrentWiFiConfiguration(context);
        }

        Log.d(TAG, TAGG+"config (before) = \""+config.toString()+"\".");

        // Wait for supplicant ping to return true
        int pingCount = 0;
        while (manager.pingSupplicant() == false) {
            //wait for ping to return true
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (pingCount++ > 10) break;
        }

        // Try to set up IpAssignment to DHCP
        Object ipAssignment = null;
        try {
            ipAssignment = getEnumValue("android.net.IpConfiguration$IpAssignment", "DHCP");
            callMethod(config, "setIpAssignment", new String[]{"android.net.IpConfiguration$IpAssignment"}, new Object[]{ipAssignment});
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught trying to set IP method: "+e.getMessage());
        }

        // Try to enact the change made above
        try {
            Log.d(TAG, TAGG+"config (after) = \""+config.toString()+"\".");
            int netId = manager.updateNetwork(config);
            boolean result = netId != -1;
            if (result) {
                boolean isDisconnected = manager.disconnect();
                boolean configSaved = manager.saveConfiguration();
                boolean isEnabled = manager.enableNetwork(config.networkId, true);
                boolean isReconnected = manager.reconnect();
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught trying to update/enact network config: "+ e.getMessage());
        }

        // Must turn off and on WiFi for network to actually come online and work
        cycleWifi(context);
    }

    /** setStaticIpConfiguration
     * (adapted from https://stackoverflow.com/questions/40155591/set-static-ip-and-gateway-programmatically-in-android-6-x-marshmallow)
     *
     * Sample usage:
     WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
     WifiConfiguration wifiConf = WifiHelper.getCurrentWiFiConfiguration(getApplicationContext());
     try {
     setStaticIpConfiguration(context, wifiManager, wifiConf, InetAddress.getByName("192.168.0.100"), 24, InetAddress.getByName("10.0.0.2"),
     new InetAddress[]{InetAddress.getByName("10.0.0.3"), InetAddress.getByName("10.0.0.4")});
     } catch (Exception e) {
     e.printStackTrace();
     }
     */
    @SuppressWarnings("unchecked")
    public void setStaticIpConfiguration(Context context, WifiManager manager, WifiConfiguration config, InetAddress ipAddress, int prefixLength, InetAddress gateway, InetAddress[] dns) throws ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, NoSuchFieldException, InstantiationException {
        if (manager == null) {
            manager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        }
        if (config == null) {
            config = getCurrentWiFiConfiguration(context);
        }

        Log.d(TAG, "setStaticIpConfiguration: config (before) = \""+config.toString()+"\".");

        // Wait for supplicant ping to return true
        int pingCount = 0;
        while (manager.pingSupplicant() == false) {
            //wait for ping to return true
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (pingCount++ > 10) break;
        }

        // First set up IpAssignment to STATIC.
        Object ipAssignment = getEnumValue("android.net.IpConfiguration$IpAssignment", "STATIC");
        callMethod(config, "setIpAssignment", new String[]{"android.net.IpConfiguration$IpAssignment"}, new Object[]{ipAssignment});

        // Then set properties in StaticIpConfiguration.
        Object staticIpConfig = newInstance("android.net.StaticIpConfiguration");
        Object linkAddress = newInstance("android.net.LinkAddress", new Class<?>[]{InetAddress.class, int.class}, new Object[]{ipAddress, prefixLength});

        setField(staticIpConfig, "ipAddress", linkAddress);
        setField(staticIpConfig, "gateway", gateway);
        getField(staticIpConfig, "dnsServers", ArrayList.class).clear();
        for (int i = 0; i < dns.length; i++)
            getField(staticIpConfig, "dnsServers", ArrayList.class).add(dns[i]);

        callMethod(config, "setStaticIpConfiguration", new String[]{"android.net.StaticIpConfiguration"}, new Object[]{staticIpConfig});

        try {
            Log.d(TAG, "setStaticIpConfiguration: config (after) = \""+config.toString()+"\".");
            int netId = manager.updateNetwork(config);
            boolean result = netId != -1;
            if (result) {
                boolean isDisconnected = manager.disconnect();
                boolean configSaved = manager.saveConfiguration();
                boolean isEnabled = manager.enableNetwork(config.networkId, true);
                boolean isReconnected = manager.reconnect();
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception caught trying to update/enact network config: "+ e.getMessage());
        }
    }
    /** BEGIN HELPER FUNCTIONS FOR ABOVE METHOD ^^^ **/
    public WifiConfiguration getCurrentWiFiConfiguration(Context context) {
        final String TAGG = "getCurrentWifiConfiguration: ";
        WifiConfiguration wifiConf = null;
        ConnectivityManager connManager;
        NetworkInfo networkInfo;
        WifiManager wifiManager;
        WifiInfo connectionInfo;

        try {
            connManager = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            networkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (networkInfo.isConnected()) {
                wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
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
    private Object newInstance(String className) throws ClassNotFoundException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        return newInstance(className, new Class<?>[0], new Object[0]);
    }
    private Object newInstance(String className, Class<?>[] parameterClasses, Object[] parameterValues) throws NoSuchMethodException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, ClassNotFoundException {
        Class<?> clz = Class.forName(className);
        Constructor<?> constructor = clz.getConstructor(parameterClasses);
        return constructor.newInstance(parameterValues);
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object getEnumValue(String enumClassName, String enumValue) throws ClassNotFoundException {
        Class<Enum> enumClz = (Class<Enum>) Class.forName(enumClassName);
        return Enum.valueOf(enumClz, enumValue);
    }
    private void setField(Object object, String fieldName, Object value) throws IllegalAccessException, IllegalArgumentException, NoSuchFieldException {
        Field field = object.getClass().getDeclaredField(fieldName);
        field.set(object, value);
    }
    private <T> T getField(Object object, String fieldName, Class<T> type) throws IllegalAccessException, IllegalArgumentException, NoSuchFieldException {
        Field field = object.getClass().getDeclaredField(fieldName);
        return type.cast(field.get(object));
    }
    public void callMethod(Object object, String methodName, String[] parameterTypes, Object[] parameterValues) throws ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException {
        Class<?>[] parameterClasses = new Class<?>[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++)
            parameterClasses[i] = Class.forName(parameterTypes[i]);

        Method method = object.getClass().getDeclaredMethod(methodName, parameterClasses);
        method.invoke(object, parameterValues);
    }
    /** END HELPER FUNCTIONS **/


    /** getWifiStrength_dBm
     */
    public int getWifiStrength_dBm(Context context) {
        final String TAGG = "getWifiStrength_dBm: ";

        try {
            WifiManager wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                return wifiInfo.getRssi();
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
                return level;
            }
        } catch (Exception e) {
            Log.e(TAG, "getWifiStrength_bars: Exception caught: "+e.getMessage());
            return 0;
        }

        Log.e(TAG, TAGG+"Could not get WifiManager instance.");
        return 0;
    }

    /** getWifiStrength_subjective
     */
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
                return "Excellent";
            } else if (dBm < -100) {
                return "No Signal";
            }

            // These handle otherwise normal values
            if (dBm > -50) {
                return "Excellent";
            } else if (dBm >= -60) {
                return "Good";
            } else if (dBm >= -70) {
                return "Fair";
            } else if (dBm >= -80) {
                return "Weak";
            } else if (dBm > WIFI_DBM_NON_EXISTENT) {
                return "Very Weak";
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


}
