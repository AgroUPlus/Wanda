package com.wander.android.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 43 to 44: an outbox flag per scrobbling service on `history`. Every existing play
 * counts as sent (default 1): forwarding starts from when a service is connected, not from the
 * first play this device ever heard.
 */
internal val MIGRATION_43_44 = object : Migration(43, 44) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `history` ADD COLUMN `listenBrainzSynced` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `history` ADD COLUMN `lastFmSynced` INTEGER NOT NULL DEFAULT 1")
    }
}
