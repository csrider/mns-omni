package com.messagenetsystems.evolution2.databases.messages;

/* MessageDatabase class, as Room DB
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
 *  2019.12.03      Chris Rider     Created.
 *  2019.12.11      Chris Rider     Updated version to support status field update to schema.
 *  2019.12.18      Chris Rider     Updated version to support "unique" entity annotation.
 *  2020.01.22      Chris Rider     Updated version to support new metadata field.
 *  2020.04.20      Chris Rider     Updated version to support new receivedAt field.
 */

import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;

@Database(entities = {Message.class}, version = 5)
public abstract class MessageDatabase extends RoomDatabase {
    private final String TAG = MessageDatabase.class.getSimpleName();

    public abstract MessageDao messageDao();
}
