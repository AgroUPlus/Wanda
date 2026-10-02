package com.wander.android.ui.components

import com.wander.android.data.model.SourceType
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
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
    var confirmingDelete by remember { mutableStateOf(false) }
    if (confirmingDelete && onDelete != null) {
        // A Wanda playlist exists only here; a backend's is deleted on the server too. Either way
        // there is no undo, so the sheet gives way to the question and either answer closes it.
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete_playlist_title, playlist.name),
            message = stringResource(
                if (playlist.source == SourceType.LOCAL) R.string.confirm_delete_playlist_local
                else R.string.confirm_delete_playlist_remote,
                playlist.source.displayName
            ),
            confirmLabel = stringResource(R.string.common_delete_playlist),
            onConfirm = onDelete,
            onDismiss = onDismiss
        )
        return
    }

    WandaSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) { animatedDismiss ->
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
                    add(MenuAction(Icons.Rounded.PlayArrow, play, ActionEmphasis.PRIMARY) { onPlay(); animatedDismiss() })
                    add(MenuAction(Icons.AutoMirrored.Rounded.PlaylistAdd, playNext, ActionEmphasis.SECONDARY) { onPlayNext(); animatedDismiss() })
                    onShare?.let { add(MenuAction(Icons.Rounded.Share, share, ActionEmphasis.ICON) { it(); animatedDismiss() }) }
                    onAddToPlaylist?.let { add(MenuAction(Icons.Rounded.LibraryAdd, addToPlaylist, ActionEmphasis.ICON) { it(); animatedDismiss() }) }
                    add(MenuAction(Icons.AutoMirrored.Rounded.QueueMusic, addQueue) { onAddToQueue(); animatedDismiss() })
                    onDelete?.let { add(MenuAction(Icons.Rounded.Delete, delete, ActionEmphasis.DANGER) { confirmingDelete = true }) }
                }
            )
        }
    }
}
