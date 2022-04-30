package com.messagenetsystems.evolution2.threads;

/* DownloadAsyncTaskTFTP
 * Performs an asynchronous TFTP download.
 * When starting or finishing, it will broadcast updates (see defined constants below).
 *
 * Usage Example:
 *  DownloadAsyncTaskTFTP downloadAsyncTaskTFTP = new DownloadAsyncTaskTFTP();
 *  downloadAsyncTaskTFTP.execute(getApplicationContext(), tftpServerIP, tftpFilename);
 *
 * Revisions:
 *  2019.11.18      Chris Rider     Created (abstracting out ConfigDownloadAsyncTask).
 *  2020.07.24      Chris Rider     Added some cleanup logic.
 */

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;

import com.bosphere.filelogger.FL;

import org.apache.commons.net.tftp.TFTP;
import org.apache.commons.net.tftp.TFTPClient;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.SocketException;
import java.net.UnknownHostException;

public class DownloadAsyncTaskTFTP extends AsyncTask<Object, Object, String> {
    private final String TAG = this.getClass().getSimpleName();

    // Constants..
    public static final String INTENTFILTER_DOWNLOAD_CONFIGURATION = "com.messagenetsystems.evolution2.DownloadAsyncTaskTFTP.intentFilterDownloadConfiguration";
    public static final String INTENTEXTRA_STATUS = "com.messagenetsystems.evolution2.DownloadAsyncTaskTFTP.status";
    public static final String INTENTEXTRA_STATUS_STARTED = "com.messagenetsystems.evolution2.DownloadAsyncTaskTFTP.status.started";
    public static final String INTENTEXTRA_STATUS_FINISHED_OK = "com.messagenetsystems.evolution2.DownloadAsyncTaskTFTP.status.finishedOK";
    public static final String INTENTEXTRA_STATUS_FINISHED_NOTOK = "com.messagenetsystems.evolution2.DownloadAsyncTaskTFTP.status.finishedNotOK";

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
    private WeakReference<Context> appContextRef;
    private int transferMode;
    private TFTPClient tftpClient;
    private int numBytesRead;

    /** Constructor */
    public DownloadAsyncTaskTFTP(Context appContext) {
        Log.v(TAG, "Instantiating.");

        this.logMethod = logMethod;

        this.appContextRef = new WeakReference<Context>(appContext);
        this.transferMode = TFTP.ASCII_MODE;
        this.tftpClient = new TFTPClient();
        this.numBytesRead = 0;
    }


    /*============================================================================================*/
    /* AsyncTask Methods */

    /** Specify what actually happens when this is executed.
     * @param objects An array of Objects passed in .execute(), containing: Context context, String server, String filename
     */
    @Override
    protected String doInBackground(Object[] objects) {
        final String TAGG = "doInBackground: ";

        Context context = (Context) objects[0];
        String remoteHost = objects[1].toString();
        String filename = objects[2].toString();

        logD(TAGG+"remoteHost = \""+remoteHost+"\" | filename = \""+filename+"\"");

        // Validate
        if (remoteHost == null || remoteHost.isEmpty()) {
            //won't be able to download from a host we know nothing about!
            logE(TAGG+"Remote host is not provided properly. Aborting.");
            return null;
        }
        if (filename == null || filename.isEmpty()) {
            //won't be able to download a file we know nothing about!
            logE(TAGG+"Remote file is not provided properly. Aborting.");
            return null;
        }

        // Create a TFTP instance to handle the file transfer
        tftpClient = new TFTPClient();

        // Timeout if a response takes too long
        tftpClient.setDefaultTimeout(10000);

        // Open local socket
        try {
            tftpClient.open();                                                                  //open a socket
            logV(TAGG+"TFTP socket opened.");
        } catch (SocketException e) {
            logE(TAGG+"Could not open a local socket: "+e.getMessage());
            //return -1;
        }

        // Prepare to receive the file
        FileOutputStream output = null;
        File file = new File(context.getCacheDir(), filename);
        logV(TAGG+"Local file-space specified (" + file.getAbsolutePath() + ").");
        try {
            output = new FileOutputStream(file);                                                //open a local file for writing
        } catch (FileNotFoundException e) {
            tftpClient.close();
            logE(TAGG+"Could not open an output stream for writing to the local file space: "+e.getMessage());
            //return -1;
        }

        // Receive the file
        try {
            numBytesRead = tftpClient.receiveFile(filename, transferMode, output, remoteHost);                 //attempt to actually download the file
        } catch (UnknownHostException e) {
            logE(TAGG+"Could not resolve the host: "+e.getMessage());
            //return -1;
        } catch (IOException e) {
            logE(TAGG+"I/O exception occurred while receiving file: "+e.getMessage());
            if(e.getMessage().contains("File not found")) {
                logD(TAGG+"Perhaps specified server is wrong?");
            }
            //return -1;
        }
        finally {
            tftpClient.close();
            try {
                if (output != null) {
                    output.close();
                }
            } catch (IOException e) {
                logE(TAGG+"Could not close local file: "+e.getMessage());
            }
        }

        context = null;

        return null;
    }

    /* Specify what should happen (can be on the UI thread) prior to starting this asynctask */
    @Override
    protected void onPreExecute() {
        final String TAGG = "onPreExecute: ";

        try {
            Intent intent = new Intent(INTENTFILTER_DOWNLOAD_CONFIGURATION);
            intent.putExtra(INTENTEXTRA_STATUS, INTENTEXTRA_STATUS_STARTED);
            appContextRef.get().sendBroadcast(intent);
        } catch (Exception e) {
            logE(TAGG+"Failed to send download status started broadcast: "+e.getMessage());
        }
    }

    /* Specify what should happen (can be on the UI thread) after the async task completes */
    @Override
    protected void onPostExecute(String result) {
        final String TAGG = "onPostExecute: ";

        logV(TAGG+"numBytesRead = "+ numBytesRead +".");

        if (numBytesRead > 0) {
            logI(TAGG+"Data was read.");

            try {
                Intent intent = new Intent(INTENTFILTER_DOWNLOAD_CONFIGURATION);
                intent.putExtra(INTENTEXTRA_STATUS, INTENTEXTRA_STATUS_FINISHED_OK);
                appContextRef.get().sendBroadcast(intent);
            } catch (Exception e) {
                logE(TAGG+"Failed to send broadcast: "+e.getMessage());
            }
        } else {
            logE(TAGG+"Nothing was read!");

            try {
                Intent intent = new Intent(INTENTFILTER_DOWNLOAD_CONFIGURATION);
                intent.putExtra(INTENTEXTRA_STATUS, INTENTEXTRA_STATUS_FINISHED_NOTOK);
                appContextRef.get().sendBroadcast(intent);
            } catch (Exception e) {
                logE(TAGG+"Failed to send download status finished broadcast: "+e.getMessage());
            }
        }

        cancel(true);

        cleanup();
    }

    private void cleanup() {
        if (tftpClient != null) {
            tftpClient.close();
            tftpClient = null;
        }

        if (appContextRef != null) {
            appContextRef.clear();
            appContextRef = null;
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
