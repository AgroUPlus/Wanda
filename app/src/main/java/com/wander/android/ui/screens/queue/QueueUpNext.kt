package com.wander.android.ui.screens.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.GroupedItemGap
import com.wander.android.ui.components.groupedItemShape
import com.wander.android.ui.components.rememberHaptics
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * The queue in three parts: the track playing now as a card of its own, then what comes next —
 * dragged into order or swiped away — and, folded away at the bottom, what has already played.
 *
 * It was one continuous list with the past above the present, which opened scrolled to the middle
 * and gave the played tracks, the part you least often want, the top of the drawer.
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
    var showPlayed by rememberSaveable { mutableStateOf(false) }

    val current = entries.firstOrNull { it.role == QueueItemRole.CURRENT }
    val played = entries.filter { it.role == QueueItemRole.PREVIOUS }

    // The upcoming order on screen. A move reaches the player over IPC and comes back a frame or
    // more later, but the reorder library needs the list to reflect a move the moment it reports
    // one — until then it draws the dragged row at its old slot, which is the flash above the
    // finger. So moves land here first, and the player's own list is adopted whenever it changes
    // outside a drag, and once more when the drag ends.
    val upcoming = entries.filter { it.role == QueueItemRole.UP_NEXT }
    var ordered by remember { mutableStateOf(upcoming) }

    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        // Only upcoming tracks move, and only among themselves: the card and the section titles
        // are fixed points, not slots a track can be dropped into.
        val fromIndex = ordered.indexOfFirst { it.key == from.key }
        val toIndex = ordered.indexOfFirst { it.key == to.key }
        if (fromIndex < 0 || toIndex < 0) return@rememberReorderableLazyListState
        val first = ordered.first().queueIndex
        ordered = ordered.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        onMove(first + fromIndex, first + toIndex)
    }

    val dragging = reorderState.isAnyItemDragging
    // Mid-drag echoes are skipped: each one reflects only the moves sent before it, and adopting
    // it would pull the row back a slot until the next one lands.
    LaunchedEffect(entries, dragging) { if (!dragging) ordered = upcoming }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(GroupedItemGap),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        current?.let { now ->
            item(key = now.key, contentType = "now-playing") {
                QueueNowPlayingCard(
                    track = now.track,
                    onLongPress = { onLongPress(now.track) },
                    modifier = Modifier.animateItem()
                )
            }
        }

        if (ordered.isNotEmpty()) {
            item(key = "up-next-title", contentType = "section-title") {
                QueueSectionTitle(R.string.queue_section_up_next, ordered.size, Modifier.animateItem())
            }
        }
        itemsIndexed(ordered, key = { _, entry -> entry.key }, contentType = { _, _ -> "queue-track" }) { index, entry ->
            ReorderableItem(reorderState, key = entry.key, enabled = canReorder) { isDragging ->
                val shape = groupedItemShape(index, ordered.size)
                QueueSwipeToRemove(
                    enabled = !isDragging,
                    shape = shape,
                    onRemove = { onRemove(entry) }
                ) {
                    QueueTrackTile(
                        entry = entry,
                        shape = shape,
                        isDragging = isDragging,
                        onPlay = { onPlay(entry.queueIndex) },
                        onLongPress = { onLongPress(entry.track) },
                        dragHandle = if (canReorder) {
                            {
                                draggableHandle(
                                    onDragStarted = { haptics.heldDown() },
                                    onDragStopped = { haptics.settled() }
                                )
                            }
                        } else null
                    )
                }
            }
        }

        playedSection(played, showPlayed, onToggle = { showPlayed = !showPlayed }, onPlay, onLongPress, onRemove)
    }
}

/** What has already played: a header that folds the tracks away, closed by default. */
private fun LazyListScope.playedSection(
    played: List<QueueEntry>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPlay: (Int) -> Unit,
    onLongPress: (UnifiedTrack) -> Unit,
    onRemove: (QueueEntry) -> Unit
) {
    if (played.isEmpty()) return
    item(key = "played-title", contentType = "section-title") {
        QueuePlayedToggle(count = played.size, expanded = expanded, onToggle = onToggle, modifier = Modifier.animateItem())
    }
    if (!expanded) return
    // Most recent first: the track just finished is the one you are likeliest to want back.
    val recentFirst = played.asReversed()
    itemsIndexed(recentFirst, key = { _, entry -> entry.key }, contentType = { _, _ -> "queue-track" }) { index, entry ->
        val shape = groupedItemShape(index, recentFirst.size)
        QueueSwipeToRemove(enabled = true, shape = shape, onRemove = { onRemove(entry) }) {
            QueueTrackTile(
                entry = entry,
                shape = shape,
                isDragging = false,
                onPlay = { onPlay(entry.queueIndex) },
                onLongPress = { onLongPress(entry.track) }
            )
        }
    }
}
