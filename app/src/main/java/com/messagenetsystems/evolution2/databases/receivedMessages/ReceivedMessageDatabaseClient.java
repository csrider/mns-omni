package com.messagenetsystems.evolution2.databases.receivedMessages;

/* ReceivedMessageDatabaseClient class
 *
 * An efficient and reliable way of providing access to the database in a controlled manner.
 * This is what you should use to work with the database, rather than creating DB from scratch.
 * In this way, you can help to avoid conflicts, race conditions, and such.
 *
 * This provides a singleton pattern, so we can ensure that only one resource (the DB) is worked
 * with at any one time, and no competition from other components. To put it in general terms,
 * this client only exists here... a static reference to its instance is available if needed, but
 * it can never be instantiated in other places (thereby preventing races or lockouts to DB).
 *
 * Usage example in your main program...
 *  ReceivedMessageDatabaseClient receivedMessageDatabaseClient = ReceivedMessageDatabaseClient.getInstance(getApplicationContext());
 *  receivedMessageDatabaseClient.addReceivedMessage(getApplicationContext(), "{\"key1\":\"value1\",\"key2\":\"value2\"}");
 *
 * Revisions:
 *  2019.12.02      Chris Rider     Created (used ReceivedRequestDatabaseClient as a template).
 *  2019.12.11      Chris Rider     Accommodated refactoring.
 *  2020.04.20      Chris Rider     Added saving of received_at field data when we add a new record from ReceivedRequest data.
 *  2020.06.29      Chris Rider     New logic to find all records containing some specified JSON, and avoidance of duplicating message by its contents. Bug happened when server sends same message multiple times (JERRY CRAP).
 *  2020.08.11      Chris Rider     Implemented lower priority for all worker threads. Changed logging INT to BYTE.
 */

import android.arch.persistence.room.Room;
import android.content.Context;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;

import java.util.Date;
import java.util.List;
import java.util.UUID;


public class ReceivedMessageDatabaseClient {
    private final String TAG = ReceivedMessageDatabaseClient.class.getSimpleName();

    // Logging stuff...
    private final byte LOG_SEVERITY_V = 1;
    private final byte LOG_SEVERITY_D = 2;
    private final byte LOG_SEVERITY_I = 3;
    private final byte LOG_SEVERITY_W = 4;
    private final byte LOG_SEVERITY_E = 5;
    private byte logMethod = Constants.LOG_METHOD_FILELOGGER;

    // Local stuff...
    private static ReceivedMessageDatabaseClient mInstance;                                         //to support singleton pattern (maintains a static reference to the lone singleton instance - gets returned via getInstance)
    private ReceivedMessageDatabase receivedMessageDatabase;                                        //to support singleton pattern

    /* A private Constructor prevents any other class from instantiating (singleton pattern)
     * This should only execute when getInstance() is called. */
    private ReceivedMessageDatabaseClient(Context appContext) {
        //creating the database with Room database builder
        String dbName = "db_receivedMessages";                                                      //this will be the filename in /data/user/0/[app]/databases/

        receivedMessageDatabase = Room.databaseBuilder(appContext, ReceivedMessageDatabase.class, dbName)
                .fallbackToDestructiveMigration()                                                   //if schema updates, this database is OK to rebuild and lose its data
                .build();
    }

