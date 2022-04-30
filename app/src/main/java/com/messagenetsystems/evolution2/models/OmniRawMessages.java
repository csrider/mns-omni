package com.messagenetsystems.evolution2.models;

/* OmniRawMessages
 * This is a container for a list of OmniRawMessage objects.
 *
 * Revisions:
 *  2019.12.09      Chris Rider     Created.
 *  2019.12.10      Chris Rider     Renamed from OmniMessages to OmniRawMessages.
 *  2019.12.11      Chris Rider     Added update and supporting find methods.
 *  2019.12.18      Chris Rider     Added ability to automatically sync with messages database.
 *  2020.01.22      Chris Rider     Added saving of metadata to update method when SYNC is true.
 *  2020.02.20      Chris Rider     Fixed bug where removeOmniRawMessage produced null-ref exception due to RAM clearing out before database -- OmniRawMessage.getMessageUUID() where OmniRawMessage became null.
 *  2020.04.20      Chris Rider     Added support for new field that lets us know when the message was originally received.
 */

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.databases.messages.Message;
import com.messagenetsystems.evolution2.databases.messages.MessageDatabaseClient;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.UUID;


public class OmniRawMessages extends ArrayList<OmniRawMessage> {
    private final String TAG = this.getClass().getSimpleName();

    // Constants...
    public static final boolean ADD_AVOIDING_DUPLICATES = true;
    public static final boolean ADD_IGNORING_DUPLICATES = false;
    public static final String ASCENDING = "ascending";     //just an arbitrary-value constant so we can make code and logs easy to read
    public static final String DESCENDING = "descending";   //just an arbitrary-value constant so we can make code and logs easy to read
    public static final boolean SYNC_DB_TRUE = true;
    public static final boolean SYNC_DB_FALSE = false;

    // Logging stuff...
    private final int LOG_SEVERITY_V = 1;
    private final int LOG_SEVERITY_D = 2;
    private final int LOG_SEVERITY_I = 3;
    private final int LOG_SEVERITY_W = 4;
    private final int LOG_SEVERITY_E = 5;
    private int logMethod = Constants.LOG_METHOD_LOGCAT;

    // Local stuff...
    private WeakReference<Context> appContextRef;
    private boolean doSyncWithDatabase;
    private MessageDatabaseClient messageDatabaseClient;


    /** Constructor
     * @param appContext            Application context.
     * @param logMethod             Logging method to use.
     * @param doSyncWithDatabase    Flag whether to automatically sync with messages database.
     */
    public OmniRawMessages(Context appContext, int logMethod, boolean doSyncWithDatabase) {
        Log.v(TAG, "Instantiating.");

        this.appContextRef = new WeakReference<Context>(appContext);
        this.logMethod = logMethod;
        this.doSyncWithDatabase = doSyncWithDatabase;

        // Prepare possible database access
        if (doSyncWithDatabase) {
            try {
                this.messageDatabaseClient = MessageDatabaseClient.getInstance(appContext);
            } catch (Exception e) {
                logE("Exception caught getting database client instance: " + e.getMessage());
                this.messageDatabaseClient = null;
            }

            if (this.messageDatabaseClient == null) {
                logE("No available database client instance, disabling sync.");
                this.doSyncWithDatabase = false;
            }
        }
    }


    /*============================================================================================*/
    /* Class methods */

