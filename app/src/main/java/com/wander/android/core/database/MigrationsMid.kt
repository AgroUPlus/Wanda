package com.wander.android.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Mid-era Room schema migrations (v14 through v24).
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM `albums` WHERE `isLibrary` = 0")
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            DELETE FROM `tracks`
            WHERE `artist` = 'Unknown Artist'
              AND `isLiked` = 0
              AND `isDownloaded` = 0
              AND `playCount` = 0
              AND `isLibrary` = 0
            """.trimIndent()
        )
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE `tracks` SET `album` = NULL
            WHERE `album` IS NOT NULL
              AND (
                `album` GLOB '*[0-9] view*' OR `album` GLOB '*[0-9] play*'
                OR `album` GLOB '*[0-9][KMB] view*' OR `album` GLOB '*[0-9][KMB] play*'
                OR TRIM(`album`) NOT GLOB '*[A-Za-z0-9]*'
              )
            """.trimIndent()
        )
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recording_splits` (
                `idA` TEXT NOT NULL,
                `idB` TEXT NOT NULL,
                `pinnedAt` INTEGER NOT NULL,
                PRIMARY KEY(`idA`, `idB`)
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `drops` ADD COLUMN `noteCiphertext` TEXT")
        db.execSQL("ALTER TABLE `drops` ADD COLUMN `isEncrypted` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recording_fingerprints` (
                `trackId` TEXT NOT NULL,
                `subHashes` BLOB NOT NULL,
                `durationMs` INTEGER NOT NULL,
                `computedAt` INTEGER NOT NULL,
                PRIMARY KEY(`trackId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recording_sub_hashes` (
                `half` INTEGER NOT NULL,
                `trackId` TEXT NOT NULL,
                PRIMARY KEY(`half`, `trackId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_recording_sub_hashes_trackId` " +
                "ON `recording_sub_hashes` (`trackId`)"
        )
    }
}

val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recording_links` (
                `idA` TEXT NOT NULL,
                `idB` TEXT NOT NULL,
                `similarity` REAL NOT NULL,
                `linkedAt` INTEGER NOT NULL,
                PRIMARY KEY(`idA`, `idB`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `canonical_metadata` (
                `trackId` TEXT NOT NULL,
                `title` TEXT,
                `artist` TEXT,
                `album` TEXT,
                `recordingId` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`trackId`)
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `track_features` (
                `trackId` TEXT NOT NULL,
                `tempo` REAL NOT NULL,
                `energy` REAL NOT NULL,
                `brightness` REAL NOT NULL,
                `danceability` REAL NOT NULL,
                `keyX` REAL NOT NULL,
                `keyY` REAL NOT NULL,
                `version` INTEGER NOT NULL,
                `measuredAt` INTEGER NOT NULL,
                PRIMARY KEY(`trackId`)
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `melody_contours` (
                `trackId` TEXT NOT NULL,
                `contour` BLOB NOT NULL,
                `version` INTEGER NOT NULL,
                `indexedAt` INTEGER NOT NULL,
                PRIMARY KEY(`trackId`)
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `fingerprints_new` (
                `hash` INTEGER NOT NULL,
                `trackId` TEXT NOT NULL,
                `anchorFrame` INTEGER NOT NULL,
                PRIMARY KEY(`hash`, `trackId`, `anchorFrame`)
            ) WITHOUT ROWID
            """.trimIndent()
        )
        db.execSQL(
            "INSERT INTO `fingerprints_new` (hash, trackId, anchorFrame) " +
                "SELECT hash, trackId, anchorFrame FROM `fingerprints`"
        )
        db.execSQL("DROP INDEX IF EXISTS `index_fingerprints_hash`")
        db.execSQL("DROP TABLE `fingerprints`")
        db.execSQL("ALTER TABLE `fingerprints_new` RENAME TO `fingerprints`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_fingerprints_trackId` " +
                "ON `fingerprints` (`trackId`)"
        )
    }
}
