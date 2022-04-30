package com.messagenetsystems.evolution2.utilities;

/* PlatformUtilsMessageNet
 * MessageNet-Connections server software platform utilities.
 * This is migrated from the old MessageNetUtils class, and is meant to support the MessageNet server software.
 *
 * Revisions:
 *  2019.12.05       Chris Rider     Created and begun migrating.
 */

import android.content.Context;
import android.util.Log;

import com.bosphere.filelogger.FL;

import java.util.Calendar;
import java.util.Date;


public class PlatformUtilsMessageNet {
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

    /** Constructor
     * @param appContext Application context
     * @param logMethod Logging method to use
     */
    public PlatformUtilsMessageNet(Context appContext, int logMethod) {
        this.logMethod = logMethod;
    }


    /** Calculates and returns a CentOS server epoch value offset from standard Unix epoch. */
    public long epochOffset_secs() {
        final String TAGG = "epochOffset_secs";

        //final long offsetSecs_GPS = 315964800;              // GPS (Jan 6, 1980)     // https://stackoverflow.com/questions/20521750/ticks-between-unix-epoch-and-gps-epoch
        final long offsetSecs_IBM = 315532800 + 14400;      // IBM (Jan 1, 1980)     // debugging was 4 hours behind for some reason, so added 14400 (240 minutes) to make up that diff (DST??!)

        long ret = offsetSecs_IBM;

        try {
            Boolean isNowDaylightSavingsTime;
            Calendar calendar = Calendar.getInstance();
            //Log.d(TAG, "ZZZ " + (calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)) / (60 * 1000));
            //Log.d(TAG, "ZZZ " + (calendar.get(Calendar.DST_OFFSET)) / (60 * 1000));
            if ((calendar.get(Calendar.DST_OFFSET) / 60 * 1000) == 0) {
                //daylight savings time is NOT in effect
                isNowDaylightSavingsTime = false;
            } else {
                isNowDaylightSavingsTime = true;
            }

            //SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy hh:mm:ss");
            //df.setTimeZone(TimeZone.getTimeZone("UTC"));

            if (isNowDaylightSavingsTime) {
                ret = offsetSecs_IBM;                //stick with our 14400 above
            } else {
                ret = offsetSecs_IBM + (60 * 60);    //add an hour (make 18000 instead of 14400)
            }

            //Date x = null;
            //Date y = null;
            //try {
            //    x = df.parse("1.1.1970 00:00:00");      //standard Unix epoch
            //    y = df.parse("1.1.1980 00:00:00");      //messagenet server epoch
            //} catch (ParseException e) {
            //    Log.w(TAG, "epochOffset: Exception caught parsing date: "+e.getMessage());
            //return offsetSecs_IBM;
            //}

            //long diff = y.getTime()
            // - x.getTime();
            //long diffSec = diff / 1000;

            //return diffSec;
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }

        logV(TAGG+"Returning "+String.valueOf(ret));
        return ret;
    }

    /** Returns Date object equal to the provided server dtsec, or current date if there was a problem. */
    public Date calculateDate_fromServerDTSEC(long unixDateTime) {
        final String TAGG = "calculateDate_fromServerDTSEC("+String.valueOf(unixDateTime)+"): ";
        Date date;

        try {
            date = new Date((unixDateTime + epochOffset_secs()) * 1000);       //server uses seconds-based epoch, but Java uses milliseconds);
        } catch (Exception e) {
            logE(TAGG+"Exception caught trying to create Date object from value, "+Long.toString(unixDateTime)+". Returning current date: "+e.getMessage());
            date = new Date();
        }

        return date;
    }

    /*
    public static int convertServerBaseVolumeToAndroidForStream(Context context, String serverVolume, int stream) {
        final String TAGG = "convertServerVolumeToAndroidForStream: ";
        // Server volume has a valid range,
        // so we need to calculate percentage of provided volume within that range,
        // and then factor that percentage to whatever the Android stream's maximum is.

        // Figure out our server base-volume range

    }
*/

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
