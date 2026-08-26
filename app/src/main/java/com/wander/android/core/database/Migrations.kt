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

/**
 * Offers the play history this device already had to Agro's fleet-wide statistics.
 *
 * [MIGRATION_3_4] marked existing rows as already-synced, on the reasoning that they predate the
 * feature. In practice that means a phone with months of listening contributes nothing to the
 * totals until it plays something new, and the fleet's figures look like the device only started
 * existing on upgrade day. Flipping them back to pending lets the outbox send them.
 *
 * The rows carry their real `playedAt`, and everything else the upload needs is joined from
 * `tracks`, so the history lands on the right days rather than piling onto today. Ingest is
 * idempotent on (account, artist, title, time), so a device that somehow ran this twice cannot
 * double-count.
 *
 * A separate migration rather than an edit to [MIGRATION_3_4]: a device may already be on 4.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE history SET agroSynced = 0")
    }
}

/**
 * Adds the recommendation-shelf cache.
 *
 * Purely additive — an empty table simply means the next Home refresh fetches, which is exactly
 * what every launch did before. Nothing is backfilled because there is nothing to backfill: the
 * feed only ever existed in memory.
 */
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

/**
 * Adds the friend-list cache.
 *
 * Additive, and an empty table is the correct starting state: an account with no Agro pairing has
 * no friends to cache, and one with a pairing refills it on the next Friends refresh. Nothing about
 * what a friend is *playing* is stored here — see [com.wander.android.core.database.entity.FriendEntity].
 */
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

/**
 * Adds the drop inbox.
 *
 * Additive, and empty is the right starting state for the same reason [MIGRATION_6_7] is: a device
 * with no Agro pairing has no inbox, and one with a pairing fills it on the next refresh. Unlike
 * the friend cache this table is genuinely durable — see
 * [com.wander.android.core.database.entity.DropEntity] for why a drop is stored when presence is
 * not.
 */
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
        // An earlier build of this migration created an index under a name the entity did not
        // declare. Room compares the live schema against the entities on every open, so that
        // mismatch threw while the database was opening and the app could not start at all.
        // Dropped rather than left alone, in case any device committed it.
        db.execSQL("DROP INDEX IF EXISTS `index_drops_incoming`")
        // The name is the one Room generates for the entity's own `@Index`. They have to agree
        // exactly, or the validation above fails for the opposite reason.
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_drops_incoming_archived_createdAt` " +
                "ON `drops` (`incoming`, `archived`, `createdAt`)"
        )
    }
}

/**
 * Marks a track as an endless stream rather than a recording.
 *
 * Defaults to 0: everything already stored was parsed before live streams were understood, and a
 * recording wrongly flagged live would lose its seek bar.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tracks` ADD COLUMN `isLive` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * The recipient's one-emoji reply to a drop.
 *
 * Nullable rather than defaulted to an empty string: "has not reacted" and "reacted with nothing"
 * are the same thing, and a null says so without a second convention to remember.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `drops` ADD COLUMN `reaction` TEXT")
    }
}

/** Every migration, in order. Room applies whichever ones a given database still needs. */
val WANDER_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
    MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10
)
