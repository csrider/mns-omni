package com.messagenetsystems.evolution2.utilities;

/* NetUtilsHttp
  This is for HTTP protocol layer related tasks.

  You should instantiate this in order to use it.
   Ex. NetUtilsHttp netUtilsHttp = new NetUtilsHttp(getApplicationContext());

  Revisions:
   2019.11.06      Chris Rider     Created.
 */

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class NetUtilsHttp {
    final private String TAG = this.getClass().getSimpleName();

    private Context appContext;

    /** Constructor
     * @param appContext
     */
    public NetUtilsHttp(Context appContext) {
        Log.v(TAG, "Instantiating.");

        this.appContext = appContext;
    }

    /** Use busybox and wget to determine connectivity to a host via HTTP protocol. */
    public boolean hostHttpIsAccessible_busyboxWget(String ipAddress) {
        final String TAGG = "hostHttpIsAccessible_busyboxWget: ";
        boolean ret = false;

        Process process;
        String command;
        BufferedReader stderr;
        String stderrLine;

        try {
            // execute the command
            command = "/system/bin/busybox wget -O - "+ipAddress;
            process = Runtime.getRuntime().exec(command);

            // read the stderr stream to get the status-result of the executed command (stdin gives us HTML content if successful)
            stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            stderrLine = stderr.readLine();
            while (stderrLine != null) {
                if (stderrLine.length() > 0 && stderrLine.contains("100%")) {
                    ret = true;
                    break;
                } else if (stderrLine.length() > 0 && stderrLine.toLowerCase().contains("not found")) {
                    //could be a 404, which might be a server problem, not an Omni problem
                    Log.v(TAG, TAGG+"Test resulted in \"404 or not-found\", but returning true since server is technically available.");
                    ret = true;
                    break;
                } else if (stderrLine.length() > 0 && stderrLine.toLowerCase().contains("connection refused")) {
                    Log.v(TAG, TAGG+"Test resulted in \"connection refused\", but returning true since server is technically available.");
                    ret = true;
                    break;
                } else if (stderrLine.length() > 0 && stderrLine.toLowerCase().contains("no route to host")) {
                    Log.v(TAG, TAGG+"Test resulted in \"no route to host\", which means server is not available.");
                    ret = false;
                    break;
                }
                stderrLine = stderr.readLine();
            }
            Log.d(TAG, TAGG+"Test command result line tested: \""+stderrLine+"\".");

            process.destroy();
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
            ret = false;
        }

        process = null;
        command = null;
        stderr = null;
        stderrLine = null;

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    public boolean doesRemoteFileExist_http(String URLName) {
        return doesRemoteFileExist_http(URLName, 0);
    }
    public boolean doesRemoteFileExist_http(String URLName, int retriesRemaining){
        final String TAGG = "doesRemoteFileExist_http: ";
        Log.v(TAG, TAGG+"Checking \""+URLName+"\" ("+String.valueOf(retriesRemaining)+" retries).");

        final int retryDelayMS = 100;
        boolean ret = false;

        try {
            int responseCode;

            HttpURLConnection.setFollowRedirects(false);
            // note : you may also need
            //        HttpURLConnection.setInstanceFollowRedirects(false)
            HttpURLConnection con = (HttpURLConnection) new URL(URLName).openConnection();
            con.setRequestMethod("HEAD");

            responseCode = con.getResponseCode();
            switch (responseCode) {
                case HttpURLConnection.HTTP_OK:
                    ret = true;
                    break;
                default:
                    Log.i(TAG, TAGG+"Unhandled response code ("+String.valueOf(responseCode)+").");
                    break;
            }

            //ret = (con.getResponseCode() == HttpURLConnection.HTTP_OK);       //OLD WAY
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }

        //if http server returned something not found and there are retries remaining, recurse with decremented retry count
        if (!ret && retriesRemaining > 0) {
            try {
                Thread.sleep(retryDelayMS);
            } catch (InterruptedException e) {
                Log.e(TAG, TAGG+"Exception caught delaying recursion: "+e.getMessage());
            }
            ret = doesRemoteFileExist_http(URLName, retriesRemaining-1);
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret));
        return ret;
    }

    /** Download a file via HTTP, given path (http://domain.com/path/) and file (myfile.txt)
     *  Returns the number of bytes downloaded and read to the local file.
     *  Returns -1 if some error happened or resource could not be accessed.
     *  NOTE: This must be run asynchronously from main UI thread, in a worker thread for example...
     *      new Thread(new Runnable() {
     *          public void run() {
     *          DownloadFile();
     *          }
     *      }).start();
     *
     *  2019.07.10  CSR     Removed check for remote file existence, since there are retries anyway.
     */
    public long downloadFileHTTP(Context context, String strUrlPath, String strUrlFile, int numOfRetries, String destinationPath){
        final String TAGG = "downloadFileHTTP: ";
        long totalBytesRead = 0;
        InputStream inputStream = null;
        int retryDelayMS = 100;
        int retryCounter;
        final String strUrlWhole = strUrlPath+"/"+strUrlFile;

        //if (doesRemoteFileExist_http(strUrlWhole)) {

        try {
            URL url = new URL(strUrlPath + "/" + strUrlFile);
            //InputStream inputStream = url.openStream();         //NOTE: this will throw an I/O error if URL is unavailable

            //routine to try to open URL, and retry if that fails
            for (retryCounter = 0; retryCounter < numOfRetries; retryCounter++) {
                try {
                    inputStream = url.openStream();
                } catch (IOException e) {
                    Log.w(TAG, TAGG + "I/O error accessing network resource (" + e.getMessage() + "). Retrying in " + retryDelayMS + "ms.");
                    Thread.sleep(retryDelayMS);
                    //continue;   //try again
                }
            }

            if (inputStream == null) {
                return -1;  //hit maximum retries and still no resource available, so return error
            }

            DataInputStream dataInputStream = new DataInputStream(inputStream);

            byte[] buffer = new byte[1024];
            int bytesRead = 0;

            //File file = new File(context.getCacheDir(), strUrlFile);
            File file = new File(destinationPath, strUrlFile);
            Log.d(TAG, TAGG + "Local file-space specified (" + file.getAbsolutePath() + ").");
            FileOutputStream fos = new FileOutputStream(file);

            while ((bytesRead = dataInputStream.read(buffer)) > 0) {
                fos.write(buffer, 0, bytesRead);
                // buffer = new byte[153600];
                totalBytesRead += bytesRead;
                // logger.debug("Downloaded {} Kb ", (totalBytesRead / 1024));

                //TODO: put progress publish stuff here?
            }

            Log.d(TAG, TAGG + "Total bytes read = " + totalBytesRead);

            dataInputStream.close();
            inputStream.close();
            fos.close();
        } catch (MalformedURLException mue) {
            Log.e(TAG, TAGG + "Malformed URL error. Aborting.", mue);
            return -1;
        } catch (IOException e) {
            Log.e(TAG, TAGG + "I/O error (" + e.getMessage() + "). Aborting.");
            return -1;
        } catch (SecurityException se) {
            Log.e(TAG, TAGG + "Security error. Aborting.", se);
            return -1;
        } catch (Exception e) {
            Log.e(TAG, TAGG + "General error. Aborting.", e);
            return -1;
        }

        //} else {
        //    Log.w(TAG, TAGG+"File does not seem to exist on server. No download attempted.");
        //}

        Log.v(TAG, TAGG+"Returning "+totalBytesRead);
        return totalBytesRead;
    }
}
