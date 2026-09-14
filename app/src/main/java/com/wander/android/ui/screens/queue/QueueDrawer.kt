package com.wander.android.ui.screens.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.AddToPlaylistHost
import com.wander.android.ui.components.TrackActionsSheet
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.components.rememberHaptics
import com.wander.android.ui.components.scrollingTitle
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * The queue, as a drawer over the player rather than a screen on top of it.
 *
 * It used to be a full navigation destination, which meant looking at what was coming next replaced
 * the thing you were listening to. A sheet keeps the player behind it and puts the running order in
 * reach of a thumb, which is what it is for.
 *
 * Only the local queue. A jam's order is voted on rather than dragged, and that screen is
 * substantially its own thing — the caller sends a jam to `QueueScreen` instead; see
 * `PlayerSheetContent`.
 */
@Composable
internal fun QueueDrawer(
    playerConnection: PlayerConnection,
    onDismiss: () -> Unit,
    onOpenArtist: ((String, String?) -> Unit)? = null,
    queueViewModel: QueueViewModel = hiltViewModel()
) {
    val state by playerConnection.state.collectAsStateWithLifecycle()
    var actionsFor by remember { mutableStateOf<UnifiedTrack?>(null) }
    val addToPlaylist = AddToPlaylistHost()
    val haptics = rememberHaptics()

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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(SheetHeightFraction)
                .navigationBarsPadding()
        ) {
            state.currentTrack?.let { current ->
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                    Text(
                        text = current.title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.scrollingTitle()
                    )
                    Text(
                        text = current.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.scrollingTitle()
                    )
                }
            }

            Text(
                text = "Up next",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp)
            )

            UpNextList(
                entries = rememberQueueEntries(state.queue, state.currentIndex),
                canReorder = !state.orderLocked,
                onPlay = { playerConnection.seekToIndex(it) },
                onMove = { from, to ->
                    playerConnection.moveInQueue(from, to)
                    haptics.settled()
                },
                onLongPress = { actionsFor = it },
                onToggleLike = { queueViewModel.toggleLike(it) },
                onRemove = { queueViewModel.removeFromQueue(it) }
            )
        }
    }
}

@Composable
private fun UpNextList(
    entries: List<QueueEntry>,
    canReorder: Boolean,
    onPlay: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onLongPress: (UnifiedTrack) -> Unit,
    onToggleLike: (UnifiedTrack) -> Unit,
    onRemove: (Int) -> Unit
) {
    val haptics = rememberHaptics()
    val listState = rememberLazyListState()

    // The move is applied to the player, not to a local copy of the list.
    //
    // `state.queue` is rebuilt from the controller's own timeline, so committing the move upstream
    // is what makes the row stay where it was dropped; keeping a local list in step as well would
    // give the same reorder two sources of truth and let them disagree mid-drag.
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromEntry = entries.getOrNull(from.index) ?: return@rememberReorderableLazyListState
        val toEntry = entries.getOrNull(to.index) ?: return@rememberReorderableLazyListState
        onMove(fromEntry.queueIndex, toEntry.queueIndex)
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(entries, key = { it.key }, contentType = { "queue-track" }) { entry ->
            ReorderableItem(reorderState, key = entry.key, enabled = canReorder) { isDragging ->
                Surface(
                    tonalElevation = if (isDragging) DraggingElevation else 0.dp,
                    shadowElevation = if (isDragging) DraggingElevation else 0.dp
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (canReorder) {
                            IconButton(
                                onClick = {},
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .draggableHandle(
                                        onDragStarted = { haptics.heldDown() },
                                        onDragStopped = { haptics.settled() }
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DragHandle,
                                    contentDescription = "Reorder ${entry.track.title}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        TrackRow(
                            track = entry.track,
                            onPlay = { onPlay(entry.queueIndex) },
                            onToggleLike = { onToggleLike(entry.track) },
                            onRemove = { onRemove(entry.queueIndex) },
                            onLongPress = { onLongPress(entry.track) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * One row of the drawer: the track, where it actually sits in the player's queue, and a key that
 * survives a reorder.
 *
 * [queueIndex] is the position in the *whole* queue, which is not the row's position in this list —
 * the list starts after whatever is playing. Every action goes through it, so dragging the third
 * row does not remove the third song in the queue.
 */
private data class QueueEntry(
    val key: String,
    val track: UnifiedTrack,
    val queueIndex: Int
)

/**
 * The queue after the current track, keyed so that reordering works.
 *
 * The key used to be `"$index-${track.id}"`, and an index in a key is fatal to a drag: every row
 * below the one being moved changes identity the instant it moves, so the list re-composes from
 * scratch under the finger instead of animating. The id alone will not do either — a queue may hold
 * the same song twice, and duplicate keys are an error in a `LazyColumn`. So the key is the id plus
 * how many times that id has already appeared, which is stable per row and unique per list.
 */
@Composable
private fun rememberQueueEntries(queue: List<UnifiedTrack>, currentIndex: Int): List<QueueEntry> =
    remember(queue, currentIndex) {
        val seen = mutableMapOf<String, Int>()
        queue.mapIndexed { index, track ->
            val occurrence = seen.merge(track.id, 1, Int::plus)!! - 1
            QueueEntry(key = "${track.id}#$occurrence", track = track, queueIndex = index)
        }.filterIndexed { index, _ -> index > currentIndex }
    }

/** Tall enough to be the queue, short enough that the player is still visibly behind it. */
private const val SheetHeightFraction = 0.82f

private val DraggingElevation = 6.dp
