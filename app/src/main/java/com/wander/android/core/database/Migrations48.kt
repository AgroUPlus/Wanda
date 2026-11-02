package com.wander.android.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Migration 47 to 48: a play can be hidden from History without leaving the scrobble outboxes. */
internal val MIGRATION_47_48 = object : Migration(47, 48) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `history` ADD COLUMN `hidden` INTEGER NOT NULL DEFAULT 0")
    }
}
