package com.wander.android.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.LikeButton
import com.wander.android.ui.components.scrollingTitle

/**
 * The head of the controls panel: the title big and left-aligned, the artist (in the primary
 * colour, the way to their page) and the album under it, and the like heart on the right.
 *
 * [shown] can run ahead of [current] while the cover carousel is mid-swipe, so the text follows the
 * gesture; the heart always belongs to [current], the track it would actually like.
 */
@Composable
internal fun PlayerTitleBlock(
    shown: UnifiedTrack,
    shownIndex: Int,
    current: UnifiedTrack,
    isLiked: Boolean,
    onToggleLike: (UnifiedTrack) -> Unit,
    onOpenArtist: ((String, String?) -> Unit)?,
    onOpenAlbum: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(verticalAlignment = Alignment.Top, modifier = modifier.fillMaxWidth()) {
        TrackTextTransition(track = shown, queueIndex = shownIndex, modifier = Modifier.weight(1f)) { track ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineLargeEmphasized,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.scrollingTitle()
                )
                ArtistAlbumLine(track, onOpenArtist, onOpenAlbum)
            }
        }
        LikeButton(
            isLiked = isLiked,
            onToggle = { onToggleLike(current) },
            size = HeartSize,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun ArtistAlbumLine(
    track: UnifiedTrack,
    onOpenArtist: ((String, String?) -> Unit)?,
    onOpenAlbum: ((String) -> Unit)?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
    ) {
        Text(
            text = track.artist,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.scrollingTitle()
                // The name is the way to the artist's page, from the one screen that is always
                // about the track playing.
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable(enabled = onOpenArtist != null && track.artist.isNotBlank()) {
                    onOpenArtist?.invoke(track.artist, track.artistId)
                }
        )
        val album = track.album
        val albumId = track.albumId
        if (!album.isNullOrBlank()) {
            Text(
                text = " · $album",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.scrollingTitle()
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(enabled = albumId != null && onOpenAlbum != null) {
                        albumId?.let { onOpenAlbum?.invoke(it) }
                    }
            )
        }
    }
}

private val HeartSize = 30.dp
