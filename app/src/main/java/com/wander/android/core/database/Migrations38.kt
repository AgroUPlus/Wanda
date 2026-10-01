package com.wander.android.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 37 to 38: add the `podcasts` table of subscribed RSS feeds. Episodes need no schema
 * change; they are `tracks` rows keyed by the `podcast:` id prefix.
 */
internal val MIGRATION_37_38 = object : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `podcasts` (
                `feedUrl` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `author` TEXT,
                `artworkUrl` TEXT,
                `etag` TEXT,
                `lastModified` TEXT,
                `lastSyncAt` INTEGER,
                `subscribedAt` INTEGER NOT NULL,
                PRIMARY KEY(`feedUrl`)
            )
            """.trimIndent()
        )
    }
}
