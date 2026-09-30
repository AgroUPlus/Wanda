package com.wander.android.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 36 to 37: Drop melody_contours table as the melody subsystem has been removed.
 */
internal val MIGRATION_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS melody_contours")
    }
}
