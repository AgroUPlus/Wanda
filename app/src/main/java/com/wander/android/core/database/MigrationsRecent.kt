package com.wander.android.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Recent Room schema migrations (v24 through v35).
 */
val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `track_embeddings` (
                `trackId` TEXT NOT NULL,
                `vector` BLOB NOT NULL,
                `dim` INTEGER NOT NULL,
                `model` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                `computedAt` INTEGER NOT NULL,
                PRIMARY KEY(`trackId`)
            )
            """.trimIndent()
        )

        val withoutRowid = db.query(
            "SELECT sql FROM sqlite_master WHERE type='table' AND name='fingerprints'"
        ).use { it.moveToFirst() && it.getString(0).uppercase().contains("WITHOUT ROWID") }
        if (withoutRowid) {
            db.execSQL(
                """
                CREATE TABLE `fingerprints_v25` (
                    `hash` INTEGER NOT NULL,
                    `trackId` TEXT NOT NULL,
                    `anchorFrame` INTEGER NOT NULL,
                    PRIMARY KEY(`hash`, `trackId`, `anchorFrame`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "INSERT OR IGNORE INTO `fingerprints_v25` (hash, trackId, anchorFrame) " +
                    "SELECT hash, trackId, anchorFrame FROM `fingerprints`"
            )
            db.execSQL("DROP TABLE `fingerprints`")
            db.execSQL("ALTER TABLE `fingerprints_v25` RENAME TO `fingerprints`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_fingerprints_trackId` " +
                    "ON `fingerprints` (`trackId`)"
            )
        }
    }
}

val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tracks` ADD COLUMN `lastAttemptAt` INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE `tracks` ADD COLUMN `attempts` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("DROP TABLE IF EXISTS `recording_sub_hashes`")
        db.execSQL("DROP TABLE IF EXISTS `recording_fingerprints`")
    }
}

val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `track_lyrics` (
                `trackId` TEXT NOT NULL,
                `plainLyrics` TEXT NOT NULL,
                `syncedLyrics` TEXT,
                `source` TEXT NOT NULL,
                `syncedAt` INTEGER NOT NULL,
                PRIMARY KEY(`trackId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS `lyrics_fts` USING fts4(
                `trackId` TEXT,
                `plainLyrics` TEXT,
                tokenize=unicode61
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `fingerprints`")
    }
}

val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `track_embeddings` ADD COLUMN `centroid` BLOB DEFAULT NULL")
    }
}

val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `track_lyrics` ADD COLUMN `absentSince` INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE `track_lyrics` ADD COLUMN `viaCatalog` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `announced_releases` (" +
                "`recordingId` TEXT NOT NULL, `announcedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`recordingId`))"
        )
    }
}

val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM artists")
    }
}

val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tracks` ADD COLUMN `downloadedAt` INTEGER DEFAULT NULL")
    }
}

val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `episode_progress` (
                `trackId` TEXT NOT NULL,
                `positionMs` INTEGER NOT NULL,
                `durationMs` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`trackId`)
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `replay_recaps` (
                `year` INTEGER NOT NULL,
                `generatedAt` INTEGER NOT NULL,
                `isFleetWide` INTEGER NOT NULL,
                `totalMinutes` INTEGER NOT NULL,
                `totalPlays` INTEGER NOT NULL,
                `longestStreakDays` INTEGER NOT NULL,
                `newArtistsCount` INTEGER NOT NULL,
                `payloadJson` TEXT NOT NULL,
                PRIMARY KEY(`year`)
            )
            """.trimIndent()
        )
    }
}
