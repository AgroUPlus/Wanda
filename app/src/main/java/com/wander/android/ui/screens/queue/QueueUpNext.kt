package com.wander.android.ui.screens.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.components.rememberHaptics
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * What is coming next: draggable by its handle, removable by a swipe.
 *
 * The row carries neither a like button nor a remove button. The heart was the same control the
 * player and every other list already offer for the track that is *playing*, repeated against songs
 * you have not heard yet — a second place to do a thing nobody comes to the queue to do. The remove
 * button is gone because a swipe does it better and, unlike the button, is undoable: see the
 * snackbar in [QueueDrawer]. What is left on the row is the song and the handle, which is what the
 * screen is for.
 *
 * Swipe direction is start-to-end only. End-to-start on a list row is the platform's second
 * gesture in mail apps, but the queue has one destructive action and a single direction cannot be
 * mistaken for the other.
 *
 * The row is written inline inside [ReorderableItem] rather than factored into its own composable:
 * `draggableHandle` exists only in that scope, and passing it outwards means passing a modifier
 * built in a scope the caller does not have.
 */
@Composable
internal fun QueueUpNext(
    entries: List<QueueEntry>,
    canReorder: Boolean,
    onPlay: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onLongPress: (UnifiedTrack) -> Unit,
    onRemove: (QueueEntry) -> Unit,
    modifier: Modifier = Modifier
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
        modifier = modifier.fillMaxWidth()
    ) {
        items(entries, key = { it.key }, contentType = { "queue-track" }) { entry ->
            ReorderableItem(reorderState, key = entry.key, enabled = canReorder) { isDragging ->
                val dismissState = rememberSwipeToDismissBoxState()

                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = true,
                    enableDismissFromEndToStart = false,
                    // Disabled mid-drag: the two gestures are perpendicular, but a row being
                    // carried by the handle should not also be removable by the hand carrying it.
                    gesturesEnabled = !isDragging,
                    onDismiss = { value ->
                        if (value == SwipeToDismissBoxValue.StartToEnd) {
                            haptics.settled()
                            onRemove(entry)
                        }
                    },
                    backgroundContent = { RemoveBackdrop() }
                ) {
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
                                onLongPress = { onLongPress(entry.track) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** What the swipe reveals underneath: the consequence, named, in the colour of consequences. */
@Composable
private fun RemoveBackdrop() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteSweep,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "Remove",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 12.dp)
            )
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
internal data class QueueEntry(
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
internal fun rememberQueueEntries(
    queue: List<UnifiedTrack>,
    currentIndex: Int
): List<QueueEntry> =
    remember(queue, currentIndex) {
        val seen = mutableMapOf<String, Int>()
        queue.mapIndexed { index, track ->
            val occurrence = seen.merge(track.id, 1, Int::plus)!! - 1
            QueueEntry(key = "${track.id}#$occurrence", track = track, queueIndex = index)
        }.filterIndexed { index, _ -> index > currentIndex }
    }

private val DraggingElevation = 6.dp
