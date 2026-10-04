package com.wander.android.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 42 to 43: a shared playlist remembers whether Agro writes it — a Blend — so its screen
 * can say so and offer no editing. Every copy kept so far is a hand-made playlist.
 */
internal val MIGRATION_42_43 = object : Migration(42, 43) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `shared_playlists` ADD COLUMN `isBlend` INTEGER NOT NULL DEFAULT 0")
    }
}
