package com.messagenetsystems.evolution2.databases.receivedRequests;

/* ReceivedRequestDao interface
 *
 * Define how we interact with the ReceivedRequest entity.
 * Think of this as where we build the interface between the DB and Java... it translates Java & SQL.
 * Room library will generate an implementation of defined methods.
 *
 * Dev-Note:
 *  If adding queries/operations, be sure to also add corresponding method to -client class.
 *
 * Revisions:
 *  2019.08.28      Chris Rider     Created (originally for v1 ActiveMsg but not finished).
 *  2019.11.21      Chris Rider     Migrated to v2 and adapted for received requests.
 *  2019.11.25      Chris Rider     Added some queries and other CRUD operations.
 *  2019.11.26      Chris Rider     Added query to delete any month-old, already-processed records.
 *  2019.12.02      Chris Rider     Added query to select records marked as processed valid messages.
 *  2019.12.11      Chris Rider     Refactored queries to make more sense and be more consistent with lessons learned in later classes.
 *  2020.09.24      Chris Rider     Updated deleteAll_olderThan method to use 'created_at' field.
 */

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

@Dao
public interface ReceivedRequestDao {

    /*============================================================================================*/
    /* Selection and find operations... */

    @Query("SELECT * FROM received_requests")
    List<ReceivedRequest> getAllReceivedRequests();

    @Query("SELECT * FROM received_requests " +
            "WHERE status = :status")
    List<ReceivedRequest> getAllReceivedRequestsWithStatus(int status);

    @Query("SELECT * FROM received_requests " +
            "WHERE request_processed_at IS NULL")
    List<ReceivedRequest> getAllUnprocessedReceivedRequests();

    @Query("SELECT * FROM received_requests " +
            "WHERE request_processed_at IS NOT NULL")
    List<ReceivedRequest> getAllProcessedReceivedRequests();


    /*============================================================================================*/
    /* Update and edit operations... */
    /* Find and load a record first, then modify it there, then feed modified version back to here. */

    @Update
    void updateRecord(ReceivedRequest receivedRequest);


    /*============================================================================================*/
    /* Insert and add operations... */

    @Insert
    void addRecord(ReceivedRequest receivedRequest);


    /*============================================================================================*/
    /* Delete operations... */

    // Find and load a record first, then feed it back to here.
    @Delete
    void deleteRecord(ReceivedRequest receivedRequest);

    // Delete all records with specified status that are older than what is specified by argument
    // Modifier-format:     [+-]NNN years|months|days|hours|minutes|seconds
    // Modifier-example:    For older than 2 hours:     '-2 hours'
    @Query("DELETE FROM received_requests " +
            "WHERE " +
            "status = :status " +
            "AND " +
            "datetime(request_processed_at_ms/1000, 'unixepoch') < datetime('now', :modifier)")
    void deleteAllWithStatus_olderThan(int status, String modifier);

    // Delete all already-processed records that are older than what is specified by argument
    // Other cleanups could/should take care of most stuff, but this can take care of any leftovers.
    // Modifier-format:     [+-]NNN years|months|days|hours|minutes|seconds
    // Modifier-example:    For older than 2 hours:     '-2 hours'
    @Query("DELETE FROM received_requests " +
            "WHERE " +
                "request_processed_at IS NOT NULL " +
                "AND " +
                "datetime(request_processed_at_ms/1000, 'unixepoch') < datetime('now', :modifier)")
    void deleteAllProcessed_olderThan(String modifier);

    // Delete all unprocessed records that are older than what is specified by argument
    // This should ideally never be required, but good to have just to avoid any risk of DB growing too large.
    // Modifier-format:     [+-]NNN years|months|days|hours|minutes|seconds
    // Modifier-example:    For older than 2 hours:     '-2 hours'
    @Query("DELETE FROM received_requests " +
            "WHERE " +
            "request_processed_at IS NULL " +
            "AND " +
            "datetime(request_processed_at_ms/1000, 'unixepoch') < datetime('now', :modifier)")
    void deleteAllUnprocessed_olderThan(String modifier);

    // Delete all records that are older than what is specified by argument
    // Other cleanups could/should take care of most stuff, but this can take care of any leftovers.
    // Date-string example: "2020-09-07"
    // EXAMPLE: sqlite3 db_receivedRequests "select * from received_requests where created_at<Date(\"2020-09-07\");"
    @Query("DELETE FROM received_requests " +
            "WHERE created_at<Date(:date)")
    void deleteAll_olderThan(String date);
}
