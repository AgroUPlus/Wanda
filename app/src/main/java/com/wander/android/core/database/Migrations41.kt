package com.wander.android.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 40 to 41: shared playlists kept in sync.
 *
 * `playlist_publications` only remembered which Agro copy a playlist had; `shared_playlists` holds
 * the copy itself, for published and followed playlists alike. Each publication carries over as a
 * copy this account owns, at revision -1 so the first sync reconciles it with the server — which
 * is what sends the edits made since it was published.
 */
internal val MIGRATION_40_41 = object : Migration(40, 41) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `shared_playlists` (
                `agroId` TEXT NOT NULL,
                `localPlaylistId` TEXT,
                `title` TEXT NOT NULL,
                `description` TEXT,
                `ownerId` TEXT NOT NULL,
                `visibility` TEXT NOT NULL,
                `editAccess` TEXT NOT NULL,
                `myRole` TEXT NOT NULL,
                `revision` INTEGER NOT NULL,
                `syncState` TEXT NOT NULL,
                `lastSyncedAt` INTEGER NOT NULL,
                `publishedAt` INTEGER NOT NULL,
                PRIMARY KEY(`agroId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `shared_playlist_items` (
                `agroId` TEXT NOT NULL,
                `itemId` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                `trackId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `artist` TEXT NOT NULL,
                `album` TEXT,
                `durationMs` INTEGER NOT NULL,
                `addedBy` TEXT,
                `addedAt` TEXT,
                PRIMARY KEY(`agroId`, `itemId`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_shared_playlist_items_trackId` ON `shared_playlist_items` (`trackId`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `shared_playlist_ops` (
                `seq` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `agroId` TEXT NOT NULL,
                `opJson` TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_shared_playlist_ops_agroId` ON `shared_playlist_ops` (`agroId`)")
        // The owner is this account; its name is filled in by the first sync.
        db.execSQL(
            """
            INSERT INTO shared_playlists (agroId, localPlaylistId, title, description, ownerId, visibility,
                editAccess, myRole, revision, syncState, lastSyncedAt, publishedAt)
            SELECT p.agroId, p.playlistId, COALESCE(l.name, ''), NULL, '', p.visibility,
                'OFF', 'OWNER', -1, 'OUT_OF_SYNC', 0, p.publishedAt
            FROM playlist_publications p LEFT JOIN local_playlists l ON l.id = p.playlistId
            """.trimIndent()
        )
        db.execSQL("DROP TABLE IF EXISTS `playlist_publications`")
    }
}
