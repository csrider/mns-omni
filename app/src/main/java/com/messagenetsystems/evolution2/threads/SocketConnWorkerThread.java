package com.messagenetsystems.evolution2.threads;

/* SocketConnWorkerThread
 * A background worker-thread to handle socket connections provided by SocketServerThread.
 * Its purpose is to take the client connection passed in, read its data, and do something with it that data in an asynchronous manner, so SocketServer can resume its primary purpose (listening for connections).
 * That "something" is really just to get the request data given to us, and store it in a Room database for received requests.
 *
 * DEV-NOTE...
 *  It's a thread, because it requires no UI thread access.
 *  Consider porting this to AsyncTask, if you need UI thread access?
 *
 * Usage Example (create):
 *  SocketConnWorkerThread socketHandlerRunnableAdmin = new SocketConnWorkerThread(getApplicationContext(), SocketConnWorkerThread.LOG_METHOD_FILELOGGER);
 *
 * Usage Example (passing to a SocketServerThread instance):
 *  SocketServerThread socketServerThread = new SocketServerThread(getApplicationContext(), SocketServerThread.LOG_METHOD_FILELOGGER, SocketServerThread.PORT_HTTP_ADMIN, socketHandlerRunnableAdmin);
 *
 * Usage Example (invocation from SocketServerThread):
 *  socketConnectionHandlerRunnable.run(socket);
 *
 * Revisions:
 *  2019.11.20      Chris Rider     Created (abstracting out ConfigDownloadAsyncTask).
 *  2019.11.21      Chris Rider     Added parsing of JSON string to object and storage to Room database.
 *  2020.02.18      Chris Rider     Added various class instances to support returning pong response data.
 *  2020.05.22      Chris Rider     Added HealthService' energy related items to pong return to server.
 *  2020.06.04      Chris Rider     Now updating certain data at the end of each response, so updated data can go out on next response.
 *                                  Added number of messages (deliverable and in-rotation); shortened pong JSON keys further.
 *  2020.07.25      Chris Rider     Removed ConfigData usage, in favor of SharedPrefsUtils, to try to improve efficiency.
 *  2020.08.21      Chris Rider     Optimized memory: logging INT to BYTE
 */

import android.content.Context;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.OmniApplication;
import com.messagenetsystems.evolution2.databases.receivedRequests.ReceivedRequestDatabaseClient;
import com.messagenetsystems.evolution2.services.DeliveryService;
import com.messagenetsystems.evolution2.services.HealthService;
import com.messagenetsystems.evolution2.services.MainService;
import com.messagenetsystems.evolution2.utilities.EnergyUtils;
import com.messagenetsystems.evolution2.utilities.NetUtils;
import com.messagenetsystems.evolution2.utilities.NetUtils_fromV1;
import com.messagenetsystems.evolution2.utilities.SharedPrefsUtils;
import com.messagenetsystems.evolution2.utilities.SystemUtils;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.net.Socket;


public class SocketConnWorkerThread extends Thread {
    private final String TAG = this.getClass().getSimpleName();

    // Constants..

    // Logging stuff...
    private final byte LOG_SEVERITY_V = 1;
    private final byte LOG_SEVERITY_D = 2;
    private final byte LOG_SEVERITY_I = 3;
    private final byte LOG_SEVERITY_W = 4;
    private final byte LOG_SEVERITY_E = 5;
    private byte logMethod = Constants.LOG_METHOD_LOGCAT;

    // Local stuff...
    private WeakReference<Context> appContextRef;
    private Socket clientSocket;
    private String defaultResponse;

    private OmniApplication omniApplication;
    private SharedPrefsUtils sharedPrefsUtils;
    private SystemUtils systemUtils;
    private NetUtils netUtils;

    //private ApiData apiDataAdministrative;

    //private ReceivedRequestDatabase receivedRequestDatabase;


