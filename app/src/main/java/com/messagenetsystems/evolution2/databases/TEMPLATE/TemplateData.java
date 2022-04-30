package com.messagenetsystems.evolution2.databases.TEMPLATE;

/* TemplateData class, as Room DB Entity
 *
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
 * | String         MY_DATA                 |
 * | Date           created_at              |
 * | Date           modified_at             |
 * +----------------------------------------+
 *
 * Revisions:
 *  2019.12.02      Chris Rider     Created (used ReceivedMessage as a template).
 */

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.arch.persistence.room.TypeConverters;

import com.messagenetsystems.evolution2.databases.TimestampConverter;

import java.io.Serializable;
import java.util.Date;


@Entity(tableName = "template_data")
public class TemplateData implements Serializable {

    // Constants...


    /*============================================================================================*/
    /* Setup entity and columns */

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "MY_DATA")
    private String MY_DATA;

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

    /** Package-private, since only the DB client needs to access this */
    void setId(int id) {
        this.id = id;
    }

    //TODO: getters/setters for MY_DATA, etc. go here!!!
    public String getMY_DATA() {
        return MY_DATA;
    }
    public void setMY_DATA(String MY_DATA) {
        this.MY_DATA = MY_DATA;
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
}
