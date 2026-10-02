package com.wander.android.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Migration 39 to 40: add `playlist_publications`, the Agro copies of Wanda playlists. */
internal val MIGRATION_39_40 = object : Migration(39, 40) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `playlist_publications` (
                `playlistId` TEXT NOT NULL,
                `agroId` TEXT NOT NULL,
                `visibility` TEXT NOT NULL,
                `publishedAt` INTEGER NOT NULL,
                PRIMARY KEY(`playlistId`)
            )
            """.trimIndent()
        )
    }
}
