package com.wander.android.core.playback

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.wander.android.core.network.HttpClientFactory
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack

/**
 * Placeholder scheme. The real stream URL is resolved lazily at load time by [StreamResolver],
 * so expiring URLs (YouTube Music) are fetched when the track actually starts, not when it is
 * queued.
 */
internal const val WANDA_SCHEME = "wanda"

private const val EXTRA_TRACK_JSON = "wanda.track"

internal fun UnifiedTrack.toMediaItem(): MediaItem {
    val extras = Bundle().apply {
        putString(EXTRA_TRACK_JSON, HttpClientFactory.jsonConfig.encodeToString(this@toMediaItem))
    }
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setArtworkUri(artworkUrl?.toUri())
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setExtras(extras)
        .build()

    // Local and already-downloaded files can be addressed directly; everything else is resolved.
    val uri = streamUri?.takeIf { it.startsWith("/") || it.startsWith("file:") || it.startsWith("content:") }
        ?.toUri()
        ?: Uri.parse("$WANDA_SCHEME://track/${Uri.encode(id)}")

    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setMediaMetadata(metadata)
        // The real URL is hidden behind the placeholder until load time, so the media-source
        // factory cannot infer the container from the URI the way it normally would. A livestream
        // arrives as an HLS manifest and needs an HlsMediaSource chosen *before* loading starts —
        // without this hint it was parsed as a progressive stream, failed, and ExoPlayer advanced
        // to the next item, which is what made live tracks look like they were being skipped.
        .apply { if (isLive) setMimeType(MimeTypes.APPLICATION_M3U8) }
        .build()
}

internal fun MediaItem.toUnifiedTrack(): UnifiedTrack? {
    val json = mediaMetadata.extras?.getString(EXTRA_TRACK_JSON)
    if (!json.isNullOrBlank()) {
        val parsed = runCatching { HttpClientFactory.jsonConfig.decodeFromString<UnifiedTrack>(json) }.getOrNull()
        if (parsed != null) return parsed
    }

    // Robust fallback: Reconstruct UnifiedTrack from MediaItem standard fields when IPC drops extras Bundle
    val trackId = mediaId.takeIf { it.isNotBlank() } ?: requestMetadata.mediaUri?.wandaTrackId() ?: return null
    val resolvedSource = when {
        trackId.startsWith(SourceType.NAVIDROME.idPrefix) -> SourceType.NAVIDROME
        trackId.startsWith(SourceType.YTMUSIC.idPrefix) -> SourceType.YTMUSIC
        trackId.startsWith(SourceType.INTERNET_ARCHIVE.idPrefix) -> SourceType.INTERNET_ARCHIVE
        trackId.startsWith(SourceType.LOCAL.idPrefix) -> SourceType.LOCAL
        else -> SourceType.LOCAL
    }
    val trackTitle = mediaMetadata.title?.toString().takeIf { !it.isNullOrBlank() } ?: "Playing Track"
    val trackArtist = mediaMetadata.artist?.toString().takeIf { !it.isNullOrBlank() } ?: "Unknown Artist"
    val trackAlbum = mediaMetadata.albumTitle?.toString()
    val trackArtwork = mediaMetadata.artworkUri?.toString()

    return UnifiedTrack(
        id = trackId,
        source = resolvedSource,
        title = trackTitle,
        artist = trackArtist,
        album = trackAlbum,
        artworkUrl = trackArtwork,
        streamUri = requestMetadata.mediaUri?.toString()
    )
}

/** Extracts the track id from a placeholder URI produced by [toMediaItem]. */
internal fun Uri.wandaTrackId(): String? =
    if (scheme == WANDA_SCHEME) Uri.decode(lastPathSegment) else null
