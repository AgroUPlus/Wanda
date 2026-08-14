package com.wander.android.core.playback

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.wander.android.core.network.HttpClientFactory
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
        .build()
}

internal fun MediaItem.toUnifiedTrack(): UnifiedTrack? {
    val json = mediaMetadata.extras?.getString(EXTRA_TRACK_JSON) ?: return null
    return runCatching { HttpClientFactory.jsonConfig.decodeFromString<UnifiedTrack>(json) }.getOrNull()
}

/** Extracts the track id from a placeholder URI produced by [toMediaItem]. */
internal fun Uri.wandaTrackId(): String? =
    if (scheme == WANDA_SCHEME) Uri.decode(lastPathSegment) else null
