package com.wander.android.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UnifiedAlbum(
    val id: String,
    val source: SourceType,
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val coverArtUrl: String? = null,
    val songCount: Int = 0,
    val durationMs: Long = 0L,
    val year: Int? = null,
    val genre: String? = null,
    val isLiked: Boolean = false
)

@Serializable
data class UnifiedArtist(
    val id: String,
    val source: SourceType,
    val name: String,
    val coverArtUrl: String? = null,
    val albumCount: Int = 0,
    val trackCount: Int = 0,
    val isLiked: Boolean = false
)

@Serializable
data class UnifiedPlaylist(
    val id: String,
    val source: SourceType,
    val name: String,
    val comment: String? = null,
    val coverArtUrl: String? = null,
    val songCount: Int = 0,
    val durationMs: Long = 0L,
    val isPublic: Boolean = false,
    val isAutoDownload: Boolean = false
)
