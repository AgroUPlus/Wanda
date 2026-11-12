package com.wander.android.ui.screens.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.GroupedItemGap
import com.wander.android.ui.components.groupedItemShape
import com.wander.android.ui.components.rememberHaptics
import com.wander.android.ui.components.rememberRubberBandOverscroll
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * The Up next tab: what comes after the playing track, dragged into order or swiped away, and —
 * after the last of it — [footer], which says what happens once it runs out.
 *
 * The playing track is not a row here: it is written on the drawer's cover, above the tabs. What
 * has already played is the other tab, `QueueHistory`.
 */
@Composable
internal fun QueueUpNext(
    entries: List<QueueEntry>,
    canReorder: Boolean,
    onPlay: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onLongPress: (QueueEntry) -> Unit,
    onRemove: (QueueEntry) -> Unit,
    footer: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()
    val listState = rememberLazyListState()
    // The upcoming order on screen. A move reaches the player over IPC and comes back a frame or
    // more later, but the reorder library needs the list to reflect a move the moment it reports
    // one — until then it draws the dragged row at its old slot, which is the flash above the
    // finger. So moves land here first, and the player's own list is adopted whenever it changes
    // outside a drag, and once more when the drag ends.
    val upcoming = entries.filter { it.role == QueueItemRole.UP_NEXT }
    var ordered by remember { mutableStateOf(upcoming) }

    // `ordered` trails `entries`, so a track that has just started playing is still in it: a row
    // for a track the cover already shows. Only the tracks that are still upcoming are kept.
    val upcomingKeys = remember(upcoming) { upcoming.mapTo(HashSet()) { it.key } }
    fun stillUpcoming() = ordered.filter { it.key in upcomingKeys }

    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        // Only upcoming tracks move, and only among themselves: the footer is a fixed point, not a
        // slot a track can be dropped into.
        val live = stillUpcoming()
        val fromIndex = live.indexOfFirst { it.key == from.key }
        val toIndex = live.indexOfFirst { it.key == to.key }
        if (fromIndex < 0 || toIndex < 0) return@rememberReorderableLazyListState
        val first = live.first().queueIndex
        ordered = live.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        onMove(first + fromIndex, first + toIndex)
    }

    val dragging = reorderState.isAnyItemDragging
    // Mid-drag echoes are skipped: each one reflects only the moves sent before it, and adopting
    // it would pull the row back a slot until the next one lands.
    LaunchedEffect(entries, dragging) { if (!dragging) ordered = upcoming }
    val shown = if (dragging) stillUpcoming() else upcoming

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(GroupedItemGap),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        // Rubber band at the bottom, the drawer at the top. The band only gets what the drawer's
        // `drawerNestedScroll` leaves: its post-scroll takes every downward pull past the top, so
        // the top edge still drags the drawer closed and never stretches; upward pulls past the
        // bottom of an open drawer are left alone, so those stretch. A fling into the top edge can
        // still bounce the band — the drawer only takes flings while it is partly open.
        overscrollEffect = rememberRubberBandOverscroll(),
        modifier = modifier.fillMaxWidth()
    ) {
        itemsIndexed(shown, key = { _, entry -> entry.key }, contentType = { _, _ -> "queue-track" }) { index, entry ->
            ReorderableItem(reorderState, key = entry.key, enabled = canReorder) { isDragging ->
                val shape = groupedItemShape(index, shown.size)
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
                        onLongPress = { onLongPress(entry) },
                        dragHandle = if (canReorder) {
                            {
                                draggableHandle(
                                    onDragStarted = { haptics.heldDown() },
                                    onDragStopped = { haptics.settled() }
                                )
                            }
                        } else {
                            null
                        },
                        onMoveUp = if (canReorder) {
                            shown.stepFrom(index, -1)?.let { to ->
                                { onMove(entry.queueIndex, to) }
                            }
                        } else {
                            null
                        },
                        onMoveDown = if (canReorder) {
                            shown.stepFrom(index, 1)?.let { to ->
                                { onMove(entry.queueIndex, to) }
                            }
                        } else {
                            null
                        }
                    )
                }
            }
        }

        footer?.let { end ->
            item(key = FOOTER_KEY, contentType = "queue-end") {
                Box(Modifier.animateItem()) { end() }
            }
        }
    }
}

/** Not a track key, which is always `id#occurrence#generation`. */
private const val FOOTER_KEY = "queue-end"