    /** Constructor */
    public SocketConnWorkerThread(Context appContext, byte logMethod, Socket clientSocket) {
        logV("Instantiating.");

        this.logMethod = logMethod;
        this.appContextRef = new WeakReference<Context>(appContext);
        this.clientSocket = clientSocket;
        this.defaultResponse = "Nothing useful received!";  //TODO: modify/stringify?

        try {
            this.omniApplication = ((OmniApplication) appContext.getApplicationContext());
        } catch (Exception e) {
            logE("Exception caught instantiating "+TAG+": "+e.getMessage());
            return;
        }

        this.sharedPrefsUtils = new SharedPrefsUtils(appContext, logMethod);
        this.systemUtils = new SystemUtils(appContext, logMethod);
        this.netUtils = new NetUtils(appContext, logMethod);

        //this.apiData = new ApiData(appContext, ApiData.LOG_METHOD_FILELOGGER);

        //this.receivedRequestDatabase = Room.databaseBuilder(appContext, ReceivedRequestDatabase.class, "db_receivedRequests").build();
    }

    /** Call this to stop the socket from listening and release resources. */
    public void cleanup() {
        final String TAGG = "cleanup: ";

        try {
            this.clientSocket.close();
            //this.apiDataAdministrative.cleanup();
        } catch (Exception e) {
            logW(TAGG+"Exception caught: "+e.getMessage());
        }

        //this.appContextRef = null;
        this.clientSocket = null;
        //this.apiDataAdministrative = null;
        //this.receivedRequestDatabase = null;

        this.omniApplication = null;

        if (this.sharedPrefsUtils != null) {
            this.sharedPrefsUtils.cleanup();
            this.sharedPrefsUtils = null;
        }

        if (this.systemUtils != null) {
            this.systemUtils.cleanup();
            this.systemUtils = null;
        }

        if (this.netUtils != null) {
            this.netUtils.cleanup();
            this.netUtils = null;
        }
    }


    /*============================================================================================*/
    /* Thread Methods */

