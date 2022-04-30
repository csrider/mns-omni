package com.messagenetsystems.evolution2.utilities;

/* DatetimeUtils
 * Date and time related tasks.
 *
 * Revisions:
 *  2019.12.05      Chris Rider     Created and migrated some old methods.
 *  2020.05.07      Chris Rider     Added another Date creation method for another style of string.
 *  2020.05.24      Chris Rider     Added more methods to check for more time scales between two Dates.
 *  2020.05.27      Chris Rider     Added methods to convert various time units to milliseconds.
 */

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.bosphere.filelogger.FL;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;


public class DatetimeUtils {
    private static final String TAG = DatetimeUtils.class.getSimpleName();

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
    public DatetimeUtils(Context appContext, int logMethod) {
        this.logMethod = logMethod;
    }


    /** Returns whether the provided dates are within the specified milliseconds of one another
     * @param d1 First Date.
     * @param d2 Second date.
     * @param diffMS Milliseconds range.
     * @return Whether the provided dates are within the specified milliseconds of one another. */
    public boolean datesAreWithinMS(@NonNull final Date d1, @NonNull final Date d2, final long diffMS) {
        final String TAGG = "datesAreWithinMS("+d1.toString()+","+d2.toString()+","+String.valueOf(diffMS)+"): ";
        boolean ret = false;

        try {
            long diff = Math.abs(d1.getTime() - d2.getTime());

            if (diff <= diffMS) {
                ret = true;
            }
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }

        logV(TAGG+"Returning "+String.valueOf(ret));
        return ret;
    }

    /** Returns whether the provided dates are within the specified seconds of one another
     * @param d1 First Date.
     * @param d2 Second date.
     * @param diffWithin Seconds range.
     * @return Whether the provided dates are within the specified seconds of one another. */
    public boolean datesAreWithinSecs(@NonNull final Date d1, @NonNull final Date d2, final long diffWithin) {
        final String TAGG = "datesAreWithinSecs("+d1.toString()+","+d2.toString()+","+String.valueOf(diffWithin)+"): ";
        boolean ret = false;

        try {
            long diffMS = Math.abs(d1.getTime() - d2.getTime());   // gets difference in milliseconds

            //secs * 1000 = ms
            if ( diffMS <= (diffWithin * 1000) ) {
                ret = true;
            }
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }

        logV(TAGG+"Returning "+String.valueOf(ret));
        return ret;
    }

    /** Returns whether the provided dates are within the specified minutes of one another
     * @param d1 First Date.
     * @param d2 Second date.
     * @param diffWithin Minutes range.
     * @return Whether the provided dates are within the specified minutes of one another. */
    public boolean datesAreWithinMins(@NonNull final Date d1, @NonNull final Date d2, final long diffWithin) {
        final String TAGG = "datesAreWithinMins("+d1.toString()+","+d2.toString()+","+String.valueOf(diffWithin)+"): ";
        boolean ret = false;

        try {
            long diffMS = Math.abs(d1.getTime() - d2.getTime());   // gets difference in milliseconds

            //mins * 60 = secs * 1000 = ms
            if ( diffMS <= (diffWithin * 60 * 1000) ) {
                ret = true;
            }
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }

        logV(TAGG+"Returning "+String.valueOf(ret));
        return ret;
    }

    /** Returns whether the provided dates are within the specified hours of one another
     * @param d1 First Date.
     * @param d2 Second date.
     * @param diffWithin Hours range.
     * @return Whether the provided dates are within the specified hours of one another. */
    public boolean datesAreWithinHours(@NonNull final Date d1, @NonNull final Date d2, final long diffWithin) {
        final String TAGG = "datesAreWithinHours("+d1.toString()+","+d2.toString()+","+String.valueOf(diffWithin)+"): ";
        boolean ret = false;

        try {
            long diffMS = Math.abs(d1.getTime() - d2.getTime());   // gets difference in milliseconds

            //hours * 60 = mins * 60 = secs * 1000 = ms
            if ( diffMS <= (diffWithin * 60 * 60 * 1000) ) {
                ret = true;
            }
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }

        logV(TAGG+"Returning "+String.valueOf(ret));
        return ret;
    }

