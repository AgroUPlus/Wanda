package com.wander.android.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 44 to 45: the catalogue's recordings are kept in their own table, with a lyrics index,
 * so a lyric search and a microphone match can name songs the library does not hold.
 */
internal val MIGRATION_44_45 = object : Migration(44, 45) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `catalog_recordings` (`recordingId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`artist` TEXT NOT NULL, `album` TEXT, `durationMs` INTEGER NOT NULL, `vector` BLOB NOT NULL, " +
                "`centroid` BLOB NOT NULL, `dim` INTEGER NOT NULL, `model` TEXT NOT NULL, `version` INTEGER NOT NULL, " +
                "`plainLyrics` TEXT, `syncedLyrics` TEXT, `lyricsSource` TEXT, `sources` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`recordingId`))"
        )
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `catalog_lyrics_fts` USING FTS4(`recordingId` TEXT NOT NULL, " +
                "`plainLyrics` TEXT NOT NULL, tokenize=unicode61)"
        )
    }
}
