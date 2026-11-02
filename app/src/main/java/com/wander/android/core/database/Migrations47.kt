package com.wander.android.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 46 to 47: where a podcast feed moved to, so a publisher's move is followed instead of
 * the old address failing for ever, and whether new episodes download on their own.
 */
internal val MIGRATION_46_47 = object : Migration(46, 47) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `podcasts` ADD COLUMN `fetchUrl` TEXT")
        db.execSQL("ALTER TABLE `podcasts` ADD COLUMN `autoDownload` INTEGER NOT NULL DEFAULT 0")
    }
}
