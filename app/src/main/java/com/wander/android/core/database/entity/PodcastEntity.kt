package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A subscribed podcast feed. Its episodes are ordinary `tracks` rows with
 * `source = PODCAST`, `isEpisode = 1` and `albumId = "podcast:<feedUrl>"`, so playback, downloads,
 * progress and backup need no podcast-specific path.
 *
 * [etag] and [lastModified] are the validators from the last successful fetch, replayed as a
 * conditional GET so an unchanged feed costs a 304 instead of a re-download and re-parse.
 */
@Entity(tableName = "podcasts")
data class PodcastEntity(
    @PrimaryKey val feedUrl: String,
    val title: String,
    val author: String?,
    val artworkUrl: String?,
    val etag: String?,
    val lastModified: String?,
    val lastSyncAt: Long?,
    val subscribedAt: Long
)
