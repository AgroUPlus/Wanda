package com.wander.android.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Early Room schema migrations (v2 through v14).
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tracks ADD COLUMN sizeBytes INTEGER")
        db.execSQL("ALTER TABLE tracks ADD COLUMN fileExtension TEXT")
        db.execSQL("ALTER TABLE tracks ADD COLUMN albumArtist TEXT")
        db.execSQL("ALTER TABLE tracks ADD COLUMN contentHash TEXT")
        db.execSQL("ALTER TABLE tracks ADD COLUMN syncedAt INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_contentHash ON tracks (contentHash)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE history ADD COLUMN agroSynced INTEGER NOT NULL DEFAULT 1")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_history_agroSynced ON history (agroSynced)")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE history SET agroSynced = 0")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `shelves` (
                `id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                `trackIds` TEXT NOT NULL,
                `fetchedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `friends` (
                `username` TEXT NOT NULL,
                `displayName` TEXT,
                `bio` TEXT,
                `avatarUrl` TEXT,
                `state` TEXT NOT NULL,
                `outgoing` INTEGER NOT NULL,
                `showNowPlaying` INTEGER NOT NULL,
                `showStats` INTEGER NOT NULL,
                `syncedAt` INTEGER NOT NULL,
                PRIMARY KEY(`username`)
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `drops` (
                `id` TEXT NOT NULL,
                `fromUser` TEXT NOT NULL,
                `toUser` TEXT NOT NULL,
                `trackTitle` TEXT NOT NULL,
                `artistName` TEXT NOT NULL,
                `albumName` TEXT,
                `artworkUrl` TEXT,
                `contentHash` TEXT,
                `trackUri` TEXT,
                `note` TEXT,
                `createdAt` TEXT NOT NULL,
                `readAt` TEXT,
                `archived` INTEGER NOT NULL,
                `incoming` INTEGER NOT NULL,
                `syncedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("DROP INDEX IF EXISTS `index_drops_incoming`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_drops_incoming_archived_createdAt` " +
                "ON `drops` (`incoming`, `archived`, `createdAt`)"
        )
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tracks` ADD COLUMN `isLive` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `drops` ADD COLUMN `reaction` TEXT")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `fingerprints` (
                `hash` INTEGER NOT NULL,
                `trackId` TEXT NOT NULL,
                `anchorFrame` INTEGER NOT NULL,
                PRIMARY KEY(`hash`, `trackId`, `anchorFrame`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_fingerprints_hash` ON `fingerprints` (`hash`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_fingerprints_trackId` ON `fingerprints` (`trackId`)"
        )
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `local_playlists` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `comment` TEXT,
                `coverArtUrl` TEXT,
                `trackIds` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `albums` ADD COLUMN `isLibrary` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("DELETE FROM `albums` WHERE `artist` = 'Unknown Artist'")
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `artists` (
                `nameKey` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `artistId` TEXT,
                `imageUrl` TEXT,
                `bio` TEXT,
                `fetchedAt` INTEGER NOT NULL,
                PRIMARY KEY(`nameKey`)
            )
            """.trimIndent()
        )
    }
}
