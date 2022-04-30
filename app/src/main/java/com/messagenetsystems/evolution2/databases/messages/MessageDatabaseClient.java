package com.messagenetsystems.evolution2.databases.messages;

/* MessageDatabaseClient class
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
 *  MessageDatabaseClient messageDatabaseClient = MessageDatabaseClient.getInstance(getApplicationContext());
 *  messageDatabaseClient.addRecord(getApplicationContext(), "My Data to Add");
 *
 * Revisions:
 *  2019.12.03      Chris Rider     Created.
 *  2019.12.11      Chris Rider     Updated add method to automatically assign NEW status, and upated delete method.
 *                                  Added a method to invoke deleteAll_withStatus.
 *  2019.12.17      Chris Rider     Added method to update status for specified record.
 *  2019.12.18      Chris Rider     Added update JSON method and organized things a bit.
 *  2020.01.22      Chris Rider     Added update metadata methods.
 *  2020.04.20      Chris Rider     Added methods and support for new received_at database field, so we can work with data knowing when we originally received the message.
 *  2020.06.17      Chris Rider     Added method to delete all records.
 *  2020.08.11      Chris Rider     Implemented lower priority for all worker threads. Changed logging INT to BYTE.
 */

import android.arch.persistence.room.Room;
import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;

import org.json.JSONObject;

import java.util.Date;
import java.util.List;
import java.util.UUID;


public class MessageDatabaseClient {
    private final String TAG = MessageDatabaseClient.class.getSimpleName();

    // Constants...
    public static final int SYNC_DB_NOEXIST_ADD = 1;
    public static final int SYNC_DB_NOEXIST_DELETE = 2;

    // Logging stuff...
    private final byte LOG_SEVERITY_V = 1;
    private final byte LOG_SEVERITY_D = 2;
    private final byte LOG_SEVERITY_I = 3;
    private final byte LOG_SEVERITY_W = 4;
    private final byte LOG_SEVERITY_E = 5;
    private byte logMethod = Constants.LOG_METHOD_FILELOGGER;

    // Local stuff...
    private final String dbFilename = "db_messages";        //this will be the filename in /data/user/0/[app]/databases/
    private static MessageDatabaseClient mInstance;         //to support singleton pattern (maintains a static reference to the lone singleton instance - gets returned via getInstance)
    private MessageDatabase messageDatabase;                //to support singleton pattern

    /* A private Constructor prevents any other class from instantiating (singleton pattern)
     * This should only execute when getInstance() is called. */
    private MessageDatabaseClient(Context appContext) {
        //creating the database with Room database builder
        messageDatabase = Room.databaseBuilder(appContext, MessageDatabase.class, dbFilename)
                .fallbackToDestructiveMigration()               //if schema updates, this database is OK to rebuild and lose its data   //TODO: something else?
                .build();
    }

