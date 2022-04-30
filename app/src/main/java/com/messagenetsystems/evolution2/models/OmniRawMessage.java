package com.messagenetsystems.evolution2.models;

/* OmniRawMessage model class
 * This is the raw Omni internal message model (essentially a Java data-typed version of Message DB record).
 *
 * It merely contains basically the same data as in ReceivedMessage (only with Java object types,
 * rather than the simple primitives that the Room-DB requires. The idea is to make that disk-based
 * database accessible in RAM for faster operations. MessageRawDataProcessor syncs data between them.
 *
 * It also contains any changed/updated data from the delivery processes, that needs to go back into
 * the Room-DB for persistent storage. Refer to OmniRawMessages for the inner-workings of that logic.
 *
 * Lifecycle:
 *  1. Created by MessageRawDataProcessor, with a copy of Message-entity's raw data (remember, this is just a RAM copy of a read-in Message object)
 *  2. Passed to MessageService.OmniMessageRawHandler (which may add it to MessageService.omniRawMessages for easy RAM-based access)
 *  3. Is used (from MessageService RAM) by MessageDeliverableProcessor as needed)
 *  4. MessageRawDataProcessor requests removal from RAM upon expiration... TODO: more?
 *  5.....
 *
 * Revisions:
 *  2019.12.09      Chris Rider     Created.
 *  2019.12.10      Chris Rider     Renamed from OmniMessage to OmniRawMessage.
 *  2019.12.11      Chris Rider     Override of .equals() method to check member field values rather than same instance.
 *                                  Added statusMessageDB, and updated status constants to work with our Message-DB-record counterpart, as well as OmniMessage stuff.
 *  2019.12.17      Chris Rider     Updated and filled out more lifecycle notes.
 *  2020.01.22      Chris Rider     Added field for metadata.
 *  2020.01.31      Chris Rider     Implemented copy constructor for creating copies instead of pass by reference.
 *  2020.04.20      Chris Rider     Added members and methods for receivedAt field value (for when message was originally received).
 */

import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;

import org.json.JSONObject;

import java.util.Date;
import java.util.UUID;


public class OmniRawMessage {
    private final String TAG = this.getClass().getSimpleName();

    // Constants...
    public final static int STATUS_UNKNOWN = 0;
    public final static int STATUS_NEW_FROM_DB = 1;                 //OmniRawMessage has been created from Message DB
    public final static int STATUS_SYNCED_FROM_OMNIMESSAGE = 2;     //OmniRawMessage has been synced from its corresponding OmniMessage
    public final static int STATUS_UPDATED_TO_DB = 3;               //OmniRawMessage has been synced to its corresponding Message DB record

    // Logging stuff...
    private final int LOG_SEVERITY_V = 1;
    private final int LOG_SEVERITY_D = 2;
    private final int LOG_SEVERITY_I = 3;
    private final int LOG_SEVERITY_W = 4;
    private final int LOG_SEVERITY_E = 5;
    private int logMethod = Constants.LOG_METHOD_LOGCAT;

    // Local stuff...


    // Model members...
    // WARNING: If adding/modifying, be sure to update .equal() override, as well as getters/setters!
    private Date createdAt;
    private Date modifiedAt;
    private Date receivedAt;
    private int status;
    private int statusMessageDB;
    private UUID messageUUID;
    private JSONObject messageJSONObject;
    private JSONObject metadataJSONObject;


    /** Constructor
     * @param logMethod     Logging method to use
     */
    public OmniRawMessage(int logMethod) {
        Log.v(TAG, "Instantiating.");

        this.logMethod = logMethod;

        try {

        } catch (Exception e) {
            logE("Exception caught instantiating "+TAG+": "+e.getMessage());
        }


    }

    /** Copy-constructor
     * DEV-NOTE: Don't forget update/add members here, as modified in this class, for complete deep-copy reliability.
     */
    OmniRawMessage(OmniRawMessage omniRawMessageToCopy) {
        Log.v(TAG, "Copy-constructor invoked.");

        createdAt = omniRawMessageToCopy.createdAt;
        modifiedAt = omniRawMessageToCopy.modifiedAt;
        receivedAt = omniRawMessageToCopy.receivedAt;
        status = omniRawMessageToCopy.status;
        statusMessageDB = omniRawMessageToCopy.statusMessageDB;
        messageUUID = omniRawMessageToCopy.messageUUID;
        messageJSONObject = omniRawMessageToCopy.messageJSONObject;
        metadataJSONObject = omniRawMessageToCopy.metadataJSONObject;
    }

    /** Override our .equals() method to actually check equality of member field values (deep comparison).
     * If you don't do this, the native method will only check if the provided object is the same instance.
     * @param o Another OmniRawMessage object to check for equality of member field values.
     * @return Whether member field values equal those of the provided object or not.
     */
    @Override
    public boolean equals(Object o) {
        final String TAGG = "equals: ";

        //The object instances are actually the very same instance, so their values obviously equal
        if (this == o) {
            logW(TAGG+"Provided object is the exact same instance. Returning true.");
            return true;
        }

        // The provided object is null or not the same class, so obviously not equal
        if (o == null || getClass() != o.getClass()) {
            logW(TAGG+"Provided object is null or not an OmniRawMessage object. Returning false.");
            return false;
        }

        // Now we can go ahead and check member-values for equality
        OmniRawMessage omniRawMessage = (OmniRawMessage) o;
        boolean ret = messageUUID.toString().equals(omniRawMessage.messageUUID.toString()) &&
                messageJSONObject.toString().equals(omniRawMessage.messageJSONObject.toString()) &&
                metadataJSONObject.toString().equals(omniRawMessage.metadataJSONObject.toString()) &&
                status == omniRawMessage.status &&
                statusMessageDB == omniRawMessage.statusMessageDB;

        logV(TAGG+"Returning "+Boolean.toString(ret)+".");
        return ret;
    }


    /*============================================================================================*/
    /* Getter & Setter Methods */

    public UUID getMessageUUID() {
        return messageUUID;
    }

    public void setMessageUUID(UUID messageUUID) {
        this.messageUUID = messageUUID;
    }

    public JSONObject getMessageJSONObject() {
        return messageJSONObject;
    }

    public void setMessageJSONObject(JSONObject messageJSONObject) {
        this.messageJSONObject = messageJSONObject;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getModifiedAt() {
        return modifiedAt;
    }

    public void setModifiedAt(Date modifiedAt) {
        this.modifiedAt = modifiedAt;
    }

    public Date getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Date receivedAt) {
        this.receivedAt = receivedAt;
    }

    public int getStatusMessageDB() {
        return statusMessageDB;
    }

    public void setStatusMessageDB(int statusMessageDB) {
        this.statusMessageDB = statusMessageDB;
    }

    public JSONObject getMetadataJSONObject() {
        return metadataJSONObject;
    }

    public void setMetadataJSONObject(JSONObject metadataJSONObject) {
        this.metadataJSONObject = metadataJSONObject;
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
