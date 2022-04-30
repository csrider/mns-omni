package com.messagenetsystems.evolution2.threads;

/* MessageRawDataProcessor
 * Processes and synchronizes data between DB ("messages" database) and RAM (MainService.omniRawMessages)...
 *  - When there's new stuff in DB (e.g. receipt of new message data), we need to load RAM with it.
 *      [messages DB record]  ----(add/update)--> [MainService.omniRawMessages]
 *  - When there's nothing in DB that corresponds to RAM, we need to remove from RAM.
 *      [messages DB record] <--(delete)--------  [MainService.omniRawMessages]
 *  - Perform housekeeping (tidy/cleanup) of old/flagged records in "messages" database.
 *      MessageDatabaseClient.deleteAll_withStatus(Message.STATUS_HOUESKEEP_DELETE)
 *
 * DEV-NOTES...
 *  It's a thread, because it requires no UI thread access.
 *  This thread is limited only to the above tasks.. for loading up deliverable-messages, refer to MessageDeliverableProcessor!
 *  Remember that the DB only stores raw (JSON-string) message data (since it's only primitive typed).
 *
 * Usage Example (declare, create, configure, and run):
 *  MessageRawDataProcessor messageRawDataProcessor;
 *  messageRawDataProcessor = new MessageRawDataProcessor(getApplicationContext(), MessageRawDataProcessor.LOG_METHOD_FILELOGGER);
 *  messageRawDataProcessor.start();
 *
 * Usage Example (stop the thread-loop and free up resources):
 *  messageRawDataProcessor.cleanup();
 *
 * Usage Example (pause processing - may be easily resumed later)
 *  messageRawDataProcessor.pauseProcessing();
 *
 * Usage Example (resume processing)
 *  messageRawDataProcessor.resumeProcessing();
 *
 * Revisions:
 *  2019.12.03      Chris Rider     Created (used TEMPLATE_THREAD as a template).
 *  2019.12.05      Chris Rider     Revisited the purpose of this thread and defined its duties more clearly.
 *  2019.12.06      Chris Rider     Renamed from MessageGovernorThread to MessageRawDataProcessor to more accurately reflect what it will be doing.
 *  2019.12.09      Chris Rider     Developing routine to parse message-JSON based on ecosystem and then copy it to RAM somehow.
 *                                  Moved instantiation/startup from MainService to MessageService, added Android-Message/Handling for interthread communication.
 *  2019.12.11      Chris Rider     Added support for new status field (to initially help with synchronization between disk and RAM).
 *                                  Added housekeeping to keep the database tidy (initially just delete any status-flagged records).
 *                                  No longer need to deal with received_messages DB, as we now have ReceivedMessageProcessor to do that for us.
 *  2019.12.17      Chris Rider     Added logic to update status for records sent to RAM; now removing records from RAM (overall now starting to actually "sync").
 *  2019.12.20      Chris Rider     Bringing in send-Android-message to MainService Android-message handler capability.
 *  2020.01.02      Chris Rider     Added comments, and refactored class-name to MessageRawDataProcessor to more clearly indicate its purpose.
 *  2020.01.03      Chris Rider     Migrated from MainService Handler/Message stuff to LocalBroadcast methodology --no longer need to instantiate with Handler that's in MainService!
 *  2020.01.20      Chris Rider     Refactored to utilize MainService-based RAM structure, instead of MessageService-based RAM structure; no longer need Android-Message Handler stuff.
 *  2020.01.22      Chris Rider     Added support for metadata field.
 *  2020.01.31      Chris Rider     Cleanup and comments and stuff to be up-to-date with actual latest state of the code.
 *  2020.04.21      Chris Rider     Made DB-read and raw-msg population use sorted received-at datetime, so the order is oldest -> newest in the omniRawMessages (and subsequent) list.
 *  2020.05.07-08   Chris Rider     Updated calls to isExpired() to support improved behavior.
 *  2020.09.28      Chris Rider     Fixed theoretical potential for uncaught overflow in loop counter.
 */

