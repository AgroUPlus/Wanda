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
 * The index on `hash` is the entire reason recognition is fast: a query is a few hundred hash
 * lookups, and without it each one is a scan of every landmark in the library.
 */
@Entity(
    tableName = "fingerprints",
    primaryKeys = ["hash", "trackId", "anchorFrame"],
    indices = [Index("hash"), Index("trackId")]
)
data class FingerprintEntity(
    val hash: Int,
    val trackId: String,
    /** Hops from the start of the track — see `AudioFormat`. */
    val anchorFrame: Int
)
