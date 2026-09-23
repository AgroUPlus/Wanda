package com.wander.android.ui.screens.queue

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.ElasticSwipeBox
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
        // Inset from the sheet's edges like a Settings group, so every row's rounded frame is
        // visible on all four sides instead of bleeding into the sheet.
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        itemsIndexed(entries, key = { _, entry -> entry.key }, contentType = { _, _ -> "queue-track" }) { index, entry ->
            ReorderableItem(reorderState, key = entry.key, enabled = canReorder) { isDragging ->
                val rowShape = groupedItemShape(index, entries.size)
                ElasticSwipeBox(
                    onSwipeStart = { onRemove(entry) },
                    onSwipeEnd = null,
                    enabled = !isDragging,
                    dismissOnStart = true,
                    // Looser than the default: removing is what this swipe is for, so it should
                    // take a comfortable pull rather than a fight.
                    stretchLimit = QueueRemoveStretch,
                    background = { side, progress -> if (side != null) RemoveBackdrop(progress) },
                    // Clipped to the row's own frame so a swipe reveals "Remove" inside that frame
                    // rather than as a full-width strip across the group.
                    modifier = Modifier.clip(rowShape)
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
                        // `ElasticSwipeBox` keeps `RemoveBackdrop` stacked directly behind this
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

/** What the swipe reveals underneath: the consequence, named, in the colour of consequences. */
@Composable
private fun RemoveBackdrop(progress: Float) {
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
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.graphicsLayer {
                    val scale = 0.6f + 0.4f * progress
                    scaleX = scale
                    scaleY = scale
                }
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

/** Reach of the remove swipe's rubber band — see `ElasticSwipeBox`. */
private const val QueueRemoveStretch = 1.2f
