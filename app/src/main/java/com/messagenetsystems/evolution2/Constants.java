package com.messagenetsystems.evolution2;

/* Constants class
 * Use this to conveniently and reliably reuse and maintain code.
 * This should focus on stuff that needs to be used across multiple classes.
 * Just use its members statically.
 *
 * Revisions:
 *  2019.12.03      Chris Rider     Created.
 *  2019.12.10      Chris Rider     Begun adding colors.
 *  2020.02.03      Chris Rider     Added Configuration class and Delivery subclass, starting with defining defaults that can be overridden by shared-prefs/config changes of the user/admin.
 *  2020.05.13      Chris Rider     Adding more colors, in Integer format.
 *  2020.05.24      Chris Rider     Tweaked bright orange to be a little more red.
 *  2020.05.27      Chris Rider     Added classes related to health monitoring, instead of using strings XML (which must be inflated and could take slightly more time to execute).
 *  2020.06.03      Chris Rider     Added Intents subclass to help keep intent strings consistent.
 *  2020.06.22      Chris Rider     Revamped constants for keeping log files cleaned out better.
 *  2020.06.28      Chris Rider     Refactored intent-related constants names and values to make code maintainability easier.
 */

import android.graphics.Color;

public class Constants {
    public static final int LOG_METHOD_LOGCAT = 1;
    public static final int LOG_METHOD_FILELOGGER = 2;

    public static final String BLE_UUID_BASE_96 = "-0000-1000-8000-00805F9B34FB";                   //the last 96-bits of the standard BLE UUID base

    // WARNING: Make sure any changes to this block coincide with other apps' Constants class files!
    public static final String NAMESPACE_MESSAGENET = "com.messagenetsystems";
    public static final String PACKAGE_NAME_MAIN_APP = NAMESPACE_MESSAGENET+".evolution2";
    public static final String PACKAGE_NAME_FLASHERS = NAMESPACE_MESSAGENET+".evolutionflasherlights";

    // WARNING: Make sure any changes to this block coincide with other apps' Constants class files!
    public static class Intents {
        public static class Filters {
            public static String MAIN_APP_HEARTBEAT = PACKAGE_NAME_MAIN_APP + ".intent.filter.heartbeat";                                   //main delivery app's heartbeat intent filter string

            public static String MAIN_APP_DELIVERY_STATUS = PACKAGE_NAME_MAIN_APP + ".intent.filter.deliveryStatus";                        //main delivery app's delivery status intent filter string
        }

        public static class Actions {
            public static String REGISTER_MAIN_APP_HEARTBEAT = PACKAGE_NAME_MAIN_APP + ".intent.action.registerHeartbeat";                  //main delivery app's heartbeat register request action string

            public static String UPDATE_NUMBER_DELIVERING_MSGS = PACKAGE_NAME_MAIN_APP + ".intent.action.updateNumberDeliveringMsgs";       //main delivery app's update # delivering msgs request action string
        }

        public static class ExtrasKeys {
            public static String NOW_DATE_MS = NAMESPACE_MESSAGENET + ".intent.extra.nowDateMilliseconds";                                  //generic extras-key for current Date.getTime() value
            public static String APP_STARTED_DATE_MS = NAMESPACE_MESSAGENET + ".intent.extra.appStartedDateMilliseconds";                   //generic extras-key string for when app started

            public static String MAIN_APP_NUMBER_DELIVERING_MSGS = PACKAGE_NAME_MAIN_APP + ".intent.extra.numOfDeliveringMsgs";             //main delivery app's extras-key string for number of delivering msgs
        }
    }


    public static class Configuration {
        public class App {
            public static final boolean LOG_TO_FILE = true;
        }