    /** Main runnable routine... executes once whenever the initialized thread is commanded to start running with .execute() method call.
     * Intended to just run once and be done - no loop required. */
    @Override
    public void run() {
        String TAGG = "run";
        logV(TAGG+": Invoked.");

        long lineCounter = 1;

        BufferedReader dataInFromSocket;        //data coming in from socket-client
        BufferedWriter dataOutToSocket;         //data going out to socket-client

        StringBuilder responseBuilder;

        String readLine, readChar;

        String line1 = "";
        String requestMethod = "";
        String requestPath = "";
        String requestProtocol = "";
        String userAgent = "";
        String contentType = "";
        long contentLength = 0;
        String body = "";

        String jsonContent = "";
        JSONObject jsonObject = null;

        boolean respondWithPong = false;
        String pongResponse = "pong";

        try {
            logD(TAGG + "Thread started for \"" + clientSocket.getRemoteSocketAddress().toString().split("/")[1] + "\" with priority "+Thread.currentThread().getPriority()+" ("+Thread.MIN_PRIORITY+"-"+Thread.MAX_PRIORITY+").");

            // Initialize some local stuff...
            dataInFromSocket = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
            dataOutToSocket = new BufferedWriter(new OutputStreamWriter(this.clientSocket.getOutputStream()));

            // Let's start reading the data coming in
            readLine = dataInFromSocket.readLine();
            if (readLine == null) {
                responseBuilder = new StringBuilder(this.defaultResponse);
            } else {
                responseBuilder = new StringBuilder("Received the following lines:\n");

                while(readLine != null) {
                    // Since we entered/are-still-in this loop, we are at a valid line, so let's look at it
                    logV(TAGG + "Raw request line #" + String.valueOf(lineCounter) + ": \"" + readLine + "\".");

                    responseBuilder.append(readLine);
                    responseBuilder.append("\n");

                    //determine request line #1 stuff
                    if (lineCounter == 1) {
                        logD(TAGG+"Parsed first line: \"" + readLine + "\".");

                        requestMethod = readLine.split(" ")[0];
                        logD(TAGG+"Parsed request method: \"" + requestMethod + "\".");

                        requestPath = readLine.split(" ")[1];
                        logD(TAGG+"Parsed request path: \"" + requestPath + "\".");

                        requestProtocol = readLine.split(" ")[2];
                        logD(TAGG+"Parsed request protocol: \"" + requestProtocol + "\".");

                        if (requestMethod.contains("GET") && requestPath.contains("/ping")) {
                            //check password validity
                            if (requestPath.contains("password="+omniApplication.getDevicePassword())) {
                                //for authenticated requestors, we send more details about the system
                                pongResponse = constructPongReplyStatusJSONString();
                            } else {
                                logD(TAGG+"Unhandled requestPath value ("+requestPath+").");
                            }

                            //platform is requesting a pong, so send it with device status
                            respondWithPong = true;
                            break;
                        }

                        // Read the next line...
                        readLine = dataInFromSocket.readLine();
                        lineCounter++;
                        continue;   //jump to next iteration/line
                    }

                    //determine user-agent
                    if (readLine.contains("User-Agent:")) {
                        userAgent = readLine.split(": ")[1];
                        logD(TAGG+"Parsed User-Agent: \"" + userAgent + "\".");

                        // Read the next line...
                        readLine = dataInFromSocket.readLine();
                        lineCounter++;
                        continue;   //jump to next iteration/line
                    }

                    //determine content-type
                    if (readLine.contains("Content-Type:")) {
                        contentType = readLine.split(": ")[1];
                        logD(TAGG+"Parsed Content-type: \"" + contentType + "\".");

                        // Read the next line...
                        readLine = dataInFromSocket.readLine();
                        lineCounter++;
                        continue;   //jump to next iteration/line
                    }

                    //determine content-length
                    if (readLine.contains("Content-Length:")) {
                        contentLength = Integer.parseInt(readLine.split(": ")[1]);
                        logD(TAGG+"Parsed Content-Length: " + contentLength + ".");

                        // Read the next line...
                        readLine = dataInFromSocket.readLine();
                        lineCounter++;
                        continue;   //jump to next iteration/line
                    }

                    //once we reach the header-body separator, figure out what we're dealing with and parse accordingly...
                    if (readLine.isEmpty()) {
                        logV(TAGG + "Header-Body separator encountered. Parsing request body...");

                        //TODO: instead of a loop, maybe just a simple assignment from readline??
                        for (int j = 0; j < contentLength; j++) {
                            readChar = Character.toString((char) dataInFromSocket.read());
                            body = body.concat(readChar);
                        }
                        logD(TAGG + "Parsed body: \"" + body + "\".");

                        responseBuilder.append(body);

                        break;
                    }

                    // Read the next line...
                    readLine = dataInFromSocket.readLine();
                    lineCounter++;

                }//end while


            }

            // Save the received request to database
            /*
            ReceivedRequest receivedRequest = new ReceivedRequest();
            receivedRequest.setRequestMethod(requestMethod);
            receivedRequest.setRequestPath(requestPath);
            receivedRequest.setRequestProtocol(requestProtocol);
            receivedRequest.setRequestUserAgent(userAgent);
            receivedRequest.setRequestContentType(contentType);
            receivedRequest.setRequestBody(body);
            Date date = new Date();
            receivedRequest.setCreatedAt(date);
            receivedRequest.setModifiedAt(date);
            receivedRequestDatabase.receivedRequestDao().addReceivedRequest(receivedRequest);
            */

            // Save the received request to database...
            // Since this class is already a background thread, we can do this directly in a blocking manner to the rest of this thread.
            ReceivedRequestDatabaseClient.getInstance(appContextRef.get()).addRecord(appContextRef.get(), requestMethod, requestPath, requestProtocol, userAgent, contentType, body);

            /* EXPERIMENTAL
            AppExecutors.getInstance().diskIO().execute(new Runnable() {
                @Override
                public void run() {
                    ReceivedRequestDatabase.receivedRequestDao().addReceivedRequest(receivedRequest);
                }
            });
            */

            // Send back the finished response to the requester
            if (respondWithPong) {
                dataOutToSocket.write("Response: "+pongResponse);
            } else {
                dataOutToSocket.write("Response: "+responseBuilder.toString());
            }
            dataOutToSocket.newLine();
            dataOutToSocket.flush();

            // Close the connection with the client
            dataOutToSocket.close(); //just to make the server stop trying to send us stuff

            // Trigger an update to certain data to get returned on next pong response
            // We do this at the end (even though it won't result in super-timely data), so we don't hold up the response.
            omniApplication.updateAppVersions(appContextRef.get().getApplicationContext());

        } catch (Exception e) {
            logE(TAGG + "Exception caught: "+e.getMessage());
        }

        logD(TAGG + "Thread ending.");
        cleanup();
    }


