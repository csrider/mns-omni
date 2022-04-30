package com.messagenetsystems.evolution2.threads;

/* ReceivedRequestProcessor
 * Periodically reads the received_requests Room-DB and processes whatever is there.
 * This processing entails the following:
 *  A) Finds new request records and updates their status flag and processing timestamps.
 *  B) Forwards (copies) new request records to an appropriate database (e.g. messages or configurations), as needed.
 *  C) Initiates regular database cleanup of old data in the received_requests Room-DB.
 *
 * NOTE: It is then up to other processes to do whatever is needed with the forwarded/copied data!
 *
 * DEV-NOTE...
 *  It's a thread, because it requires no UI thread access.
 *
 * Usage Example (declare, create, configure, and run):
 *  ReceivedRequestProcessor receivedRequestProcessor;
 *  receivedRequestProcessor = new ReceivedRequestProcessor(getApplicationContext(), ReceivedRequestProcessor.LOG_METHOD_FILELOGGER);
 *  receivedRequestProcessor.start();
 *
 * Usage Example (stop the thread-loop and free up resources):
 *  receivedRequestProcessor.cleanup();
 *
 * Usage Example (pause processing - may be easily resumed later)
 *  receivedRequestProcessor.pauseProcessing();
 *
 * Usage Example (resume processing)
 *  receivedRequestProcessor.resumeProcessing();
 *
 * Revisions:
 *  2019.11.25      Chris Rider     Created (used SocketServerThread as a template).
 *  2019.11.26      Chris Rider     Added functionality processing of records.
 *  2019.12.11      Chris Rider     Accommodated received_requests DB refactoring.
 *                                  Brought over (deprecated) ReceivedRequestForwarder duties into this to help keep things simple to understand.
 *  2020.02.18      Chris Rider     Implemented support for MNS API v1 (legacy server messages) -- still a bit to do, but it's a start.
 *  2020.04.16      Chris Rider     Added ability to process "stopscrollingmessage" legacy Banner command, process the ZX record and banish corresponding message from all delivery forever.
 *  2020.04.20      Chris Rider     Added copy of ReceivedRequest created_at field to ReceivedMessage received_at field, so we can pass along datetime when message was originally received.
 *  2020.08.21      Chris Rider     Optimized memory: logging INT to BYTE; increased interval to 2 seconds; migrated sleep to doSleep method; implemented better status constants updating; new run-every-X-iterations logic.
 *  2020.09.24      Chris Rider     Fixed bug where delete all older records was not working due to wrong date format and logical comparison mistake in SQL/DAO.
 *                                  Fixed logging annoyance where ping/pongs were logged as an error due to no content-type.
 *  2020.09.28      Chris Rider     Fixed theoretical potential for uncaught overflow in loop counter.
 */

import android.content.Context;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.OmniApplication;
import com.messagenetsystems.evolution2.databases.receivedMessages.ReceivedMessageDatabaseClient;
import com.messagenetsystems.evolution2.databases.receivedRequests.ReceivedRequest;
import com.messagenetsystems.evolution2.databases.receivedRequests.ReceivedRequestDatabaseClient;
import com.messagenetsystems.evolution2.services.MainService;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;


public class ReceivedRequestProcessor extends Thread {
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
    private WeakReference<Context> appContextRef;   //since this thread is very long running, we prefer a weak context reference

    private volatile boolean isStopRequested;       //flag to set/check for the thread to interrupt itself
    private volatile boolean isThreadRunning;       //just a status flag
    private volatile boolean pauseProcessing;       //flag to set if you want to pause processing work (may double as a kind of "is paused" flag)

    private int activeProcessingSleepDuration;      //duration (in milliseconds) to sleep during active processing (to help ensure CPU cycles aren't eaten like crazy)
    private int pausedProcessingSleepDuration;      //duration (in milliseconds) to sleep during paused processing (to help ensure CPU cycles aren't eaten like crazy)

    private long loopIterationCounter;

