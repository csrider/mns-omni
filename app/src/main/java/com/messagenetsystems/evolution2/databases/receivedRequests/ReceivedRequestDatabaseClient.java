package com.messagenetsystems.evolution2.databases.receivedRequests;

/* ReceivedRequestDatabaseClient class
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
 *  ReceivedRequestDatabaseClient receivedRequestDatabaseClient = ReceivedRequestDatabaseClient.getInstance(getApplicationContext());
 *  receivedRequestDatabaseClient.addReceivedRequest(getApplicationContext(), "POST", "/", "HTTP/1.1", "curl/7.35.0", "application/json", "{\"key1\":\"value1\",\"key2\":\"value2\"}");
 *
 * Revisions:
 *  2019.08.28      Chris Rider     Created (originally for v1 ActiveMsg but not finished).
 *  2019.11.21      Chris Rider     Migrated to v2 and adapted for received requests.
 *  2019.11.26      Chris Rider     Added methods to run the delete-old query, and to update processed-at fields.
 *  2019.12.02      Chris Rider     Added method and thread to get records marked as processed valid messages.
 *                                  Fixed bug where modified timestamp field would not have updated during an update operation.
 *  2019.12.11      Chris Rider     Refactored various methods.
 *  2020.08.11      Chris Rider     Implemented lower priority for all worker threads. Changed logging INT to BYTE.
 *  2020.09.24      Chris Rider     Updated deleteAll_olderThan method to use 'created_at' field.
 */

import android.arch.persistence.room.Room;
import android.content.Context;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;

import java.util.Date;
import java.util.List;


public class ReceivedRequestDatabaseClient {
    private final String TAG = ReceivedRequestDatabaseClient.class.getSimpleName();

    // Logging stuff...
    private final byte LOG_SEVERITY_V = 1;
    private final byte LOG_SEVERITY_D = 2;
    private final byte LOG_SEVERITY_I = 3;
    private final byte LOG_SEVERITY_W = 4;
    private final byte LOG_SEVERITY_E = 5;
    private byte logMethod = Constants.LOG_METHOD_FILELOGGER;

    // Local stuff...
    private static ReceivedRequestDatabaseClient mInstance;                                         //to support singleton pattern (maintains a static reference to the lone singleton instance - gets returned via getInstance)
    private ReceivedRequestDatabase receivedRequestDatabase;                                        //to support singleton pattern

    /* A private Constructor prevents any other class from instantiating (singleton pattern)
     * This should only execute when getInstance() is called. */
    private ReceivedRequestDatabaseClient(Context appContext) {
        //creating the database with Room database builder
        String dbName = "db_receivedRequests";                                                      //this will be the filename in /data/user/0/[app]/databases/

        //receivedRequestDatabase = Room.databaseBuilder(appContext, ReceivedRequestDatabase.class, dbName).allowMainThreadQueries().build();       //use this if you want to allow main/UI thread processing (not recommended)
        receivedRequestDatabase = Room.databaseBuilder(appContext, ReceivedRequestDatabase.class, dbName)
                .fallbackToDestructiveMigration()                                                   //if schema updates, this database is OK to rebuild and lose its data
                .build();
    }

