package com.messagenetsystems.evolution2.utilities;

/* SystemUtils
 * This is primarily for general device settings and configurations.
 *
 * Revisions:
 *  2019.11.06      Chris Rider     Created.
 *  2020.02.18      Chris Rider     Renamed class from SystemUtils to SystemUtils and migrated in OsUtils to help consolidate stuff.
 *                                  Also migrated in a lot of supporting methods from v1 app, primarily for gathering and returning battery and system data.
 *  2020.02.20      Chris Rider     Fixed potential null-ref bug if OmniApplication is unavailable.
 *  2020.04.17      Chris Rider     Fixed and improved uptime hour parsing and calculations.
 *  2020.05.12      Chris Rider     Added methods to return whether USB or DC external power is present.
 *  2020.05.14      Chris Rider     New getFreeExternalStorageSpaceBytes() method, and new battery voltage method.
 *  2020.05.18      Chris Rider     Added getWhichPowerPlugged method to return which type of power supply is connected.
 *  2020.06.04      Chris Rider     Additions to configScreen_hideNavigationBar to better get rid of black nav bar at bottom of screen.
 *  2020.06.08      Chris Rider     Added method to whitelist all apps from chatty logcat filtering.
 *  2020.06.17      Chris Rider     Added static method for granting permissions.
 *  2020.07.27      Chris Rider     Added static method for figuring out the parent/caller process from stacktrace.
 *  2020.09.29      Chris Rider     Added asynchronous execution capability to screen min and max brightness via shell command, so calling routines don't have to wait on it to finish.
 */

import android.app.Activity;
import android.app.AlarmManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.OmniApplication;
import com.messagenetsystems.evolution2.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Set;

import static android.content.Context.BATTERY_SERVICE;


public class SystemUtils {
    private static final String TAG = SystemUtils.class.getSimpleName();

    // Constants...
    public static final byte DO_SYNCHRONOUSLY = 1;
    public static final byte DO_ASYNCHRONOUSLY = 2;

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
    private Context appContext;     //TODO: migrate this to WeakReference??
    private OmniApplication omniApplication;

    /** Constructor
     * @param appContext Application context
     * @param logMethod Logging method to use
     */
    public SystemUtils(Context appContext, int logMethod) {
        this.logMethod = logMethod;
        this.appContext = appContext;

        try {
            this.omniApplication = ((OmniApplication) appContext.getApplicationContext());
        } catch (Exception e) {
            Log.e(TAG, "Exception caught instantiating "+TAG+": "+e.getMessage());
            return;
        }
    }

    public void cleanup() {
        appContext = null;
        this.omniApplication = null;
    }


    /*============================================================================================*/
    /* System/OS Related Methods */

    public static String getParentClassNameFromStack(Class processClass) {
        final String TAGG = "getParentClassNameFromStack: ";
        String ret;

        try {
            String parentFullClassName = "";
            StackTraceElement[] stackTraceElementArray = Thread.currentThread().getStackTrace();
            /* Something like:
                stackTraceElementArray[0]:  dalvik.system.VMStack.getThreadStackTrace(Native Method)
                stackTraceElementArray[1]:  java.lang.Thread.getStackTrace(Thread.java:1566)
                stackTraceElementArray[2]:  com.messagenetsystems.evolution2.models.ProcessStatusList.addAndRegisterProcess(ProcessStatusList.java:88)
                stackTraceElementArray[3]:  com.messagenetsystems.evolution2.threads.HealthThreadHeartbeat.<init>(HealthThreadHeartbeat.java:92)
                stackTraceElementArray[4]:  com.messagenetsystems.evolution2.services.HealthService.onCreate(HealthService.java:248)
                stackTraceElementArray[5]:  android.app.ActivityThread.handleCreateService(ActivityThread.java:3192)
                stackTraceElementArray[6]:  android.app.ActivityThread.-wrap5(ActivityThread.java)
                stackTraceElementArray[7]:  android.app.ActivityThread$H.handleMessage(ActivityThread.java:1568)
                stackTraceElementArray[8]:  android.os.Handler.dispatchMessage(Handler.java:102)
                stackTraceElementArray[9]:  android.os.Looper.loop(Looper.java:154)
                stackTraceElementArray[10]: android.app.ActivityThread.main(ActivityThread.java:6121)
                stackTraceElementArray[11]: java.lang.reflect.Method.invoke(Native Method)
                stackTraceElementArray[12]: com.android.internal.os.ZygoteInit$MethodAndArgsCaller.run(ZygoteInit.java:905)
                stackTraceElementArray[13]: com.android.internal.os.ZygoteInit.main(ZygoteInit.java:795)
             */
            //FL.v(TAG, Arrays.toString(stackTraceElementArray));   //DEBUG
            for (int i = 0; i < stackTraceElementArray.length; i++) {
                if (stackTraceElementArray[i].getClassName().contains(processClass.getSimpleName())) {
                    //we found the specified process, so its parent is one away from it in the call stack
                    parentFullClassName = stackTraceElementArray[i+1].getClassName();                   //something like ex.: "com.messagenetsystems.evolution2.services.HealthService"
                    break;
                }
            }
            ret = parentFullClassName.substring(parentFullClassName.lastIndexOf(".")+1);    //parse the class name from something like: ex. "com.messagenetsystems.evolution2.services.HealthService"
        } catch (Exception e) {
            ret = "";
        }

        return ret;
    }

