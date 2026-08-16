package com.wander.android.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema upgrades that must not lose data.
 *
 * The database used to be built with `fallbackToDestructiveMigration`, which was defensible while
 * every row was a cache of something a source could hand back. It stopped being defensible with
 * library sync: `contentHash` costs minutes of hashing to recompute, and `syncedAt` is the only
 * record that the server confirmed it holds a file — losing it would either re-upload the entire
 * library or, worse, make it impossible to tell which local copies are safe to delete.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // All nullable with no default, so existing rows simply have nothing to say about sync
        // until the next scan and hash pass fills them in.
        db.execSQL("ALTER TABLE tracks ADD COLUMN sizeBytes INTEGER")
        db.execSQL("ALTER TABLE tracks ADD COLUMN fileExtension TEXT")
        db.execSQL("ALTER TABLE tracks ADD COLUMN albumArtist TEXT")
        db.execSQL("ALTER TABLE tracks ADD COLUMN contentHash TEXT")
        db.execSQL("ALTER TABLE tracks ADD COLUMN syncedAt INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_contentHash ON tracks (contentHash)")
    }
}

/**
 * Adds the Agro half of the play outbox.
 *
 * Existing rows default to *synced* rather than pending. They predate centralised statistics, so
 * there is no device name or client type to attribute them to and nothing on the server expecting
 * them — uploading a year of history the moment the feature is switched on would be a large,
 * surprising, and not especially accurate import.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE history ADD COLUMN agroSynced INTEGER NOT NULL DEFAULT 1")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_history_agroSynced ON history (agroSynced)")
    }
}

/** Every migration, in order. Room applies whichever ones a given database still needs. */
val WANDER_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_2_3, MIGRATION_3_4)
