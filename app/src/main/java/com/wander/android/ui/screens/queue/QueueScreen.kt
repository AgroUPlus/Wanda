package com.wander.android.ui.screens.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.sources.agro.Jam
import com.wander.android.data.sources.agro.JamTrack
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.screens.social.JamViewModel

@Composable
internal fun QueueScreen(
    playerConnection: PlayerConnection,
    onClose: () -> Unit,
    onOpenJam: () -> Unit = {},
    jamViewModel: JamViewModel = hiltViewModel()
) {
    val state by playerConnection.state.collectAsStateWithLifecycle()
    val jamState by jamViewModel.state.collectAsStateWithLifecycle()
    val jam = jamState.jam

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 8.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Close queue")
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
                        text = "${jam.members.size} members · Tap to open Jam",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (jam != null) {
                FilledTonalButton(onClick = onOpenJam) {
                    Text("Room")
                }
            } else if (state.queue.isNotEmpty()) {
                IconButton(onClick = playerConnection::clearQueue) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear queue")
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
                playerConnection = playerConnection
            )
        }
    }
}

@Composable
private fun JamQueueContent(
    jam: Jam,
    onApprove: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Now playing card for the Jam
        jam.nowPlaying?.let { now ->
            item(key = "jam_now_playing") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Artwork(
                            url = now.artworkUrl,
                            contentDescription = now.title,
                            sizeDp = 56.dp,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.GraphicEq,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "PLAYING IN JAM",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = now.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = now.artist,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Proposals if any
        if (jam.proposals.isNotEmpty()) {
            item(key = "proposals_header") {
                Text(
                    text = "Waiting for room votes",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }
            items(jam.proposals, key = { "proposal_${it.id}" }) { track ->
                JamProposalItem(track = track, onApprove = { onApprove(track.id) })
            }
        }

        // Up next items in the Jam
        item(key = "up_next_header") {
            Text(
                text = "Up next in room",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
        }

        if (jam.queue.isEmpty()) {
            item(key = "jam_queue_empty") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "Jam queue is empty. Tap any song in the app to suggest or add it to the room!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            itemsIndexed(jam.queue, key = { index, track -> "jam_track_${track.id}_$index" }) { index, track ->
                JamQueueItem(
                    index = index + 1,
                    track = track,
                    canRemove = jam.isHost || track.addedBy.equals(jam.members.firstOrNull(), ignoreCase = true),
                    onRemove = { onRemove(track.id) }
                )
            }
        }
    }
}

@Composable
private fun JamQueueItem(
    index: Int,
    track: JamTrack,
    canRemove: Boolean,
    onRemove: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
        ) {
            Text(
                text = "$index",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp)
            )
            Artwork(
                url = track.artworkUrl,
                contentDescription = track.title,
                sizeDp = 44.dp,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = " · @${track.addedBy}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }

            if (canRemove) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Remove from Jam",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun JamProposalItem(
    track: JamTrack,
    onApprove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Artwork(
                url = track.artworkUrl,
                contentDescription = track.title,
                sizeDp = 44.dp,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} · from @${track.addedBy}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (track.approved) {
                Text(
                    text = "Voted (${track.approvals})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            } else {
                FilledTonalButton(onClick = onApprove) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Vote")
                }
            }
        }
    }
}

@Composable
private fun LocalQueueContent(
    state: com.wander.android.core.playback.PlaybackState,
    playerConnection: PlayerConnection
) {
    if (state.queue.isEmpty()) {
        EmptyState(
            title = "The queue is empty",
            message = "Play something and it will show up here.",
            modifier = Modifier.padding(top = 96.dp)
        )
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        itemsIndexed(
            items = state.queue,
            key = { index, track -> "$index-${track.id}" },
            contentType = { _, _ -> "track" }
        ) { index, track ->
            TrackRow(
                track = track,
                isPlaying = index == state.currentIndex,
                onPlay = { playerConnection.seekToIndex(index) },
                onRemove = { playerConnection.removeFromQueue(index) }
            )
        }
    }
}
