package com.messagenetsystems.evolution2.databases.messages;

/* Message class, as Room DB Entity
 * This is the main database of messages for this sign.
 * We need a persistent repository, in case app crashes or device restarts, so we can keep track of certain message fields (like audio repeats, etc).
 *
 * Note: Think of this as a one-row table with a single record.
 * The Room library will handle creating a multi-record table for us.
 *
 * DEV-NOTE...
 * If you change the schema (e.g. add fields, etc.), you MUST also update version number in the
 *  corresponding Database file's @Database annotation (e.g. ReceivedRequestDatabase.java)! If you
 *  don't, the result will be a runtime exception.
 * Alongside this requirement, you must also be sure to provide a migration option
 *  (RoomDatabase.Builder.addMigration(Migration ...) or allow for destructive migrations via one
 *  of the RoomDatabase.Builder.fallbackToDestructiveMigration* methods.
 *
 * Message Record Lifecycle in DB:
 *  1. Created by MessageRawDataProcessor (with STATUS_NEW)
 *  2. Copied to RAM-based MessageService.omniRawMessages (with STATUS_COPIED_TO_RAM)
 *  3. Updated periodically, as needed, with latest data in corresponding MessageService.omniRawMessages[OmniRawMessage] object (with STATUS_COPIED_FROM_RAM), as long as it's alive
 *  4. Deleted once status become STATUS_HOUESKEEP_DELETE (as set in step-3 update above)
 *
 * +----------------------------------------+
 * | message                                |
 * +----------------------------------------+
 * |*int            id                      |
 * | Date           created_at              |
 * | Date           modified_at             |
 * | Date           received_at             |
 * | int            status                  |
 * | String         msg_uuid                |
 * | String         msg_json                |
 * +----------------------------------------+
 *
 * Revisions:
 *  2019.12.03      Chris Rider     Created.
 *  2019.12.11      Chris Rider     Added status field (to support disk/RAM sync and related operations, initially).
 *  2019.12.18      Chris Rider     Made msg_uuid unique, so the DB won't allow adding a record with duplicate UUID value.
 *  2020.01.22      Chris Rider     Added field for metadata (e.g. ScrollsDone, etc.).
 *  2020.04.20      Chris Rider     Added field for received-at date (should coincide with "processed_at" field of ReceivedMessage record).
 */

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;
import android.arch.persistence.room.TypeConverters;

import com.messagenetsystems.evolution2.databases.TimestampConverter;

import java.io.Serializable;
import java.util.Date;


@Entity(tableName = "messages",
        indices = {@Index(value = {"msg_uuid"},
        unique = true)})
public class Message implements Serializable {

    // Constants...
    public final static int STATUS_UNKNOWN = 0;
    public final static int STATUS_NEW = 1;                 //Message record is new (copied from ReceivedMessages and not yet processed by MessageRawDataProcessor)
    public final static int STATUS_COPIED_TO_RAM = 2;       //Message record has been copied to OmniRawMessages
    public final static int STATUS_COPIED_FROM_RAM = 3;    //Message record has been updated with data from its OmniRawMessage companion
    public final static int STATUS_HOUSEKEEP_DELETE = 4;    //Message record is ready to be deleted by MessageRawDataProcessor's housekeeping routine

    /*============================================================================================*/
    /* Setup entity and columns */

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "created_at")
    @TypeConverters({TimestampConverter.class})
    private Date createdAt;

    @ColumnInfo(name = "modified_at")
    @TypeConverters({TimestampConverter.class})
    private Date modifiedAt;

    @ColumnInfo(name = "received_at")
    @TypeConverters({TimestampConverter.class})
    private Date receivedAt;

    @ColumnInfo(name = "status")
    private int status;

    @ColumnInfo(name = "msg_uuid")
    private String msgUUID;

    @ColumnInfo(name = "msg_json")
    private String msgJSON;

    @ColumnInfo(name = "meta_json")
    private String metaJSON;


    /*============================================================================================*/
    /* Getters & Setters */

    public int getId() {
        return id;
    }

    /** Package-private, since only the DB client needs to access this */
    void setId(int id) {
        this.id = id;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    /** Package-private, since only the DB client needs to access this */
    void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
    void setCreatedAt() {
        setCreatedAt(new Date());
    }

    public Date getModifiedAt() {
        return modifiedAt;
    }

    /** Package-private, since only the DB client needs to access this */
    void setModifiedAt(Date modifiedAt) {
        this.modifiedAt = modifiedAt;
    }
    void setModifiedAt() {
        setModifiedAt(new Date());
    }

    public Date getReceivedAt() {
        return receivedAt;
    }

    /** Package-private, since only the DB client needs to access this */
    void setReceivedAt(Date receivedAt) {
        this.receivedAt = receivedAt;
    }
    void setReceivedAt() {
        setReceivedAt(new Date());
    }

    public String getMsgUUID() {
        return msgUUID;
    }

    /** Package-private, since only the DB client needs to access this */
    void setMsgUUID(String msgUUID) {
        this.msgUUID = msgUUID;
    }

    public String getMsgJSON() {
        return msgJSON;
    }

    public void setMsgJSON(String msgJSON) {
        this.msgJSON = msgJSON;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getMetaJSON() {
        return metaJSON;
    }

    public void setMetaJSON(String metaJSON) {
        this.metaJSON = metaJSON;
    }
}
