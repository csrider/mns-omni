package com.messagenetsystems.evolution2.databases.receivedMessages;

/* ReceivedMessageDatabase class, as Room DB
 *
 * This is a kind of HOLDER class for a list of entities.
 * Effectively putting entities and DAO together as a "database."
 *
 * Note: Creating an object of this class is expensive, so you should create a single instance of
 * this as a kind of dedicated "client."
 *
 * Note: It's a good idea to use singleton-client approach for the database; so there, you need to
 * create a static method which will return the instance of ReceivedMessageDatabase.
 *
 * Revisions:
 *  2019.12.02      Chris Rider     Created (used ReceivedRequestDatabase as a template).
 *  2019.12.11      Chris Rider     Updated status field from String to int data type.
 *  2020.04.20      Chris Rider     Updated version to reflect addition of new received_at field.
 */

import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;

@Database(entities = {ReceivedMessage.class}, version = 3)
public abstract class ReceivedMessageDatabase extends RoomDatabase {
    private final String TAG = ReceivedMessageDatabase.class.getSimpleName();

    public abstract ReceivedMessageDao receivedMessageDao();
}
