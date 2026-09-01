package com.wander.android.core.database.entity

import androidx.room.Entity

/**
 * The acoustic vector measured from a track's audio, one row per track.
 *
 * Its own table for the usual reason: `tracks` is rewritten from the backend on every refetch, and
 * a measurement that costs a full decode must not be thrown away by a metadata sync. It is also
 * genuinely different data — everything in `tracks` is what somebody typed, and this is the only
 * thing in the database measured from the audio itself.
 *
 * [version] is what makes the measurement re-runnable. Change the extractor and old rows are
 * stale in a way no schema migration can fix, because the numbers can only be recovered by
 * decoding the file again; bumping this marks every existing row for remeasurement instead.
 */
@Entity(tableName = "track_features", primaryKeys = ["trackId"])
data class TrackFeatureEntity(
    val trackId: String,
    val tempo: Float,
    val energy: Float,
    val brightness: Float,
    val danceability: Float,
    val keyX: Float,
    val keyY: Float,
    val version: Int,
    val measuredAt: Long
)
