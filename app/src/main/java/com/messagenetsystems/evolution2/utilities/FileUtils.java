package com.messagenetsystems.evolution2.utilities;

/* FileUtils
  File related tasks.
  These are intended to be for local, on-device files.

  Revisions:
   2019.11.08      Chris Rider     Created.
 */

import android.content.Context;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;

import com.bosphere.filelogger.FL;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;


public class FileUtils {
    private final String TAG = this.getClass().getSimpleName();

    public static final int FILE_PATH_EXTERNAL_STORAGE = 1;

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
    private Context appContext;

    /** Constructor
     * @param appContext Application context
     * @param logMethod Logging method to use
     */
    public FileUtils(Context appContext, int logMethod) {
        this.appContext = appContext;
        this.logMethod = logMethod;
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


    /*============================================================================================*/
    /* Check-existence Methods */

    // Simply check whether file exists.
    // Several overloads are provided for you to use in any way you need.
    public boolean doesFileExist(int filePathOption, String fileName) {
        final String TAGG = "doesFileExist("+filePathOption+",\""+fileName+"\"): ";
        boolean ret = false;

        try {
            File filePath;

            switch (filePathOption) {
                case FILE_PATH_EXTERNAL_STORAGE:
                    filePath = Environment.getExternalStorageDirectory();
                    break;
                default:
                    logE(TAGG+"Unhandled filePathOption. Defaulting to FILE_PATH_EXTERNAL_STORAGE.");
                    filePath = Environment.getExternalStorageDirectory();
                    break;
            }
            logD(TAGG+"Filepath will be: "+String.valueOf(filePath.getAbsolutePath()));

            File file = new File(filePath, fileName);
            ret = doesFileExist(file);
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logD(TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }
    public boolean doesFileExist(String filePath, String fileName) {
        final String TAGG = "doesFileExist(\""+filePath+"\",\""+fileName+"\"): ";
        boolean ret = false;

        try {
            File file = new File(filePath, fileName);
            ret = doesFileExist(file);
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logD(TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }
    public boolean doesFileExist(@NonNull File file) {
        final String TAGG = "doesFileExist("+file.getAbsolutePath()+"): ";
        boolean ret = false;

        try {
            if (file.exists()) {
                ret = true;
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logD(TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }


    /*============================================================================================*/
    /* File-object Methods */

    /** Get a File object for the external storage directory.
     * Use this in cases where you don't want to include Environment in your code.
     * @return File object for the external storage directory.
     */
    public File getFileObjectForExternalStorageDir() {
        final String TAGG = "getFileObjectForExternalStorageDir: ";
        File ret = null;

        try {
            ret = Environment.getExternalStorageDirectory();
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        if (ret != null) {
            logD(TAGG + "Returning File object for directory " + ret.getAbsolutePath() + ".");
        }
        return ret;
    }

    /** Get a File object for the cache directory.
     * Use this in cases where you don't want to include Environment in your code.
     * @return File object for the cache directory.
     */
    public File getFileObjectForCacheDir() {
        final String TAGG = "getFileObjectForCacheDir: ";
        File ret = null;

        try {
            ret = this.appContext.getCacheDir();
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        if (ret != null) {
            logD(TAGG + "Returning File object for directory " + ret.getAbsolutePath() + ".");
        }
        return ret;
    }

    /** Get a File object for the specified path.
     * @param path  Directory (including its absolute path) to get a File object for
     * @return File object for the specified path
     */
    public File getFileObjectForPath(String path) {
        final String TAGG = "getFileObjectForPath: ";
        File ret = null;

        try {
            ret = new File(path);
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        if (ret != null) {
            logD(TAGG + "Returning File object for directory " + ret.getAbsolutePath() + ".");
        }
        return ret;
    }

    /** Get a File object for the specified file path and name.
     * @param fileDir   Directory of the file
     * @param fileName  Name of the file
     * @return File object or null
     */
    public File getFileObjectForFile(File fileDir, String fileName) {
        final String TAGG = "getFileObjectForFile: ";
        File ret = null;

        try {
            ret = new File(fileDir, fileName);
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        if (ret != null) {
            logD(TAGG + "Returning File object for " + ret.getAbsolutePath() + ".");
        }
        return ret;
    }


    /*============================================================================================*/
    /* File-reading Methods */

    // Read a plain-text file and return its contents in various ways...
    // Several overloads and return types are provided for you to use in any way you need.
    public String readTextFile(@NonNull File file) {
        final String TAGG = "readTextFile: ";
        String ret = "";

        try {
            // Open an input stream for the file represented by the provided File object
            InputStream fileInputStream = new FileInputStream(file);

            // Get the raw text in the file and save it to our return value
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte buf[] = new byte[1024];
            int len;

            while ((len = fileInputStream.read(buf)) != -1) {
                outputStream.write(buf, 0,len);
            }

            outputStream.close();
            fileInputStream.close();

            ret = outputStream.toString();
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logD(TAGG+"Returning...\n"+String.valueOf(ret)+".");
        return ret;
    }
}
