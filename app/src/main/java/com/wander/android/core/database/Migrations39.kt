package com.wander.android.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Migration 38 to 39: add `episode_extras`, the chapters and transcript addresses of RSS episodes. */
internal val MIGRATION_38_39 = object : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `episode_extras` (
                `trackId` TEXT NOT NULL,
                `chaptersUrl` TEXT,
                `transcriptUrl` TEXT,
                `transcriptMime` TEXT,
                PRIMARY KEY(`trackId`)
            )
            """.trimIndent()
        )
    }
}