import android.content.Context;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.OmniApplication;
import com.messagenetsystems.evolution2.databases.messages.Message;
import com.messagenetsystems.evolution2.databases.messages.MessageDatabaseClient;
import com.messagenetsystems.evolution2.databases.receivedMessages.ReceivedMessage;
import com.messagenetsystems.evolution2.models.OmniMessage;
import com.messagenetsystems.evolution2.models.OmniRawMessage;
import com.messagenetsystems.evolution2.models.OmniRawMessages;
import com.messagenetsystems.evolution2.services.MainService;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.List;
import java.util.UUID;


public class MessageRawDataProcessor extends Thread {
    private final String TAG = this.getClass().getSimpleName();

    // Constants..


    // Logging stuff...
    private final int LOG_SEVERITY_V = 1;
    private final int LOG_SEVERITY_D = 2;
    private final int LOG_SEVERITY_I = 3;
    private final int LOG_SEVERITY_W = 4;
    private final int LOG_SEVERITY_E = 5;
    private int logMethod = Constants.LOG_METHOD_LOGCAT;

    // Local stuff...
    private WeakReference<Context> appContextRef;       //since this thread is very long running, we prefer a weak context reference
    private OmniApplication omniApplication;

    private volatile boolean isStopRequested;           //flag to set/check for the thread to interrupt itself
    private volatile boolean isThreadRunning;           //just a status flag
    private volatile boolean pauseProcessing;           //flag to set if you want to pause processing work (may double as a kind of "is paused" flag)

    private int activeProcessingSleepDuration;          //duration (in milliseconds) to sleep during active processing (to help ensure CPU cycles aren't eaten like crazy)
    private int pausedProcessingSleepDuration;          //duration (in milliseconds) to sleep during paused processing (to help ensure CPU cycles aren't eaten like crazy)

    private long loopIterationCounter;

    private MessageDatabaseClient messageDatabaseClient;