    /** Add the provided OmniRawMessage object to the collection.
     * If DB sync flag is set, this will also add to DB if doesn't already exist.
     * @param omniRawMessage The OmniRawMessage object to add.
     * @return Whether the object was added.
     */
    public synchronized boolean addOmniRawMessage(@NonNull OmniRawMessage omniRawMessage, @NonNull boolean avoidDuplicates) {
        final String TAGG = "addOmniRawMessage: ";
        boolean ret;

        // Handle possibly adding the provided OmniRawMessage object to this list
        if (avoidDuplicates && doesOmniRawMessageExist(omniRawMessage)) {
            logD(TAGG+"Duplicate detected (and we were told avoid duplicates). Not adding.");
            ret = false;
        }
        else {
            try {
                ret = this.add(omniRawMessage);
            } catch (Exception e) {
                //the Collection interface throws an exception if something went wrong
                logW(TAGG + "Provided OmniRawMessage could not be added.");
                ret = false;
            }
        }

        // Handle possibly adding the provided OmniRawMessage object to the database
        // (the DB back-end/DAO/SQL figure the "possibly" out for us)
        if (doSyncWithDatabase) {
            try {
                this.messageDatabaseClient.addRecord(this.appContextRef.get(),
                        omniRawMessage.getMessageUUID().toString(),
                        omniRawMessage.getMessageJSONObject().toString(),
                        Message.STATUS_COPIED_FROM_RAM,
                        omniRawMessage.getReceivedAt());
            } catch (Exception e) {
                logE(TAGG + "Provided OmniRawMessage could not be added to DB."+e.getMessage());
                ret = false;
            }
        }

        logV(TAGG+"Returning "+Boolean.toString(ret)+" (at this time, there are "+String.valueOf(this.size())+" OmniRawMessage objects in the list).");
        return ret;
    }

