package com.wander.android.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log

internal const val DATABASE_NAME = "wanda_music.db"

/**
 * Tells whether the database already on the device is older than any migration can upgrade.
 *
 * Room opens lazily, on the first query, and throws from wherever that happens to be. Reading the
 * version the file carries before anything queries it is what lets the app say so instead.
 */
object DatabaseCompatibility {
    private const val TAG = "DatabaseCompatibility"

    /** Derived from [WANDER_MIGRATIONS], so dropping an old migration moves this on its own. */
    val minSupportedVersion: Int get() = WANDER_MIGRATIONS.minOf { it.startVersion }

    /** Null when there is no database yet (a fresh install) or it has no version to compare. */
    internal fun isTooOld(storedVersion: Int?, minSupported: Int = minSupportedVersion): Boolean =
        storedVersion != null && storedVersion in 1 until minSupported

    fun isTooOld(context: Context): Boolean = isTooOld(storedVersion(context))

    private fun storedVersion(context: Context): Int? {
        val file = context.getDatabasePath(DATABASE_NAME)
        if (!file.exists()) return null
        return try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
        } catch (e: SQLiteException) {
            // Not ours to report: if the file really is unreadable, Room says so when it opens it.
            Log.w(TAG, "Could not read the stored database version: ${e.message}")
            null
        }
    }

    /** Deletes the database and its journal files, so the next launch creates it from scratch. */
    fun reset(context: Context) {
        check(context.deleteDatabase(DATABASE_NAME)) { "Could not delete $DATABASE_NAME" }
    }
}
