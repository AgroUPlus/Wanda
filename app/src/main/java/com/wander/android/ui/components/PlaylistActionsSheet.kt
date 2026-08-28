package com.wander.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
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
import com.wander.android.data.model.UnifiedPlaylist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistActionsSheet(
    playlist: UnifiedPlaylist,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onDismiss: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Artwork(
                    url = playlist.coverArtUrl,
                    contentDescription = null,
                    sizeDp = 52.dp,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(52.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.scrollingTitle()
                    )
                    Text(
                        text = "${playlist.source.displayName}${if (playlist.songCount > 0) " • ${playlist.songCount} tracks" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            PlaylistSheetAction(
                icon = Icons.Rounded.PlayArrow,
                label = "Play"
            ) {
                onPlay()
                onDismiss()
            }

            PlaylistSheetAction(
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                label = "Play next"
            ) {
                onPlayNext()
                onDismiss()
            }

            PlaylistSheetAction(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                label = "Add to queue"
            ) {
                onAddToQueue()
                onDismiss()
            }

            if (onAddToPlaylist != null) {
                PlaylistSheetAction(
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    label = "Add to playlist"
                ) {
                    onAddToPlaylist()
                    onDismiss()
                }
            }

            if (onShare != null) {
                PlaylistSheetAction(
                    icon = Icons.Rounded.Share,
                    label = "Share"
                ) {
                    onShare()
                    onDismiss()
                }
            }

            if (onDelete != null) {
                PlaylistSheetAction(
                    icon = Icons.Rounded.Delete,
                    label = "Delete playlist",
                    tint = MaterialTheme.colorScheme.error
                ) {
                    onDelete()
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun PlaylistSheetAction(
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
