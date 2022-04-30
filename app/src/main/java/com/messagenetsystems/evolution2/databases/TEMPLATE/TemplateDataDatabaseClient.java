package com.messagenetsystems.evolution2.databases.TEMPLATE;

/* TemplateDataDatabaseClient class
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
 *  TemplateDataDatabaseClient templateDataDatabaseClient = TemplateDataDatabaseClient.getInstance(getApplicationContext());
 *  templateDataDatabaseClient.addRecord(getApplicationContext(), "My Data to Add");
 *
 * Revisions:
 *  2019.12.02      Chris Rider     Created (used ReceivedMessageDatabaseClient as a template).
 */

import android.arch.persistence.room.Room;
import android.content.Context;
import android.util.Log;

import com.bosphere.filelogger.FL;

import java.util.Date;
import java.util.List;


public class TemplateDataDatabaseClient {
    private final String TAG = TemplateDataDatabaseClient.class.getSimpleName();

    // Logging stuff...
    public static final int LOG_METHOD_LOGCAT = 1;
    public static final int LOG_METHOD_FILELOGGER = 2;
    private final int LOG_SEVERITY_V = 1;
    private final int LOG_SEVERITY_D = 2;
    private final int LOG_SEVERITY_I = 3;
    private final int LOG_SEVERITY_W = 4;
    private final int LOG_SEVERITY_E = 5;
    private int logMethod = LOG_METHOD_FILELOGGER;

    // Local stuff...
    private final String dbFilename = "db_templateData";        //this will be the filename in /data/user/0/[app]/databases/
    private static TemplateDataDatabaseClient mInstance;        //to support singleton pattern (maintains a static reference to the lone singleton instance - gets returned via getInstance)
    private TemplateDataDatabase templateDataDatabase;          //to support singleton pattern

    /* A private Constructor prevents any other class from instantiating (singleton pattern)
     * This should only execute when getInstance() is called. */
    private TemplateDataDatabaseClient(Context appContext) {
        //creating the database with Room database builder
        templateDataDatabase = Room.databaseBuilder(appContext, TemplateDataDatabase.class, dbFilename)
                .fallbackToDestructiveMigration()               //if schema updates, this database is OK to rebuild and lose its data
                .build();
    }

    /* Static 'instance' method (singleton pattern)
     * Its purpose is to instantiate the class locally and return reference to that instance. */
    public static synchronized TemplateDataDatabaseClient getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new TemplateDataDatabaseClient(context);
        }
        return mInstance;
    }

    /* Returns a reference to the local instance (singleton pattern) */
    public TemplateDataDatabase getTemplateDataDatabase() {
        return templateDataDatabase;
    }


    /*============================================================================================*/
    /* Main Routines... */

    /** Add a new record to the database
     * This automatically generates and assigns timestamps.
     * @param appContext    Application context.
     * @param dataToSave    Data to save.
     */
    public void addRecord(final Context appContext, final String dataToSave) {
        final String TAGG = "addRecord: ";

        new Thread(){
            @Override
            public void run() {
                logV(TAGG+"Background worker thread running for database operation.");

                try {
                    //create a record...
                    TemplateData templateData = new TemplateData();                        //this is the object that we'll insert below

                    //add the provided data...
                    templateData.setMY_DATA(dataToSave);

                    //populate timestamp fields...
                    Date currDateTime = new Date();
                    templateData.setCreatedAt(currDateTime);
                    templateData.setModifiedAt(currDateTime);

                    //add the record to database...
                    TemplateDataDatabaseClient.getInstance(appContext).getTemplateDataDatabase().templateDataDao().addRecord(templateData);
                } catch (Exception e) {
                    logE(TAGG+"Exception caught: "+e.getMessage());
                }
            }
        }.start();
    }

    /** Method to find and return all records.
     * NOTE: This is a blocking operation, and won't return until its sub-process is done.
     * @param appContext Application context.
     * @return List object containing any TemplateData objects that might have been found.
     */
    public List<TemplateData> findAllRecords(final Context appContext) {
        final String TAGG = "findAllRecords: ";

        FindAllRecords findAllRecords;
        findAllRecords = new FindAllRecords(appContext);

        findAllRecords.start();

        while (!findAllRecords.isDone) {
            //wait here until it's done
        }

        return findAllRecords.result;
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
                logV(TAGG+"Background worker thread running for database operation.");

                try {
                    //run the query on the database
                    TemplateDataDatabaseClient.getInstance(appContext).getTemplateDataDatabase().templateDataDao().deleteAll_olderThan(sqliteDatetimeModifier);
                } catch (Exception e) {
                    logE(TAGG+"Exception caught: "+e.getMessage());
                }
            }
        }.start();
    }

    /** Method to update an existing record.
     * You should first load in an existing TemplateData, modify it, then feed it back to this method to do the update.
     * @param appContext    Application context.
     * @param record        The modified record object to put back in the database.
     */
    public void updateRecord(final Context appContext, final TemplateData record) {
        final String TAGG = "updateRecord: ";

        new Thread(){
            @Override
            public void run() {
                logV(TAGG+"Background worker thread running for database operation.");

                try {
                    //update modified timestamp field...
                    record.setModifiedAt(new Date());

                    //run the update on the database
                    TemplateDataDatabaseClient.getInstance(appContext).getTemplateDataDatabase().templateDataDao().update(record);
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
        List<TemplateData> result = null;
        volatile boolean isDone = false;
        FindAllRecords(final Context appContext) {
            this.appContext = appContext;
        }
        @Override
        public void run() {
            final String TAGGG = "run: ";
            try {
                result = TemplateDataDatabaseClient.getInstance(appContext).getTemplateDataDatabase().templateDataDao().getAllRecords();
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