    /*============================================================================================*/
    /* Supporting Methods */

    private String constructPongReplyStatusJSONString() {
        final String TAGG = "constructPongReplyStatusJSON: ";

        String ret;

        try {
            /*
            String ipMode = NetworkUtils.getCurrentIpMethod_wifi(context);
            */
            int batteryMilliAmpState = systemUtils.getBatteryNetCurrentNow() / 1000;

            // WARNING!!!!
            // If adding data to this log, add to end, since some scripts depend on fields being in certain locations!
            ret = "{" +
                    "\"rec\":\"" + String.valueOf(sharedPrefsUtils.getStringValueFor(SharedPrefsUtils.spKeyName_thisDeviceRecno, null)) + "\"" +
                    ",\"msgs\":\"" + DeliveryService.omniMessageUUIDsToRotate.size() + "\"" +
                    ",\"upHr\":" + String.valueOf(systemUtils.getSystemUptime_hours()) + "" +
                    ",\"appHr\":" + String.valueOf(omniApplication.getAppRunningHours()) + "" +
                    ",\"vMA\":\"" + omniApplication.getAppVersion() + "\"" +
                    ",\"vFL\":\"" + omniApplication.getAppVersion_flashers() + "\"" +
                    /*
                    ",\"vWD\":\"" + omniApplication.getAppVersion_watchdog() + "\"" +
                    ",\"vUD\":\"" + omniApplication.getAppVersion_updater() + "\"" +
                    ",\"vWW\":\"" + omniApplication.getAppVersion_watcher() + "\"" +
                    */
                    ",\"ipAcq\":\"" + netUtils.getCurrentIpMethod_activeInterface() + "\"" +
                    ",\"ipAdr\":\"" + netUtils.getDeviceIpAddressAsString() + "\"" +
                    ",\"NIC\":\"" + netUtils.getActiveNIC() + "\"" +
                    ",\"dBm\":\"" + netUtils.getWifiStrength_dBm() +
                    ",\"CPU\":\"" + String.format("%.0f%%", systemUtils.readUsageCPU() * 100) + "\"" +
                    ",\"heapAv\":\"" + String.valueOf(systemUtils.getAppHeapAvailable_MB()) + "MB\"" +
                    ",\"pwr\":\"" + EnergyUtils.getEnglish_chargePlugState(HealthService.energy_rawPowerSupplyWhichConnected) + "\"" +
                    ",\"chging\":\"" + Boolean.toString(HealthService.energy_isBatteryCharging) + "\"" +
                    ",\"chgLvl\":\"" + HealthService.energy_hrBatteryPercent + "\"" +
                    ",\"mA\":\"" + HealthService.energy_rawMilliAmpsAtBattery + "\"" +
                    ",\"mv\":\"" + HealthService.energy_rawMilliVoltage + "\"" +
                    ",\"batt\":\"" + EnergyUtils.getEnglish_batteryHealthState(HealthService.energy_derivedBatteryHealthCondition) + "\"" +
                    ",\"freeEx\":\"" + HealthService.storage_hrAvailableBytes_external + "\"" +
                    /* ",\"chgTime\":\"" + systemUtils.getBatteryTimeRemaining(batteryMilliAmpState) + "mins\"" + */
                    "}";
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+ e.getMessage());
            ret = "(failed to construct device stats)";
        }

        logV(TAGG+"Returning: "+ret);
        return ret;
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
    private void log(byte logSeverity, String tagg) {
        switch (logMethod) {
            case Constants.LOG_METHOD_LOGCAT:
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
            case Constants.LOG_METHOD_FILELOGGER:
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
