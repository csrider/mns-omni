package com.messagenetsystems.evolution2.utilities;

/* StorageUtils class
 * Storage and disk related utilities.
 *
 * Uses a weak reference to context, so it's safe to use in long running processes.
 *
 * Revisions:
 *  2020.05.26      Chris Rider     Created (using EnergyUtils as a template).
 *  2020.06.04      Chris Rider     Added flasher lights app log files to cleanup.
 *  2020.06.22      Chris Rider     Updated/New methods and classes to deal with cleaning out log files better.
 */

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.OmniApplication;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;


public class StorageUtils {
    private final static String TAG = StorageUtils.class.getSimpleName();

    public static final int SPACE_STATE_EXTERNAL_UNKNOWN = -1;
    public static final int SPACE_STATE_EXTERNAL_OK = 1;
    public static final int SPACE_STATE_EXTERNAL_LOW = 2;
    public static final int SPACE_STATE_EXTERNAL_FULL = 3;

    // Logging stuff...
    public static final int LOG_METHOD_LOGCAT = 1;
    public static final int LOG_METHOD_FILELOGGER = 2;
    private static final int LOG_SEVERITY_V = 1;
    private static final int LOG_SEVERITY_D = 2;
    private static final int LOG_SEVERITY_I = 3;
    private static final int LOG_SEVERITY_W = 4;
    private static final int LOG_SEVERITY_E = 5;
    private static int logMethod = LOG_METHOD_LOGCAT;

    // Local stuff...
    private WeakReference<Context> appContextRef;   //since this thread is very long running, we prefer a weak context reference
    private static final AtomicInteger instanceCounter = new AtomicInteger();


    /*============================================================================================*/
    /* Class & Support Methods */

    /** Constructor
     * @param appContext Application context
     * @param logMethod Logging method to use
     */
    public StorageUtils(Context appContext, int logMethod) {
        this.logMethod = logMethod;
        this.appContextRef = new WeakReference<Context>(appContext);

        instanceCounter.incrementAndGet();

        initialize(appContext);
    }

    /** We can use this to tell if this class has been instantiated or not.
     * This is for determining whether we are using an instance or static.
     */
    public static int getInstanceCount() {
        return instanceCounter.get();
    }
    public static boolean getIsInstantiated() {
        if (getInstanceCount() > 0) {
            return true;
        } else {
            return false;
        }
    }

    /** Initialize
     * Note: Not required for static-use cases.
     */
    public void initialize(Context context) {
        final String TAGG = "initialize: ";


    }

    /** Cleanup
     * Call this whenever you're finished using an instance of this class.
     * Note: Not required for static-use cases.
     */
    public void cleanup() {
        if (appContextRef != null) {
            appContextRef.clear();
            appContextRef = null;
        }
    }


    /*============================================================================================*/
    /* Utility Methods */