    /* Static 'instance' method (singleton pattern)
     * Its purpose is to instantiate the class locally and return reference to that instance. */
    public static synchronized ReceivedRequestDatabaseClient getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new ReceivedRequestDatabaseClient(context);
        }
        return mInstance;
    }

    /* Returns a reference to the local instance (singleton pattern) */
    public ReceivedRequestDatabase getReceivedRequestDatabase() {
        return receivedRequestDatabase;
    }


    /*============================================================================================*/
    /* Main Routines... */

    /** Add a new received request record to the database
     * @param appContext        Application context.
     * @param requestMethod     Request method to save.
     * @param requestPath       Request path to save.
     * @param requestProtocol   Request protocol to save.
     * @param userAgent         Request user-agent to save.
     * @param contentType       Request content mime type to save.
     * @param body              Request data/body to save.
     */
    public void addRecord(final Context appContext, final String requestMethod, final String requestPath, final String requestProtocol, final String userAgent, final String contentType, final String body) {
        final String TAGG = "addRecord: ";

        new Thread(){
            @Override
            public void run() {
                this.setPriority(Thread.MIN_PRIORITY);
                logV(TAGG+"Background worker thread running for database operation.");

                try {
                    //create a record...
                    ReceivedRequest receivedRequest = new ReceivedRequest();                        //this is the object that we'll insert below

                    //add the provided data...
                    receivedRequest.setRequestMethod(requestMethod);
                    receivedRequest.setRequestPath(requestPath);
                    receivedRequest.setRequestProtocol(requestProtocol);
                    receivedRequest.setRequestUserAgent(userAgent);
                    receivedRequest.setRequestContentType(contentType);
                    receivedRequest.setRequestBody(body);

                    //initially set status...
                    receivedRequest.setStatus(ReceivedRequest.STATUS_NEW);

                    //initially set processed flag to nothing...
                    receivedRequest.setRequestProcessedAt(null);
                    receivedRequest.setRequestProcessedAtMs(null);

                    //populate timestamp fields...
                    Date currDateTime = new Date();
                    receivedRequest.setCreatedAt(currDateTime);
                    receivedRequest.setModifiedAt(currDateTime);

                    //add the record to database...
                    ReceivedRequestDatabaseClient.getInstance(appContext).getReceivedRequestDatabase().receivedRequestDao().addRecord(receivedRequest);
                } catch (Exception e) {
                    logE(TAGG+"Exception caught: "+e.getMessage());
                }
            }
        }.start();
    }

    /** Method to find and return all unprocessed requests.
     * NOTE: This is a blocking operation, and won't return until its sub-process is done.
     * @param appContext Application context.
     * @return List object containing any ReceivedRequest objects that might have been found.
     */
    public List<ReceivedRequest> findUnprocessedReceivedRequests(final Context appContext) {
        final String TAGG = "findUnprocessedReceivedRequests: ";

        FindUnprocessedReceivedRequests findUnprocessedReceivedRequests;
        findUnprocessedReceivedRequests = new FindUnprocessedReceivedRequests(appContext);

        findUnprocessedReceivedRequests.start();

        while (!findUnprocessedReceivedRequests.isDone) {
            //wait here until it's done
        }

        return findUnprocessedReceivedRequests.result;
    }

    /** Method to find and return all processed requests.
     * NOTE: This is a blocking operation, and won't return until its sub-process is done.
     * @param appContext Application context.
     * @return List object containing any ReceivedRequest objects that might have been found.
     */
    public List<ReceivedRequest> findProcessedReceivedRequests(final Context appContext) {
        final String TAGG = "findProcessedReceivedRequests: ";

        FindProcessedReceivedRequests findProcessedReceivedRequests;
        findProcessedReceivedRequests = new FindProcessedReceivedRequests(appContext);

        findProcessedReceivedRequests.start();

        while (!findProcessedReceivedRequests.isDone) {
            //wait here until it's done
        }

        return findProcessedReceivedRequests.result;
    }

    /** Method to invoke the database backend to find and delete any old already-forwarded records.
     * @param appContext Application context.
     */
    public void deleteAllForwarded_olderThan(final Context appContext, final String sqliteDatetimeModifier) {
        final String TAGG = "deleteAllForwarded_olderThan: ";

        new Thread(){
            @Override
            public void run() {
                this.setPriority(Thread.MIN_PRIORITY);
                logV(TAGG+"Background worker thread running for database operation.");

                try {
                    //run the query on the database
                    ReceivedRequestDatabaseClient.getInstance(appContext).getReceivedRequestDatabase().receivedRequestDao().deleteAllWithStatus_olderThan(ReceivedRequest.STATUS_FORWARDED, sqliteDatetimeModifier);
                } catch (Exception e) {
                    logE(TAGG+"Exception caught: "+e.getMessage());
                }
            }
        }.start();
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
                    ReceivedRequestDatabaseClient.getInstance(appContext).getReceivedRequestDatabase().receivedRequestDao().deleteAllProcessed_olderThan(sqliteDatetimeModifier);
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
                    ReceivedRequestDatabaseClient.getInstance(appContext).getReceivedRequestDatabase().receivedRequestDao().deleteAllUnprocessed_olderThan(sqliteDatetimeModifier);
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
    //public void deleteAll_olderThan(final Context appContext, final String sqliteDatetimeModifier) {
    public void deleteAll_olderThan(final Context appContext, final String date) {
        final String TAGG = "deleteAll_olderThan: ";

        new Thread(){
            @Override
            public void run() {
                this.setPriority(Thread.MIN_PRIORITY);
                logV(TAGG+"Background worker thread running for database operation.");

                try {
                    //run the query on the database
                    //ReceivedRequestDatabaseClient.getInstance(appContext).getReceivedRequestDatabase().receivedRequestDao().deleteAll_olderThan(sqliteDatetimeModifier);
                    ReceivedRequestDatabaseClient.getInstance(appContext).getReceivedRequestDatabase().receivedRequestDao().deleteAll_olderThan(date);
                } catch (Exception e) {
                    logE(TAGG+"Exception caught: "+e.getMessage());
                }
            }
        }.start();
    }

    /** Method to update an existing record.
     * You should first load in an existing ReceivedRequest, modify it, then feed it back to this method to do the update.
     * @param appContext                Application context.
     * @param updatedReceivedRequest    The modified record object to put back in the database.
     */
    public void updateRecord(final Context appContext, final ReceivedRequest updatedReceivedRequest) {
        final String TAGG = "updateReceivedRequest: ";

        new Thread(){
            @Override
            public void run() {
                this.setPriority(Thread.MIN_PRIORITY);
                logV(TAGG+"Background worker thread running for database operation.");

                try {
                    //update modified timestamp field...
                    updatedReceivedRequest.setModifiedAt(new Date());

                    //run the update on the database
                    ReceivedRequestDatabaseClient.getInstance(appContext).getReceivedRequestDatabase().receivedRequestDao().updateRecord(updatedReceivedRequest);
                } catch (Exception e) {
                    logE(TAGG+"Exception caught: "+e.getMessage());
                }
            }
        }.start();
    }


    /*============================================================================================*/
    /* Subclasses... */

    /** A thread to access the database and find all unprocessed requests.
     * You should wrap this in a loop to monitor for when it's done and its results are ready.
     *
     * It will find all requests that don't have a process-date or have a "new" status.
     *
     * Usage example:
     *  FindUnprocessedReceivedRequests findUnprocessedReceivedRequests;
     *  findUnprocessedReceivedRequests = new FindUnprocessedReceivedRequests(appContext);
     *  findUnprocessedReceivedRequests.start();
     *  while (!findUnprocessedReceivedRequests.isDone) {
     *      //wait here until it's done
     *  }
     *  return findUnprocessedReceivedRequests.result;
     */
    private class FindUnprocessedReceivedRequests extends Thread {
        private final String TAGG = this.getClass().getSimpleName();
        private Context appContext = null;
        List<ReceivedRequest> result = null;
        volatile boolean isDone = false;
        FindUnprocessedReceivedRequests(final Context appContext) {
            this.appContext = appContext;
        }
        @Override
        public void run() {
            final String TAGGG = "run: ";
            this.setPriority(Thread.MIN_PRIORITY);
            try {
                result = ReceivedRequestDatabaseClient.getInstance(appContext).getReceivedRequestDatabase().receivedRequestDao().getAllUnprocessedReceivedRequests();
            } catch (Exception e) {
                logE(TAGG+TAGGG+"Exception caught: "+e.getMessage());
            }
            this.isDone = true;
        }
    }

    /** A thread to access the database and find all already-processed requests.
     * You should wrap this in a loop to monitor for when it's done and its results are ready.
     *
     * Usage example:
     *  FindProcessedReceivedRequests findProcessedReceivedRequests;
     *  findProcessedReceivedRequests = new FindProcessedReceivedRequests(appContext);
     *  findProcessedReceivedRequests.start();
     *  while (!findProcessedReceivedRequests.isDone) {
     *      //wait here until it's done
     *  }
     *  return findProcessedReceivedRequests.result;
     */
    private class FindProcessedReceivedRequests extends Thread {
        private final String TAGG = this.getClass().getSimpleName();
        private Context appContext = null;
        List<ReceivedRequest> result = null;
        volatile boolean isDone = false;
        FindProcessedReceivedRequests(final Context appContext) {
            this.appContext = appContext;
        }
        @Override
        public void run() {
            final String TAGGG = "run: ";
            this.setPriority(Thread.MIN_PRIORITY);
            try {
                result = ReceivedRequestDatabaseClient.getInstance(appContext).getReceivedRequestDatabase().receivedRequestDao().getAllProcessedReceivedRequests();
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
