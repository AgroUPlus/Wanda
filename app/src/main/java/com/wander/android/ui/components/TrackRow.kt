package com.wander.android.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.UnifiedTrack

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: UnifiedTrack,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    onToggleLike: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    /** Long press. Null leaves the row without a context menu, as in the queue's reorder mode. */
    onLongPress: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onPlay, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Artwork(
            url = track.artworkUrl,
            contentDescription = null,
            sizeDp = 52.dp,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.size(52.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isPlaying) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // No quality badge here: it belongs to the track you are listening to, not to every
            // row in every list. Now Playing is the one place that shows it.
            Text(
                text = track.subtitle(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Rounded.RemoveCircleOutline,
                    contentDescription = "Remove from queue",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isPlaying) {
            Icon(
                imageVector = Icons.Rounded.Equalizer,
                contentDescription = "Now playing",
                tint = MaterialTheme.colorScheme.primary
            )
        } else if (onToggleLike != null) {
            IconButton(onClick = onToggleLike) {
                Icon(
                    imageVector = if (track.isLiked) Icons.Rounded.Favorite
                    else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (track.isLiked) "Remove from liked" else "Add to liked",
                    tint = if (track.isLiked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** "Artist · Album · 3:41", skipping whatever the source did not provide. */
private fun UnifiedTrack.subtitle(): String = listOfNotNull(
    artist.takeIf { it.isNotBlank() },
    album?.takeIf { it.isNotBlank() },
    durationFormatted.takeIf { durationMs > 0 }
).joinToString(" · ")