        public class Delivery {
            // Define some defaults to load into a virgin config/shared-prefs...
            // Was just in a mood one day to like this paradigm better than XML strings values.
            // (the user should be able to override these... these are not meant to be functional runtime values used in the main code)
            public static final boolean DEFAULT_LOWER_PRIORITY_INTERRUPTIBLE_BY_HIGH = true;
            public static final boolean DEFAULT_LOWER_PRIORITY_CAN_COEXIST_WITH_HIGH = true;
            public static final boolean DEFAULT_MSG_TYPE_OVERRIDES_PRIORITY = false;
        }
    }


    public static class Health {
        public class Energy {
            /*
            Notes about volts vs. amps...
                - The device reports volts seemingly more reliably than amps.
                - Amps are oddly reported, sometimes appearing to show nearly 0 even though battery is charging.
                - A battery's charge level is also more closely tied to volts than amps.
                - For those reasons, we rely more on voltages to know what's going on; and amperages to fill any gaps.
                - Volts are like "pressure," so if a charger is connected, volts will be higher (to push current into battery), but if disconnected, they will be lower (for same charge level).
            */

            public static final int MILLIVOLTS_SYSTEM_BECOMES_BRICKED = 3200;                       //voltage at which the tablet (BIOS) will no longer be able to even take a charge, much less start up properly (bricked)
            public static final int MILLIVOLTS_SYSTEM_SHUTDOWN_DANGER = 390;                        //voltage level at which the tablet (OS/BIOS) will force shutdown itself, no matter if we want it to or not
            public static final int MILLIVOLTS_DO_PREEMPTIVE_SHUTDOWN = 3500;                       //voltage level to pre-emptively and gracefully shutdown the device (note: this can only happen if power is lost!)
            public static final int MILLIVOLTS_DEAD_BATTERY_CHARGING = 3770;                        //minimum voltage we expect to see when a completely dead battery is beginning to charge
            public static final int MILLIVOLTS_MAXIMUM_POSSIBLE = 4200;                             //theoretical design maximum possible voltage the battery is rated at

            public static final double MILLIVOLT_HEALTH_TOLERANCE_PERCENT = 2.3;                    //percentage of nominal voltage to consider battery as healthy (volts can vary due to screen brightness, CPU usage, etc.)
            public static final int MILLIVOLTS_EXPECTED_CHARGED20_POWER_LOST = 3500;                //expected voltage of a healthy 20% charged battery, when external power is lost
            public static final int MILLIVOLTS_EXPECTED_CHARGED50_POWER_LOST = 3525;                //expected voltage of a healthy 50% charged battery, when external power is lost
            public static final int MILLIVOLTS_EXPECTED_CHARGED80_POWER_LOST = 3590;                //expected voltage of a healthy 80% charged battery, when external power is lost
            public static final int MILLIVOLTS_EXPECTED_CHARGED100_POWER_LOST = 3730;               //expected voltage of a healthy 100% charged battery, when external power is lost
            public static final int MILLIVOLTS_EXPECTED_CHARGED100_POWER_CONNECTED = 4090;          //expected voltage of a healthy 100% charged battery, when external power is connected

            public static final int MILLIAMPS_HEALTH_TOLERANCE_CHARGED100 = 3;                      //milliamps tolerance to allow for +/- the expected value below
            public static final int MILLIAMPS_EXPECTED_CHARGED100 = -1;                             //nominal or median 100% charge milliamps we expect to see (health checks may allow +/- tolerance)
            public static final int MILLIAMPS_EXPECTED_CHARGED100_MIN = -10;                        //minimum expected milliamps for a 100% charged device (this is intended to be a more hard limit than nominal +/- tolerance)
            public static final int MILLIAMPS_EXPECTED_CHARGED100_MAX = 10;                         //maximum expected milliamps for a 100% charged device (this is intended to be a more hard limit than nominal +/- tolerance)
        }

        public class Storage {
            public static final int MULTIPLIER_KB_FOR_B = 1024;
            public static final int MULTIPLIER_MB_FOR_B = 1024 * 1024;
            public static final long MULTIPLIER_GB_FOR_B = 1024 * 1024 * 1024;

