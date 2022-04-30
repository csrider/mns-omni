package com.messagenetsystems.evolution2.databases.receivedRequests;

/* ReceivedRequest class, as Room DB Entity
 *
 * Note: Think of this as a one-row table with a single record.
 * The Room library will handle creating a multi-record table for us.
 *
 * DEV-NOTE...
 * If you change the scheme (e.g. add fields, etc.), you MUST also update version number in the
 *  corresponding Database file's @Database annotation (e.g. ReceivedRequestDatabase.java)! If you
 *  don't, the result will be a runtime exception.
 * Alongside this requirement, you must also be sure to provide a migration option
 *  (RoomDatabase.Builder.addMigration(Migration ...) or allow for destructive migrations via one
 *  of the RoomDatabase.Builder.fallbackToDestructiveMigration* methods.
 *
 * +----------------------------------------+
 * | active_message                         |
 * +----------------------------------------+
 * |*int            id                      |
 * | String         request_method          |
 * | String         request_user_agent      |
 * | String         request_host            |
 * | String         request_type            |
 * | String         request_body            |
 * | byte           status                  |
 * | Date           request_processed_at    |
 * | String         request_processed_at_ms |
 * | Date           created_at              |
 * | Date           modified_at             |
 * +----------------------------------------+
 *
 * Revisions:
 *  2019.08.28      Chris Rider     Created (originally for v1 ActiveMsg but not finished).
 *  2019.11.21      Chris Rider     Migrated to v2 and adapted for received requests.
 *  2019.11.25      Chris Rider     Added field for processing-date.
 *  2019.11.26      Chris Rider     Added field for processing-date in milliseconds from epoch.
 *                                  Added field for status, and constants to use for that field.
 *  2019.12.11      Chris Rider     Updated String-requestStatus to int-status, and tweaked status constants.
 *  2020.02.18      Chris Rider     Added constant for application type.
 *  2020.08.21      Chris Rider     Optimized memory usage by changing status INT to BYTE, and added more STATUS constants.
 */

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.arch.persistence.room.TypeConverters;

import com.messagenetsystems.evolution2.databases.TimestampConverter;

import java.io.Serializable;
import java.util.Date;


@Entity(tableName = "received_requests")
public class ReceivedRequest implements Serializable {

    // Constants
    public static final String CONTENT_TYPE_TYPE_TEXT = "text";
    public static final String CONTENT_TYPE_TYPE_APPLICATION = "application";
    public static final String CONTENT_TYPE_SUBTYPE_JSON = "json";

    public static final byte STATUS_UNKNOWN = 0;
    public static final byte STATUS_NEW = 1;
    public static final byte STATUS_FORWARDED = 2;
    public static final byte STATUS_PROCESSED = 3;
    public static final byte STATUS_PROCESSING_ERROR = 4;


    /*============================================================================================*/
    /* Setup entity and columns */

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "request_method")
    private String requestMethod;

    @ColumnInfo(name = "request_path")
    private String requestPath;

    @ColumnInfo(name = "request_protocol")
    private String requestProtocol;

    @ColumnInfo(name = "request_user_agent")
    private String requestUserAgent;

    @ColumnInfo(name = "request_content_type")
    private String requestContentType;

    @ColumnInfo(name = "request_body")
    private String requestBody;

    @ColumnInfo(name = "status")
    private byte status;

    @ColumnInfo(name = "request_processed_at")
    @TypeConverters({TimestampConverter.class})
    private Date requestProcessedAt;

    @ColumnInfo(name = "request_processed_at_ms")
    private String requestProcessedAtMs;

    @ColumnInfo(name = "created_at")
    @TypeConverters({TimestampConverter.class})
    private Date createdAt;

    @ColumnInfo(name = "modified_at")
    @TypeConverters({TimestampConverter.class})
    private Date modifiedAt;


    /*============================================================================================*/
    /* Getters & Setters */

    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }

    public String getRequestMethod() {
        return requestMethod;
    }
    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
    }

    public String getRequestPath() {
        return requestPath;
    }
    public void setRequestPath(String requestPath) {
        this.requestPath = requestPath;
    }

    public String getRequestProtocol() {
        return requestProtocol;
    }
    public void setRequestProtocol(String requestProtocol) {
        this.requestProtocol = requestProtocol;
    }

    public String getRequestUserAgent() {
        return requestUserAgent;
    }
    public void setRequestUserAgent(String requestUserAgent) {
        this.requestUserAgent = requestUserAgent;
    }

    public String getRequestContentType() {
        return requestContentType;
    }
    public void setRequestContentType(String requestContentType) {
        this.requestContentType = requestContentType;
    }

    public String getRequestBody() {
        return requestBody;
    }
    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
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
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
    public void setCreatedAt() {
        setCreatedAt(new Date());
    }

    public Date getModifiedAt() {
        return modifiedAt;
    }
    public void setModifiedAt(Date modifiedAt) {
        this.modifiedAt = modifiedAt;
    }
    public void setModifiedAt() {
        setModifiedAt(new Date());
    }
}
