package com.wander.android.core.database.entity

import androidx.room.Entity

/**
 * What the shared catalogue says a track is called, kept apart from what its source says.
 *
 * Stored in its own table for the reason [RecordingSplitEntity] is: `TrackSourceFields` rewrites
 * `tracks` from the backend on every refetch, so a canonical title written into that row survives
 * only until the next library sync. Here it survives, and can be applied again.
 *
 * One row per track rather than per recording. The catalogue is keyed on the recording, but what
 * gets displayed is a row, and two rows of one recording can perfectly well need different
 * corrections — a YouTube upload's title is wrong in a way a Navidrome tag is not.
 */
@Entity(tableName = "canonical_metadata", primaryKeys = ["trackId"])
data class CanonicalMetadataEntity(
    val trackId: String,
    /** Null where the catalogue had nothing better than what the source already gave. */
    val title: String?,
    val artist: String?,
    val album: String?,
    /** The catalogue's id for the recording, so a correction can be traced back to its entry. */
    val recordingId: String,
    val updatedAt: Long
)
