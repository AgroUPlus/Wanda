package com.wander.android.core.database.entity

/**
 * The subset of [TrackEntity] a backend is allowed to overwrite when it returns a track we already
 * hold.
 *
 * Everything absent from this class is user state — `isLiked`, `isDownloaded`, `localFilePath`,
 * `playCount`, `lastPlayedTimestamp`, `addedTimestamp`, `isLibrary` — and a refetch must never
 * touch it. Room applies this as a partial `@Update` keyed on [id], so the omitted columns keep
 * their stored values.
 *
 * Built via [TrackEntity.toSourceFields].
 */
data class TrackSourceFields(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val albumId: String?,
    val artistId: String?,
    val durationMs: Long,
    val artworkUrl: String?,
    val streamUri: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val genre: String?,
    val bitRateKbps: Int?,
    val format: String?
)