            public static final int EXTERNAL_LOW_THRESHOLD_MB = 2000;                               //megabytes remaining on the external storage (sdcard) partition, below at which to be StorageUtils.SPACE_STATE_EXTERNAL_LOW
            public static final int EXTERNAL_FULL_THRESHOLD_MB = 500;                               //megabytes remaining on the external storage (sdcard) partition, below at which to be StorageUtils.SPACE_STATE_EXTERNAL_FULL

            public static final long MIN_TOTAL_BYTES_TO_KEEP_FILES_LOGS_MAIN_APP = 8 * MULTIPLIER_GB_FOR_B;
            public static final long MIN_TOTAL_BYTES_TO_KEEP_FILES_LOGS_FLASHERS = 1 * MULTIPLIER_GB_FOR_B;
            public static final int MAX_DAYS_KEEP_FILES_VIDEOS = 14;
            public static final int MAX_DAYS_KEEP_FILES_IMAGES = 14;
            public static final int MAX_DAYS_KEEP_FILES_DOWNLOADS = 14;
            public static final int MAX_DAYS_KEEP_FILES_SCREENSHOTS = 14;
        }

        public class Display {
            public static final int SCREEN_BRIGHTNESS_PERCENT_MAX = 100;
            public static final int SCREEN_BRIGHTNESS_PERCENT_NOMINAL = 80;
            public static final int SCREEN_BRIGHTNESS_PERCENT_POWER_SAVE = 25;
            public static final int SCREEN_BRIGHTNESS_PERCENT_MIN = 15;
        }
    }

    public static class Database {
        // Date-time modifiers
        //  https://www.sqlite.org/lang_datefunc.html
        // Older-than DAO usage example:
        //  @Query("DELETE FROM messages WHERE strftime('%s', datetime('now', :modifier)) > strftime('%s', modified_at)")
        //  void deleteAll_olderThan(String modifier);
        public static final String SQLITE_DTMOD_OLDERTHAN_1SECOND = "-1 second";
        public static final String SQLITE_DTMOD_OLDERTHAN_1MINUTE = "-1 minute";
        public static final String SQLITE_DTMOD_OLDERTHAN_1HOUR = "-1 hour";
        public static final String SQLITE_DTMOD_OLDERTHAN_2HOURS = "-2 hours";
        public static final String SQLITE_DTMOD_OLDERTHAN_3HOURS = "-3 hours";
        public static final String SQLITE_DTMOD_OLDERTHAN_4HOURS = "-4 hours";
        public static final String SQLITE_DTMOD_OLDERTHAN_5HOURS = "-5 hours";
        public static final String SQLITE_DTMOD_OLDERTHAN_6HOURS = "-6 hours";
        public static final String SQLITE_DTMOD_OLDERTHAN_7HOURS = "-7 hours";
        public static final String SQLITE_DTMOD_OLDERTHAN_8HOURS = "-8 hours";
        public static final String SQLITE_DTMOD_OLDERTHAN_9HOURS = "-9 hours";
        public static final String SQLITE_DTMOD_OLDERTHAN_10HOURS = "-10 hours";
        public static final String SQLITE_DTMOD_OLDERTHAN_11HOURS = "-11 hours";
        public static final String SQLITE_DTMOD_OLDERTHAN_12HOURS = "-12 hours";
        public static final String SQLITE_DTMOD_OLDERTHAN_1DAY = "-1 day";
        public static final String SQLITE_DTMOD_OLDERTHAN_2DAYS = "-2 days";
        public static final String SQLITE_DTMOD_OLDERTHAN_3DAYS = "-3 days";
        public static final String SQLITE_DTMOD_OLDERTHAN_4DAYS = "-4 days";
        public static final String SQLITE_DTMOD_OLDERTHAN_5DAYS = "-5 days";
        public static final String SQLITE_DTMOD_OLDERTHAN_6DAYS = "-6 days";
        public static final String SQLITE_DTMOD_OLDERTHAN_1WEEK = "-1 week";
        public static final String SQLITE_DTMOD_OLDERTHAN_2WEEKS = "-2 weeks";
        public static final String SQLITE_DTMOD_OLDERTHAN_3WEEKS = "-3 weeks";
        public static final String SQLITE_DTMOD_OLDERTHAN_4WEEKS = "-4 weeks";
        public static final String SQLITE_DTMOD_OLDERTHAN_1MONTH = "-1 month";
    }

