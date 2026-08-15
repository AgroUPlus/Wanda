package com.wander.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.UnifiedTrack

/**
 * What you can do with a track, on long press.
 *
 * Everything here was previously reachable only by playing the track first and then finding the
 * control in the player — or, for queueing, not at all. Actions the track's source cannot perform
 * are absent rather than present-and-disabled: a source never advertises a feature it lacks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionsSheet(
    track: UnifiedTrack,
    isLiked: Boolean,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onStartRadio: (() -> Unit)?,
    onToggleLike: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
    /** Null unless the track's source can publish a public link — see `SourceCapabilities.share`. */
    onShare: (() -> Unit)? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Artwork(
                    url = track.artworkUrl,
                    contentDescription = null,
                    sizeDp = 48.dp,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(48.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SheetAction(Icons.Rounded.PlayArrow, "Play now") { onPlay(); onDismiss() }
            SheetAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Play next") { onPlayNext(); onDismiss() }
            SheetAction(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") { onAddToQueue(); onDismiss() }

            onStartRadio?.let {
                SheetAction(Icons.Rounded.Radio, "Start radio from this") { it(); onDismiss() }
            }
            onShare?.let {
                SheetAction(Icons.Rounded.Share, "Share a link") { it(); onDismiss() }
            }
            onToggleLike?.let {
                SheetAction(
                    icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    label = if (isLiked) "Remove from liked" else "Add to liked"
                ) { it(); onDismiss() }
            }
            onRemove?.let {
                SheetAction(
                    icon = Icons.Rounded.Delete,
                    label = "Remove from queue",
                    tint = MaterialTheme.colorScheme.error
                ) { it(); onDismiss() }
            }
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}
