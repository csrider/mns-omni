package com.messagenetsystems.evolution2.databases.receivedMessages;

/* ReceivedMessage class, as Room DB Entity
 * This is where all received_requests records that get forwarded as a message go.
 * From here, an appropriate message-handling process will do what's needed.
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
 * +----------------------------------------+
 * | message                                |
 * +----------------------------------------+
 * |*int            id                      |
 * | String         message_uuid            |
 * | String         message_json            |
 * | byte           status                  |
 * | Date           processed_at            |
 * | String         processed_at_ms         |
 * | Date           created_at              |
 * | Date           modified_at             |
 * | Date           received_at             |
 * +----------------------------------------+
 *
 * Revisions:
 *  2019.12.02      Chris Rider     Created (used ReceivedRequest as a template).
 *  2019.12.11      Chris Rider     Renamed constant from STATUS_ALREADY_PROCESSED to STATUS_PROCESSED, and refactored as int type; added processed fields.
 *  2020.04.20      Chris Rider     Added received_at field (which will be populate with the timestamp the original request was received.
 *  2020.08.21      Chris Rider     Optimized memory usage by changing status INT to BYTE, and added more STATUS constants.
 */

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.arch.persistence.room.TypeConverters;

import com.messagenetsystems.evolution2.databases.TimestampConverter;

import java.io.Serializable;
import java.util.Date;


@Entity(tableName = "received_messages")
public class ReceivedMessage implements Serializable {

    // Constants
    public static final byte STATUS_UNKNOWN = 0;
    public static final byte STATUS_NEW = 1;
    public static final byte STATUS_FORWARDED = 2;
    public static final byte STATUS_PROCESSED = 3;
    public static final byte STATUS_PROCESSING_ERROR = 4;


    /*============================================================================================*/
    /* Setup entity and columns */

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "message_uuid")
    private String messageUUID;

    @ColumnInfo(name = "message_json")
    private String messageJson;

    @ColumnInfo(name = "status")
    private byte status;

    @ColumnInfo(name = "processed_at")
    @TypeConverters({TimestampConverter.class})
    private Date requestProcessedAt;

    @ColumnInfo(name = "processed_at_ms")
    private String requestProcessedAtMs;

    @ColumnInfo(name = "created_at")
    @TypeConverters({TimestampConverter.class})
    private Date createdAt;

    @ColumnInfo(name = "modified_at")
    @TypeConverters({TimestampConverter.class})
    private Date modifiedAt;

    @ColumnInfo(name = "received_at")
    @TypeConverters({TimestampConverter.class})
    private Date receivedAt;


    /*============================================================================================*/
    /* Getters & Setters */

    public int getId() {
        return id;
    }

    /** Package-private, since only the DB client needs to access this */
    void setId(int id) {
        this.id = id;
    }

    public String getMessageUUID() {
        return messageUUID;
    }

    /** Package-private, since only the DB client needs to access this */
    void setMessageUUID(String messageUUID) {
        this.messageUUID = messageUUID;
    }

    public String getMessageJson() {
        return messageJson;
    }

    public void setMessageJson(String messageJson) {
        this.messageJson = messageJson;
    }

    public byte getStatus() {
        return status;
    }
    public void setStatus(byte requestStatus) {
        this.status = requestStatus;
    }

    public Date getRequestProcessedAt() {
        return requestProcessedAt;
    }
    public void setRequestProcessedAt(Date processedAt) {
        this.requestProcessedAt = processedAt;
    }
    public void setRequestProcessedAt() {
        setRequestProcessedAt(new Date());
    }

    public String getRequestProcessedAtMs() {
        return requestProcessedAtMs;
    }
    public void setRequestProcessedAtMs(String processedAtMs) {
        this.requestProcessedAtMs = processedAtMs;
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
}
