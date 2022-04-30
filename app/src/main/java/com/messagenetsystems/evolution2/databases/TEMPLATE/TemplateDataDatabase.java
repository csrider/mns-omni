package com.messagenetsystems.evolution2.databases.TEMPLATE;

/* TemplateDataDatabase class, as Room DB
 *
 * This is a kind of HOLDER class for a list of entities.
 * Effectively putting entities and DAO together as a "database."
 *
 * Note: Creating an object of this class is expensive, so you should create a single instance of
 * this as a kind of dedicated "client."
 *
 * Note: It's a good idea to use singleton-client approach for the database; so there, you need to
 * create a static method which will return the instance of TemplateDataDatabase.
 *
 * Revisions:
 *  2019.12.02      Chris Rider     Created (used ReceivedMessageDatabase as a template).
 */

import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;

@Database(entities = {TemplateData.class}, version = 1)
public abstract class TemplateDataDatabase extends RoomDatabase {
    private final String TAG = TemplateDataDatabase.class.getSimpleName();

    public abstract TemplateDataDao templateDataDao();
}