    private int alternateRunIteration_tidyDB;       //every X iterations, run the tidy-DB routine (some things just don't need run every iteration of the loop)

    private ReceivedRequestDatabaseClient receivedRequestDatabaseClient;
    private ReceivedMessageDatabaseClient receivedMessageDatabaseClient;
    //TODO received-configuration DB client


    /** Constructor */
    public ReceivedRequestProcessor(Context appContext, byte logMethod) {
        Log.v(TAG, "Instantiating.");

        this.logMethod = logMethod;

        this.appContextRef = new WeakReference<Context>(appContext);

        this.isStopRequested = false;
        this.isThreadRunning = false;
        this.pauseProcessing = false;

        this.activeProcessingSleepDuration = 2000;  //TODO: stringify
        this.pausedProcessingSleepDuration = 5000;  //TODO: stringify

        this.loopIterationCounter = 1;

        this.alternateRunIteration_tidyDB = 10;     //every X iterations, run the tidy-DB routine (some things just don't need run every iteration of the loop)

        try {
            this.receivedRequestDatabaseClient = ReceivedRequestDatabaseClient.getInstance(appContext);
            this.receivedMessageDatabaseClient = ReceivedMessageDatabaseClient.getInstance(appContext);
            //TODO received-configuration DB client
            //TODO received-bad-request DB client
        } catch (Exception e) {
            logE("Exception caught getting database client instance, aborting: "+e.getMessage());
            this.receivedRequestDatabaseClient = null;
            this.receivedMessageDatabaseClient = null;
            //TODO received-configuration DB client
            //TODO received-bad-request DB client
        }
    }


    /*============================================================================================*/
    /* Thread Methods */