    /** Returns whether the provided dates are within the specified days of one another
     * @param d1 First Date.
     * @param d2 Second date.
     * @param diffWithin Days range.
     * @return Whether the provided dates are within the specified days of one another. */
    public boolean datesAreWithinDays(@NonNull final Date d1, @NonNull final Date d2, final long diffWithin) {
        final String TAGG = "datesAreWithinDays("+d1.toString()+","+d2.toString()+","+String.valueOf(diffWithin)+"): ";
        boolean ret = false;

        try {
            long diffMS = Math.abs(d1.getTime() - d2.getTime());   // gets difference in milliseconds

            //days * 24 = hours * 60 = mins * 60 = secs * 1000 = ms
            if ( diffMS <= (diffWithin * 24 * 60 * 60 * 1000) ) {
                ret = true;
            }
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }

        logV(TAGG+"Returning "+String.valueOf(ret));
        return ret;
    }

    /** Returns whether the provided dates are within the specified weeks of one another
     * @param d1 First Date.
     * @param d2 Second date.
     * @param diffWithin Weeks range.
     * @return Whether the provided dates are within the specified weeks of one another. */
    public boolean datesAreWithinWeeks(@NonNull final Date d1, @NonNull final Date d2, final long diffWithin) {
        final String TAGG = "datesAreWithinWeeks("+d1.toString()+","+d2.toString()+","+String.valueOf(diffWithin)+"): ";
        boolean ret = false;

        try {
            long diffMS = Math.abs(d1.getTime() - d2.getTime());   // gets difference in milliseconds

            //weeks * 7 = days * 24 = hours * 60 = mins * 60 = secs * 1000 = ms
            if ( diffMS <= (diffWithin * 7 * 24 * 60 * 60 * 1000) ) {
                ret = true;
            }
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }

        logV(TAGG+"Returning "+String.valueOf(ret));
        return ret;
    }

    /** Returns a Date object representing the calculated expiration N-seconds from provided base-Date.
     * @param base Date object from which to calculate duration from.
     * @param durationSecs Seconds duration to calculate expiration with.
     * @return Date object representing the calculated expiration N-seconds from provided base-Date. */
    public Date calculateExpirationDate_fromDurationInSecs(Date base, int durationSecs) {
        final String TAGG = "calculateExpirationDate_fromDurationInSecs("+String.valueOf(base)+","+Integer.toString(durationSecs)+"): ";

        Date ret;
        Calendar calendar;

        try {
            if (base == null) {
                base = new Date();  //use current date-time
                logV(TAGG+"Provided base Date is null, using current Date ("+base.toString()+").");
            }

            if (durationSecs == 0) {
                //banner gives us zero for no-expire, so make max int value
                logV(TAGG+"Duration is 0, so assuming this is no-expire and using max Integer value.");
                durationSecs = Integer.MAX_VALUE - 1;   //-1 just to be extra safe from out-of-bounds problems
            }

            //calculate the addition of specified seconds duration
            calendar = Calendar.getInstance();
            calendar.setTime(base);
            calendar.add(Calendar.SECOND, durationSecs);

            ret = calendar.getTime();
        } catch (Exception e) {
            logE(TAGG+"Exception caught trying to add "+Integer.toString(durationSecs)+" seconds to Date: "+e.getMessage());
            ret = null;
        }

        logV(TAGG+"Returning "+String.valueOf(ret));
        return ret;
    }

