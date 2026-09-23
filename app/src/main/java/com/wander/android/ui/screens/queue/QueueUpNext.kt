package com.wander.android.ui.screens.queue

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.components.GroupedItemGap
import com.wander.android.ui.components.groupedItemShape
import com.wander.android.ui.components.rememberHaptics
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * The unified queue timeline: a single continuous list of all tracks.
 * Past tracks sit above the currently playing track with muted opacity,
 * the active song is seamlessly highlighted with its animated equalizer,
 * and upcoming tracks sit below. Every track can be reordered or swiped to remove.
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

    val currentIdx = entries.indexOfFirst { it.role == QueueItemRole.CURRENT }
    LaunchedEffect(Unit) {
        if (currentIdx > 0) {
            listState.scrollToItem((currentIdx - 1).coerceAtLeast(0))
        }
    }

    // The order on screen. A move reaches the player over IPC and comes back a frame or more later,
    // but the reorder library needs the list to reflect a move the moment it reports one — until
    // then it draws the dragged row at its old slot, which is the flash above the finger. So moves
    // land here first, and the player's own list is adopted whenever it changes outside a drag, and
    // once more when the drag ends. Row positions match player indices one to one.
    var ordered by remember { mutableStateOf(entries) }

    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        ordered = ordered.toMutableList().apply { add(to.index, removeAt(from.index)) }
        onMove(from.index, to.index)
    }

    val dragging = reorderState.isAnyItemDragging
    // Mid-drag echoes are skipped: each one reflects only the moves sent before it, and adopting
    // it would pull the row back a slot until the next one lands.
    LaunchedEffect(entries, dragging) { if (!dragging) ordered = entries }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(GroupedItemGap),
        // Inset from the sheet's edges like a Settings group, so every row's rounded frame is
        // visible on all four sides instead of bleeding into the sheet.
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        itemsIndexed(ordered, key = { _, entry -> entry.key }, contentType = { _, _ -> "queue-track" }) { index, entry ->
            ReorderableItem(reorderState, key = entry.key, enabled = canReorder) { isDragging ->
                val rowShape = groupedItemShape(index, ordered.size)
                QueueSwipeToRemove(
                    enabled = !isDragging,
                    shape = rowShape,
                    onRemove = { onRemove(entry) }
                ) {
                    Surface(
                        tonalElevation = if (isDragging) DraggingElevation else 0.dp,
                        shadowElevation = if (isDragging) DraggingElevation else 0.dp,
                        // Rounded per its position in the list — tight on the edge shared with a
                        // neighbour, full on the edge it isn't — the same grouped-card look
                        // Settings uses, so each track reads as its own row rather than one
                        // unbroken list.
                        shape = rowShape,
                        // Opaque, and one step above the sheet's own `surfaceContainerLow` —
                        // matching it made every frame invisible. Opaque because
                        // `SwipeToDismissBox` keeps `RemoveBackdrop` stacked directly behind this
                        // at full size the whole time; a transparent row let the "Remove" label
                        // peek through behind every track, not just a swiped one.
                        // The playing track is marked by its whole cell changing colour, not by
                        // a second highlight painted inside the cell.
                        color = animateColorAsState(
                            targetValue = when {
                                isDragging -> MaterialTheme.colorScheme.surfaceContainerHighest
                                entry.role == QueueItemRole.CURRENT ->
                                    MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                            label = "queueCellColor"
                        ).value
                    ) {
                        val isCurrent = entry.role == QueueItemRole.CURRENT
                        val isPrevious = entry.role == QueueItemRole.PREVIOUS

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (isPrevious) 0.60f else 1f)
                        ) {
                            if (canReorder) {
                                IconButton(
                                    onClick = {},
                                    shapes = IconButtonDefaults.shapes(),
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .draggableHandle(
                                            onDragStarted = { haptics.heldDown() },
                                            onDragStopped = { haptics.settled() }
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DragIndicator,
                                        contentDescription = stringResource(R.string.queue_reorder, entry.track.title),
                                        tint = if (isCurrent) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            TrackRow(
                                track = entry.track,
                                isPlaying = isCurrent,
                                showBackground = false,
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

private val DraggingElevation = 6.dp

