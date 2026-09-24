package com.wander.android.ui.screens.queue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.AddToPlaylistHost
import com.wander.android.ui.components.TrackActionsSheet
import com.wander.android.ui.screens.social.JamViewModel

@Composable
internal fun QueueScreen(
    playerConnection: PlayerConnection,
    onClose: () -> Unit,
    onOpenJam: () -> Unit = {},
    onOpenArtist: ((String, String?) -> Unit)? = null,
    jamViewModel: JamViewModel = hiltViewModel(),
    queueViewModel: QueueViewModel = hiltViewModel()
) {
    val state by playerConnection.state.collectAsStateWithLifecycle()
    val jamState by jamViewModel.state.collectAsStateWithLifecycle()
    val jam = jamState.jam
    var actionsFor by remember { mutableStateOf<UnifiedTrack?>(null) }
    val addToPlaylist = AddToPlaylistHost()

    actionsFor?.let { track ->
        TrackActionsSheet(
            track = track,
            isLiked = track.isLiked,
            onPlayNext = {
                queueViewModel.playNext(track)
                actionsFor = null
            },
            onAddToQueue = {
                queueViewModel.addToQueue(track)
                actionsFor = null
            },
            onStartRadio = {
                queueViewModel.startRadio(track)
                actionsFor = null
            },
            onToggleLike = { queueViewModel.toggleLike(track) },
            onRemove = {
                val idx = state.queue.indexOfFirst { it.id == track.id }
                if (idx >= 0) queueViewModel.removeFromQueue(idx)
                actionsFor = null
            },
            onOpenArtist = track.artist
                .takeIf { it.isNotBlank() }
                ?.let { artist -> onOpenArtist?.let { open -> { open(artist, track.artistId) } } },
            onDismiss = { actionsFor = null },
            onShare = if (queueViewModel.canShare(track)) {
                { queueViewModel.share(track) }
            } else null,
            onAddToPlaylist = if (addToPlaylist.canAdd(track)) {
                { addToPlaylist.open(track) }
            } else null
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.queue_close_queue))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
                    .then(if (jam != null) Modifier.clickable(onClick = onOpenJam) else Modifier)
            ) {
                Text(
                    text = if (jam != null) "Jam Queue · ${jam.code}" else "Up next",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (jam != null) {
                    Text(
                        text = stringResource(R.string.queue_members_tap_open_jam, jam.members.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (jam == null && state.queue.isNotEmpty()) {
                IconButton(onClick = playerConnection::clearQueue) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = stringResource(R.string.action_clear_queue))
                }
            }
        }

        if (jam != null) {
            JamQueueContent(
                jam = jam,
                onApprove = jamViewModel::approve,
                onRemove = jamViewModel::remove
            )
        } else {
            LocalQueueContent(
                state = state,
                playerConnection = playerConnection,
                onTrackLongPress = { actionsFor = it },
                onToggleLike = { queueViewModel.toggleLike(it) }
            )
        }
    }
}
