package com.wander.android.ui.screens.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromEntry = entries.firstOrNull { it.key == from.key } ?: return@rememberReorderableLazyListState
        val toEntry = entries.firstOrNull { it.key == to.key } ?: return@rememberReorderableLazyListState
        onMove(fromEntry.queueIndex, toEntry.queueIndex)
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(GroupedItemGap),
        modifier = modifier.fillMaxWidth()
    ) {
        itemsIndexed(entries, key = { _, entry -> entry.key }, contentType = { _, _ -> "queue-track" }) { index, entry ->
            ReorderableItem(reorderState, key = entry.key, enabled = canReorder) { isDragging ->
                val dismissState = rememberSwipeToDismissBoxState()

                LaunchedEffect(entry.key) {
                    if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                        dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                    }
                }

                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = true,
                    enableDismissFromEndToStart = false,
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
                        shadowElevation = if (isDragging) DraggingElevation else 0.dp,
                        // Rounded per its position in the list — tight on the edge shared with a
                        // neighbour, full on the edge it isn't — the same grouped-card look
                        // Settings uses, so each track reads as its own row rather than one
                        // unbroken list.
                        shape = groupedItemShape(index, entries.size),
                        // Opaque even at rest, matching the sheet's own container colour —
                        // `SwipeToDismissBox` keeps `RemoveBackdrop` composed and stacked
                        // directly behind this at full size the whole time, only sliding this
                        // foreground aside to reveal it. A transparent resting colour let it
                        // bleed through every gap the row itself didn't paint over (the drag
                        // handle's margin, the space right of the title) — the "Remove" icon
                        // and label peeking out behind every track, not just a swiped one.
                        color = if (isDragging) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
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
                                // The row paints its own highlight, cross-fading like every
                                // other list in the app. This used to be a single overlay
                                // positioned over the list from `LazyListState.layoutInfo` so
                                // it could *travel* between rows — but an overlay outside the
                                // list has none of the list's geometry: it drew over the header
                                // whenever the current row's offset went negative (scrolled
                                // half out of the viewport), and it chased the scroll through a
                                // coroutine, so a fling left it visibly adrift from its row.
                                // A background inside the item is positioned and clipped by the
                                // list itself, which is what makes both of those unrepresentable.
                                showBackground = true,
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
                text = stringResource(R.string.common_remove),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

private val DraggingElevation = 6.dp
