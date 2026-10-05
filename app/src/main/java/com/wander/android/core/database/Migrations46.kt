package com.wander.android.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Migration 45 to 46: when recognition last used a catalogue recording, so the least used go first. */
internal val MIGRATION_45_46 = object : Migration(45, 46) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `catalog_recordings` ADD COLUMN `lastUsedAt` INTEGER NOT NULL DEFAULT 0")
    }
}
