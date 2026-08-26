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

/**
 * Appended to a livestream's placeholder URI so the container can be inferred from it.
 *
 * `Util.inferContentTypeForUriAndMimeType` falls back to the last path segment's extension when no
 * MIME type reaches it, which is what happens across the controller-to-service boundary.
 */
internal const val LIVE_SUFFIX = ".m3u8"

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
    //
    // A livestream's placeholder carries an `.m3u8` suffix. The media-source factory picks
    // progressive or HLS *before* anything is fetched, and it decides from the MIME type or, with
    // no MIME type, from the URI — and the MIME type lives in `localConfiguration`, which is the
    // part of a MediaItem that does not survive the trip across IPC to the playback service. The
    // suffix does, because the URI is the one field that must arrive or nothing plays at all.
    val uri = streamUri?.takeIf { it.startsWith("/") || it.startsWith("file:") || it.startsWith("content:") }
        ?.toUri()
        ?: Uri.parse(
            "$WANDA_SCHEME://track/${Uri.encode(id)}" + if (isLive) LIVE_SUFFIX else ""
        )

    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setMediaMetadata(metadata)
        // Set as well as the URI suffix above. This is the hint the factory prefers when it
        // survives; the suffix is what makes it work when it does not.
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
internal fun Uri.wandaTrackId(): String? {
    if (scheme != WANDA_SCHEME) return null
    // The suffix is a hint to the media-source factory, not part of the id — see [toMediaItem].
    val segment = lastPathSegment?.removeSuffix(LIVE_SUFFIX) ?: return null
    return Uri.decode(segment)
}
