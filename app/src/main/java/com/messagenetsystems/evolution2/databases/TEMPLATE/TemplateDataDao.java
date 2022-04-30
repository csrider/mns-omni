package com.messagenetsystems.evolution2.databases.TEMPLATE;

/* TemplateDataDao interface
 *
 * Define how we interact with the @entity class.
 * Think of this as where we build the interface between the DB and Java... it translates Java & SQL.
 * Room library will generate an implementation of defined methods.
 *
 * Dev-Note:
 *  If adding queries/operations, be sure to also add corresponding method to -client class.
 *
 * Revisions:
 *  2019.12.02      Chris Rider     Created (used ReceivedMessageDao as a template).
 */

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

@Dao
public interface TemplateDataDao {

    /*============================================================================================*/
    /* Selection and find operations... */

    @Query("SELECT * FROM template_data")
    List<TemplateData> getAllRecords();


    /*============================================================================================*/
    /* Update and edit operations... */
    /* Find and load a record first, then modify it there, then feed modified version back to here. */

    @Update
    void update(TemplateData templateData);


    /*============================================================================================*/
    /* Insert and add operations... */

    @Insert
    void addRecord(TemplateData templateData);


    /*============================================================================================*/
    /* Delete operations... */

    // Find and load a record first, then feed it back to here.
    @Delete
    void delete(TemplateData templateData);

    // Delete all records that are older than what is specified by argument
    // Other cleanups could/should take care of most stuff, but this can take care of any leftovers.
    // Modifier-format:     [+-]NNN years|months|days|hours|minutes|seconds
    // Modifier-example:    For older than 2 hours:     '-2 hours'
    @Query("DELETE FROM template_data " +
            "WHERE strftime('%s', datetime('now', :modifier)) > strftime('%s', modified_at)")
    void deleteAll_olderThan(String modifier);
}
