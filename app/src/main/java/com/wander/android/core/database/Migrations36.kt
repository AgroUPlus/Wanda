package com.wander.android.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Remembers which tracks are podcast episodes.
 *
 * The flag used to live only on the in-memory model, so an episode reloaded from Room — queue
 * restore, history, library — came back as a song and lost its resume point. The backfill
 * recovers every episode that already has saved progress, which is the only evidence left.
 */
val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tracks` ADD COLUMN `isEpisode` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_isEpisode` ON `tracks` (`isEpisode`)")
        db.execSQL("UPDATE `tracks` SET `isEpisode` = 1 WHERE `id` IN (SELECT `trackId` FROM `episode_progress`)")
    }
}