    /** Main runnable routine... executes once whenever the initialized thread is commanded to start running with .start() or .execute() method call.
     * Remember that .start() implicitly spawns a thread and calls .execute() to invoke this run() method.
     * If you directly call .execute(), this run() method will invoke on the same thread you call it from. */
    @Override
    public void run() {
        final String TAGG = "run: ";
        logV(TAGG+"Invoked.");

        List<ReceivedRequest> dbResults;
        ReceivedRequest receivedRequest;

        logD(TAGG + "Thread started with priority "+Thread.currentThread().getPriority()+" ("+Thread.MIN_PRIORITY+"-"+Thread.MAX_PRIORITY+").");

        // Verify database client instance (and thus access to data through its methods)
        if (receivedRequestDatabaseClient == null) {
            logE(TAGG+"No available database client instance, aborting.");
            return;
        }

        // As long as our thread is supposed to be running...
        while (!Thread.currentThread().isInterrupted()) {

            // Our thread has started or is still running
            isThreadRunning = true;

            // Either do nothing (if paused) or allow work to happen (if not paused)...
            if (pauseProcessing) {
                // Do a short delay to help prevent the thread loop from eating cycles
                doSleep(pausedProcessingSleepDuration);

                logD(TAGG + "(iteration #"+loopIterationCounter+") Processing is paused. Thread continuing to run, but no work is occurring.");
            } else {
                // Do a short delay to help prevent the thread loop from eating cycles
                doSleep(activeProcessingSleepDuration);

                logV(TAGG + "(iteration #"+loopIterationCounter+") Processing...");

                try {
                    ////////////////////////////////////////////////////////////////////////////////
                    // DO THE BULK OF THE ACTUAL WORK HERE...

                    // Find unprocessed (new) requests in received_requests Room-database...
                    // FYI: That is defined by not having any processed-at timestamp data yet.
                    dbResults = receivedRequestDatabaseClient.findUnprocessedReceivedRequests(appContextRef.get());
                    logV(TAGG + "Found " + dbResults.size() + " unprocessed results.");
                    for (int i = 0; i < dbResults.size(); i++) {
                        receivedRequest = dbResults.get(i);
                        logV(TAGG + " #" + i + ") " + receivedRequest.getRequestPath() + " " + receivedRequest.getRequestBody());

                        processReceivedRequest(receivedRequest);
                    }

                    // Short time for things to stabilize in the DB world, just to be safe...
                    doSleep(100);

                    // Tidy-up the database (this obeys every X iterations as defined, as it does not need to run on every single iteration)...
                    if (loopIterationCounter % alternateRunIteration_tidyDB == 0) {
                        tidyDatabase();
                    }

                    // END THE BULK OF THE ACTUAL WORK HERE...
                    ////////////////////////////////////////////////////////////////////////////////
                } catch (NullPointerException npe) {
                    // This can happen if MainService dies (taking context reference with it) before this loop breaks
                    // So, let's make sure that's not what's happening (we can depend on this flag to be set by .cleanup() which is called on MainService destruction)...
                    if (!isStopRequested) {
                        logW(TAGG + "MainService's context reference has gone AWOL. Context is required for this thread to run; shutting down!");
                        Thread.currentThread().interrupt();
                    }
                }
            }

            try {
                if (loopIterationCounter + 1 < Long.MAX_VALUE)
                    loopIterationCounter++;
                else
                    loopIterationCounter = 1;
            } catch (Exception e) {
                logW(TAGG+"Exception caught incrementing loop counter. Resetting to 1: "+e.getMessage());
                loopIterationCounter = 1;
            }

            // this is the end of the loop-iteration, so check whether we will stop or continue
            if (Thread.currentThread().isInterrupted()) {
                logI(TAGG+"Thread will now stop.");
                isThreadRunning = false;
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

    private void doSleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            logE("doSleep: Exception caught: "+e.getMessage());
        }
    }

    /** Call this to pause processing.
     * This essentially just sets the pause flag (which prevents any work being done).
     */
    public void pauseProcessing() {
        this.pauseProcessing = true;
    }

    /** Call this to resume paused processing.
     * This essentially just resets the pause flag (which allows work to be done).
     */
    public void resumeProcessing() {
        this.pauseProcessing = false;
    }

    /** Call this to terminate the loop and release resources. */
    public void cleanup() {
        final String TAGG = "cleanup: ";

        try {
            // Flag the thread-loop to break
            this.isStopRequested = true;

        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        this.appContextRef = null;
    }

    /** Do the work of processing a request (keeps the loop above neat and tidy).
     * This examines the record, forwards as needed, and updates some fields (e.g. status, dates)
     * @param receivedRequest ReceivedRequest object to process.
     * @return Status value to return (ReceivedRequest.STATUS_*)
     */
    private byte processReceivedRequest(ReceivedRequest receivedRequest) {
        final String TAGG = "processReceivedRequest: ";
        byte receivedRequestStatusToSet = ReceivedRequest.STATUS_UNKNOWN;

        try {
            // If the request is a valid type, process it
            logV(TAGG+"Request's content type = \""+receivedRequest.getRequestContentType()+"\"");
            if ((receivedRequest.getRequestContentType().equalsIgnoreCase(ReceivedRequest.CONTENT_TYPE_TYPE_TEXT+"/"+ReceivedRequest.CONTENT_TYPE_SUBTYPE_JSON))
                    || (receivedRequest.getRequestContentType().equalsIgnoreCase(ReceivedRequest.CONTENT_TYPE_TYPE_APPLICATION+"/"+ReceivedRequest.CONTENT_TYPE_SUBTYPE_JSON))) {

                // Determine which API the request is for (defined by the path) and act appropriately
                if (receivedRequest.getRequestPath().equalsIgnoreCase("/config")) {
                    logD(TAGG+"Config-API.");
                    copyRxReqToRxConfig(receivedRequest);
                    receivedRequestStatusToSet = ReceivedRequest.STATUS_FORWARDED;
                } else if (receivedRequest.getRequestPath().equalsIgnoreCase("/message")) {
                    logD(TAGG + "ReceivedMessage-API.");
                    copyRxReqToRxMsg(receivedRequest);
                    receivedRequestStatusToSet = ReceivedRequest.STATUS_FORWARDED;
                } else {
                    // We could be operating under old MessageNet ecosystem
                    try {
                        OmniApplication omniApplication = ((OmniApplication) appContextRef.get());
                        if (omniApplication.getEcosystem() == OmniApplication.ECOSYSTEM_MESSAGENET_CONNECTIONS_V1) {
                            /* TODO: Additional legacy commands go here? e.g. stop, clear, etc.  */
                            if (receivedRequest.getRequestBody().contains("\"bannerpurpose\":\"updateseq\"")) {
                                logI(TAGG + "Old API, and requestBody contains \"'bannerpurpose':'updateseq'\".");

                                //TODO (handle bannerpurpose updateseq)
                                receivedRequestStatusToSet = ReceivedRequest.STATUS_PROCESSED;
                            }
                            else if (receivedRequest.getRequestBody().contains("\"bannerpurpose\":\"clearsign\"")) {
                                logI(TAGG + "Old API, and requestBody contains \"'bannerpurpose':'clearsign'\".");
                                // This is received when the server is certain there should be no messages delivering on the device.
                                // NOTE: This may be (redundantly) followed by a "stopscrollingmessage" request, as well.

                                //TODO (handle bannerpurpose clearsign)
                                //TODO DEV-NOTE: In quick testing, the server sends this, even if there are multiple active msgs and you only close one. WTF?!?! -- investigate the banner node for Omni
                                receivedRequestStatusToSet = ReceivedRequest.STATUS_PROCESSED;
                            }
                            else if (receivedRequest.getRequestBody().contains("\"bannerpurpose\":\"stopscrollingmessage\"")) {
                                logI(TAGG + "Old API, and requestBody contains \"'bannerpurpose':'stopscrollingmessage'\".");
                                //DEV-NOTE: For now, may be best to ignore clearsign above, and just use ZX recno (dtsec is too hard to obtain and send from server) to lookup msg to stop delivering

                                // Parse ZX record number
                                // Example request body:   "{"password":"511","bannerpurpose":"stopscrollingmessage","recno_zx":"196"}"
                                int recno = Integer.parseInt(receivedRequest.getRequestBody().split("\"recno_zx\":\"")[1].split("\"")[0]);

                                // Find matching record in MainService.omniMessages_deliverable
                                // Goal will be to remove the matching record from that RAM object and have it propagate automatically back toward omniRawMessages and MessageDatabase
                                try {
                                    //MainService.omniMessages_deliverable.updateOmniMessage(this.omniMessageToDeliver);
                                    MainService.omniMessages_deliverable.removeOmniMessage_byBannerRecnoZX(recno);
                                    receivedRequestStatusToSet = ReceivedRequest.STATUS_PROCESSED;
                                } catch (Exception e) {
                                    logE(TAGG+"Exception caught: "+e.getMessage());
                                    receivedRequestStatusToSet = ReceivedRequest.STATUS_PROCESSING_ERROR;
                                }

                            }
                            else if (receivedRequest.getRequestBody().contains("\"bannerpurpose\":\"") && receivedRequest.getRequestBody().contains("bannermessage")) {
                                logI(TAGG + "Old API, and requestBody contains \"bannerpurpose\" and \"bannermessage\", treating as message.");
                                copyRxReqToRxMsg(receivedRequest);
                                receivedRequestStatusToSet = ReceivedRequest.STATUS_FORWARDED;
                            }
                            else {
                                logV(TAGG+"Old API, but requestBody contains unhandled data, doing nothing.");
                                receivedRequestStatusToSet = ReceivedRequest.STATUS_UNKNOWN;
                            }
                        } else {
                            logV(TAGG+"Non-old API, but unhandled requestPath ("+receivedRequest.getRequestPath()+").");
                            receivedRequestStatusToSet = ReceivedRequest.STATUS_UNKNOWN;
                        }
                    } catch (Exception e) {
                        logE(TAGG+"Exception caught: "+e.getMessage());
                        receivedRequestStatusToSet = ReceivedRequest.STATUS_PROCESSING_ERROR;
                    }
                }

            } else {
                if (receivedRequest.getRequestPath() != null
                        && receivedRequest.getRequestPath().contains("/ping?")) {
                    //ping so we've already taken care of it and ignore it here and now
                } else {
                    logW(TAGG + "Unhandled Content-Type (\"" + receivedRequest.getRequestContentType() + "\").");
                    //TODO Forward to received_bad_requests DB
                    //receivedRequestStatusToSet = ReceivedRequest.STATUS_FORWARDED;
                    receivedRequestStatusToSet = ReceivedRequest.STATUS_UNKNOWN;
                }
            }

            // Issue the job to update the received_requests record's "processed" and status fields
            Date now = new Date();
            receivedRequest.setStatus(receivedRequestStatusToSet);
            receivedRequest.setRequestProcessedAt(now);
            receivedRequest.setRequestProcessedAtMs(String.valueOf(now.getTime()));
            receivedRequestDatabaseClient.updateRecord(appContextRef.get(), receivedRequest);

        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning \""+String.valueOf(receivedRequestStatusToSet)+"\".");
        return receivedRequestStatusToSet;
    }

    /** Do the work of copying the request to the received_messages database.
     * @param receivedRequest ReceivedRequest object to process.
     */
    private void copyRxReqToRxMsg(ReceivedRequest receivedRequest) {
        final String TAGG = "copyRxReqToRxMsg: ";

        try {
            // Get the data we need to forward...
            String messageJson = receivedRequest.getRequestBody();

            // Insert that data to the received_messages database...
            // Note: the addReceivedMessage method takes care of creating everything else the new records needs
            //  - automatically generates a random UUID for the new message
            //  - automatically sets the new record's status to ReceivedMessage.STATUS_NEW
            receivedMessageDatabaseClient.addRecord(appContextRef.get(), messageJson, receivedRequest.getCreatedAt());
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }

    /** TODO Do the work of copying the request to the received_configurations database.
     * @param receivedRequest ReceivedRequest object to process.
     */
    private void copyRxReqToRxConfig(ReceivedRequest receivedRequest) {
        final String TAGG = "copyRxReqToRxConfig: ";

        try {
            logW(TAGG+"DEV-NOTE: Not finished yet!");   //TODO
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }

    /** Tidy-up the received_requests Room database. */
    private void tidyDatabase() {
        final String TAGG = "tidyDatabase: ";

        try {
            logV(TAGG+"Tidying up database...");

            // Have the database clean out any older stuff that we know has been taken care of by the forwarding process.
            // Remember, we keep it around here for a little bit just in case it's ever needed again for some strange reason.
            //receivedRequestDatabaseClient.deleteAllForwarded_olderThan(appContextRef.get(), Constants.Database.SQLITE_DTMOD_OLDERTHAN_1DAY);
            receivedRequestDatabaseClient.deleteAllForwarded_olderThan(appContextRef.get(), Constants.Database.SQLITE_DTMOD_OLDERTHAN_1HOUR); //TESTING

            // Finally, have the database clean out any potentially uncaught leftovers...
            //receivedRequestDatabaseClient.deleteAll_olderThan(appContextRef.get(), Constants.Database.SQLITE_DTMOD_OLDERTHAN_1WEEK);
            //receivedRequestDatabaseClient.deleteAll_olderThan(appContextRef.get(), Constants.Database.SQLITE_DTMOD_OLDERTHAN_1DAY); //TESTING
            Calendar cal = Calendar.getInstance();
            cal.setTime(new Date());
            cal.add(Calendar.DAY_OF_MONTH, -7);
            String olderThanDateString = new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime());
            logV(TAGG+"Deleting all records created before: "+olderThanDateString);
            receivedRequestDatabaseClient.deleteAll_olderThan(appContextRef.get(), olderThanDateString); //TESTING
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }


    /*============================================================================================*/
    /* Getter/Setter Methods */

    public boolean isThreadRunning() {
        return this.isThreadRunning;
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