    /** Grant permission for allowing logfile.
     * This should only be a one-time (on first startup) deal.
     * No special requirements, just invoke it statically.
     * @param appContext
     */
    public static void grantPermissionForLogging(Context appContext) {
        final String TAG = SystemUtils.class.getSimpleName();
        final String TAGG = "grantPermissionForLogging: ";

        try {
            String cliCommand ="/system/xbin/su -c \"/system/bin/pm grant "+String.valueOf(appContext.getPackageName())+" WRITE_EXTERNAL_STORAGE\"";
            Log.v(TAG, TAGG+"cliCommand will be:\n"+cliCommand);
            Process proc = Runtime.getRuntime().exec(cliCommand);
            if ((proc.waitFor() != 0)) {
                throw new SecurityException();
            }
        } catch (IOException e) {
            Log.e(TAG, TAGG + "IO Exception caught: "+e.getMessage());
        } catch (InterruptedException e) {
            Log.e(TAG, TAGG + "Interrupted Exception caught: "+e.getMessage());
        } catch (SecurityException e) {
            Log.e(TAG, TAGG + "Security Exception caught: "+e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: "+e.getMessage());
        }
    }

    /** Grant specified Android permision, using PackageManager command.
     * Requires root.
     * Requires WRITE_EXTERNAL_STORAGE permission to have been granted.
     * @param permission
     */
    public static void grantPermission(String appPackageName, String permission) {
        final String TAGG = "grantPermission(\""+permission+"\"): ";
        FL.v(TAG, TAGG+"Invoked.");

        try {
            Process proc = Runtime.getRuntime().exec("su -c /system/bin/pm grant "+appPackageName+" "+permission);
            if ((proc.waitFor() != 0)) {
                throw new SecurityException();
            }
        } catch (IOException e) {
            FL.e(TAG, TAGG + "IO Exception caught!", e);
        } catch (InterruptedException e) {
            FL.e(TAG, TAGG + "Interrupted Exception caught!", e);
        } catch (SecurityException e) {
            FL.e(TAG, TAGG + "Security Exception caught!", e);
        } catch (Exception e) {
            FL.e(TAG, TAGG + "Exception caught!", e);
        }
    }
    public void grantPermission(String permission) {
        final String TAGG = "grantPermission(\""+permission+"\"): ";
        FL.v(TAG, TAGG+"Invoked.");

        try {
            Process proc = Runtime.getRuntime().exec("su -c /system/bin/pm grant "+omniApplication.getAppPackageName()+" "+permission);
            if ((proc.waitFor() != 0)) {
                throw new SecurityException();
            }
        } catch (IOException e) {
            FL.e(TAG, TAGG + "IO Exception caught!", e);
        } catch (InterruptedException e) {
            FL.e(TAG, TAGG + "Interrupted Exception caught!", e);
        } catch (SecurityException e) {
            FL.e(TAG, TAGG + "Security Exception caught!", e);
        } catch (Exception e) {
            FL.e(TAG, TAGG + "Exception caught!", e);
        }
    }

    /** Adds our app to the whitelist for logcat, so chatty won't obfuscate frequent debugging/verbose logs */
    public static void whitelistLogcat() {
        final String TAGG = "whitelistLogcat: ";

        Process process;

        try{
            Log.d(TAG, TAGG+"Adding all apps to logcat whitelist...");
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", "logcat -P ''"});
            if ((process.waitFor() != 0)) {
                throw new SecurityException();
            }
        }catch(IOException e){
            Log.e(TAG, TAGG+"IO Exception caught: "+ e.getMessage());
        }catch(InterruptedException e){
            Log.e(TAG, TAGG+"Interrupted Exception caught: "+ e.getMessage());
        }catch(SecurityException e){
            Log.e(TAG, TAGG+"Security Exception caught: "+ e.getMessage());
        }catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+ e.getMessage());
        }

