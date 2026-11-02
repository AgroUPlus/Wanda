package com.wander.android.core.playback.browse

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaConstants
import com.wander.android.data.model.UnifiedTrack

/**
 * The [MediaItem]s a media browser is shown.
 *
 * Deliberately not [com.wander.android.core.playback.toMediaItem]: that one is the *queue's* item,
 * and carries the whole track as JSON in its extras for the player's own use. A browse item goes
 * to another app, so it carries the id, what to print and the artwork — nothing that names where
 * the audio lives. The stream is resolved when the item is played, exactly as for any other.
 *
 * Artwork is passed as its URI. Media3 loads it through the session's bitmap loader before
 * handing the item to a legacy browser (Android Auto is one), which is the client that signs
 * Navidrome's artwork requests — the car itself never fetches it.
 */
internal fun folderItem(
    id: BrowseId.Node,
    title: CharSequence,
    mediaType: Int,
    subtitle: CharSequence? = null,
    artworkUrl: String? = null,
    grid: Boolean = false,
    playable: Boolean = false
): MediaItem = MediaItem.Builder()
    .setMediaId(id.encode())
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setArtworkUri(artworkUrl?.toUri())
            .setIsBrowsable(true)
            .setIsPlayable(playable)
            .setMediaType(mediaType)
            .apply { if (grid) setExtras(gridChildren()) }
            .build()
    )
    .build()

/** [track] as listed under [parent]; see [BrowseId.Track] for why the parent is in the id. */
internal fun trackItem(parent: BrowseId.Node, track: UnifiedTrack, partlyPlayed: Boolean = false): MediaItem =
    MediaItem.Builder()
        .setMediaId(BrowseId.Track(parent, track.id).encode())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setArtworkUri(track.artworkUrl?.toUri())
                .setDurationMs(track.durationMs.takeIf { it > 0L })
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(
                    if (track.isEpisode) MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE else MediaMetadata.MEDIA_TYPE_MUSIC
                )
                .apply { if (partlyPlayed) setExtras(partlyPlayedExtras()) }
                .build()
        )
        .build()

/** Asks the browser to draw this folder's children as tiles rather than rows. */
private fun gridChildren() = Bundle().apply {
    putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
    putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
}

/**
 * The car's "partly played" badge on an episode.
 *
 * Spelled out rather than taken from a constants class: these are `androidx.media`'s
 * `DESCRIPTION_EXTRAS_KEY_COMPLETION_STATUS` and its partly-played value, which Media3 passes
 * through to the browser untouched.
 */
private fun partlyPlayedExtras() = Bundle().apply {
    putInt(EXTRA_COMPLETION_STATUS, COMPLETION_STATUS_PARTIALLY_PLAYED)
}

private const val EXTRA_COMPLETION_STATUS = "android.media.extra.PLAYBACK_STATUS"
private const val COMPLETION_STATUS_PARTIALLY_PLAYED = 1
