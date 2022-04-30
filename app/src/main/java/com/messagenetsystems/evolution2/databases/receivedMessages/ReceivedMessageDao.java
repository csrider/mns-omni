package com.messagenetsystems.evolution2.databases.receivedMessages;

/* ReceivedMessageDao interface
 *
 * Define how we interact with the ReceivedMessage entity.
 * Think of this as where we build the interface between the DB and Java... it translates Java & SQL.
 * Room library will generate an implementation of defined methods.
 *
 * Dev-Note:
 *  If adding queries/operations, be sure to also add corresponding method to -client class.
 *
 * Revisions:
 *  2019.12.02      Chris Rider     Created (used ReceivedRequestDao as a template).
 *  2019.12.11      Chris Rider     Refactored to accommodate changed and added fields.
 *  2020.06.29      Chris Rider     New queries to find and cound all records containing some specified JSON, either portion or whole.
 */

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

@Dao
public interface ReceivedMessageDao {

    //TODO: See if there's some way to specify the ReceivedMessage.STATUS_* constants directly in the SQL, to make code more easily maintainable.

    /*============================================================================================*/
    /* Selection and find operations... */

    @Query("SELECT * FROM received_messages")
    List<ReceivedMessage> getAllReceivedMessages();

    @Query("SELECT * FROM received_messages " +
            "WHERE status = :status")
    List<ReceivedMessage> getAllReceivedMessagesWithStatus(int status);

    @Query("SELECT * FROM received_messages " +
            "WHERE processed_at IS NULL")
    List<ReceivedMessage> getAllUnprocessedReceivedMessages();

    @Query("SELECT * FROM received_messages " +
            "WHERE processed_at IS NOT NULL")
    List<ReceivedMessage> getAllProcessedReceivedMessages();

    @Query("SELECT * FROM received_messages " +
            "WHERE message_json LIKE :substringToFind")
    List<ReceivedMessage> getAllReceivedMessagesContainingJSON(String substringToFind);             // Provide a string with value something like -->  String myStr = "%\"dbb_rec_dtsec\":\"1277661599\",\"recno_zx\":\"209\"%"

    @Query("SELECT * FROM received_messages " +
            "WHERE message_json = :jsonFieldValueToMatch")
    List<ReceivedMessage> getAllReceivedMessagesWithMatchingWholeJSON(String jsonFieldValueToMatch);

    @Query("SELECT Count(*) FROM received_messages " +
            "WHERE message_json = :jsonFieldValueToMatch")
    int countReceivedMessagesWithMatchingWholeJSON(String jsonFieldValueToMatch);


    /*============================================================================================*/
    /* Update and edit operations... */
    /* Find and load a record first, then modify it there, then feed modified version back to here. */

    @Update
    void updateRecord(ReceivedMessage receivedMessage);


    /*============================================================================================*/
    /* Insert and add operations... */

    @Insert
    void addRecord(ReceivedMessage receivedMessage);


    /*============================================================================================*/
    /* Delete operations... */

    // Find and load a record first, then feed it back to here.
    @Delete
    void deleteRecord(ReceivedMessage receivedMessage);

    // Delete all records with specified status that are older than what is specified by argument
    // Modifier-format:     [+-]NNN years|months|days|hours|minutes|seconds
    // Modifier-example:    For older than 2 hours:     '-2 hours'
    @Query("DELETE FROM received_messages " +
            "WHERE " +
                "status = :status " +
                "AND " +
                "strftime('%s',datetime('now',:modifier)) > strftime('%s',modified_at)")
    void deleteAllWithStatus_olderThan(int status, String modifier);

    // Delete all processed records that are older than what is specified by argument
    // This should ideally never be required, but good to have just to avoid any risk of DB growing too large.
    // Modifier-format:     [+-]NNN years|months|days|hours|minutes|seconds
    // Modifier-example:    For older than 2 hours:     '-2 hours'
    @Query("DELETE FROM received_messages " +
            "WHERE " +
            "processed_at IS NOT NULL " +
            "AND " +
            "strftime('%s', datetime('now', :modifier)) > strftime('%s', modified_at)")
    void deleteAllProcessed_olderThan(String modifier);

    // Delete all unprocessed records that are older than what is specified by argument
    // This should ideally never be required, but good to have just to avoid any risk of DB growing too large.
    // Modifier-format:     [+-]NNN years|months|days|hours|minutes|seconds
    // Modifier-example:    For older than 2 hours:     '-2 hours'
    @Query("DELETE FROM received_messages " +
            "WHERE " +
                "processed_at IS NULL " +
                "AND " +
                "strftime('%s', datetime('now', :modifier)) > strftime('%s', modified_at)")
    void deleteAllUnprocessed_olderThan(String modifier);

    // Delete all records that are older than what is specified by argument
    // Other cleanups could/should take care of most stuff, but this can take care of any leftovers.
    // Modifier-format:     [+-]NNN years|months|days|hours|minutes|seconds
    // Modifier-example:    For older than 2 hours:     '-2 hours'
    @Query("DELETE FROM received_messages " +
            "WHERE strftime('%s', datetime('now', :modifier)) > strftime('%s', modified_at)")
    void deleteAll_olderThan(String modifier);
}
