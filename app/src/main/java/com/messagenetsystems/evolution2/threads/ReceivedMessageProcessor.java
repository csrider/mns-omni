package com.messagenetsystems.evolution2.threads;

/* ReceivedMessageProcessor
 * Periodically reads the received_messages Room-DB and processes whatever is there.
 * This processing entails the following:
 *  A) Finds new received_messages DB records and updates their status flag and processing timestamps.
 *  B) Forwards (copies) new received_messages DB records to messages DB, as needed.
 *  C) Initiates regular cleanup of old data in the received_messages DB.
 *
 * This is intended to work with received message data that has already been processed from
 * received_requests and copied to received_messages by ReceivedRequestProcessor! It is then up to
 * other processes to do whatever is needed with the message data!
 *
 * DEV-NOTE...
 *  It's a thread, because it requires no UI thread access.
 *
 * Usage Example (declare, create, configure, and run):
 *  ReceivedMessageProcessor receivedMessageProcessor;
 *  receivedMessageProcessor = new ReceivedMessageProcessor(getApplicationContext(), ReceivedMessageProcessor.LOG_METHOD_FILELOGGER);
 *  receivedMessageProcessor.start();
 *
 * Usage Example (stop the thread-loop and free up resources):
 *  receivedMessageProcessor.cleanup();
 *
 * Usage Example (pause processing - may be easily resumed later)
 *  receivedMessageProcessor.pauseProcessing();
 *
 * Usage Example (resume processing)
 *  receivedMessageProcessor.resumeProcessing();
 *
 * Revisions:
 *  2019.12.11      Chris Rider     Created (used ReceivedRequestForwarder as a template).
 *  2020.04.20      Chris Rider     Addition of original request's created-at datetime, so we know when a message was originally actually received.
 *  2020.08.21      Chris Rider     Optimized memory: logging INT to BYTE; migrated sleep to doSleep method; new run-every-X-iterations logic.
 *  2020.09.28      Chris Rider     Fixed theoretical potential for uncaught overflow in loop counter.
 */

import android.content.Context;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.databases.messages.Message;
import com.messagenetsystems.evolution2.databases.messages.MessageDatabaseClient;
import com.messagenetsystems.evolution2.databases.receivedMessages.ReceivedMessage;
import com.messagenetsystems.evolution2.databases.receivedMessages.ReceivedMessageDatabaseClient;
import com.messagenetsystems.evolution2.databases.receivedRequests.ReceivedRequest;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.List;


public class ReceivedMessageProcessor extends Thread {
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

    private ReceivedMessageDatabaseClient receivedMessageDatabaseClient;
    private MessageDatabaseClient messageDatabaseClient;


    /** Constructor */
    public ReceivedMessageProcessor(Context appContext, byte logMethod) {
        Log.v(TAG, "Instantiating.");

        this.logMethod = logMethod;

        this.appContextRef = new WeakReference<Context>(appContext);

        this.isStopRequested = false;
        this.isThreadRunning = false;
        this.pauseProcessing = false;

        this.activeProcessingSleepDuration = 1000;  //TODO: stringify
        this.pausedProcessingSleepDuration = 5000;  //TODO: stringify

        this.loopIterationCounter = 1;

        this.alternateRunIteration_tidyDB = 10;     //every X iterations, run the tidy-DB routine (some things just don't need run every iteration of the loop)

        try {
            this.receivedMessageDatabaseClient = ReceivedMessageDatabaseClient.getInstance(appContext);
            this.messageDatabaseClient = MessageDatabaseClient.getInstance(appContext);
        } catch (Exception e) {
            logE("Exception caught getting database client instance, aborting: "+e.getMessage());
            this.receivedMessageDatabaseClient = null;
            this.messageDatabaseClient = null;
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

        List<ReceivedMessage> dbResults;
        ReceivedMessage receivedMessage;

        long pid = Thread.currentThread().getId();
        logI(TAGG+"Thread starting as process ID #"+ pid);

        // Verify database client instance (and thus access to data through its methods)
        if (receivedMessageDatabaseClient == null
                || messageDatabaseClient == null) {
            logE(TAGG+"No available database client instance(s), aborting.");
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
                    dbResults = receivedMessageDatabaseClient.findUnprocessedReceivedMessages(appContextRef.get());
                    logV(TAGG + "Found " + dbResults.size() + " unprocessed results.");
                    for (int i = 0; i < dbResults.size(); i++) {
                        receivedMessage = dbResults.get(i);
                        logV(TAGG + " #" + i + ") " + receivedMessage.getMessageUUID() + " " + receivedMessage.getMessageJson());

                        processReceivedMessage(receivedMessage);
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

    /** Do the work of reading and potentially copying ReceivedMessage records from the received_messages Room-DB to the messages Room-DB.
     * Basically, all this really does is checks if it exists in the messages Room-DB, and adds it if it doesn't.
     * @param receivedMessage A ReceivedMessage record to process.
     */
    private void processReceivedMessage(ReceivedMessage receivedMessage) {
        final String TAGG = "processReceivedMessage: ";

        try {
            // Figure out if ReceivedMessage already exists in Message Room-DB and add it if not...
            if (messageDatabaseClient.doesMessageExist(appContextRef.get(), receivedMessage.getMessageUUID())) {
                // UUID value already exists, will not insert, so as to avoid duplicates
                // Note: This is a normal situation and nothing to worry about.
                logD(TAGG+"A record with the same UUID already exists in the messages database. Not adding.");
            } else {
                logD(TAGG+"Received message does not exist in messages database. Adding it there now...");

                // Add the received message as a new record to messages DB...
                // Note: status will automatically be saved as Message.STATUS_NEW by addRecord!
                messageDatabaseClient.addRecord(appContextRef.get(),
                        receivedMessage.getMessageUUID(),
                        receivedMessage.getMessageJson(),
                        Message.STATUS_NEW,
                        receivedMessage.getReceivedAt());   //passing along the original ReceivedRequest.created_at datetime, so we can know when a message was originally received

                // Update existing record in received_messages database to flag that we've forwarded the data...
                Date now = new Date();
                receivedMessage.setStatus(ReceivedMessage.STATUS_FORWARDED);
                receivedMessage.setRequestProcessedAt(now);
                receivedMessage.setRequestProcessedAtMs(String.valueOf(now.getTime()));
                receivedMessageDatabaseClient.updateRecord(appContextRef.get(), receivedMessage);
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }

    /** Do the work of tidying-up the database. */
    private void tidyDatabase() {
        final String TAGG = "tidyDatabase: ";

        try {
            // Have the database clean out any older stuff that we know has been taken care of.
            // Remember, we keep it around here for a little bit just in case it's ever needed again for some strange reason.
            //TODO

            // Finally, have the database clean out any potentially uncaught leftovers...
            receivedMessageDatabaseClient.deleteAll_olderThan(appContextRef.get(), Constants.Database.SQLITE_DTMOD_OLDERTHAN_1WEEK);
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
    /* Subclasses */



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