    /** Constructor */
    public MessageRawDataProcessor(Context appContext, int logMethod) {
        Log.v(TAG, "Instantiating.");

        this.logMethod = logMethod;

        this.appContextRef = new WeakReference<Context>(appContext);

        try {
            this.omniApplication = ((OmniApplication) appContext);
        } catch (Exception e) {
            logE("Exception caught instantiating "+TAG+": "+e.getMessage());
            return;
        }

        // Initialize values
        this.isStopRequested = false;
        this.isThreadRunning = false;
        this.pauseProcessing = false;
        this.activeProcessingSleepDuration = 1000;
        this.pausedProcessingSleepDuration = 1000;
        this.loopIterationCounter = 1;

        // Prepare database access
        try {
            this.messageDatabaseClient = MessageDatabaseClient.getInstance(appContext);
        } catch (Exception e) {
            logE("Exception caught getting database client instance, aborting: "+e.getMessage());
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

        List<ReceivedMessage> dbResults_receivedMessages;
        ReceivedMessage receivedMessage;

        List<Message> dbResults_messages;
        Message message_fromDB;

        OmniMessage omniMessage = new OmniMessage(appContextRef.get(), logMethod);
        int actionCount;

        long pid = Thread.currentThread().getId();
        logI(TAGG+"Thread starting as process ID #"+ pid);

        if (messageDatabaseClient == null) {
            logE(TAGG+"No available database client instance, aborting.");
            return;
        }

        // As long as our thread is supposed to be running...
        while (!Thread.currentThread().isInterrupted()) {

            //if (loopIterationCounter > 10) break;     //FOR TESTING THREAD MONITORING AND RESTART

            // Our thread has started or is still running
            isThreadRunning = true;

            // Either do nothing (if paused) or allow work to happen (if not paused)...
            logV(TAGG+"-------- Iteration #"+loopIterationCounter+" ------------------------");
            if (pauseProcessing) {
                // Do a short delay to help prevent the thread loop from eating cycles
                try {
                    Thread.sleep(pausedProcessingSleepDuration);
                } catch (InterruptedException e) {
                    logW(TAGG + "Exception caught trying to sleep during pause: " + e.getMessage());
                }

                logD(TAGG + "Processing is paused. Thread continuing to run, but no work is occurring.");
            } else {
                // Do a short delay to help prevent the thread loop from eating cycles
                try {
                    Thread.sleep(activeProcessingSleepDuration);
                } catch (InterruptedException e) {
                    logW(TAGG + "Exception caught trying to sleep: " + e.getMessage());
                }

                try {
                    ////////////////////////////////////////////////////////////////////////////////
                    // DO THE BULK OF THE ACTUAL WORK HERE...

                    // RULES:
                    // There should be no messages in RAM that aren't in DB... (DB is authoritative, as far as existence of records goes)

                    // Tidy up the database so we only work with relevant message records, first
                    messageDatabaseClient.deleteAll_withStatus(appContextRef.get(), Message.STATUS_HOUSEKEEP_DELETE);
                    messageDatabaseClient.deleteAll_olderThan(appContextRef.get(), Constants.Database.SQLITE_DTMOD_OLDERTHAN_1DAY);
                    dbResults_messages = messageDatabaseClient.findAllRecords(appContextRef.get());
                    logV(TAGG + "Read DB for deletion of expired messages: messageDatabaseClient found " + dbResults_messages.size() + " results.");
                    actionCount = 0;
                    for (int i = 0; i < dbResults_messages.size(); i++) {
                        message_fromDB = dbResults_messages.get(i);

                        // Extract and model raw message data as an OmniMessage object so we can more easily work with the data
                        omniMessage.initWithRawData(omniApplication.getEcosystem(), convertDBMsgToOmniRawMsg(message_fromDB));

                        // Determine if message is expired (needs removed and not processed if so)
                        if (omniMessage.isExpired(OmniMessage.EXPIRATION_CALC_METHOD_RELATIVE_DURATION_FROM_DELIVERY, false, appContextRef.get())) {
                            removeRawMessageFromRAM(message_fromDB);
                            messageDatabaseClient.deleteRecord(appContextRef.get(), message_fromDB);
                            actionCount++;
                        }
                    }
                    logV(TAGG+" "+actionCount+" expired message(s) deleted from MainService.omniRawMessages list.");


                    // Sync existence of message records...
                    // Database is authoritative over RAM.
                    // First loop adds records from DB to RAM.
                    // Second loop removes records from RAM where none exist in database.
                    //dbResults_messages = messageDatabaseClient.findAllRecords(appContextRef.get());
                    dbResults_messages = messageDatabaseClient.findAllRecords_sortedOrderReceivedAscending(appContextRef.get());
                    logV(TAGG + "Read DB for RAM existence authority: messageDatabaseClient found " + dbResults_messages.size() + " results.");
                    if (dbResults_messages.size() == 0) {
                        if (MainService.omniRawMessages.size() != 0) {
                            MainService.omniRawMessages.clear();
                            logV(TAGG + " Cleared MainService.omniRawMessages.");
                        }
                    } else {
                        actionCount = 0;
                        for (int i = 0; i < dbResults_messages.size(); i++) {
                            message_fromDB = dbResults_messages.get(i);
                            logV(TAGG + " #" + i + ") " + message_fromDB.getMsgUUID() + "\n" +
                                    "          \"" + message_fromDB.getMsgJSON() + "\"\n" +
                                    "          \"" + message_fromDB.getMetaJSON() + "\""
                                    );

                            // Ensure it's added to RAM (the routine will avoid duplicates)
                            logV(TAGG + " Sending DB message (" + message_fromDB.getMsgUUID() + ") to MainService RAM for possible inclusion...");
                            addRawMessageToRAM(message_fromDB);

                            // Update DB record with status flag to indicate we copied it to RAM
                            messageDatabaseClient.updateStatusFor(appContextRef.get(), message_fromDB.getMsgUUID(), Message.STATUS_COPIED_TO_RAM);
                        }
                        logV(TAGG+" "+actionCount+" messages ensured to be sent to RAM.");
                        actionCount = 0;
                        for (int i = 0; i < MainService.omniRawMessages.size(); i++) {
                            OmniRawMessage omniRawMessage = MainService.omniRawMessages.get(i);

                            //check if this message exists in database and remove it from RAM if not
                            message_fromDB = messageDatabaseClient.findSpecifiedRecord_uuid(appContextRef.get(), omniRawMessage.getMessageUUID());
                            if (message_fromDB == null) {
                                //this RAM message does not exist in database, so we should remove it
                                logV(TAGG + " Sending DB message (" + message_fromDB.getMsgUUID() + ") to MainService RAM for removal...");
                                removeRawMessageFromRAM(message_fromDB);
                            }
                        }
                        logV(TAGG+" "+actionCount+" DB messages removed to RAM.");
                    }

                    // END THE BULK OF THE ACTUAL WORK HERE...
                    ////////////////////////////////////////////////////////////////////////////////
                } catch (NullPointerException e) {
                    // This can happen if parent process dies (taking context reference with it) before this loop breaks
                    // So, let's make sure that's not what's happening (we can depend on this flag to be set by .cleanup() which should be called upon destruction of parent process)...
                    if (!isStopRequested) {
                        logW(TAGG + "Parent process's context has gone AWOL. Context is required for this thread to run; shutting down!");
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
            this.isStopRequested = true;

            // Note: At this point, the thread-loop should break on its own, since we check isInterrupted in the while-loop's condition
        } catch (Exception e) {
            logE(TAGG+"Exception caught invoking .interrupt(): "+e.getMessage());
        }

        this.appContextRef = null;
    }


    /*============================================================================================*/
    /* Processing Methods */

    /** Take provided Room-DB Message and convert it to OmniRawMessage object.
     * @param message_fromDB A Message record (from database) to process.
     * @return An OmniRawMessage object with provided DB record's data.
     */
    private OmniRawMessage convertDBMsgToOmniRawMsg(Message message_fromDB) {
        final String TAGG = "convertDBMsgToOmniRawMsg: ";
        OmniRawMessage ret = new OmniRawMessage(this.logMethod);

        try {
            ret.setMessageUUID(UUID.fromString(message_fromDB.getMsgUUID()));
            ret.setMessageJSONObject(new JSONObject(message_fromDB.getMsgJSON()));
            ret.setStatus(OmniRawMessage.STATUS_UNKNOWN);                                           //NOTE: be sure to set this later when you actually save it
            ret.setStatusMessageDB(message_fromDB.getStatus());
            ret.setCreatedAt(new Date());
            ret.setModifiedAt(ret.getCreatedAt());

            try {
                ret.setMetadataJSONObject(new JSONObject(message_fromDB.getMetaJSON()));
            } catch (JSONException e) {
                logW(TAGG+"JSON exception caught parsing metadata JSON (this might be OK for undelivered records): "+e.getMessage()+"\n(metadata string from DB = \""+message_fromDB.getMetaJSON()+"\")");
                ret.setMetadataJSONObject(new JSONObject());
            }
        } catch (JSONException e) {
            logE(TAGG+"JSON exception caught parsing JSON: "+e.getMessage());
            ret = null;
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
            ret = null;
        }

        return ret;
    }

    private void addRawMessageToRAM(Message dbMessage) {
        final String TAGG = "addRawMessageToRAM: ";

        try {
            logV(TAGG+"Adding "+dbMessage.getMsgUUID()+" to MainService.omniRawMessages...");
            OmniRawMessage omniRawMessage = convertDBMsgToOmniRawMsg(dbMessage);
            MainService.omniRawMessages.addOmniRawMessage(omniRawMessage, OmniRawMessages.ADD_AVOIDING_DUPLICATES);
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }

    private void removeRawMessageFromRAM(Message dbMessage) {
        final String TAGG = "removeRawMessageFromRAM: ";

        try {
            logV(TAGG+"Removing "+dbMessage.getMsgUUID()+" from MainService.omniRawMessages...");
            OmniRawMessage omniRawMessage = convertDBMsgToOmniRawMsg(dbMessage);
            MainService.omniRawMessages.removeOmniRawMessage(omniRawMessage);
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
    private void log(int logSeverity, String tagg) {
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
