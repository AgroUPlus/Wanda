package com.wander.android.ui.screens.playlist

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.wander.android.data.model.UnifiedTrack
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

/** A playlist's rows while they can be dragged into a new order; see [rememberPlaylistReorder]. */
@Stable
internal class PlaylistReorder(
    val state: ReorderableLazyListState,
    /** The rows to draw: the dragged order mid-drag, the playlist's own otherwise. */
    val shown: List<PlaylistEntry>,
    val onDragStarted: (PlaylistEntry) -> Unit,
    val onDragStopped: () -> Unit
)

/**
 * Drag-to-reorder for a playlist, as one edit per drop.
 *
 * The queue sends every step of a drag to the player as it happens, which is cheap there. Here a
 * step is a write to Room or a whole playlist rewritten on a server, so the steps only move rows
 * on screen and the drop sends a single move, from where the row was read to where it landed.
 *
 * [onMove] shows that move in the playlist at once and says whether it could; the dragged order
 * is held until the moved list arrives, so the row never flashes back to the slot it came from.
 */
@Composable
internal fun rememberPlaylistReorder(
    listState: LazyListState,
    tracks: List<UnifiedTrack>,
    onMove: (from: Int, to: Int) -> Boolean
): PlaylistReorder {
    val entries = remember(tracks) { playlistEntries(tracks) }
    var ordered by remember { mutableStateOf<List<PlaylistEntry>?>(null) }
    var dragged by remember { mutableStateOf<PlaylistEntry?>(null) }
    val currentOnMove by rememberUpdatedState(onMove)
    val currentEntries by rememberUpdatedState(entries)

    // The header, banners and search field are not slots a row can be dropped into, and
    // `moving` leaves the order alone for any key that is not a row's.
    val state = rememberReorderableLazyListState(listState) { from, to ->
        ordered = (ordered ?: currentEntries).moving(from.key, to.key)
    }

    LaunchedEffect(entries) { if (dragged == null) ordered = null }

    return PlaylistReorder(
        state = state,
        shown = ordered ?: entries,
        onDragStarted = { entry ->
            dragged = entry
            ordered = currentEntries
        },
        onDragStopped = {
            val entry = dragged
            dragged = null
            val to = entry?.let { e -> ordered?.indexOfFirst { it.key == e.key } } ?: -1
            val moved = entry != null && to >= 0 && to != entry.index && currentOnMove(entry.index, to)
            if (!moved) ordered = null
        }
    )
}
