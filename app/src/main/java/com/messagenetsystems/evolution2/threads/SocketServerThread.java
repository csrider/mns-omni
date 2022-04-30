package com.messagenetsystems.evolution2.threads;

/* SocketServerThread
 * Serves socket connection requests.
 * Hands connections off to worker thread instances as they come in (SocketConnWorkerThread).
 * Once executed, it will remain running and waiting for requests on the socket, until you stop it or tell it otherwise.
 *
 * DEV-NOTE...
 *  It's a thread, because it requires no UI thread access.
 *
 * Usage Example (declare, create, configure, and run):
 *  SocketServerThread socketServerThread;
 *  socketServerThread = new SocketServerThread(getApplicationContext(), SocketServerThread.LOG_METHOD_FILELOGGER, SocketServerThread.PORT_HTTP_ADMIN);
 *  socketServerThread.setSocketReceiveBufferSize(64000);                                           //optional, defaults to 64000
 *  socketServerThread.setSocketMaxBacklog(50);                                                     //optional, defaults to 50
 *  socketServerThread.setSocketConnectionTimeImportance(SocketServerThread.IMPORTANCE_MID);        //optional, defaults to SocketServerThread.IMPORTANCE_MID
 *  socketServerThread.setSocketLatencyImportance(SocketServerThread.IMPORTANCE_MAX);               //optional, defaults to SocketServerThread.IMPORTANCE_MAX
 *  socketServerThread.setSocketBandwidthImportance(SocketServerThread.IMPORTANCE_MIN);             //optional, defaults to SocketServerThread.IMPORTANCE_MIN
 *  socketServerThread.start();                                                                     //starts the thread loop, and allows SocketServer to begin listening for connections
 *
 * Usage Example (close the socket, stop the thread-loop, and free up resources):
 *  socketServerThread.cleanup();
 *
 * Usage Example (pause listening - may be easily resumed later)
 *  socketServerThread.pauseListening();
 *
 * Usage Example (resume listening)
 *  socketServerThread.resumeListening();
 *
 * Revisions:
 *  2019.11.19-20   Chris Rider     Created (abstracting out ConfigDownloadAsyncTask).
 *  2020.08.21      Chris Rider     Optimized memory: logging INT to BYTE
 */

import android.content.Context;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;


public class SocketServerThread extends Thread {
    private final String TAG = this.getClass().getSimpleName();

    // Constants..
    public static final int PORT_HTTP_NORMAL = 8080;
    public static final int IMPORTANCE_MIN = 0;
    public static final int IMPORTANCE_MID = 1;
    public static final int IMPORTANCE_MAX = 2;

    // Logging stuff...
    private final byte LOG_SEVERITY_V = 1;
    private final byte LOG_SEVERITY_D = 2;
    private final byte LOG_SEVERITY_I = 3;
    private final byte LOG_SEVERITY_W = 4;
    private final byte LOG_SEVERITY_E = 5;
    private byte logMethod = Constants.LOG_METHOD_LOGCAT;

    // Local stuff...
    private WeakReference<Context> appContextRef;   //since this thread is very long running, we prefer a weak context reference
    private ServerSocket serverSocket;

    private volatile boolean isStopRequested;       //flag to set/check for the thread to interrupt itself
    private volatile boolean isThreadRunning;
    private volatile boolean isSocketListening;
    private volatile boolean pauseListening;
    private int portToListenOn;
    private int socketReceiveBufferSize;
    private int socketMaxBacklog;                   //maximum length of the queue of incoming connections before socket rejects incoming requests
    private int socketPerfPrefConnectionTime;       //an int expressing the relative importance of a short connection time
    private int socketPerfPrefLatency;              //an int expressing the relative importance of low latency
    private int socketPerfPrefBandwidth;            //an int expressing the relative importance of high bandwidth