    /** Check if provided OmniRawMessage object already exists in the dataset.
     * @param omniRawMessage The OmniRawMessage object to check for.
     * @return Whether the specified OmniRawMessage exists.
     */
    public boolean doesOmniRawMessageExist(@NonNull OmniRawMessage omniRawMessage) {
        final String TAGG = "doesOmniRawMessageExist: ";
        boolean ret = false;

        try {
            for (OmniRawMessage om : this) {
                if (om.getMessageUUID().equals(omniRawMessage.getMessageUUID())) {
                    ret = true;
                    break;
                }
            }
        } catch (Exception e) {
            logW(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning "+Boolean.toString(ret)+".");
        return ret;
    }

    /** Retrieves the OmniRawMessage object corresponding to the specified UUID.
     * @param uuid The UUID of the OmniRawMessage you want to get.
     * @return The OmniRawMessage object found with the specified UUID, or null if not found.
     */
    public OmniRawMessage getOmniRawMessage(@NonNull UUID uuid) {
        final String TAGG = "getOmniRawMessage: ";
        OmniRawMessage ret = null;

        try {
            for (OmniRawMessage om : this) {
                if (om.getMessageUUID().equals(uuid)) {
                    ret = om;
                    break;
                }
            }
        } catch (Exception e) {
            logW(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning OmniRawMessage object for "+uuid.toString()+".");
        return ret;
    }

    /** Retrieves the OmniRawMessage object's position in the list.
     * @param uuid The UUID of the OmniRawMessage you want to find.
     * @return The position in the list that it was found, or -1 if not found.
     */
    public int getOmniRawMessageListPosition(@NonNull UUID uuid) {
        final String TAGG = "getOmniRawMessageListPosition: ";
        int ret = -1;

        try {
            for (int i = 0; i < this.size(); i++) {
                if (this.get(i).getMessageUUID().equals(uuid)) {
                    ret = i;
                    break;
                }
            }
        } catch (Exception e) {
            logW(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning int "+String.valueOf(ret)+".");
        return ret;
    }

    /** Retrieves the OmniRawMessage object's position in the list.
     * @param omniRawMessage The OmniRawMessage you want to find.
     * @return The position in the list that it was found, or -1 if not found.
     */
    public int getOmniRawMessageListPosition(@NonNull OmniRawMessage omniRawMessage) {
        final String TAGG = "getOmniRawMessageListPosition: ";
        int ret = -1;

        try {
            for (int i = 0; i < this.size(); i++) {
                if (this.get(i).getMessageUUID().equals(omniRawMessage.getMessageUUID())) {
                    ret = i;
                    break;
                }
            }
        } catch (Exception e) {
            logW(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning int "+String.valueOf(ret)+".");
        return ret;
    }

    /** Remove the specified OmniRawMessage object from the collection.
     * @param omniRawMessage The OmniRawMessage object to find and remove.
     * @return Whether the object was removed from RAM.
     */
    public synchronized boolean removeOmniRawMessage(@NonNull OmniRawMessage omniRawMessage) {
        final String TAGG = "removeOmniRawMessage: ";
        boolean ret;

        // Handle possibly removing the provided OmniRawMessage object from the database
        // (the DB back-end/DAO/SQL figure the "possibly" out for us)
        if (doSyncWithDatabase && omniRawMessage != null) {
            try {
                this.messageDatabaseClient.deleteRecord(this.appContextRef.get(),
                        omniRawMessage.getMessageUUID());
            } catch (Exception e) {
                logE(TAGG + "Provided OmniRawMessage could not be removed from DB."+e.getMessage());
            }
        }

        try {
            ret = this.remove(omniRawMessage);
        } catch (Exception e) {
            //the Collection interface throws an exception if something went wrong
            logW(TAGG+"Specified OmniRawMessage could not be removed from RAM.");
            ret = false;
        }

        logV(TAGG+"Returning "+Boolean.toString(ret)+".");
        return ret;
    }

    /** Find and update an existing OmniRawMessage with the provided one.
     * Uses the UUID value to match (which should never change).
     * @param omniRawMessage The OmniRawMessage object to update into the list.
     * @return Boolean of whether any change occurred.
     */
    public synchronized boolean updateOmniRawMessage(@NonNull OmniRawMessage omniRawMessage) {
        final String TAGG = "updateOmniRawMessage: ";
        boolean ret;

        try {
            // Find the position of the matching existing object
            int positionOfExistingMatch = getOmniRawMessageListPosition(omniRawMessage);
            if (positionOfExistingMatch < 0 || positionOfExistingMatch >= this.size()) {
                logW(TAGG+"Invalid matching position. Cannot continue.");
                return false;
            }

            // Check if the object is equal to its matching object already (no need to update if it is)
            //logE(TAGG+"\nOrig = \""+this.get(positionOfExistingMatch).getMetadataJSONObject().toString()+"\"\nArg = \""+omniRawMessage.getMetadataJSONObject().toString()+"\""); //TODO REMOVE AFTER DEBUGGING
            if (this.get(positionOfExistingMatch).equals(omniRawMessage)) {
                logD(TAGG+"Existing match is the same as provided. No update necessary.");
                return false;
            }

            // Update the item at the found position (returns previous object that was replaced)
            OmniRawMessage omniRawMesssage_prev = this.set(positionOfExistingMatch, omniRawMessage);

            // Check if the change was successful or not
            if (omniRawMesssage_prev.equals(omniRawMessage)) {
                logW(TAGG+"Specified OmniRawMessage did not result in a change!");
                ret = false;
            } else {
                ret = true;
            }
        } catch (Exception e) {
            //the Collection interface throws an exception if something went wrong
            logW(TAGG+"Specified OmniRawMessage could not be updated.");
            ret = false;
        }

        // Handle possibly updating the provided OmniRawMessage object in the database
        // (the DB back-end/DAO/SQL figure the "possibly" out for us)
        doFlushOmniRawMessageToDB(omniRawMessage);

        logV(TAGG+"Returning "+Boolean.toString(ret)+".");
        return ret;
    }

    public void doFlushOmniRawMessageToDB(OmniRawMessage omniRawMessage) {
        final String TAGG = "doFlushOmniRawMessageToDB: ";

        if (doSyncWithDatabase) {
            try {
                this.messageDatabaseClient.updateJsonFor(this.appContextRef.get(),
                        omniRawMessage.getMessageUUID(),
                        omniRawMessage.getMessageJSONObject());
                this.messageDatabaseClient.updateMetaFor(this.appContextRef.get(),
                        omniRawMessage.getMessageUUID(),
                        omniRawMessage.getMetadataJSONObject());
                this.messageDatabaseClient.updateStatusFor(this.appContextRef.get(),
                        omniRawMessage.getMessageUUID(),
                        Message.STATUS_COPIED_FROM_RAM);
            } catch (Exception e) {
                logE(TAGG + "Provided OmniRawMessage could not be updated in DB."+e.getMessage());
            }
        }
    }


    /*============================================================================================*/
    /* Class housekeeping methods */

    /** Call this to cleanup everything. */
    public void cleanup() {
        final String TAGG = "cleanup: ";

        try {
            this.clear();
        } catch (Exception e) {
            logE(TAGG+"Exception caught invoking .interrupt(): "+e.getMessage());
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
