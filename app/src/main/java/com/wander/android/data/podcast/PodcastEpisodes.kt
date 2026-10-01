package com.wander.android.data.podcast

import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.SourceType
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset

/**
 * How a feed's episodes become `tracks` rows.
 *
 * Ids hash the feed URL and the episode GUID rather than embedding them: a private feed's URL
 * carries the listener's token, and track ids end up in backups, queues and logs.
 */
internal object PodcastEpisodes {

    fun feedKey(feedUrl: String): String = sha256(feedUrl).take(KEY_LENGTH)

    /** The `albumId` shared by every episode of a feed, so a feed's episodes can be found and pruned together. */
    fun albumIdOf(feedUrl: String): String = SourceType.PODCAST.idPrefix + feedKey(feedUrl)

    fun toTrack(feedUrl: String, show: ParsedFeed, episode: ParsedEpisode, now: Long): TrackEntity {
        val sourceId = "${feedKey(feedUrl)}:${sha256(episode.guid).take(KEY_LENGTH)}"
        val published = episode.publishedAt ?: now
        return TrackEntity(
            id = SourceType.PODCAST.idPrefix + sourceId,
            sourceTrackId = sourceId,
            source = SourceType.PODCAST,
            title = episode.title,
            artist = show.author ?: show.title,
            album = show.title,
            albumId = albumIdOf(feedUrl),
            artistId = null,
            durationMs = episode.durationMs,
            artworkUrl = episode.artworkUrl ?: show.artworkUrl,
            streamUri = episode.audioUrl,
            trackNumber = null,
            discNumber = null,
            year = Instant.ofEpochMilli(published).atZone(ZoneOffset.UTC).year,
            genre = null,
            bitRateKbps = null,
            format = episode.mimeType,
            isEpisode = true,
            // Inbox order is publication order, and `addedTimestamp` is what every list sorts on.
            addedTimestamp = published
        )
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private const val KEY_LENGTH = 16
}
