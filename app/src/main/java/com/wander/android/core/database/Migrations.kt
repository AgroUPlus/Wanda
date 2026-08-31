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

/**
 * The landmark index behind microphone recognition.
 *
 * Created empty. The index is built from the user's own files in the background and can always be
 * rebuilt from them, so there is nothing here worth carrying forward from an earlier schema — and
 * a migration that tried to would be inventing fingerprints it never computed.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `fingerprints` (
                `hash` INTEGER NOT NULL,
                `trackId` TEXT NOT NULL,
                `anchorFrame` INTEGER NOT NULL,
                PRIMARY KEY(`hash`, `trackId`, `anchorFrame`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_fingerprints_hash` ON `fingerprints` (`hash`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_fingerprints_trackId` ON `fingerprints` (`trackId`)"
        )
    }
}

/**
 * Adds the local and imported playlist store.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `local_playlists` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `comment` TEXT,
                `coverArtUrl` TEXT,
                `trackIds` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}

/**
 * Separates albums you own from albums you have merely looked at.
 *
 * Defaults to 0 — not owned. The Library tab therefore looks empty of albums until the next
 * library refresh marks the real ones, which is the correct trade: an album wrongly absent for a
 * few seconds is recoverable, while the previous behaviour filed every artist page you opened into
 * your collection permanently.
 *
 * The delete is the other half. Album rows credited to "Unknown Artist" were written by the
 * artist-tile parser before it learned to read a tile's subtitle properly, and `rememberAlbums`
 * only ever *inserts* rows Room has not seen — so those rows could never be corrected by any
 * amount of re-browsing. They are pure cache and regenerate on next fetch.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `albums` ADD COLUMN `isLibrary` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("DELETE FROM `albums` WHERE `artist` = 'Unknown Artist'")
    }
}

/**
 * What we already know about an artist — see [com.wander.android.core.database.entity.ArtistEntity].
 *
 * Created empty. Every column is re-derivable from the backend, so there is nothing to carry
 * forward; the first visit to each artist fills their row in.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `artists` (
                `nameKey` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `artistId` TEXT,
                `imageUrl` TEXT,
                `bio` TEXT,
                `fetchedAt` INTEGER NOT NULL,
                PRIMARY KEY(`nameKey`)
            )
            """.trimIndent()
        )
    }
}

/**
 * Throws away the browsed-album cache, so mislabelled rows are refetched.
 *
 * `InnerTubeSubtitle` used to read an artist by position, so a release line — `Single • 2023` —
 * credited the record to an artist called "Single". The parser no longer does that, but
 * `CatalogRepository.rememberAlbums` only ever *inserts* albums Room has not seen, so every row
 * already written that way was permanent: one library here had seventy-five albums filed under an
 * artist named "Single".
 *
 * Deleting by `isLibrary = 0` rather than by matching the bad names is deliberate. The labels
 * arrive translated — `hl` is the device language — so a list of English words would miss exactly
 * the users whose language is not English. Non-library rows are pure cache: they carry nothing the
 * next browse cannot rebuild, and rebuilding them is now correct.
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM `albums` WHERE `isLibrary` = 0")
    }
}

/**
 * Drops cached tracks that were credited to nobody.
 *
 * An artist's own page does not repeat their name on every row — you are already on it — so songs
 * parsed off a "Songs" shelf reached Room as "Unknown Artist". The parser now stamps the page's
 * artist onto them, but rows already written that way never correct themselves.
 *
 * Guarded so nothing the user has touched is lost: a liked, downloaded or played row survives its
 * bad credit rather than being deleted for tidiness. What goes is pure cache, and the next fetch
 * rebuilds it correctly.
 */
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

/**
 * Clears album names that are actually view counts.
 *
 * `InnerTubeSubtitle.album` took the token after the artist and rejected only durations, so a row
 * reading `Artist • 15M views` filed the song under a record called "15M views" — 242 of them in
 * one library. The same song found twice then looked like two different releases, which is also
 * what the recording-merge preview flagged it as.
 *
 * Nulled rather than deleted: the row itself is fine, and its like, play count and downloaded file
 * are all worth keeping. Only the wrong field goes, and the next fetch fills it in properly.
 */
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

/**
 * Adds the table that lets a user overrule the matcher.
 *
 * Two rows the deduplicator calls one recording move together — a like written against either
 * lands on both — and the play-count migration will eventually fold them into a single row. That
 * is right almost always and unappealable when it is wrong. A pinned pair is the appeal, and it
 * has to exist before anything merges: once a year of history sits on the wrong row there is
 * nothing left to pin apart.
 *
 * Purely additive. No existing row is read, changed or deleted.
 */
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

/**
 * Adds end-to-end encrypted payload storage to drops table.
 */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `drops` ADD COLUMN `noteCiphertext` TEXT")
        db.execSQL("ALTER TABLE `drops` ADD COLUMN `isEncrypted` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Adds the canonical recording fingerprints and the sub-hash index that proposes candidates.
 *
 * Purely additive: both tables start empty and are filled by indexing, so there is nothing to
 * backfill and no existing row to rewrite. The index is on the half alone, which is the column
 * every candidate lookup filters on.
 */
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

/** Every migration, in order. Room applies whichever ones a given database still needs. */
val WANDER_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
    MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20
)
