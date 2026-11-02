package com.wander.android.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 48 to 49: a backend's library kept in Room rather than only fetched live.
 *
 * - `remote_playlists`: Navidrome, YouTube Music and Deezer playlists, with their track ids once
 *   opened, so the library lists them and they open offline.
 * - `pending_likes`: likes and unlikes not yet confirmed by their backend, retried until they are.
 * - `remote_likes`: which tracks a backend has confirmed as liked, so an unlike made elsewhere is
 *   mirrored without touching a like that only ever existed in Wanda.
 */
internal val MIGRATION_48_49 = object : Migration(48, 49) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `remote_playlists` (`id` TEXT NOT NULL, `source` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `comment` TEXT, `coverArtUrl` TEXT, `songCount` INTEGER NOT NULL, " +
                "`durationMs` INTEGER NOT NULL, `isPublic` INTEGER NOT NULL, `isEditable` INTEGER NOT NULL, " +
                "`position` INTEGER NOT NULL, `trackIds` TEXT, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_remote_playlists_source` ON `remote_playlists` (`source`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pending_likes` (`trackId` TEXT NOT NULL, `liked` INTEGER NOT NULL, " +
                "`attempts` INTEGER NOT NULL, `queuedAt` INTEGER NOT NULL, PRIMARY KEY(`trackId`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `remote_likes` (`trackId` TEXT NOT NULL, `source` TEXT NOT NULL, " +
                "PRIMARY KEY(`trackId`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_remote_likes_source` ON `remote_likes` (`source`)")
    }
}