    public static class Colors {
        public static final String BLACK_HEX = "000000";
        public static final String WHITE_HEX = "ffffff";
        public static final String RED_BRIGHT_HEX = "ff0000";
        public static final String GREEN_BRIGHT_HEX = "00ff00";
        public static final String BLUE_BRIGHT_HEX = "0000ff";
        public static final String YELLOW_BRIGHT_HEX = "ffff00";
        public static final String TEAL_BRIGHT_HEX = "00ffff";

        public static final int WHITE = Color.rgb(Integer.parseInt("ff",16), Integer.parseInt("ff",16), Integer.parseInt("ff",16));
        public static final int GRAY_LIGHT = Color.rgb(Integer.parseInt("bb",16), Integer.parseInt("bb",16), Integer.parseInt("bb",16));
        public static final int GRAY = Color.rgb(Integer.parseInt("88",16), Integer.parseInt("88",16), Integer.parseInt("88",16));
        public static final int GRAY_DARK = Color.rgb(Integer.parseInt("44",16), Integer.parseInt("44",16), Integer.parseInt("44",16));
        public static final int BLACK = Color.rgb(0, 0, 0);

        public static final int RED_BRIGHT = Color.rgb(Integer.parseInt("ff",16), 0, 0);
        public static final int RED = Color.rgb(Integer.parseInt("cc",16), 0, 0);
        public static final int RED_DARK = Color.rgb(Integer.parseInt("99",16), 0, 0);

        public static final int GREEN_BRIGHT = Color.rgb(0, Integer.parseInt("ff",16), 0);
        public static final int GREEN = Color.rgb(0, Integer.parseInt("bb",16), 0);
        public static final int GREEN_DARK = Color.rgb(0, Integer.parseInt("99",16), 0);

        public static final int BLUE_BRIGHT = Color.rgb(0, 0, Integer.parseInt("ff",16));
        public static final int BLUE = Color.rgb(0, 0, Integer.parseInt("bb",16));
        public static final int BLUE_DARK = Color.rgb(0, 0, Integer.parseInt("99",16));

        public static final int YELLOW_BRIGHT = Color.rgb(Integer.parseInt("ff",16), Integer.parseInt("ff",16), 0);
        public static final int YELLOW = Color.rgb(Integer.parseInt("bb",16), Integer.parseInt("bb",16), 0);
        public static final int YELLOW_DARK = Color.rgb(Integer.parseInt("99",16), Integer.parseInt("99",16), 0);

        public static final int ORANGE_BRIGHT = Color.rgb(Integer.parseInt("ff",16), Integer.parseInt("88",16), 0);
        public static final int ORANGE = Color.rgb(Integer.parseInt("bb",16), Integer.parseInt("77",16), 0);
        public static final int ORANGE_DARK = Color.rgb(Integer.parseInt("99",16), Integer.parseInt("55",16), 0);

        public static final int TEAL_BRIGHT = Color.rgb(0, Integer.parseInt("ff",16), Integer.parseInt("ff",16));
        public static final int TEAL = Color.rgb(0, Integer.parseInt("bb",16), Integer.parseInt("bb",16));
        public static final int TEAL_DARK = Color.rgb(0, Integer.parseInt("99",16), Integer.parseInt("99",16));
    }

}
