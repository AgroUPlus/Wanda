package com.wander.android.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.FingerprintStatus

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
    onLongPress: (() -> Unit)? = null,
    /**
     * Whether tapping the row would actually play something. Defaults to the offline rule, so
     * every list dims the same tracks without being told to; the queue passes its own value.
     */
    enabled: Boolean = track.isPlayableNow(),
    /**
     * Whether this track has been fingerprinted. Null draws nothing at all.
     *
     * Null by default so the dozens of lists that have no opinion on it are unchanged, and so the
     * status is never fetched by a row that is not showing it — see `FingerprintStatusRepository`
     * on why this arrives as one map for a whole screen rather than a query per row.
     */
    fingerprintStatus: FingerprintStatus? = null
) {
    // Dimming the whole row rather than each piece: the row is one object, and fading the parts
    // separately made the artwork and the text disagree about how unavailable the track was.
    val rowAlpha = if (enabled) 1f else DisabledAlpha

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            // Long press stays live while the tap does not: the actions sheet is still useful on
            // an unplayable track — it is where "download" and "add to playlist" live.
            .combinedClickable(
                onClick = { if (enabled) onPlay() },
                onLongClick = onLongPress
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Artwork(
            url = track.artworkUrl,
            contentDescription = null,
            sizeDp = 52.dp,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .size(52.dp)
                .graphicsLayer { alpha = rowAlpha }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .graphicsLayer { alpha = rowAlpha }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    // `weight` rather than the bare scrolling modifier: the dot needs a reserved
                    // place, and without this a long title would push it off the row entirely.
                    modifier = Modifier.weight(1f, fill = false).scrollingTitle()
                )
                fingerprintStatus?.let {
                    Spacer(Modifier.width(6.dp))
                    FingerprintBadge(it)
                }
            }
            // No quality badge here: it belongs to the track you are listening to, not to every
            // row in every list. Now Playing is the one place that shows it.
            // The badge leads the subtitle rather than replacing it: which station this is still
            // matters, and a livestream's subtitle is where the channel name lives. Static here —
            // see [LiveChip]. Without it a live row was indistinguishable from an hour-long
            // recording until you played it and found you could not scrub.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                if (track.isLive) {
                    LiveChip(pulsing = false)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = track.subtitle(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.scrollingTitle()
                )
            }
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
            LikeButton(isLiked = track.isLiked, onToggle = onToggleLike)
        }
    }
}

/** Material's disabled-content opacity. */
private const val DisabledAlpha = 0.38f

/** "Artist · Album · 3:41", skipping whatever the source did not provide. */
private fun UnifiedTrack.subtitle(): String = listOfNotNull(
    artist.takeIf { it.isNotBlank() },
    album?.takeIf { it.isNotBlank() },
    durationFormatted.takeIf { durationMs > 0 }
).joinToString(" · ")