        process = null;
    }

    /** Return the current system uptime value.
     * Note: this effectively will return a rounded-down value, as detailed below...
     *  In the case of days and hours, it will return the lowest multiple of 24 hours (ex. 1 1/2 days would return 24 hours instead of 36-ish)
     *  In the case of hours and minutes, it will return the hour value (ex. 1 hour and 30 minutes would return 1 hour)
     *  In the case of just minutes, it will return 0 hours.
     */
    public int getSystemUptime_hours() {
        final String TAGG = "getSystemUptime_hours: ";

        int ret = 0;
        Process process;
        OutputStream stdin;     //used to write commands to shell... using OutputStream type, we can execute commands like writing commands in terminal
        InputStream stdout;     //used to read output of a command we executed... using InputStream type, we can input command's output to our routine here
        InputStream stderr;     //used to read errors of a command we executed... using InputStream type, we can input command's errors to our routine here
        BufferedReader br;
        String line;

        try {

            // Start a super-user process under which to execute our commands as root
            Log.v(TAG, TAGG + "Starting super-user shell session...");
            process = Runtime.getRuntime().exec("su");

            // Get process streams
            stdin = process.getOutputStream();
            stdout = process.getInputStream();
            stderr = process.getErrorStream();

            // Construct and execute command (new-line is like hitting enter)
            //stdin.write(("/system/bin/uptime | /system/bin/busybox awk '{printf $3}' | /system/bin/cut -d':' -f1\n").getBytes());     //was not sufficient due to variability of command's output!
            //stdin.write(("/system/bin/uptime\n").getBytes());
            // Ex.  " 12:36:02 up 1 day, 43 min,  0 users,  load average: 1.79, 2.64, 2.84"
            // Ex.  " 11:24:01 up 9 days, 15:09,  0 users,  load average: 0.94, 1.02, 1.01"
            // Ex.  " 11:39:30 up  1:05,  0 users,  load average: 3.19, 3.53, 3.48"
            // Ex.  " 11:25:18 up 0 min,  0 users,  load average: 1.33, 0.35, 0.12"
            //stdin.write(("/system/bin/uptime | /system/bin/cut -d',' -f1\n").getBytes());   //will need to parse the result below
            // Ex.  " 11:36:31 up 11 min"
            // Ex.  " 11:39:30 up  1:05"
            // Ex.  " 11:24:01 up 9 days"
            stdin.write(("/system/bin/uptime\n").getBytes());   //will need to parse the result below

            // Exit the shell
            stdin.write(("exit\n").getBytes());

            // Flush and close the stdin stream
            stdin.flush();
            stdin.close();

            // Read output of the executed command and parse it for what we need
            Log.v(TAG, TAGG+"Reading output of executed command...");
            br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                Log.v(TAG, TAGG+"  stdout line: "+line);
                //ret = Integer.parseInt(line);

                String rawResult = line.toLowerCase();

                //TESTING...
                //String rawResult = " 11:25:18 up 2 min,  0 users,  load average: 1.33, 0.35, 0.12"; //TEST (should report 0)
                //String rawResult = " 11:39:30 up  1:05,  0 users,  load average: 3.19, 3.53, 3.48"; //TEST (should report 1)
                //String rawResult = " 12:36:02 up 1 day, 43 min,  0 users,  load average: 1.79, 2.64, 2.84"; //TEST (should report 24)
                //String rawResult = " 11:24:01 up 9 days, 15:09,  0 users,  load average: 0.94, 1.02, 1.01"; //TEST (should report 231)

                int hoursToReturn;
                if (rawResult.contains("day,") || rawResult.contains("days,")) {
                    // DAYS+
                    // Ex.  " 12:36:02 up 1 day, 43 min,  0 users,  load average: 1.79, 2.64, 2.84"
                    // Ex.  " 11:24:01 up 9 days, 15:09,  0 users,  load average: 0.94, 1.02, 1.01"
                    int dayCount = Integer.parseInt(rawResult.split("up")[1].split("day")[0].trim());
                    int hourCount;
                    if (rawResult.contains("min,")) {
                        // Ex.  " 12:36:02 up 1 day, 43 min,  0 users,  load average: 1.79, 2.64, 2.84"
                        // We have minutes (not even an hour), so just use the day (24) multiple that we got above
                        hourCount = 0;
                        Log.v(TAG, TAGG+"(DAYS+MINUTES) dayCount="+dayCount+", hourCount="+hourCount);
                    } else {
                        // Ex.  " 11:24:01 up 9 days, 15:09,  0 users,  load average: 0.94, 1.02, 1.01"
                        hourCount = Integer.parseInt(rawResult.split(",")[1].split(":")[0].trim());
                        Log.v(TAG, TAGG+"(DAYS+HOURS) dayCount="+dayCount+", hourCount="+hourCount);
                    }
                    hoursToReturn = (dayCount * 24) + hourCount;
                } else if (rawResult.contains("min,")) {
                    // MINUTES
                    // Ex.  " 11:25:18 up 0 min,  0 users,  load average: 1.33, 0.35, 0.12"
                    hoursToReturn = 0;
                    Log.v(TAG, TAGG+"(MINUTES)");
                } else {
                    // HOURS (less than a full day, more than 59 minutes)
                    // Ex.  " 11:39:30 up  1:05,  0 users,  load average: 3.19, 3.53, 3.48"
                    Log.v(TAG, TAGG+"(HOURS)");
                    hoursToReturn = Integer.parseInt(rawResult.split("up")[1].split(":")[0].trim());
                }
                ret = hoursToReturn;

                /* OLD...
                if (line.toLowerCase().contains("min")) {
                    //uptime is minutes (less than 1 hour)
                    //ex:   line = " 11:36:31 up 11 min"
                    ret = 0;                                        //just return 0 hours if we're less than 1 hour (in minutes territory)
                    Log.d(TAG, TAGG+"Uptime is currently measured in minutes ("+line.split(" ")[3]+" minutes). Will return "+ret+" hours.");
                } else if (line.toLowerCase().contains("day")) {
                    //uptime is days (more than 23:59)
                    //ex:   line = " 11:24:01 up 9 days"
                    ret = Integer.parseInt(line.split(" ")[3]);     //parse days value from result
                    Log.d(TAG, TAGG+"Uptime is currently measured in days ("+ret+") + hours, but will return a multiple of 24 hours.");
                    ret = ret * 24;                                 //calculate hours for days
                } else {
                    //uptime is hours (more than 59 minutes and less than 24 hours)
                    //ex:   line = " 11:39:30 up  1:05"
                    try {
                        ret = Integer.parseInt(line.split(" ")[3].split(":")[0]);
                        Log.d(TAG, TAGG+"Uptime is currently measured in hours + minutes ("+line.split(" ")[3]+"), but will return only the hour ("+ret+").");
                    } catch (Exception e) {
                        //the extra space in front of the hour messed us up so move to next element in split array, which should be our guy
                        ret = Integer.parseInt(line.split(" ")[4].split(":")[0]);
                        Log.d(TAG, TAGG+"Uptime is currently measured in hours + minutes ("+line.split(" ")[4]+"), but will return only the hour ("+ret+").");
                    }
                }
                */
            }
            br.close();

            // Read error stream of the executed command
            Log.v(TAG, TAGG+"Reading errors of executed command...");
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                Log.w(TAG, TAGG+"  stderr line: "+line);
            }
            br.close();

            // Wait for process to finish
            process.waitFor();
            process.destroy();
        } catch(SecurityException e) {
            Log.e(TAG, TAGG +"Security Exception caught: " + String.valueOf(e));
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning: \""+ ret +"\".");
        return ret;
    }

    /** Return the current system uptime value (raw). */
    public String getSystemUptime_raw() {
        final String TAGG = "getSystemUptime_raw: ";

        String ret = "";
        Process process;
        OutputStream stdin;     //used to write commands to shell... using OutputStream type, we can execute commands like writing commands in terminal
        InputStream stdout;     //used to read output of a command we executed... using InputStream type, we can input command's output to our routine here
        InputStream stderr;     //used to read errors of a command we executed... using InputStream type, we can input command's errors to our routine here
        BufferedReader br;
        String line;

        try {

            // Start a super-user process under which to execute our commands as root
            Log.v(TAG, TAGG + "Starting super-user shell session...");
            process = Runtime.getRuntime().exec("su");

            // Get process streams
            stdin = process.getOutputStream();
            stdout = process.getInputStream();
            stderr = process.getErrorStream();

            // Construct and execute command (new-line is like hitting enter)
            stdin.write(("/system/bin/uptime\n").getBytes());   //will need to parse the result below
            // Ex.  " 11:24:01 up 9 days, 15:09,  0 users,  load average: 0.94, 1.02, 1.01"
            // Ex.  " 11:39:30 up  1:05,  0 users,  load average: 3.19, 3.53, 3.48"
            // Ex.  " 11:25:18 up 0 min,  0 users,  load average: 1.33, 0.35, 0.12"

            // Exit the shell
            stdin.write(("exit\n").getBytes());

            // Flush and close the stdin stream
            stdin.flush();
            stdin.close();

            // Read output of the executed command and parse it for what we need
            Log.v(TAG, TAGG+"Reading output of executed command...");
            br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                Log.v(TAG, TAGG+"  stdout line: "+line);
                if (line.toLowerCase().contains("up")) {
                    //parse uptime and we're done
                    boolean inTimeSection = false;
                    String[] cmdResult = line.split(" ");
                    for (int i = 0; i < cmdResult.length; i++) {
                        if (i > 0 && cmdResult[i-1].equalsIgnoreCase("up")) {
                            //this "word" is beginning of time value ("9" or "1:05," or "0" for example)
                            inTimeSection = true;
                            ret = cmdResult[i];
                            continue;
                        }
                        if (inTimeSection) {
                            //after we have begun parsing through the time section
                            //append strings until we reach a comma
                            if (cmdResult[i].contains(",")) {
                                //we're at the final word that we care about
                                ret = ret + " " + cmdResult[i].replace(",", "");
                                break;
                            } else {
                                ret = ret + " " + cmdResult[i];
                            }
                        }
                    }
                    cmdResult = null;
                    break;
                }
            }
            br.close();

            // Read error stream of the executed command
            Log.v(TAG, TAGG+"Reading errors of executed command...");
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                Log.w(TAG, TAGG+"  stderr line: "+line);
            }
            br.close();

            // Wait for process to finish
            process.waitFor();
            process.destroy();
        } catch(SecurityException e) {
            Log.e(TAG, TAGG +"Security Exception caught: " + String.valueOf(e));
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning: \""+ ret +"\".");
        return ret;
    }

    public long getAppHeapAvailable_MB() {
        final String TAGG = "getAppHeapAvailable_MB: ";

        try {
            final Runtime runtime = Runtime.getRuntime();
            final long usedMemInMB = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
            final long maxHeapSizeInMB = runtime.maxMemory() / 1048576L;
            final long availHeapSizeInMB = maxHeapSizeInMB - usedMemInMB;
            return availHeapSizeInMB;
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+ e.getMessage());
            return 0;
        }
    }

    public float readUsageCPU() {
        final String TAGG = "readUsageCPU: ";

        try {
            long idle1 = 0;
            long cpu1 = 0;

            RandomAccessFile reader = new RandomAccessFile("/proc/stat", "r");
            String load = reader.readLine();

            String[] toks = load.split(" +");  // Split on one or more spaces

            try {
                idle1 = Long.parseLong(toks[4]);
                cpu1 = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[5])
                        + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);
            } catch (Exception e) {
                Log.e(TAG, TAGG+"Exception caught parsing result of reading /proc/stat: " + e.getMessage());
                return 0;
            }

            try {
                Thread.sleep(360);
            } catch (Exception e) {
                Log.w(TAG, TAGG+"Exception caught sleep: "+e.getMessage());
            }

            reader.seek(0);
            load = reader.readLine();
            reader.close();

            toks = load.split(" +");

            long idle2 = Long.parseLong(toks[4]);
            long cpu2 = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[5])
                    + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);

            return (float) (cpu2 - cpu1) / ((cpu2 + idle2) - (cpu1 + idle1));

        } catch (IOException e) {
            Log.e(TAG, TAGG+"IOException caught: "+ e.getMessage());
        }

        return 0;
    }

    /** Returns whether battery is charging.
     * For SDK < 23, this is inferred via net-current.
     * For 23+, we use the BatteryManager.isCharging method (note: this returns true even if net current is negative, as long as plugged in). **/
    public boolean isBatteryCharging() {
        final String TAGG = "isBatteryCharging: ";
        boolean isCharging = false;
        final boolean useIsChargingMethod = false;        //COMPILE-FLAG whether to use the BatteryManager .isCharging method (might be buggy on some implementations, returning false no matter what)
        try {
            BatteryManager bm = (BatteryManager) appContext.getSystemService(BATTERY_SERVICE);
            if (useIsChargingMethod && android.os.Build.VERSION.SDK_INT >= 23) {
                isCharging = bm.isCharging();
                if (isCharging) {
                    //double-check (it might be possible to report true simply if power source is plugged in, but net could be negative)
                    if (getBatteryNetCurrentNow() <= 0) {
                        Log.v(TAG, TAGG+"Even though there seems to be power connected, net current is negative (discharging).");
                        isCharging = false;
                    }
                }
            } else {
                Log.i(TAG, TAGG+"Checking net-current.");
                if (getBatteryNetCurrentNow() > 0) {
                    Log.v(TAG, TAGG+"Net current is positive (charging).");
                    isCharging = true;
                } else {
                    Log.v(TAG, TAGG+"Net current is negative (discharging).");
                    isCharging = false;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getBatteryNetCurrentAvg: Exception caught trying to read charge level (returning false): "+ e.getMessage() +".");
            isCharging = false;
        }
        Log.v(TAG, TAGG+"Returning "+isCharging+".");
        return isCharging;
    }

    public boolean isUsbPowerConnected() {
        final String TAGG = "isUsbPowerConnected: ";
        boolean ret = false;
        Process process;
        OutputStream stdin;     //used to write commands to shell... using OutputStream type, we can execute commands like writing commands in terminal
        InputStream stdout;     //used to read output of a command we executed... using InputStream type, we can input command's output to our routine here
        InputStream stderr;     //used to read errors of a command we executed... using InputStream type, we can input command's errors to our routine here
        BufferedReader br;
        String line;

        try {
            // Start a super-user process under which to execute our commands as root
            logV(TAGG + "Starting super-user shell session...");
            process = Runtime.getRuntime().exec("su");

            // Get process streams
            stdin = process.getOutputStream();
            stdout = process.getInputStream();
            stderr = process.getErrorStream();

            // Construct and execute command (new-line is like hitting enter)
            stdin.write(("/system/bin/cat /sys/class/power_supply/usb/online\n").getBytes());   //will need to parse the result below
            //result is string 0 or 1

            // Exit the shell
            stdin.write(("exit\n").getBytes());

            // Flush and close the stdin stream
            stdin.flush();
            stdin.close();

            // Read output of the executed command and parse it for what we need
            logV(TAGG+"Reading output of executed command...");
            br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                logV(TAGG+"  stdout line: "+line);
                if (line.contains("0")) {
                    ret = false;
                    break;
                } else if (line.contains("1")) {
                    ret = true;
                    break;
                } else {
                    logW(TAGG+"Unexpected result of command.");
                }
            }
            br.close();

            // Read error stream of the executed command
            logV(TAGG+"Reading errors of executed command...");
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                logV(TAGG+"  stderr line: "+line);
            }
            br.close();

            // Wait for process to finish
            process.waitFor();
            process.destroy();
        } catch (Exception e) {
            logE(TAGG+"Exception caught trying to read charge level (returning false): "+ e.getMessage() +".");
            ret = false;
        }

        logV(TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    public boolean isDcPowerConnected() {
        final String TAGG = "isDcPowerConnected: ";
        boolean ret = false;
        Process process;
        OutputStream stdin;     //used to write commands to shell... using OutputStream type, we can execute commands like writing commands in terminal
        InputStream stdout;     //used to read output of a command we executed... using InputStream type, we can input command's output to our routine here
        InputStream stderr;     //used to read errors of a command we executed... using InputStream type, we can input command's errors to our routine here
        BufferedReader br;
        String line;

        try {
            // Start a super-user process under which to execute our commands as root
            logV(TAGG + "Starting super-user shell session...");
            process = Runtime.getRuntime().exec("su");

            // Get process streams
            stdin = process.getOutputStream();
            stdout = process.getInputStream();
            stderr = process.getErrorStream();

            // Construct and execute command (new-line is like hitting enter)
            stdin.write(("/system/bin/cat /sys/class/power_supply/ac/online\n").getBytes());   //will need to parse the result below
            //result is string 0 or 1

            // Exit the shell
            stdin.write(("exit\n").getBytes());

            // Flush and close the stdin stream
            stdin.flush();
            stdin.close();

            // Read output of the executed command and parse it for what we need
            logV(TAGG+"Reading output of executed command...");
            br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                logV(TAGG+"  stdout line: "+line);
                if (line.contains("0")) {
                    ret = false;
                    break;
                } else if (line.contains("1")) {
                    ret = true;
                    break;
                } else {
                    logW(TAGG+"Unexpected result of command.");
                }
            }
            br.close();

            // Read error stream of the executed command
            logV(TAGG+"Reading errors of executed command...");
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                logV(TAGG+"  stderr line: "+line);
            }
            br.close();

            // Wait for process to finish
            process.waitFor();
            process.destroy();
        } catch (Exception e) {
            logE(TAGG+"Exception caught trying to read charge level (returning false): "+ e.getMessage() +".");
            ret = false;
        }

        logV(TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    public static int getWhichPowerPlugged(Context context, int defaultReturn) {
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, defaultReturn);
        return plugged;
    }

    /** Returns battery charge percentage as an integer value **/
    public int getBatteryLevel() {
        final String TAGG = "getBatteryLevel: ";
        int batLevel;
        BatteryManager bm;

        try {
            bm = (BatteryManager) appContext.getSystemService(BATTERY_SERVICE);
            if (bm != null) {
                batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            } else {
                batLevel = appContext.getResources().getInteger(R.integer.battery_reserve_shutdown) + 5;   //return a worst-case value, but just high enough to prevent shutdown
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught trying to read charge level: "+ e.getMessage() +".");
            batLevel = appContext.getResources().getInteger(R.integer.battery_reserve_shutdown) + 5;   //return a worst-case value, but just high enough to prevent shutdown
        }

        bm = null;

        Log.v(TAG, TAGG+"Returning "+batLevel+".");
        return batLevel;
    }

    /** Returns battery charge percentage as a decimal value **/
    public double getBatteryLevel_double() {
        final String TAGG = "getBatteryLevel_double: ";
        double batLevel;
        BatteryManager bm;

        try {
            bm = (BatteryManager) appContext.getApplicationContext().getSystemService(BATTERY_SERVICE);
            if (bm != null) {
                batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                batLevel /= 100;
            } else {
                Log.e(TAG, TAGG+"Could not get a BatteryManager instance.");
                batLevel = appContext.getResources().getInteger(R.integer.battery_reserve_shutdown) + 5;   //return a worst-case value, but just high enough to prevent shutdown
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught trying to read charge level: " + e.getMessage() + ".");
            batLevel = appContext.getResources().getInteger(R.integer.battery_reserve_shutdown) + 5;   //return a worst-case value, but just high enough to prevent shutdown
        }

        bm = null;

        Log.v(TAG, TAGG+"Returning " + batLevel + ".");
        return batLevel;
    }

    /** Returns the current battery net-charge/discharge current in micro-amps as an integer value **/
    public int getBatteryNetCurrentNow() {
        final String TAGG = "getBatteryNetCurrentNow: ";
        int batCurrent;
        BatteryManager bm;

        try {
            bm = (BatteryManager) appContext.getApplicationContext().getSystemService(BATTERY_SERVICE);
            if (bm != null) {
                batCurrent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            } else {
                Log.e(TAG, TAGG+"Could not get a BatteryManager instance.");
                batCurrent = 0;
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught trying to read charge level: "+ e.getMessage() +".");
            batCurrent = 0;
        }

        bm = null;

        Log.v(TAG, TAGG+"Returning "+batCurrent+".");
        return batCurrent;
    }

    public int getBatteryVoltage_milliVolts() {
        final String TAGG = "getBatteryVoltage_milliVolts: ";
        int batVoltage = 0;

        try {
            Process process;
            OutputStream stdin;     //used to write commands to shell... using OutputStream type, we can execute commands like writing commands in terminal
            InputStream stdout;     //used to read output of a command we executed... using InputStream type, we can input command's output to our routine here
            InputStream stderr;     //used to read errors of a command we executed... using InputStream type, we can input command's errors to our routine here
            BufferedReader br;
            String line;

            //cat /sys/class/power_supply/battery/voltage_now

            // Start a super-user process under which to execute our commands as root
            Log.v(TAG, TAGG + "Starting super-user shell session...");
            process = Runtime.getRuntime().exec("su");

            // Get process streams
            stdin = process.getOutputStream();
            stdout = process.getInputStream();
            stderr = process.getErrorStream();

            // Construct and execute command (new-line is like hitting enter)
            stdin.write(("cat /sys/class/power_supply/battery/voltage_now\n").getBytes()); //should give us micro-volts

            // Exit the shell
            stdin.write(("exit\n").getBytes());

            // Flush and close the stdin stream
            stdin.flush();
            stdin.close();

            // Read output of the executed command and parse it for what we need
            Log.v(TAG, TAGG+"Reading output of executed command...");
            br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                Log.v(TAG, TAGG+"  stdout line: "+line);
                batVoltage = Integer.parseInt(line);
            }
            br.close();

            // Read error stream of the executed command
            Log.v(TAG, TAGG+"Reading errors of executed command...");
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                Log.w(TAG, TAGG+"  stderr line: "+line);
            }
            br.close();

            // Wait for process to finish
            process.waitFor();
            process.destroy();
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage()+".");
        }

        //normalize
        batVoltage = batVoltage / 1000; //convert micro-volts to milli-volts

        logV(TAGG+"Returning: "+Integer.toString(batVoltage)+" mV.");
        return batVoltage;
    }

    /** Returns whole minutes remaining for current battery state (which capacity is defined in prefs)
     * If discharging, returns negative number (hours remaining until dead)
     * If charging, returns positive number (hours remaining until full)
     * Requires net current value to be positive or negative, accordingly. **/
    public int getBatteryTimeRemaining(int netCurrent) {
        //Battery Life = Battery Capacity in Milli amps per hour / Load Current in Mill amps * 0.70
        int batteryCapacity;        //mAh rating
        double timeRemainingHours;
        double timeRemainingMins;
        try {
            batteryCapacity = getBatteryCapacity();
            if (netCurrent > 0) {
                timeRemainingHours = (batteryCapacity - (batteryCapacity * getBatteryLevel_double())) / netCurrent * 0.70;
            } else {
                timeRemainingHours = (batteryCapacity * getBatteryLevel_double()) / netCurrent * 0.70;
            }
            timeRemainingMins = timeRemainingHours * 60;
            return (int) Math.round(timeRemainingMins);
        } catch (Exception e) {
            Log.e(TAG, "getBatteryTimeRemaining: Exception caught: "+e.getMessage()+".");
            return 0;
        }
    }

    /** Returns the battery capacity for the current detected device, from shared prefs */
    public int getSharedPrefsBatteryCapacityForDetectedDevice() {
        final String TAGG = "getSharedPrefsBatteryCapacityForDetectedDevice: ";
        int ret = 5000;
        try {
            ret = appContext.getResources().getInteger(R.integer.battery_capacity_mah_default);
        } catch (Exception e) {
            Log.w(TAG, TAGG+"Exception caught getting default battery capacity (will use "+ret+" if can't be determined): "+e.getMessage());
        }

        try {
            if (getDetectedDeviceModel().equals(appContext.getResources().getString(R.string.tablet_13_multigold_2018Q3_model))) {
                Log.v(TAG, TAGG+"Detected 13-inch MultiGold tablet, revision 2018-Q3.");
                ret = appContext.getResources().getInteger(R.integer.tablet_13_multigold_2018Q3_battery_capacity);
            } else {
                Log.w(TAG, TAGG+"Unknown device.");
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning "+ret+".");
        return ret;
    }
    public int getBatteryCapacity() {
        return getSharedPrefsBatteryCapacityForDetectedDevice();
    }

    /** Returns the detected device */
    public String getDetectedDeviceModel() {
        final String TAGG = "getDetectedDeviceModel: ";
        String ret = "";

        try {
            ret = Build.MODEL;
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning \""+ret+"\".");
        return ret;
    }


    /*============================================================================================*/
    /* Screen/Display-related Methods */

    /** This hides the top title/menu/options bar.
     * @param activity
     */
    public void configScreen_hideActionBar(AppCompatActivity activity) {
        final String TAGG = "configScreen_hideActionBar: ";
        logV(TAGG+"Invoked.");

        try {
            ActionBar actionBar = activity.getSupportActionBar();
            if (actionBar != null) {
                actionBar.hide();
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }

    /** This keeps the screen turned on.
     * @param activity
     */
    public void configScreen_keepScreenOn(AppCompatActivity activity) {
        final String TAGG = "configScreen_keepScreenOn: ";
        logV(TAGG+"Invoked.");

        try {
            Window window = activity.getWindow();
            if (window != null) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }

    /** This hides the bottom icon bar (for home, recent, back, etc.)
     * @param contentView The top-most View of the activity
     */
    public void configScreen_hideNavigationBar(View contentView) {
        final String TAGG = "configScreen_hideActionBar: ";
        logV(TAGG+"Invoked.");

        try {
            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            contentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }

    /** Set screen brightness via shell command.
     * Note: By default, this (and its overload method) will execute synchronously! You must explicitly specify async if you want to do it that way instead.
     * @param brightnessValue Raw Android/Linux CLI value for screen brightness
     * @param synchronicityFlag Whether to perform it synchronously or asynchronously (sync will yield valid return value, but async will just assume it worked and return true)
     * @return If synchronous, whether it worked verifiably, otherwise if async, always returns true regardless of success or not
     */
    public static boolean setScreenBrightnessFromShell(final int brightnessValue, byte synchronicityFlag) {
        final String TAGG = "setScreenBrightnessFromShell: ";
        boolean ret = false;

        if (synchronicityFlag == DO_ASYNCHRONOUSLY) {

            Thread threadInstance = new Thread() {
                public void run() {
                    try {
                        Process process;
                        OutputStream stdin;     //used to write commands to shell... using OutputStream type, we can execute commands like writing commands in terminal
                        InputStream stdout;     //used to read output of a command we executed... using InputStream type, we can input command's output to our routine here
                        InputStream stderr;     //used to read errors of a command we executed... using InputStream type, we can input command's errors to our routine here
                        BufferedReader br;
                        String line;

                        // Start a super-user process under which to execute our commands as root
                        Log.v(TAG, TAGG + "Starting super-user shell session...");
                        process = Runtime.getRuntime().exec("su");

                        // Get process streams
                        stdin = process.getOutputStream();
                        stdout = process.getInputStream();
                        stderr = process.getErrorStream();

                        // Construct and execute command (new-line is like hitting enter)
                        //stdin.write(("echo "+Integer.toString(brightnessValue)+" /sys/devices/platform/backlight/backlight/backlight/brightness\n").getBytes());
                        stdin.write(("settings put system screen_brightness " + Integer.toString(brightnessValue) + "\n").getBytes());

                        // Exit the shell
                        stdin.write(("exit\n").getBytes());

                        // Flush and close the stdin stream
                        stdin.flush();
                        stdin.close();

                        // Read output of the executed command and parse it for what we need
                        Log.v(TAG, TAGG + "Reading output of executed command...");
                        br = new BufferedReader(new InputStreamReader(stdout));
                        while ((line = br.readLine()) != null) {
                            Log.v(TAG, TAGG + "  stdout line: " + line);
                        }
                        br.close();

                        // Read error stream of the executed command
                        Log.v(TAG, TAGG + "Reading errors of executed command...");
                        br = new BufferedReader(new InputStreamReader(stderr));
                        while ((line = br.readLine()) != null) {
                            Log.w(TAG, TAGG + "  stderr line: " + line);
                        }
                        br.close();

                        // Wait for process to finish
                        process.waitFor();
                        process.destroy();
                    } catch (Exception e) {
                        Log.e(TAG, TAGG + "Exception caught: " + e.getMessage() + ".");
                    }
                }
            };
            threadInstance.setPriority(ThreadUtils.PRIORITY_LOW);
            threadInstance.start();

            ret = true;

        } else {

            try {
                Process process;
                OutputStream stdin;     //used to write commands to shell... using OutputStream type, we can execute commands like writing commands in terminal
                InputStream stdout;     //used to read output of a command we executed... using InputStream type, we can input command's output to our routine here
                InputStream stderr;     //used to read errors of a command we executed... using InputStream type, we can input command's errors to our routine here
                BufferedReader br;
                String line;

                // Start a super-user process under which to execute our commands as root
                Log.v(TAG, TAGG + "Starting super-user shell session...");
                process = Runtime.getRuntime().exec("su");

                // Get process streams
                stdin = process.getOutputStream();
                stdout = process.getInputStream();
                stderr = process.getErrorStream();

                // Construct and execute command (new-line is like hitting enter)
                //stdin.write(("echo "+Integer.toString(brightnessValue)+" /sys/devices/platform/backlight/backlight/backlight/brightness\n").getBytes());
                stdin.write(("settings put system screen_brightness " + Integer.toString(brightnessValue) + "\n").getBytes());

                // Exit the shell
                stdin.write(("exit\n").getBytes());

                // Flush and close the stdin stream
                stdin.flush();
                stdin.close();

                // Read output of the executed command and parse it for what we need
                Log.v(TAG, TAGG + "Reading output of executed command...");
                br = new BufferedReader(new InputStreamReader(stdout));
                while ((line = br.readLine()) != null) {
                    Log.v(TAG, TAGG + "  stdout line: " + line);
                }
                br.close();

                // Read error stream of the executed command
                Log.v(TAG, TAGG + "Reading errors of executed command...");
                br = new BufferedReader(new InputStreamReader(stderr));
                while ((line = br.readLine()) != null) {
                    Log.w(TAG, TAGG + "  stderr line: " + line);
                }
                br.close();

                // Wait for process to finish
                process.waitFor();
                process.destroy();

                // Check result
                if (getScreenBrightnessFromShell() == brightnessValue) {
                    ret = true;
                }
            } catch (Exception e) {
                Log.e(TAG, TAGG + "Exception caught: " + e.getMessage() + ".");
            }

        }

        Log.v(TAG, TAGG+"Returning: "+Boolean.toString(ret));
        return ret;
    }
    public static boolean setScreenBrightnessFromShell(int brightnessValue) {
        return setScreenBrightnessFromShell(brightnessValue, DO_SYNCHRONOUSLY);
    }

    public static int getScreenBrightnessFromShell() {
        final String TAGG = "getScreenBrightnessFromShell: ";
        int ret = -1;

        try {
            Process process;
            OutputStream stdin;     //used to write commands to shell... using OutputStream type, we can execute commands like writing commands in terminal
            InputStream stdout;     //used to read output of a command we executed... using InputStream type, we can input command's output to our routine here
            InputStream stderr;     //used to read errors of a command we executed... using InputStream type, we can input command's errors to our routine here
            BufferedReader br;
            String line;

            // Start a super-user process under which to execute our commands as root
            Log.v(TAG, TAGG + "Starting super-user shell session...");
            process = Runtime.getRuntime().exec("su");

            // Get process streams
            stdin = process.getOutputStream();
            stdout = process.getInputStream();
            stderr = process.getErrorStream();

            // Construct and execute command (new-line is like hitting enter)
            //stdin.write(("cat /sys/devices/platform/backlight/backlight/backlight/brightness\n").getBytes()); //should give us micro-volts
            stdin.write(("settings get system screen_brightness\n").getBytes());

            // Exit the shell
            stdin.write(("exit\n").getBytes());

            // Flush and close the stdin stream
            stdin.flush();
            stdin.close();

            // Read output of the executed command and parse it for what we need
            Log.v(TAG, TAGG + "Reading output of executed command...");
            br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                Log.v(TAG, TAGG + "  stdout line: " + line);
                ret = Integer.parseInt(line);
            }
            br.close();

            // Read error stream of the executed command
            Log.v(TAG, TAGG + "Reading errors of executed command...");
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                Log.w(TAG, TAGG + "  stderr line: " + line);
            }
            br.close();

            // Wait for process to finish
            process.waitFor();
            process.destroy();
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage()+".");
        }

        Log.v(TAG, TAGG+"Returning: "+Integer.toString(ret));
        return ret;
    }

    private final static int minBrightnessValue = 20;      //minimum visible/useful brightness    TODO: make this not hardcoded someday
    private final static int maxBrightnessValue = 255;     //TODO: make this not hardcoded someday

    /** Set screen brightness to minimum.
     * Note: By default, this (and its overload method) will execute synchronously! You must explicitly specify async if you want to do it that way instead.
     * @param synchronicityFlag Whether to perform it synchronously or asynchronously (sync will yield valid return value, but async will just assume it worked and return true)
     * @return If synchronous, whether it worked verifiably, otherwise if async, always returns true regardless of success or not
     */
    public static boolean setScreenBrightness_minimum(byte synchronicityFlag) {
        return setScreenBrightnessFromShell(minBrightnessValue, synchronicityFlag);
    }
    public static boolean setScreenBrightness_minimum() {
        return setScreenBrightness_minimum(DO_SYNCHRONOUSLY);
    }

    /** Set screen brightness to maximum.
     * Note: By default, this (and its overload method) will execute synchronously! You must explicitly specify async if you want to do it that way instead.
     * @param synchronicityFlag Whether to perform it synchronously or asynchronously (sync will yield valid return value, but async will just assume it worked and return true)
     * @return If synchronous, whether it worked verifiably, otherwise if async, always returns true regardless of success or not
     */
    public static boolean setScreenBrightness_maximum(byte synchronicityFlag) {
        return setScreenBrightnessFromShell(maxBrightnessValue, synchronicityFlag);
    }
    public static boolean setScreenBrightness_maximum() {
        return setScreenBrightness_maximum(DO_SYNCHRONOUSLY);
    }

    public static boolean setScreenBrightness_nominal() {
        return setScreenBrightnessToPercent(80);
    }

    public static boolean setScreenBrightnessToPercent(int percentToSet) {
        final String TAGG = "setScreenBrightnessToPercent("+Integer.toString(percentToSet)+"%): ";
        boolean ret = false;

        // Validate
        if (percentToSet < 0)
            percentToSet = 0;
        if (percentToSet > 100)
            percentToSet = 100;

        try {
            // Convert provided percentage to absolute brightness value
            int amountToSet = Math.round( (float)maxBrightnessValue * (float)percentToSet/100 );

            // Normalize
            if (amountToSet < minBrightnessValue)
                amountToSet = minBrightnessValue;
            if (amountToSet > maxBrightnessValue)
                amountToSet = maxBrightnessValue;

            // Set the brightness
            ret = setScreenBrightnessFromShell(amountToSet);

        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage()+".");
        }

        Log.v(TAG, TAGG+"Returning: "+Boolean.toString(ret));
        return ret;
    }

    public static boolean reduceScreenBrightnessBy(int percentToReduce) {
        final String TAGG = "reduceScreenBrightness("+Integer.toString(percentToReduce)+"%): ";
        boolean ret = false;

        if (percentToReduce <= 0 || percentToReduce > 100) {
            Log.w(TAG, TAGG+"Percent to reduce by not valid, aborting.");
            return ret;
        }

        try {
            final int minBrightnessValue = 20;      //minimum visible/useful brightness    TODO: make this not hardcoded someday
            final int maxBrightnessValue = 255;     //TODO: make this not hardcoded someday

            // Calculate percent of max, by what they provided
            int amountToReduceBy = Math.round( (float)maxBrightnessValue * (float)percentToReduce/100 );

            // Calculate what value to actually set
            int amountToReduceTo = getScreenBrightnessFromShell() - amountToReduceBy;

            // Validate
            if (amountToReduceTo >= minBrightnessValue) {
                if (setScreenBrightnessFromShell(amountToReduceTo)) {
                    ret = true;
                }
            } else {
                if (setScreenBrightnessFromShell(minBrightnessValue)) {
                    ret = true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage()+".");
        }

        Log.v(TAG, TAGG+"Returning: "+Boolean.toString(ret));
        return ret;
    }

    public static boolean increaseScreenBrightnessBy(int percentToIncreaseBy) {
        final String TAGG = "increaseScreenBrightness("+Integer.toString(percentToIncreaseBy)+"%): ";
        boolean ret = false;

        if (percentToIncreaseBy <= 0 || percentToIncreaseBy > 100) {
            Log.w(TAG, TAGG+"Percent to increase by not valid, aborting.");
            return ret;
        }

        try {
            final int minBrightnessValue = 20;      //minimum visible/useful brightness    TODO: make this not hardcoded someday
            final int maxBrightnessValue = 255;     //TODO: make this not hardcoded someday

            // Calculate percent of max, by what they provided
            int amountToChangeBy = Math.round( (float)maxBrightnessValue * (float)percentToIncreaseBy/100 );

            // Calculate what value to actually set
            int amountToChangeTo = getScreenBrightnessFromShell() + amountToChangeBy;

            // Validate
            if (amountToChangeTo <= maxBrightnessValue) {
                if (setScreenBrightnessFromShell(amountToChangeTo)) {
                    ret = true;
                }
            } else {
                if (setScreenBrightnessFromShell(maxBrightnessValue)) {
                    ret = true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage()+".");
        }

        Log.v(TAG, TAGG+"Returning: "+Boolean.toString(ret));
        return ret;
    }


    /*============================================================================================*/
    /* Date and Time Methods */

    /** Sets the device's time zone.
     * @param timeZone
     */
    public void setTimeZone(@NonNull String timeZone) {
        final String TAGG = "setTimeZone("+timeZone+"): ";
        logV(TAGG+"Invoked.");

        try {
            AlarmManager am = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);

            if (am != null) {
                am.setTimeZone(timeZone);
            } else {
                logE(TAGG+"Failed to get an AlarmManager instance, unable to set time zone to device.");
                /* TODO? The following code runs the adb shell command for setting the timezone unnecessary at this time
                Process proc = Runtime.getRuntime().exec("su -c 'setprop persist.sys.timezone America/Los_Angeles; stop; sleep 5; start'");

                if ((proc.waitFor() != 0)) {
                 throw new SecurityException();
                */
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
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
