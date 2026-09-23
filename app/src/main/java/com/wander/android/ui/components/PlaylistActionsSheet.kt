package com.wander.android.ui.components

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
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R
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
    WandaSheet(
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

            val play = stringResource(R.string.action_play)
            val playNext = stringResource(R.string.action_play_next)
            val addQueue = stringResource(R.string.common_add_queue)
            val addToPlaylist = stringResource(R.string.action_add_to_playlist)
            val share = stringResource(R.string.action_share)
            val delete = stringResource(R.string.common_delete_playlist)
            ActionButtonGroup(
                modifier = Modifier.padding(top = 8.dp),
                actions = buildList {
                    add(MenuAction(Icons.Rounded.PlayArrow, play, ActionEmphasis.PRIMARY) { onPlay(); onDismiss() })
                    add(MenuAction(Icons.AutoMirrored.Rounded.PlaylistAdd, playNext, ActionEmphasis.SECONDARY) { onPlayNext(); onDismiss() })
                    onShare?.let { add(MenuAction(Icons.Rounded.Share, share, ActionEmphasis.ICON) { it(); onDismiss() }) }
                    onAddToPlaylist?.let { add(MenuAction(Icons.Rounded.LibraryAdd, addToPlaylist, ActionEmphasis.ICON) { it(); onDismiss() }) }
                    add(MenuAction(Icons.AutoMirrored.Rounded.QueueMusic, addQueue) { onAddToQueue(); onDismiss() })
                    onDelete?.let { add(MenuAction(Icons.Rounded.Delete, delete, ActionEmphasis.DANGER) { it(); onDismiss() }) }
                }
            )
        }
    }
}
