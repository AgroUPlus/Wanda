package com.wander.android.core.database.entity

import androidx.room.Entity

/**
 * One finding that two rows hold the same recording, decided on the audio.
 *
 * The fingerprinter's answer, written down. It is stored rather than recomputed because the
 * comparison needs both fingerprints in memory and runs over a candidate list — affordable once,
 * inside the indexing worker that already has the samples decoded, and not affordable on every
 * library query.
 *
 * Pairwise like [RecordingSplitEntity], and for the same reason: pins and links have to compose
 * with each other, and a group label on the track could not express "A links to B, but the user has
 * kept A apart from C which also links to B".
 *
 * Kept out of `tracks` because `TrackSourceFields` rewrites backend metadata on every refetch — a
 * column there would be silently clobbered by the next sync.
 */
@Entity(tableName = "recording_links", primaryKeys = ["idA", "idB"])
data class RecordingLinkEntity(
    /** The lexicographically smaller of the two track ids, so one pair has exactly one row. */
    val idA: String,
    /** The larger of the two. */
    val idB: String,
    /**
     * How alike the two fingerprints were, kept for the day the threshold is questioned.
     *
     * A row exists only above [com.wander.android.data.repository.RecordingIdentityRepository]'s
     * threshold, so this is never a record of a rejection — only of how comfortably one passed.
     */
    val similarity: Double,
    val linkedAt: Long
)
