package com.wander.android.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A subscribed podcast feed. Its episodes are ordinary `tracks` rows with
 * `source = PODCAST`, `isEpisode = 1` and `albumId = "podcast:<feedUrl>"`, so playback, downloads,
 * progress and backup need no podcast-specific path.
 *
 * [etag] and [lastModified] are the validators from the last successful fetch, replayed as a
 * conditional GET so an unchanged feed costs a 304 instead of a re-download and re-parse.
 *
 * [feedUrl] is the subscription's identity: episode ids hash it, so it never changes. When the
 * publisher moves the feed (`<itunes:new-feed-url>` or a permanent redirect), the new address goes
 * in [fetchUrl] and every later fetch uses that; null means the feed still lives at [feedUrl].
 *
 * [autoDownload] queues each new episode for download on Wi-Fi, keeping the newest few.
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
    val subscribedAt: Long,
    val fetchUrl: String? = null,
    @ColumnInfo(defaultValue = "0") val autoDownload: Boolean = false
)

/** Where the feed is fetched from now: the address it moved to, or the one subscribed to. */
val PodcastEntity.currentUrl: String get() = fetchUrl ?: feedUrl