    /** Returns whether the provided date has elapsed past the current date.
     * @param dateArg Date object to test.
     * @return Whether provided Date is after the current date. */
    public boolean dateHasPassedCurrentDate(@NonNull final Date dateArg) {
        final String TAGG = "dateHasPassedCurrentDate("+dateArg.toString()+"): ";
        boolean ret = false;

        try {
            if (dateArg.after(new Date())) {
                ret = true;
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning "+String.valueOf(ret));
        return ret;
    }

    /** Returns whether the provided date is before the current date.
     * Useful for checking if a message has expired.
     * @param dateArg Date object to test.
     * @return Whether provided Date is before the current date. */
    public boolean currentDateHasPassedDate(@NonNull final Date dateArg) {
        final String TAGG = "currentDateHasPassedDate("+dateArg.toString()+"): ";
        boolean ret = false;

        try {
            if (new Date().after(dateArg)) {
                return true;
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
            return false;
        }

        logV(TAGG+"Returning "+String.valueOf(ret));
        return ret;
    }

    /** Returns Date object for provided string "EEE MMM d HH:mm:ss z yyyy".
     * @param dateString Date-string in SimpleDateFormat "EEE MMM d HH:mm:ss z yyyy".
     * @return Date object for specified date-string. */
    public Date getDateFromString(@NonNull final String dateString) {
        final String TAGG = "getDateFromString(\""+dateString+"\"): ";
        Date ret = null;

        try {
            @SuppressLint("SimpleDateFormat")
            SimpleDateFormat format = new SimpleDateFormat("EEE MMM d HH:mm:ss z yyyy");
            ret = format.parse(dateString);
        } catch (ParseException e) {
            logE(TAGG+"Exception caught trying to parse String to Date.");
        }

        logV(TAGG+"Returning "+String.valueOf(ret));
        return ret;
    }

    /** Returns Date object for provided string "MM-dd-yyyy" (ex. 12-31-2020).
     * @param dateString Date-string in SimpleDateFormat "MM-dd-yyyy".
     * @return Date object for specified date-string. */
    public Date getDateFromString_mmddyyyy(@NonNull final String dateString) {
        final String TAGG = "getDateFromString_mmddyyyy(\""+dateString+"\"): ";
        Date ret = null;

        try {
            @SuppressLint("SimpleDateFormat")
            SimpleDateFormat format = new SimpleDateFormat("MM-dd-yyyy");
            ret = format.parse(dateString);
        } catch (ParseException e) {
            logE(TAGG+"Exception caught trying to parse String to Date.");
        }

        logV(TAGG+"Returning "+String.valueOf(ret));
        return ret;
    }

    public static String generateHumanDifference(long ms1, long ms2) {
        final String TAGG = "generateHumanDifference: ";
        String ret = "unknown";

        final int secsInMinute = 60;
        final int secsInHour = secsInMinute * 60;
        final int secsInDay = secsInHour * 24;

        try {
            long diff_ms = Math.abs(ms1 - ms2);
            if (diff_ms < 1000) {
                //we're dealing with milliseconds time scale
                ret = diff_ms + " milliseconds";
            } else {
                long diff_s = Math.round((float)diff_ms / 1000);
                if (diff_s >= secsInDay) {
                    //we're dealing with days time scale
                    ret = Math.round((float)diff_s / secsInDay) + " days";
                } else if (diff_s >= secsInHour) {
                    //we're dealing with hours time scale
                    ret = Math.round((float)diff_s / secsInHour) + " hours";
                } else if (diff_s >= secsInMinute) {
                    //we're dealing with minutes time scale
                    ret = Math.round((float)diff_s / secsInMinute) + " minutes";
                } else {
                    //we must be dealing with purely just seconds
                    ret = diff_s + " seconds";
                }
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning: \""+ret+"\"");
        return ret;
    }

    public static long getMillisecondsInDays(int days) {
        return (long)days * 24 * 60 * 60 * 1000;
    }

    public static long getMillisecondsInHours(int hours) {
        return (long)hours * 60 * 60 * 1000;
    }

    public static long getMillisecondsInMinutes(int minutes) {
        return (long)minutes * 60 * 1000;
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
