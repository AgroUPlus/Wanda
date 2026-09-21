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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.agro.Jam
import com.wander.android.data.sources.agro.JamTrack
import com.wander.android.ui.components.AddToPlaylistHost
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.TrackActionsSheet
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.components.scrollingTitle
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
        // Header
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

            // No "Room" button: the header beside it already opens the room, and two controls
            // one tap apart doing the same thing is a menu asking which one is real.
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
                    shape = MaterialTheme.shapes.medium,
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
                            shape = MaterialTheme.shapes.small,
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
                                    text = stringResource(R.string.queue_playing_jam),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = now.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier.scrollingTitle()
                            )
                            Text(
                                text = now.artist,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier.scrollingTitle()
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
                    text = stringResource(R.string.queue_waiting_room_votes),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }
            items(jam.proposals, key = { "proposal_${it.id}" }) { track ->
                JamProposalItem(
                    track = track,
                    onApprove = { onApprove(track.id) },
                    modifier = Modifier.animateItem()
                )
            }
        }

        // Up next items in the Jam
        item(key = "up_next_header") {
            Text(
                text = stringResource(R.string.queue_up_next_room),
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
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.queue_jam_queue_empty_tap_any),
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
                    onRemove = { onRemove(track.id) },
                    modifier = Modifier.animateItem()
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
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth()
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
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.scrollingTitle()
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
                        contentDescription = stringResource(R.string.queue_remove_from_jam),
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
    onApprove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Artwork(
                url = track.artworkUrl,
                contentDescription = track.title,
                sizeDp = 44.dp,
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.scrollingTitle()
                )
                Text(
                    text = stringResource(R.string.queue_from, track.artist, track.addedBy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (track.approved) {
                Text(
                    text = stringResource(R.string.queue_voted, track.approvals),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            } else {
                FilledTonalButton(onClick = onApprove, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.queue_vote))
                }
            }
        }
    }
}

@Composable
private fun LocalQueueContent(
    state: com.wander.android.core.playback.PlaybackState,
    playerConnection: PlayerConnection,
    onTrackLongPress: (UnifiedTrack) -> Unit,
    onToggleLike: (UnifiedTrack) -> Unit
) {
    if (state.queue.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.queue_queue_empty),
            message = stringResource(R.string.queue_play_something_will_show_up),
            modifier = Modifier.padding(top = 96.dp)
        )
        return
    }

    // A tonal backdrop behind the whole list, not a card per row — grouping the *unit* the queue
    // is (everything coming up) without un-lazifying it or turning every track into its own card,
    // which is exactly what a `GroupedCard` per row would have done to a list this long.
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(
                items = state.queue,
                key = { index, track -> "$index-${track.id}" },
                contentType = { _, _ -> "track" }
            ) { index, track ->
                TrackRow(
                    track = track,
                    isPlaying = index == state.currentIndex,
                    onPlay = { playerConnection.seekToIndex(index) },
                    onToggleLike = { onToggleLike(track) },
                    // No remove button. Swiping a row away removes it, with an undo — a second
                    // control for the same action costs a slot on every row to say what the
                    // gesture already does, and it was the one thing between the row and its like
                    // button.
                    onLongPress = { onTrackLongPress(track) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}