    /* Static 'instance' method (singleton pattern)
     * Its purpose is to instantiate the class locally and return reference to that instance. */
    public static synchronized MessageDatabaseClient getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new MessageDatabaseClient(context);
        }
        return mInstance;
    }

    /* Returns a reference to the local instance (singleton pattern) */
    public MessageDatabase getMessageDatabase() {
        return messageDatabase;
    }


    /*============================================================================================*/
    /* Add/Insert Routines... */

    /** Add a new record to the database
     * This automatically generates and assigns timestamps.
     * NOTE: The "unique" declaration in the @Entity annotation should automatically prevent duplicate UUID records from adding.
     * @param appContext    Application context.
     * @param msgUUID       Message's internal UUID (assigned during ReceivedMessage DB add operation).
     * @param msgJSON       Message as a string of JSON.
     * @param status        Message status to add with (refer to Message constants).
     * @param receivedAt    Datetime the message was originally received (equivalent to ReceivedRequest's created_at field).
     */
    public void addRecord(final Context appContext, final String msgUUID, final String msgJSON, final int status, final Date receivedAt) {
        final String TAGG = "addRecord: ";

        new Thread(){
            @Override
            public void run() {
                currentThread().setPriority(Thread.MIN_PRIORITY);
                logV(TAGG+"Background worker thread running for database operation.");

                try {
                    //create a record...
                    Message message = new Message();                        //this is the object that we'll insert below
                    message.setStatus(status);

                    //add the provided data...
                    message.setMsgUUID(msgUUID);
                    message.setMsgJSON(msgJSON);
                    message.setReceivedAt(receivedAt);      //saving the original ReceivedRequest's created_at field value (when we first received the message)

                    //populate timestamp fields...
                    Date currDateTime = new Date();
                    message.setCreatedAt(currDateTime);
                    message.setModifiedAt(currDateTime);

                    //initialize metadata field...
                    message.setMetaJSON("");

                    //add the record to database...
                    MessageDatabaseClient.getInstance(appContext).getMessageDatabase().messageDao().addRecord(message);
                } catch (Exception e) {
                    if (e.getMessage().contains("UNIQUE constraint failed")) {
                        logI(TAGG + "Possible \"exception\" caught (prevented duplicate record): " + e.getMessage());
                    } else {
                        logE(TAGG + "Exception caught: " + e.getMessage());
                    }
                }
            }
        }.start();
    }


    /*============================================================================================*/
    /* Find/Select Routines... */

    /** Method to find and return all records.
     * NOTE: This is a blocking operation, and won't return until its sub-process is done.
     * @param appContext Application context.
     * @return List object containing any Message objects that might have been found.
     */
    public List<Message> findAllRecords(final Context appContext) {
        final String TAGG = "findAllRecords: ";

        FindAllRecords findAllRecords;
        findAllRecords = new FindAllRecords(appContext);

        findAllRecords.start();

        while (!findAllRecords.isDone) {
            //wait here until it's done
        }

        return findAllRecords.result;
    }

    /** Method to find and return all records, sorted by ascending received_at field value.
     * NOTE: This is a blocking operation, and won't return until its sub-process is done.
     * @param appContext Application context.
     * @return List object containing any Message objects that might have been found.
     */
    public List<Message> findAllRecords_sortedOrderReceivedAscending(final Context appContext) {
        final String TAGG = "findAllRecords_sortedOrderReceivedAscending: ";

        FindAllRecords_sortReceivedAscending findAllRecords;
        findAllRecords = new FindAllRecords_sortReceivedAscending(appContext);

        findAllRecords.start();

        while (!findAllRecords.isDone) {
            //wait here until it's done
        }

        return findAllRecords.result;
    }

    /** Method to find and return all records, sorted by descending received_at field value.
     * NOTE: This is a blocking operation, and won't return until its sub-process is done.
     * @param appContext Application context.
     * @return List object containing any Message objects that might have been found.
     */
    public List<Message> findAllRecords_sortedOrderReceivedDescending(final Context appContext) {
        final String TAGG = "findAllRecords_sortedOrderReceivedDescending: ";

        FindAllRecords_sortReceivedDescending findAllRecords;
        findAllRecords = new FindAllRecords_sortReceivedDescending(appContext);

        findAllRecords.start();

        while (!findAllRecords.isDone) {
            //wait here until it's done
        }

        return findAllRecords.result;
    }

    /** Method to find and return specified record.
     * NOTE: This is a blocking operation, and won't return until its sub-process is done.
     * @param appContext Application context.
     * @param uuid UUID to find.
     * @return Message object that might have been found, or null if none.
     */
    public Message findSpecifiedRecord_uuid(final Context appContext, final UUID uuid) {
        final String TAGG = "findSpecifiedRecords_uuid: ";

        FindSpecifiedRecord_uuid findSpecifiedRecord_uuid;
        findSpecifiedRecord_uuid = new FindSpecifiedRecord_uuid(appContext, uuid);

        findSpecifiedRecord_uuid.start();

        while (!findSpecifiedRecord_uuid.isDone) {
            //wait here until it's done
        }

        if (findSpecifiedRecord_uuid.result.size() == 0) {
            //none found
            return null;
        } else {
            //record(s) found
            return findSpecifiedRecord_uuid.result.get(0);
        }
    }

    //TODO: Does this need to be threaded?
    public boolean doesMessageExist(final Context appContext, String uuid) {
        final String TAGG = "doesMessageExist(\""+String.valueOf(uuid)+"\"): ";
        boolean ret = false;

        try {
            ret = MessageDatabaseClient.getInstance(appContext).getMessageDatabase().messageDao().doesMessageExist(uuid);
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }

        return ret;
    }


    /*============================================================================================*/
    /* Modify/Update Routines... */

    /** Method to update an existing record.
     * You should first load in an existing Message, modify it, then feed it back to this method to do the update.
     * @param appContext    Application context.
     * @param record        The modified record object to put back in the database.
     */
    public void updateRecord(final Context appContext, final Message record) {
        final String TAGG = "updateRecord: ";

        new Thread(){
            @Override
            public void run() {
                currentThread().setPriority(Thread.MIN_PRIORITY);
                logV(TAGG+"Background worker thread running for database operation.");

                try {
                    //update modified timestamp field...
                    record.setModifiedAt(new Date());

                    //run the update on the database
                    MessageDatabaseClient.getInstance(appContext).getMessageDatabase().messageDao().update(record);
                } catch (Exception e) {
                    logE(TAGG+"Exception caught: "+e.getMessage());
                }
            }
        }.start();
    }

    /** Method to update an existing record's status.
     * @param appContext
     * @param uuid
     * @param status
     */
    public void updateStatusFor(final Context appContext, final String uuid, final int status) {
        final String TAGG = "updateStatusFor: ";

        new Thread(){
            @Override
            public void run() {
                currentThread().setPriority(Thread.MIN_PRIORITY);
                logV(TAGG+"Background worker thread running for database operation.");

                try {
                    //run the update on the database
                    MessageDatabaseClient.getInstance(appContext).getMessageDatabase().messageDao().updateStatusFor(uuid, status);
                } catch (Exception e) {
                    logE(TAGG+"Exception caught: "+e.getMessage());
                }
            }
        }.start();
    }
    public void updateStatusFor(final Context appContext, final UUID uuid, final int status) {
        final String TAGG = "updateStatusFor: ";

        try {
            updateStatusFor(appContext, uuid.toString(), status);
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }

    /** Method to update an existing record's json.
     * @param appContext
     * @param uuid
     * @param json
     */
    public void updateJsonFor(final Context appContext, final String uuid, final String json) {
        final String TAGG = "updateJsonFor: ";

        new Thread(){
            @Override
            public void run() {
                currentThread().setPriority(Thread.MIN_PRIORITY);
                logV(TAGG+"Background worker thread running for database operation.");

                try {
                    //run the update on the database
                    MessageDatabaseClient.getInstance(appContext).getMessageDatabase().messageDao().updateJsonFor(uuid, json);
                } catch (Exception e) {
                    logE(TAGG+"Exception caught: "+e.getMessage());
                }
            }
        }.start();
    }
    public void updateJsonFor(final Context appContext, final UUID uuid, final JSONObject json) {
        final String TAGG = "updateJsonFor: ";

        try {
            updateJsonFor(appContext, uuid.toString(), json.toString());
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }

    /** Method to update an existing record's metadata json.
     * @param appContext
     * @param uuid
     * @param json
     */
    public void updateMetaFor(final Context appContext, final String uuid, final String json) {
        final String TAGG = "updateMetaFor: ";

        new Thread(){
            @Override
            public void run() {
                currentThread().setPriority(Thread.MIN_PRIORITY);
                logV(TAGG+"Background worker thread running for database operation.");

                try {
                    //run the update on the database
                    MessageDatabaseClient.getInstance(appContext).getMessageDatabase().messageDao().updateMetaFor(uuid, json);
                } catch (Exception e) {
                    logE(TAGG+"Exception caught: "+e.getMessage());
                }
            }
        }.start();
    }
    public void updateMetaFor(final Context appContext, final UUID uuid, final JSONObject json) {
        final String TAGG = "updateMetaFor: ";

        try {
            updateMetaFor(appContext, uuid.toString(), json.toString());
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }


    /*============================================================================================*/
    /* Delete Routines... */

    public void deleteRecord(final Context appContext, final Message record) {
        final String TAGG = "deleteRecord: ";

        new Thread(){
            @Override
            public void run() {
                currentThread().setPriority(Thread.MIN_PRIORITY);
                logV(TAGG+"Background worker thread running for database operation.");

                try {
                    //run the delete on the database
                    MessageDatabaseClient.getInstance(appContext).getMessageDatabase().messageDao().delete(record);
                } catch (Exception e) {
                    logE(TAGG+"Exception caught: "+e.getMessage());
                }
            }
        }.start();
    }

    public void deleteRecord(final Context appContext, final String uuid) {
        final String TAGG = "deleteRecord: ";

        new Thread(){
            @Override
            public void run() {
                currentThread().setPriority(Thread.MIN_PRIORITY);
                logV(TAGG+"Background worker thread running for database operation.");

                try {
                    //run the delete on the database
                    MessageDatabaseClient.getInstance(appContext).getMessageDatabase().messageDao().delete(uuid);
                } catch (Exception e) {
                    logE(TAGG+"Exception caught: "+e.getMessage());
                }
            }
        }.start();
    }

    public void deleteRecord(final Context appContext, @NonNull final UUID uuid) {
        deleteRecord(appContext, uuid.toString());
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
                    MessageDatabaseClient.getInstance(appContext).getMessageDatabase().messageDao().deleteAll_olderThan(sqliteDatetimeModifier);
                } catch (Exception e) {
                    logE(TAGG+"Exception caught: "+e.getMessage());
                }
            }
        }.start();
    }

    /** Method to invoke the database backend to find and delete all records with specified status (refer to Message constants).
     * @param appContext Application context.
     * @param status Message status (refer to Message constants) for which to delete records.
     */
    public void deleteAll_withStatus(final Context appContext, final int status) {
        final String TAGG = "deleteAll_withStatus: ";

        new Thread(){
            @Override
            public void run() {
                this.setPriority(Thread.MIN_PRIORITY);
                logV(TAGG+"Background worker thread running for database operation.");

                try {
                    //run the query on the database
                    MessageDatabaseClient.getInstance(appContext).getMessageDatabase().messageDao().deleteAll_withStatus(status);
                } catch (Exception e) {
                    logE(TAGG+"Exception caught: "+e.getMessage());
                }
            }
        }.start();
    }

    /** Method to invoke the database backend to find and delete all records.
     * @param appContext Application context.
     */
    public void deleteAll(final Context appContext) {
        final String TAGG = "deleteAll: ";

        new Thread(){
            @Override
            public void run() {
                this.setPriority(Thread.MIN_PRIORITY);
                logV(TAGG+"Background worker thread running for database operation.");

                try {
                    //run the query on the database
                    MessageDatabaseClient.getInstance(appContext).getMessageDatabase().messageDao().deleteAll();
                } catch (Exception e) {
                    logE(TAGG+"Exception caught: "+e.getMessage());
                }
            }
        }.start();
    }


    /** Method to synchronize provided record TO the database.
     * This means we make a change TO the database, according to what's provided.
     * @param appContext
     * @param uuid
     * @param json
     * @param dbRecordExistsAction
     */
    /* Tried, but not needed, and not satisfied with it...
    public void syncRecord(final Context appContext, @NonNull final String uuid, final String json, final int dbRecordExistsAction) {
        final String TAGG = "syncRecord("+uuid+"): ";

        try {
            if (doesMessageExist(appContext, uuid)) {
                Message messageToUpdate = findSpecifiedRecord_uuid(appContext, UUID.fromString(uuid));
                messageToUpdate.setMsgJSON(json);
                logV(TAGG+"Updating existing record in DB...");
                updateRecord(appContext, messageToUpdate);
            } else {
                switch (dbRecordExistsAction) {
                    case SYNC_DB_NOEXIST_ADD:
                        logV(TAGG+"Adding record to DB...");
                        addRecord(appContext, uuid, json);
                        break;
                    case SYNC_DB_NOEXIST_DELETE:
                        logV(TAGG+"Deleting existing record from DB...");
                        deleteRecord(appContext, uuid);
                }
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }
    */


    /*============================================================================================*/
    /* Subclasses... */

    /** A thread to access the database and find all records.
     * You should wrap this in a loop to monitor for when it's done and its results are ready.
     *
     * Usage example:
     *  FindAllRecords findAllRecords;
     *  findAllRecords = new FindAllRecords(appContext);
     *  findAllRecords.start();
     *  while (!findAllRecords.isDone) {
     *      //wait here until it's done
     *  }
     *  return findAllRecords.result;
     */
    private class FindAllRecords extends Thread {
        private final String TAGG = this.getClass().getSimpleName();
        private Context appContext = null;
        List<Message> result = null;
        volatile boolean isDone = false;
        FindAllRecords(final Context appContext) {
            this.appContext = appContext;
        }
        @Override
        public void run() {
            final String TAGGG = "run: ";
            this.setPriority(Thread.MIN_PRIORITY);
            try {
                result = MessageDatabaseClient.getInstance(appContext).getMessageDatabase().messageDao().getAllRecords();
            } catch (Exception e) {
                logE(TAGG+TAGGG+"Exception caught: "+e.getMessage());
            }
            this.isDone = true;
        }
    }

    /** A thread to access the database and find all records, sorted by received_at field, ascending.
     * You should wrap this in a loop to monitor for when it's done and its results are ready.
     *
     * Usage example:
     *  FindAllRecords_sortReceivedAscending findAllRecords_sorted;
     *  findAllRecords_sorted = new FindAllRecords_sortReceivedAscending(appContext);
     *  findAllRecords_sorted.start();
     *  while (!findAllRecords_sorted.isDone) {
     *      //wait here until it's done
     *  }
     *  return findAllRecords_sorted.result;
     */
    private class FindAllRecords_sortReceivedAscending extends Thread {
        private final String TAGG = this.getClass().getSimpleName();
        private Context appContext = null;
        List<Message> result = null;
        volatile boolean isDone = false;
        FindAllRecords_sortReceivedAscending(final Context appContext) {
            this.appContext = appContext;
        }
        @Override
        public void run() {
            final String TAGGG = "run: ";
            this.setPriority(Thread.MIN_PRIORITY);
            try {
                result = MessageDatabaseClient.getInstance(appContext).getMessageDatabase().messageDao().getAllRecords_sortedByReceivedAscending();
            } catch (Exception e) {
                logE(TAGG+TAGGG+"Exception caught: "+e.getMessage());
            }
            this.isDone = true;
        }
    }

    /** A thread to access the database and find all records, sorted by received_at field, descending.
     * You should wrap this in a loop to monitor for when it's done and its results are ready.
     *
     * Usage example:
     *  FindAllRecords_sortReceivedDescending findAllRecords_sorted;
     *  findAllRecords_sorted = new FindAllRecords_sortReceivedDescending(appContext);
     *  findAllRecords_sorted.start();
     *  while (!findAllRecords_sorted.isDone) {
     *      //wait here until it's done
     *  }
     *  return findAllRecords_sorted.result;
     */
    private class FindAllRecords_sortReceivedDescending extends Thread {
        private final String TAGG = this.getClass().getSimpleName();
        private Context appContext = null;
        List<Message> result = null;
        volatile boolean isDone = false;
        FindAllRecords_sortReceivedDescending(final Context appContext) {
            this.appContext = appContext;
        }
        @Override
        public void run() {
            final String TAGGG = "run: ";
            this.setPriority(Thread.MIN_PRIORITY);
            try {
                result = MessageDatabaseClient.getInstance(appContext).getMessageDatabase().messageDao().getAllRecords_sortedByReceivedDescending();
            } catch (Exception e) {
                logE(TAGG+TAGGG+"Exception caught: "+e.getMessage());
            }
            this.isDone = true;
        }
    }

    /** A thread to access the database and find the matching record.
     * You should wrap this in a loop to monitor for when it's done and its result are ready.
     *
     * Usage example:
     *  FindSpecifiedRecord_uuid findSpecifiedRecord_uuid;
     *  findSpecifiedRecord_uuid = new FindSpecifiedRecord_uuid(appContext);
     *  findSpecifiedRecord_uuid.start();
     *  while (!findSpecifiedRecord_uuid.isDone) {
     *      //wait here until it's done
     *  }
     *  return findSpecifiedRecord_uuid.result;
     */
    private class FindSpecifiedRecord_uuid extends Thread {
        private final String TAGG = this.getClass().getSimpleName();
        private Context appContext = null;
        private UUID uuid = UUID.randomUUID();
        List<Message> result = null;
        volatile boolean isDone = false;
        FindSpecifiedRecord_uuid(final Context appContext, final UUID uuid) {
            this.appContext = appContext;
            this.uuid = uuid;
        }
        @Override
        public void run() {
            final String TAGGG = "run: ";
            this.setPriority(Thread.MIN_PRIORITY);
            try {
                result = MessageDatabaseClient.getInstance(appContext).getMessageDatabase().messageDao().getSpecificRecord_uuid(uuid.toString());
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
