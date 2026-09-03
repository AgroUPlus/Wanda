package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * One landmark of one indexed track.
 *
 * There is no primary key of its own and rows are never updated — a track is deleted from the
 * index wholesale and re-added. An identity column would be a second index to maintain over a
 * table that is written in bulk and only ever read by [hash].
 *
 * No `Index("hash")` here, though a query is a few hundred hash lookups and depends on one
 * existing: since [MIGRATION_23_24] the table itself is `WITHOUT ROWID` with hash leading the
 * primary key, so the table *is* that index rather than needing a second copy of it. Room cannot
 * see or declare `WITHOUT ROWID` — it is invisible to the `PRAGMA table_info` / `index_list` Room
 * validates against — so this class carries no annotation for it; the migration's raw SQL is the
 * only place it exists. Do not add `Index("hash")` back: it would restore the redundant B-tree
 * this was written to remove.
 */
@Entity(
    tableName = "fingerprints",
    primaryKeys = ["hash", "trackId", "anchorFrame"],
    indices = [Index("trackId")]
)
data class FingerprintEntity(
    val hash: Int,
    val trackId: String,
    /** Hops from the start of the track — see `AudioFormat`. */
    val anchorFrame: Int
)
