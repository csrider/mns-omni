package com.messagenetsystems.evolution2.databases.receivedRequests;

/* ReceivedRequestDatabase class, as Room DB
 *
 * This is a kind of HOLDER class for a list of entities.
 * Effectively putting entities and DAO together as a "database."
 *
 * Note: Creating an object of this class is expensive, so you should create a single instance of
 * this as a kind of dedicated "client."
 *
 * Note: It's a good idea to use singleton-client approach for the database; so there, you need to
 * create a static method which will return the instance of ReceivedRequestDatabase.
 *
 * Revisions:
 *  2019.08.28      Chris Rider     Created (originally for v1 ActiveMsg but not finished).
 *  2019.11.21      Chris Rider     Migrated to v2 and adapted for received requests.
 *  2019.11.25      Chris Rider     Now supporting exportation of schema, thanks to annotationProcessorOptions directive in app's build.gradle config.
 *                                  Updated version number to reflect added field (request_processed_at).
 *  2019.11.26      Chris Rider     Updated version number to reflect added field (request_processed_at_ms & request_status).
 *  2019.12.11      Chris Rider     Updated String-requestStatus to int-status.
 */

import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;

@Database(entities = {ReceivedRequest.class}, version = 6)
public abstract class ReceivedRequestDatabase extends RoomDatabase {
    private final String TAG = ReceivedRequestDatabase.class.getSimpleName();

    public abstract ReceivedRequestDao receivedRequestDao();
}