    /* Static 'instance' method (singleton pattern)
     * Its purpose is to instantiate the class locally and return reference to that instance. */
    public static synchronized ReceivedMessageDatabaseClient getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new ReceivedMessageDatabaseClient(context);
        }
        return mInstance;
    }

    /* Returns a reference to the local instance (singleton pattern) */
    public ReceivedMessageDatabase getReceivedMessageDatabase() {
        return receivedMessageDatabase;
    }


    /*============================================================================================*/
    /* Main Routines... */

    /** Add a new received message record to the database
     * This automatically generates and assigns a UUID, as well as timestamps.
     * @param appContext    Application context.
     * @param messageJson   Message JSON string to save.
     */
    public void addRecord(final Context appContext, final String messageJson, final Date requestReceivedAt) {
        final String TAGG = "addRecord: ";

        new Thread(){
            @Override
            public void run() {
                this.setPriority(Thread.MIN_PRIORITY);
                logV(TAGG+"Background worker thread running for database operation.");

                // First, we need to determine whether the received request would be a duplicate...
                // Sometimes Jerry's shitty code will send an identical message multiple times. The
                // only way to know for certain is to cross check recno and dtsec, since those will
                // be unique no matter what.
                if (doesReceivedMessageExistMatchingWholeTextInJsonField(appContext, messageJson)) {
                    logW(TAGG+"Record already exists in database, aborting record-add to avoid duplicate messages.");
                    return;
                }

                // Only add the new record if it's unique, determined by recno/dtsec combo above.
                try {
                    //create a record...
                    ReceivedMessage receivedMessage = new ReceivedMessage();                        //this is the object that we'll insert below

                    //add the provided data...
                    receivedMessage.setMessageJson(messageJson);

                    //initially set status...
                    receivedMessage.setStatus(ReceivedMessage.STATUS_NEW);

                    //generate and set a random UUID...
                    receivedMessage.setMessageUUID(UUID.randomUUID().toString());

                    //populate timestamp fields...
                    Date currDateTime = new Date();
                    receivedMessage.setCreatedAt(currDateTime);
                    receivedMessage.setModifiedAt(currDateTime);
                    receivedMessage.setReceivedAt(requestReceivedAt);                               //datetime the ReceivedRequest was originally created

                    //add the record to database...
                    ReceivedMessageDatabaseClient.getInstance(appContext).getReceivedMessageDatabase().receivedMessageDao().addRecord(receivedMessage);
                } catch (Exception e) {
                    logE(TAGG + "Exception caught: " + e.getMessage());
                }
            }
        }.start();
    }

    /** Method to find and return all records.
     * NOTE: This is a blocking operation, and won't return until its sub-process is done.
     * @param appContext Application context.
     * @return List object containing any ReceivedMessage objects that might have been found.
     */
    public List<ReceivedMessage> findAllReceivedMessages(final Context appContext) {
        final String TAGG = "findAllReceivedMessages: ";

        FindAllReceivedMessages findAllReceivedMessages;
        findAllReceivedMessages = new FindAllReceivedMessages(appContext);

        findAllReceivedMessages.start();

        while (!findAllReceivedMessages.isDone) {
            //wait here until it's done
        }

        return findAllReceivedMessages.result;
    }

    /** Method to find and return all unprocessed records.
     * NOTE: This is a blocking operation, and won't return until its sub-process is done.
     * @param appContext Application context.
     * @return List object containing any ReceivedMessage objects that might have been found.
     */
    public List<ReceivedMessage> findUnprocessedReceivedMessages(final Context appContext) {
        final String TAGG = "findUnprocessedReceivedMessages: ";

        FindUnprocessedReceivedMessages findUnprocessedReceivedMessages;
        findUnprocessedReceivedMessages = new FindUnprocessedReceivedMessages(appContext);

        findUnprocessedReceivedMessages.start();

        while (!findUnprocessedReceivedMessages.isDone) {
            //wait here until it's done
        }

        return findUnprocessedReceivedMessages.result;
    }

    /** Method to find and return all processed records.
     * NOTE: This is a blocking operation, and won't return until its sub-process is done.
     * @param appContext Application context.
     * @return List object containing any ReceivedMessage objects that might have been found.
     */
    public List<ReceivedMessage> findProcessedReceivedMessages(final Context appContext) {
        final String TAGG = "findProcessedReceivedMessages: ";

        FindProcessedReceivedMessages findProcessedReceivedMessages;
        findProcessedReceivedMessages = new FindProcessedReceivedMessages(appContext);

        findProcessedReceivedMessages.start();

        while (!findProcessedReceivedMessages.isDone) {
            //wait here until it's done
        }

        return findProcessedReceivedMessages.result;
    }

    /** Method to find and return all records with specified substring text to find in JSON field.
     * NOTE: This is a blocking operation, and won't return until its sub-process is done.
     * @param appContext Application context.
     * @param textToFind Substring text to find in the JSON field.
     * @return List object containing any ReceivedMessage objects that might have been found.
     */
    public List<ReceivedMessage> findAllReceivedMessagesContainingTextInJsonField(final Context appContext, final String textToFind) {
        final String TAGG = "findAllReceivedMessagesContainingTextInJsonField: ";

        FindReceivedMessagesContainingJson findReceivedMessagesContainingJson;
        findReceivedMessagesContainingJson = new FindReceivedMessagesContainingJson(appContext, textToFind);

        findReceivedMessagesContainingJson.start();

        while (!findReceivedMessagesContainingJson.isDone) {
            //wait here until it's done
        }

        return findReceivedMessagesContainingJson.result;
    }

    /** Method to find and return all records with specified whole text to find in JSON field.
     * NOTE: This is a blocking operation, and won't return until its sub-process is done.
     * @param appContext Application context.
     * @param textToFind Whole text to match for the JSON field.
     * @return List object containing any ReceivedMessage objects that might have been found.
     */
    public List<ReceivedMessage> findAllReceivedMessagesMatchingWholeTextInJsonField(final Context appContext, final String textToFind) {
        final String TAGG = "findAllReceivedMessagesMatchingWholeTextInJsonField: ";

        FindReceivedMessagesWithJsonWholeValue findReceivedMessagesWithJsonWholeValue;
        findReceivedMessagesWithJsonWholeValue = new FindReceivedMessagesWithJsonWholeValue(appContext, textToFind);

        findReceivedMessagesWithJsonWholeValue.start();

        while (!findReceivedMessagesWithJsonWholeValue.isDone) {
            //wait here until it's done
        }

        return findReceivedMessagesWithJsonWholeValue.result;
    }

    /** Method to find and count all records with specified whole text to find in JSON field.
     * NOTE: This is a blocking operation, and won't return until its sub-process is done.
     * @param appContext Application context.
     * @param textToFind Whole text to match for the JSON field.
     * @return Integer count of how many ReceivedMessage objects that were found.
     */
    public int countReceivedMessagesMatchingWholeTextInJsonField(final Context appContext, final String textToFind) {
        final String TAGG = "countReceivedMessagesMatchingWholeTextInJsonField: ";

        CountReceivedMessagesWithJsonWholeValue countReceivedMessagesWithJsonWholeValue;
        countReceivedMessagesWithJsonWholeValue = new CountReceivedMessagesWithJsonWholeValue(appContext, textToFind);

        countReceivedMessagesWithJsonWholeValue.start();

        while (!countReceivedMessagesWithJsonWholeValue.isDone) {
            //wait here until it's done
        }

        return countReceivedMessagesWithJsonWholeValue.result;
    }
    public boolean doesReceivedMessageExistMatchingWholeTextInJsonField(final Context appContext, final String textToFind) {
        final String TAGG = "doesReceivedMessageExistMatchingWholeTextInJsonField: ";
        int count = countReceivedMessagesMatchingWholeTextInJsonField(appContext, textToFind);
        if (count > 0) {
            return true;
        } else if (count == 0) {
            return false;
        } else {
            logW(TAGG+"Unhandled count value returned to us by database ("+String.valueOf(count)+"), returning false.");
            return false;
        }
    }

    /** Method to invoke the database backend to find and delete any old unneeded records.
     * This should probably be intended for any uncaught leftovers as a final clean-sweep
     * @param appContext Application context.
     */
    public void deleteAllProcessed_olderThan(final Context appContext, final String sqliteDatetimeModifier) {
        final String TAGG = "deleteAllProcessed_olderThan: ";

        new Thread(){
            @Override
            public void run() {
                this.setPriority(Thread.MIN_PRIORITY);
                logV(TAGG+"Background worker thread running for database operation.");

                try {
                    //run the query on the database
                    ReceivedMessageDatabaseClient.getInstance(appContext).getReceivedMessageDatabase().receivedMessageDao().deleteAllProcessed_olderThan(sqliteDatetimeModifier);
                } catch (Exception e) {
                    logE(TAGG+"Exception caught: "+e.getMessage());
                }
            }
        }.start();
    }

    /** Method to invoke the database backend to find and delete any old unprocessed records.
     * This should probably be intended for any uncaught leftovers as a final clean-sweep
     * Since it deletes UNprocessed records, it should never ideally be needed/used, but it's available just to keep DB a healthy size.
     * @param appContext Application context.
     */
    public void deleteAllUnprocessed_olderThan(final Context appContext, final String sqliteDatetimeModifier) {
        final String TAGG = "deleteAllUnprocessed_olderThan: ";

        new Thread(){
            @Override
            public void run() {
                this.setPriority(Thread.MIN_PRIORITY);
                logV(TAGG+"Background worker thread running for database operation.");

                try {
                    //run the query on the database
                    ReceivedMessageDatabaseClient.getInstance(appContext).getReceivedMessageDatabase().receivedMessageDao().deleteAllUnprocessed_olderThan(sqliteDatetimeModifier);
                } catch (Exception e) {
                    logE(TAGG+"Exception caught: "+e.getMessage());
                }
            }
        }.start();
    }

    /** Method to invoke the database backend to find and delete ANY old records.
     * This should probably be intended for any uncaught leftovers as a final clean-sweep
     * Since it deletes ANY/ALL old records, it should never ideally be needed/used, but it's available just to keep DB a healthy size.
     * @param appContext Application context.
     */
    public void deleteAll_olderThan(final Context appContext, final String sqliteDatetimeModifier) {
        final String TAGG = "deleteAll_olderThan: ";

        new Thread(){
            @Override
            public void run() {
                this.setPriority(Thread.MIN_PRIORITY);
                logV(TAGG+"Background worker thread running for database operation.");

                try {
                    //run the query on the database
                    ReceivedMessageDatabaseClient.getInstance(appContext).getReceivedMessageDatabase().receivedMessageDao().deleteAll_olderThan(sqliteDatetimeModifier);
                } catch (Exception e) {
                    logE(TAGG+"Exception caught: "+e.getMessage());
                }
            }
        }.start();
    }

    /** Method to update an existing record.
     * You should first load in an existing ReceivedMessage, modify it, then feed it back to this method to do the update.
     * @param appContext                Application context.
     * @param updatedReceivedMessage    The modified record object to put back in the database.
     */
    public void updateRecord(final Context appContext, final ReceivedMessage updatedReceivedMessage) {
        final String TAGG = "updateRecord: ";

        new Thread(){
            @Override
            public void run() {
                this.setPriority(Thread.MIN_PRIORITY);
                logV(TAGG+"Background worker thread running for database operation.");

                try {
                    //update modified timestamp field...
                    updatedReceivedMessage.setModifiedAt(new Date());

                    //run the update on the database
                    ReceivedMessageDatabaseClient.getInstance(appContext).getReceivedMessageDatabase().receivedMessageDao().updateRecord(updatedReceivedMessage);
                } catch (Exception e) {
                    logE(TAGG+"Exception caught: "+e.getMessage());
                }
            }
        }.start();
    }


    /*============================================================================================*/
    /* Subclasses... */

    /** A thread to access the database and find all records.
     * You should wrap this in a loop to monitor for when it's done and its results are ready.
     *
     * It will find all records that have a "new" status.
     *
     * Usage example:
     *  FindAllReceivedMessages findAllReceivedMessages;
     *  findAllReceivedMessages = new FindAllReceivedMessages(appContext);
     *  findAllReceivedMessages.start();
     *  while (!findAllReceivedMessages.isDone) {
     *      //wait here until it's done
     *  }
     *  return findAllReceivedMessages.result;
     */
    private class FindAllReceivedMessages extends Thread {
        private final String TAGG = this.getClass().getSimpleName();
        private Context appContext = null;
        List<ReceivedMessage> result = null;
        volatile boolean isDone = false;
        FindAllReceivedMessages(final Context appContext) {
            this.appContext = appContext;
        }
        @Override
        public void run() {
            final String TAGGG = "run: ";
            this.setPriority(Thread.MIN_PRIORITY);
            try {
                result = ReceivedMessageDatabaseClient.getInstance(appContext).getReceivedMessageDatabase().receivedMessageDao().getAllReceivedMessages();
            } catch (Exception e) {
                logE(TAGG+TAGGG+"Exception caught: "+e.getMessage());
            }
            this.isDone = true;
        }
    }

    /** A thread to access the database and find all unprocessed records.
     * You should wrap this in a loop to monitor for when it's done and its results are ready.
     *
     * It will find all records that have a "new" status.
     *
     * Usage example:
     *  FindUnprocessedReceivedMessages findUnprocessedReceivedMessages;
     *  findUnprocessedReceivedMessages = new FindUnprocessedReceivedMessages(appContext);
     *  findUnprocessedReceivedMessages.start();
     *  while (!findUnprocessedReceivedMessages.isDone) {
     *      //wait here until it's done
     *  }
     *  return findUnprocessedReceivedMessages.result;
     */
    private class FindUnprocessedReceivedMessages extends Thread {
        private final String TAGG = this.getClass().getSimpleName();
        private Context appContext = null;
        List<ReceivedMessage> result = null;
        volatile boolean isDone = false;
        FindUnprocessedReceivedMessages(final Context appContext) {
            this.appContext = appContext;
        }
        @Override
        public void run() {
            final String TAGGG = "run: ";
            this.setPriority(Thread.MIN_PRIORITY);
            try {
                result = ReceivedMessageDatabaseClient.getInstance(appContext).getReceivedMessageDatabase().receivedMessageDao().getAllUnprocessedReceivedMessages();
            } catch (Exception e) {
                logE(TAGG+TAGGG+"Exception caught: "+e.getMessage());
            }
            this.isDone = true;
        }
    }

    /** A thread to access the database and find all already-processed records.
     * You should wrap this in a loop to monitor for when it's done and its results are ready.
     *
     * Usage example:
     *  FindProcessedReceivedMessages findProcessedReceivedMessages;
     *  findProcessedReceivedMessages = new FindProcessedReceivedMessages(appContext);
     *  findProcessedReceivedMessages.start();
     *  while (!findProcessedReceivedMessages.isDone) {
     *      //wait here until it's done
     *  }
     *  return findProcessedReceivedMessages.result;
     */
    private class FindProcessedReceivedMessages extends Thread {
        private final String TAGG = this.getClass().getSimpleName();
        private Context appContext = null;
        List<ReceivedMessage> result = null;
        volatile boolean isDone = false;
        FindProcessedReceivedMessages(final Context appContext) {
            this.appContext = appContext;
        }
        @Override
        public void run() {
            final String TAGGG = "run: ";
            this.setPriority(Thread.MIN_PRIORITY);
            try {
                result = ReceivedMessageDatabaseClient.getInstance(appContext).getReceivedMessageDatabase().receivedMessageDao().getAllProcessedReceivedMessages();
            } catch (Exception e) {
                logE(TAGG+TAGGG+"Exception caught: "+e.getMessage());
            }
            this.isDone = true;
        }
    }

    /** A thread to access the database and find all records with specified substring text in the field.
     * You should wrap this in a loop to monitor for when it's done and its results are ready.
     *
     * Usage example:
     *  FindReceivedMessagesContainingJson findReceivedMessagesContainingJson;
     *  findReceivedMessagesContainingJson = new FindReceivedMessagesContainingJson(appContext);
     *  findReceivedMessagesContainingJson.start();
     *  while (!findReceivedMessagesContainingJson.isDone) {
     *      //wait here until it's done
     *  }
     *  return findReceivedMessagesContainingJson.result;
     */
    private class FindReceivedMessagesContainingJson extends Thread {
        private final String TAGG = this.getClass().getSimpleName();
        private Context appContext = null;
        private String substringToFind = "";
        List<ReceivedMessage> result = null;
        volatile boolean isDone = false;
        FindReceivedMessagesContainingJson(final Context appContext, final String substringToFind) {
            this.appContext = appContext;
            this.substringToFind = substringToFind;
        }
        @Override
        public void run() {
            final String TAGGG = "run: ";
            this.setPriority(Thread.MIN_PRIORITY);
            try {
                result = ReceivedMessageDatabaseClient.getInstance(appContext).getReceivedMessageDatabase().receivedMessageDao().getAllReceivedMessagesContainingJSON(substringToFind);
            } catch (Exception e) {
                logE(TAGG+TAGGG+"Exception caught: "+e.getMessage());
            }
            this.isDone = true;
        }
    }

    /** A thread to access the database and find all records with specified exact whole text in the field.
     * You should wrap this in a loop to monitor for when it's done and its results are ready.
     *
     * Usage example:
     *  FindReceivedMessagesWithJsonWholeValue findReceivedMessagesWithJsonWholeValue;
     *  findReceivedMessagesWithJsonWholeValue = new FindReceivedMessagesWithJsonWholeValue(appContext);
     *  findReceivedMessagesWithJsonWholeValue.start();
     *  while (!findReceivedMessagesWithJsonWholeValue.isDone) {
     *      //wait here until it's done
     *  }
     *  return findReceivedMessagesWithJsonWholeValue.result;
     */
    private class FindReceivedMessagesWithJsonWholeValue extends Thread {
        private final String TAGG = this.getClass().getSimpleName();
        private Context appContext = null;
        private String wholeStringToFind = "";
        List<ReceivedMessage> result = null;
        volatile boolean isDone = false;
        FindReceivedMessagesWithJsonWholeValue(final Context appContext, final String wholeStringToFind) {
            this.appContext = appContext;
            this.wholeStringToFind = wholeStringToFind;
        }
        @Override
        public void run() {
            final String TAGGG = "run: ";
            this.setPriority(Thread.MIN_PRIORITY);
            try {
                result = ReceivedMessageDatabaseClient.getInstance(appContext).getReceivedMessageDatabase().receivedMessageDao().getAllReceivedMessagesWithMatchingWholeJSON(wholeStringToFind);
            } catch (Exception e) {
                logE(TAGG+TAGGG+"Exception caught: "+e.getMessage());
            }
            this.isDone = true;
        }
    }

    /** A thread to access the database and count all records with specified exact whole text in the field.
     * You should wrap this in a loop to monitor for when it's done and its results are ready.
     *
     * Usage example:
     *  CountReceivedMessagesWithJsonWholeValue countReceivedMessagesWithJsonWholeValue;
     *  countReceivedMessagesWithJsonWholeValue = new CountReceivedMessagesWithJsonWholeValue(appContext);
     *  countReceivedMessagesWithJsonWholeValue.start();
     *  while (!countReceivedMessagesWithJsonWholeValue.isDone) {
     *      //wait here until it's done
     *  }
     *  return countReceivedMessagesWithJsonWholeValue.result;
     */
    private class CountReceivedMessagesWithJsonWholeValue extends Thread {
        private final String TAGG = this.getClass().getSimpleName();
        private Context appContext = null;
        private String wholeStringToFind = "";
        int result = -1;
        volatile boolean isDone = false;
        CountReceivedMessagesWithJsonWholeValue(final Context appContext, final String wholeStringToFind) {
            this.appContext = appContext;
            this.wholeStringToFind = wholeStringToFind;
        }
        @Override
        public void run() {
            final String TAGGG = "run: ";
            this.setPriority(Thread.MIN_PRIORITY);
            try {
                result = ReceivedMessageDatabaseClient.getInstance(appContext).getReceivedMessageDatabase().receivedMessageDao().countReceivedMessagesWithMatchingWholeJSON(wholeStringToFind);
            } catch (Exception e) {
                logE(TAGG+TAGGG+"Exception caught: "+e.getMessage());
            }
            this.isDone = true;
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