    /** Get free available bytes of space on the "external" partition.
     * @return Bytes of free/available space
     */
    public static long getAvailableSpace_external() {
        final String TAGG = "getAvailableSpace_external: ";
        long freeBytes = -1;

        try {
            StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
            long bytesAvailable;

            bytesAvailable = stat.getBlockSizeLong() * stat.getAvailableBlocksLong();

            freeBytes = bytesAvailable;
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning: "+Long.toString(freeBytes));
        return freeBytes;
    }

    /** Get free available bytes of space on the "root" partition.
     * @return Bytes of free/available space
     */
    public static long getAvailableSpace_root() {   //TODO: Untested!
        final String TAGG = "getAvailableSpace_root: ";
        long freeBytes = -1;

        try {
            StatFs stat = new StatFs(Environment.getRootDirectory().getPath());
            long bytesAvailable;

            bytesAvailable = stat.getBlockSizeLong() * stat.getAvailableBlocksLong();

            freeBytes = bytesAvailable;
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning: "+Long.toString(freeBytes));
        return freeBytes;
    }

    /** Get free available bytes of space on the "data" partition.
     * @return Bytes of free/available space
     */
    public static long getAvailableSpace_data() {   //TODO: Untested!
        final String TAGG = "getAvailableSpace_data: ";
        long freeBytes = -1;

        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long bytesAvailable;

            bytesAvailable = stat.getBlockSizeLong() * stat.getAvailableBlocksLong();

            freeBytes = bytesAvailable;
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning: "+Long.toString(freeBytes));
        return freeBytes;
    }

    /** Get free available bytes of space on the "download/cache" partition.
     * @return Bytes of free/available space
     */
    public static long getAvailableSpace_cache() {  //TODO: Untested!
        final String TAGG = "getAvailableSpace_cache: ";
        long freeBytes = -1;

        try {
            StatFs stat = new StatFs(Environment.getDownloadCacheDirectory().getPath());
            long bytesAvailable;

            bytesAvailable = stat.getBlockSizeLong() * stat.getAvailableBlocksLong();

            freeBytes = bytesAvailable;
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning: "+Long.toString(freeBytes));
        return freeBytes;
    }

    /** Free-up disk space and remove some files.
     * WARNING: This could take some time (as it's disk-I/O), so be sure to invoke it from a worker thread where necessary!
     * @return Number of bytes that were freed-up
     */
    public static long freeUpSpace_external() {
        final String TAGG = "freeUpSpace_external: ";
        long freedBytes = 0;

        try {
            // Delete old log files (this is probably the main culprit)
            Log.v(TAG, TAGG+"Deleting old log files for main app...");
            freedBytes += deleteAllFilesInDirExceptMostRecentSpecifiedBytes(
                    Environment.getExternalStorageDirectory().getPath() + "/logs_" + String.valueOf(OmniApplication.getAppPackageNameStatic()),
                    Constants.Health.Storage.MIN_TOTAL_BYTES_TO_KEEP_FILES_LOGS_MAIN_APP);

            // Delete old log files (this is probably the main culprit)
            Log.v(TAG, TAGG+"Deleting old log files for flasher light driver app...");
            freedBytes += deleteAllFilesInDirExceptMostRecentSpecifiedBytes(
                    Environment.getExternalStorageDirectory().getPath() + "/logs_" + String.valueOf(Constants.PACKAGE_NAME_FLASHERS),
                    Constants.Health.Storage.MIN_TOTAL_BYTES_TO_KEEP_FILES_LOGS_FLASHERS);

            //TODO: additional packages' log files go here

            // Delete old video files
            Log.v(TAG, TAGG+"Deleting old video files...");
            freedBytes += deleteFilesInDirectoryOlderThan(
                    Environment.getExternalStorageDirectory().getPath() + "/Movies",
                    DatetimeUtils.getMillisecondsInDays(Constants.Health.Storage.MAX_DAYS_KEEP_FILES_VIDEOS));

            // Delete old image files
            Log.v(TAG, TAGG+"Deleting old image files...");
            freedBytes += deleteFilesInDirectoryOlderThan(
                    Environment.getExternalStorageDirectory().getPath() + "/Pictures",
                    DatetimeUtils.getMillisecondsInDays(Constants.Health.Storage.MAX_DAYS_KEEP_FILES_IMAGES));

            // Delete old downloaded files
            Log.v(TAG, TAGG+"Deleting old downloaded files...");
            freedBytes += deleteFilesInDirectoryOlderThan(
                    Environment.getExternalStorageDirectory().getPath() + "/Download",
                    DatetimeUtils.getMillisecondsInDays(Constants.Health.Storage.MAX_DAYS_KEEP_FILES_DOWNLOADS));

            // Delete old screenshot files
            Log.v(TAG, TAGG+"Deleting old screenshot files...");
            freedBytes += deleteFilesInDirectoryOlderThan(
                    Environment.getExternalStorageDirectory().getPath() + "/Screenshots",
                    DatetimeUtils.getMillisecondsInDays(Constants.Health.Storage.MAX_DAYS_KEEP_FILES_SCREENSHOTS));

        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning: "+Long.toString(freedBytes)+" ("+getBytesWithHumanUnit(freedBytes, 3)+")");
        return freedBytes;
    }

    public static long deleteFilesInDirectoryOlderThan(String dirPath, long milliseconds) {
        final String TAGG = "deleteFilesInDirectoryOlderThan: ";
        long freedBytes = 0;
        int deletedFileCounter = 0;

        try {
            final File directory = new File(dirPath);
            if (directory.exists()) {
                final File[] listFiles = directory.listFiles();
                final long nowMS = new Date().getTime();
                final long purgeTimeMS = nowMS - milliseconds;

                logD(TAGG+"Looking for old files to delete in "+dirPath+"...");

                // Looping through all files in the directory...
                for(File listFile : listFiles) {
                    logV(TAGG+"Examining \""+listFile.getName()+"\": modified="+listFile.lastModified()+"ms ("+DatetimeUtils.generateHumanDifference(nowMS, listFile.lastModified())+" ago)");
                    if(listFile.lastModified() < purgeTimeMS) {
                        logD(TAGG+" Deleting \""+listFile.getName()+"\" ("+listFile.lastModified()+") ("+listFile.length()+" bytes)...");
                        freedBytes += listFile.length();
                        if (listFile.delete()) deletedFileCounter++;
                    }
                }
            } else {
                logE(TAGG+"Directory not found!");
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning: "+Long.toString(freedBytes)+" ("+getBytesWithHumanUnit(freedBytes,3)+" across "+deletedFileCounter+" files deleted)");
        return freedBytes;
    }

    /** A decorate-sort-undecorate pattern that speeds up sorting of files by modified date.
     * You just have to feed an instance of this to Arrays.sort() method. */
    static class PairComparableOldestFirst implements Comparable {
        public long t;
        public File f;

        public PairComparableOldestFirst(File file) {
            f = file;
            t = file.lastModified();
        }

        public int compareTo(Object o) {
            long u = ((PairComparableOldestFirst) o).t;
            return t < u ? -1 : t == u ? 0 : 1;
        }
    }
    static class PairComparableNewestFirst implements Comparable {
        public long t;
        public File f;

        public PairComparableNewestFirst(File file) {
            f = file;
            t = file.lastModified();
        }

        public int compareTo(Object o) {
            long u = ((PairComparableNewestFirst) o).t;
            return t < u ? 1 : t == u ? 0 : -1;
        }
    }

    public static long deleteOldestFilesInDirectoryUntilBytesDeleted(String dirPath, long bytesToDelete) {
        final String TAGG = "deleteOldestFilesInDirectoryUntilBytesDeleted: ";
        long deletedBytes = 0;
        int deletedFileCounter = 0;

        try {
            final File directory = new File(dirPath);
            if (directory.exists()) {
                // Obtain the files and then populate an array of (file, timestamp) pairs
                final File[] listFiles = directory.listFiles();
                PairComparableOldestFirst[] pairs = new PairComparableOldestFirst[listFiles.length];
                for (int i = 0; i < listFiles.length; i++) {
                    pairs[i] = new PairComparableOldestFirst(listFiles[i]);
                }

                // Sort those pairs by timestamp
                Arrays.sort(pairs);

                // Take the sorted pairs and extract only the file part, discarding timestamp since we're now done with it
                for (int i = 0; i < listFiles.length; i++) {
                    listFiles[i] = pairs[i].f;
                }

                // Now that we have a sorted file list, let's now go through it and start deleting
                final long nowMS = new Date().getTime();
                for(File listFile : listFiles) {
                    if (listFile.length() <= (bytesToDelete - deletedBytes)) {
                        deletedBytes += listFile.length();
                        logV(TAGG + "Deleting \"" + listFile.getName() + "\" (modified " + DatetimeUtils.generateHumanDifference(nowMS, listFile.lastModified()) + " ago), " + getBytesWithHumanUnit(bytesToDelete - deletedBytes, 3) + " remaining...");
                        //if (listFile.delete()) deletedFileCounter++; //TODO TEST
                    } else {
                        logV(TAGG+"Done!");
                        break;
                    }
                }
            } else {
                logE(TAGG+"Directory not found!");
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning: "+Long.toString(deletedBytes)+" ("+getBytesWithHumanUnit(deletedBytes,3)+" across "+deletedFileCounter+" files deleted)");
        return deletedBytes;
    }

    public static long deleteAllFilesInDirExceptMostRecentSpecifiedBytes(String dirPath, long newestBytesToKeep) {
        final String TAGG = "deleteAllFilesInDirExceptMostRecentSpecifiedBytes: ";
        long deletedBytes = 0;
        int deletedFileCounter = 0;

        try {
            final File directory = new File(dirPath);
            if (directory.exists()) {
                // Obtain the files
                final File[] listFiles = directory.listFiles();

                // Create and populate an array of (file, timestamp) pairs
                PairComparableNewestFirst[] pairs = new PairComparableNewestFirst[listFiles.length];
                for (int i = 0; i < listFiles.length; i++) {
                    pairs[i] = new PairComparableNewestFirst(listFiles[i]);
                }

                // Sort those pairs by timestamp, newest ones first at beginning of array
                Arrays.sort(pairs);

                // Take the sorted pairs and extract only the file part, discarding timestamp since we're now done with it
                for (int i = 0; i < listFiles.length; i++) {
                    listFiles[i] = pairs[i].f;
                }

                // Now that we have a sorted file list, let's now go through it and start deleting
                // We want to skip the first files until we meet newestBytesToKeep value, and then start deleting everything after that
                long accruedBytes = 0;
                final long nowMS = new Date().getTime();
                for(File listFile : listFiles) {
                    accruedBytes += listFile.length();
                    if (accruedBytes > newestBytesToKeep) {
                        //we're now past the number of specified bytes to keep and starting to get into older files, so let's start deleting now
                        logD(TAGG + "Deleting \"" + listFile.getName() + "\" (modified " + DatetimeUtils.generateHumanDifference(nowMS, listFile.lastModified()) + " ago)...");
                        deletedBytes += listFile.length();
                        if (listFile.delete()) deletedFileCounter++;
                    } else {
                        logV(TAGG + "The file \"" + listFile.getName() + "\" (modified " + DatetimeUtils.generateHumanDifference(nowMS, listFile.lastModified()) + " ago) brings us up to "+getBytesWithHumanUnit(accruedBytes, 3)+" (we will keep this one)...");
                    }
                }
            } else {
                logE(TAGG+"Directory not found!");
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning: "+Long.toString(deletedBytes)+" ("+getBytesWithHumanUnit(deletedBytes,3)+" across "+deletedFileCounter+" files deleted)");
        return deletedBytes;
    }


    /*============================================================================================*/
    /* Analysis Methods */

    /** Process the given bytes into a human-readable String format.
     * @param bytes Number of bytes to process
     * @return Human-readable bytes value
     */
    public static String getBytesWithHumanUnit(long bytes, int decimalPlaces) {
        final String TAGG = "getBytesWithHumanUnit("+Long.toString(bytes)+"): ";

        final String unitByte = "B";
        final String unitKiloByte = "KB";   // >3
        final String unitMegaByte = "MB";   // >6
        final String unitGigaByte = "GB";   // >9

        String ret = Long.toString(bytes)+unitByte;

        try {
            int numberOfDigits = (int) (Math.log10(bytes) + 1);     //most efficient/easy method to get number of digits

            int divFactor;
            float bytesPreResult;
            String bytesResult;
            String unitToUse;

            if (numberOfDigits > 9) {
                //gigabytes
                divFactor = 1000*1000*1000;
                unitToUse = unitGigaByte;
            } else if (numberOfDigits > 6) {
                //megabytes
                divFactor = 1000*1000;
                unitToUse = unitMegaByte;
            } else if (numberOfDigits > 3) {
                //kilobytes
                divFactor = 1000;
                unitToUse = unitKiloByte;
            } else {
                //bytes
                divFactor = 1;
                unitToUse = unitByte;
            }

            bytesPreResult = (float)bytes / divFactor;
            if (decimalPlaces > 0) {
                bytesResult = String.format(Locale.US, "%." + decimalPlaces + "f", bytesPreResult);
            } else {
                bytesResult = Integer.toString(Math.round(bytesPreResult));
            }
            ret = bytesResult + unitToUse;
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning: \""+ret+"\"");
        return ret;
    }


    /*============================================================================================*/
    /* Conversion and Translation Methods */

    /** Get a plain-English String for the our custom storage space state value.
     * @param state Value of StorageUtils.SPACE_STATE constants
     * @return English meaning of the constant value
     */
    public static String getEnglish_storageSpaceState(int state) {
        final String TAGG = "getEnglish_storageSpaceState: ";
        String ret = "Unknown";

        try {
            switch (state) {
                case StorageUtils.SPACE_STATE_EXTERNAL_OK:
                    ret = "OK";
                    break;
                case StorageUtils.SPACE_STATE_EXTERNAL_LOW:
                    ret = "Low";
                    break;
                case StorageUtils.SPACE_STATE_EXTERNAL_FULL:
                    ret = "Full";
                    break;
                case StorageUtils.SPACE_STATE_EXTERNAL_UNKNOWN:
                default:
                    ret = "Unknown";
            }
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }

        logV(TAGG+"Returning: \""+ret+"\"");
        return ret;
    }


    /*============================================================================================*/
    /* Logging Methods */

    private static void logV(String tagg) {
        if (getIsInstantiated()) {
            log(LOG_SEVERITY_V, tagg);
        } else {
            logStatic(LOG_SEVERITY_V, tagg);
        }
    }

    private static void logD(String tagg) {
        if (getIsInstantiated()) {
            log(LOG_SEVERITY_D, tagg);
        } else {
            logStatic(LOG_SEVERITY_D, tagg);
        }
    }

    private static void logI(String tagg) {
        if (getIsInstantiated()) {
            log(LOG_SEVERITY_I, tagg);
        } else {
            logStatic(LOG_SEVERITY_I, tagg);
        }
    }

    private static void logW(String tagg) {
        if (getIsInstantiated()) {
            log(LOG_SEVERITY_W, tagg);
        } else {
            logStatic(LOG_SEVERITY_W, tagg);
        }
    }

    private static void logE(String tagg) {
        if (getIsInstantiated()) {
            log(LOG_SEVERITY_E, tagg);
        } else {
            logStatic(LOG_SEVERITY_E, tagg);
        }
    }

    private static void log(int logSeverity, String tagg) {
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
    private static void logStatic(int logSeverity, String tagg) {
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
    }
}
