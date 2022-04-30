package com.messagenetsystems.evolution2.databases.messages;

/* MessageDao interface
 *
 * Define how we interact with the @entity class.
 * Think of this as where we build the interface between the DB and Java... it translates Java & SQL.
 * Room library will generate an implementation of defined methods.
 *
 * Dev-Note:
 *  If adding queries/operations, be sure to also add corresponding method to -client class.
 *
 * Revisions:
 *  2019.12.03      Chris Rider     Created.
 *  2019.12.11      Chris Rider     Added query to delete all appropriately-flagged records.
 *  2019.12.17      Chris Rider     Added query to update status for specified record.
 *  2019.12.18      Chris Rider     Added update-json query, and fixed modified_at not updating with updateStatusFor.
 *  2020.01.22      Chris Rider     Added update method for new metadata field.
 *  2020.04.20      Chris Rider     Added find method for sorting by new receivedAt field.
 *  2020.06.17      Chris Rider     Added method to delete all records.
 */

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

@Dao
public interface MessageDao {

    /*============================================================================================*/
    /* Selection and find operations... */

    @Query("SELECT * FROM messages")
    List<Message> getAllRecords();

    @Query("SELECT * FROM messages ORDER BY received_at ASC")
    List<Message> getAllRecords_sortedByReceivedAscending();

    @Query("SELECT * FROM messages ORDER BY received_at DESC")
    List<Message> getAllRecords_sortedByReceivedDescending();

    @Query("SELECT * FROM messages WHERE msg_uuid=:uuid LIMIT 1")
    List<Message> getSpecificRecord_uuid(String uuid);

    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE msg_uuid=:uuid LIMIT 1)")
    Boolean doesMessageExist(String uuid);


    /*============================================================================================*/
    /* Update and edit operations... */
    /* Find and load a record first, then modify it there, then feed modified version back to here. */

    @Update
    void update(Message message);

    @Query("UPDATE messages " +
            "SET msg_json = :json, " +
                "modified_at = datetime('now') " +
            "WHERE msg_uuid = :uuid")
    void updateJsonFor(String uuid, String json);

    @Query("UPDATE messages " +
            "SET status = :status, "+
                "modified_at = datetime('now') " +
            "WHERE msg_uuid = :uuid")
    void updateStatusFor(String uuid, int status);

    @Query("UPDATE messages " +
            "SET meta_json = :meta, " +
            "modified_at = datetime('now') " +
            "WHERE msg_uuid = :uuid")
    void updateMetaFor(String uuid, String meta);


    /*============================================================================================*/
    /* Insert and add operations... */

    @Insert
    void addRecord(Message message);


    /*============================================================================================*/
    /* Delete operations... */

    // Find and load a record first, then feed it back to here.
    @Delete
    void delete(Message message);

    @Query("DELETE FROM messages WHERE msg_uuid = :uuid")
    void delete(String uuid);

    // Delete all records that are older than what is specified by argument
    // Other cleanups could/should take care of most stuff, but this can take care of any leftovers.
    // Modifier-format:     [+-]NNN years|months|days|hours|minutes|seconds
    // Modifier-example:    For older than 2 hours:     '-2 hours'
    @Query("DELETE FROM messages " +
            "WHERE strftime('%s', datetime('now', :modifier)) > strftime('%s', modified_at)")
    void deleteAll_olderThan(String modifier);

    // Delete all records that have a status matching what is specified by argument
    @Query("DELETE FROM messages " +
            "WHERE status=:status")
    void deleteAll_withStatus(int status);

    // Delete all records
    @Query("DELETE FROM messages")
    void deleteAll();
}