    /** Constructor */
    public SocketServerThread(Context appContext, byte logMethod, int port) {
        Log.v(TAG, "Instantiating.");

        this.logMethod = logMethod;

        this.appContextRef = new WeakReference<Context>(appContext);

        try {
            this.serverSocket = new ServerSocket();
        } catch (Exception e) {
            logE("Exception caught creating a ServerSocket instance: "+e.getMessage());
        }

        this.isStopRequested = false;
        this.isThreadRunning = false;
        this.isSocketListening = false;
        this.pauseListening = false;
        this.portToListenOn = port | PORT_HTTP_NORMAL;
        this.socketReceiveBufferSize = 64000;
        this.socketMaxBacklog = 50;                                 //maximum length of the queue of incoming connections before socket rejects incoming requests
        this.socketPerfPrefConnectionTime = IMPORTANCE_MID;         //relative importance of short connection time
        this.socketPerfPrefLatency = IMPORTANCE_MAX;                //relative importance of low latency
        this.socketPerfPrefBandwidth = IMPORTANCE_MIN;              //relative importance of high bandwidth
    }


    /*============================================================================================*/
    /* Thread Methods */

    /** Main runnable routine... executes once whenever the initialized thread is commanded to start running with .execute() method call */
    @Override
    public void run() {
        final String TAGG = "run: ";
        logV(TAGG+"Invoked.");

        Socket socket;

        long pid = Thread.currentThread().getId();
        logD(TAGG+"Thread starting as process ID #"+ pid);

        // Initialize and setup our SocketServer instance
        try {
            initializeSocketServerAsConfigured();
        } catch (Exception e) {
            logE(TAGG+"Exception caught! Thread will not start.");
            Thread.currentThread().interrupt();
        }

        // As long as our thread is supposed to be running...
        // Actually listen for connections on the socket (the .accept method below is blocking)
        while (!Thread.currentThread().isInterrupted()) {

            isThreadRunning = true;

            // Either sleep a short bit or allow socket to listen for connections...
            if (pauseListening) {
                logI(TAGG + "Listening is paused. Thread will continue to run, but SocketServer won't listen for connections.");
                isSocketListening = false;

                // Do a short delay to help prevent the thread loop from eating cycles
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logW(TAGG + "Exception caught trying to sleep: " + e.getMessage());
                }
            } else {
                logI(TAGG + "Listening for a socket connection.");
                isSocketListening = true;

                try {
                    // Listen for a connection to the socket.
                    // Once a connection is made, it returns and the execution of our code can continue.
                    // NOTE: This blocks execution of all following code until that happens!
                    socket = serverSocket.accept();     //HOLD HERE UNTIL CONNECTION IS MADE

                    // Once a connection is made...
                    socket.setKeepAlive(true);          //not sure if necessary - works with or without
                    //socket.setTcpNoDelay(true);       //(not necessary; don't seem to hurt) disable Nagle's algorithm (a buffering scheme that delays things) by setting TCP_NODELAY on the socket
                    //socket.setTrafficClass(0x10);     //IPTOS_LOWDELAY (probably only obeyed by nearest router... maybe not at all)

                    // Pass the socket connection to our worker thread to handle the communication, and start it (starts a new thread and runs it there)
                    Log.d(TAG, TAGG + "A socket connection was received from \"" + socket.getRemoteSocketAddress().toString().split("/")[1] + "\". Passing it to worker thread for processing, so SocketServer may resume listening for connections.");
                    new SocketConnWorkerThread(appContextRef.get(), logMethod, socket).start();

                } catch (NullPointerException e) {
                    // This can happen if MainService dies (taking context reference with it) before this loop breaks
                    // So, let's make sure that's not what's happening (we can depend on this flag to be set by .cleanup() which is called on MainService destruction)...
                    if (!isStopRequested) {
                        logW(TAGG + "MainService's context reference has gone AWOL. Context is required for this thread to run; shutting down!");
                        Thread.currentThread().interrupt();
                    }
                } catch (SocketTimeoutException e) {
                    logW(TAGG+"SocketTimeoutException caught: "+ e.getMessage());
                } catch (SocketException e) {
                    //this can happen if .close() is called from another thread (a way to cancel the .accept() hold)
                    logI(TAGG+"SocketException caught: "+ e.getMessage());
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    logE(TAGG+"IOException caught: "+ e.getMessage());
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    logE(TAGG+"Exception caught: "+ e.getMessage());
                    Thread.currentThread().interrupt();
                }
            }

            // this is the end of the loop-iteration, so check whether we will stop or continue
            if (Thread.currentThread().isInterrupted()) {
                logI(TAGG+"Thread will now stop.");
                isThreadRunning = false;
                isSocketListening = false;
            }
            if (isStopRequested) {
                logI(TAGG+"Thread has been requested to stop and will now do so.");
                isThreadRunning = false;
                break;
            }
        }
    }


    /*============================================================================================*/
    /* Supporting Methods */

    private void initializeSocketServerAsConfigured() throws Exception {
        final String TAGG = "initializeSocketServerAsConfigured: ";

        serverSocket.setReceiveBufferSize(socketReceiveBufferSize);
        serverSocket.setPerformancePreferences(socketPerfPrefConnectionTime, socketPerfPrefLatency, socketPerfPrefBandwidth);   //NOTE: must be done before binding to an address happens
        serverSocket.setReuseAddress(true); //true allows the socket to be bound even though a previous connection is in a timeout state

        serverSocket.bind(new InetSocketAddress(PORT_HTTP_NORMAL), socketMaxBacklog);

        logD(TAGG+"serverSocket instantiated...\n" +
                "Local Port:     " + serverSocket.getLocalPort() +"\n" +
                "Rx Buffer Size: " + serverSocket.getReceiveBufferSize());
    }

    /** Call this to cancel the SocketServer.accept() hold (stop listening).
     * This does not destroy the SocketServer, just cancels the hold and allows execution of the rest of the thread-loop iteration.
     * @throws Exception The SocketServer.close() call failed, so hold may still be active.
     */
    private void stopListening() throws Exception {
        final String TAGG = "stopListening: ";

        if (serverSocket != null) {
            // This will cause serverSocket to throw a SocketException which will be caught in run() above and set the interrupt flag
            serverSocket.close();

            isSocketListening = false;
        }
    }

    /** Call this to pause SocketServer listening.
     * This essentially just sets the pause flag (which prevents .accept from being called), and
     * also cancels any currently listening SocketServer.
     */
    public void pauseListening() {
        final String TAGG = "pauseListening: ";

        try {
            // First, set the pause flag, so the blocking SocketServer.accept() method can't be called
            pauseListening = true;

            // Next, if the blocking SocketServer.accept() method is active, we have to cancel its hold to allow loop iteration
            if (isSocketListening) {
                try {
                    stopListening();
                } catch (Exception e) {
                    logW(TAGG+"Exception caught (SocketServer.accept hold may still be active?): "+e.getMessage());
                }
            }

            // Note: From here on, with the pause-flag set, the thread loop won't allow .accept() to be called, but thread loop will continue to run every second
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }

    /** Call this to resume paused listening.
     * This essentially just resets the pause flag (which allows .accept to be called)
     */
    public void resumeListening() {
        pauseListening = false;
    }

    /** Call this to stop the socket from listening, terminate the loop, and release resources. */
    public void cleanup() {
        final String TAGG = "cleanup: ";

        try {
            this.isStopRequested = true;

            // Next, if the blocking SocketServer.accept() method is active, we have to cancel its hold to allow loop iteration
            if (isSocketListening) {
                try {
                    stopListening();
                } catch (Exception e) {
                    logW(TAGG+"Exception caught (SocketServer.accept hold may still be active?): "+e.getMessage());
                }
            }

            // Note: At this point, the thread-loop should break on its own
        } catch (Exception e) {
            logE(TAGG+"Exception caught calling stopListening(): "+e.getMessage());
        }

        this.appContextRef = null;
        this.serverSocket = null;
    }


    /*============================================================================================*/
    /* Getter/Setter Methods */

    public boolean isThreadRunning() {
        return this.isThreadRunning;
    }

    public boolean isSocketListening() {
        return this.isSocketListening;
    }

    public boolean isSocketListeningPaused() {
        return this.pauseListening;
    }

    public void setSocketReceiveBufferSize(int size) {
        this.socketReceiveBufferSize = size;
    }

    public void setSocketMaxBacklog(int maxConnections) {
        this.socketMaxBacklog = maxConnections;
    }

    public void setSocketConnectionTimeImportance(int importance) {
        this.socketPerfPrefConnectionTime = importance;
    }

    public void setSocketLatencyImportance(int importance) {
        this.socketPerfPrefLatency = importance;
    }

    public void setSocketBandwidthImportance(int importance) {
        this.socketPerfPrefBandwidth = importance;
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
