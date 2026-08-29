package com.wander.android.core.database.entity

import androidx.room.Entity

/**
 * One declaration that two rows are *not* the same performance.
 *
 * The matcher is deliberately conservative, but it is not infallible, and its mistakes are written
 * to disk: a like moves across every rendition of a recording, and the play-count migration will
 * eventually fold their rows together. A wrong merge there hides a recording the user owns with
 * nothing on screen to say so. This is the appeal against that judgement — and it has to exist
 * before the merge does, because it cannot be retrofitted onto history that has already merged.
 *
 * Pairwise rather than a group label on the track, so pins compose: A can be kept apart from B
 * while both still match C. Kept in its own table rather than as a column on `tracks` because
 * `TrackSourceFields` rewrites backend metadata on every refetch — a column there would be
 * silently clobbered by the next sync, and would not survive a cached row being evicted.
 */
@Entity(tableName = "recording_splits", primaryKeys = ["idA", "idB"])
data class RecordingSplitEntity(
    /** The lexicographically smaller of the two track ids, so one pair has exactly one row. */
    val idA: String,
    /** The larger of the two. */
    val idB: String,
    val pinnedAt: Long
)
