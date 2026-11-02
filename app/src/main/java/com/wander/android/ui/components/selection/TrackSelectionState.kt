package com.wander.android.ui.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import com.wander.android.data.model.UnifiedTrack

/**
 * One selected row. [key] names the row rather than the track — a playlist may hold a song twice,
 * and a play log certainly does — and [ref] is what the screen needs to edit that row: a playlist
 * position, a history entry's id. Zero where the screen has no such edit.
 */
@Immutable
data class SelectedTrack(val key: String, val track: UnifiedTrack, val ref: Long = 0L)

/**
 * Which rows of a track list are selected, and so whether the list is in selection mode at all:
 * it is exactly while anything is selected, and leaves it when the last row is let go.
 *
 * Kept in selection order, which is the order a batch Play queues them in.
 */
@Stable
class TrackSelectionState {
    private val items = mutableStateListOf<SelectedTrack>()

    val selected: List<SelectedTrack> get() = items

    val tracks: List<UnifiedTrack> get() = items.map { it.track }

    val isActive: Boolean get() = items.isNotEmpty()

    val count: Int get() = items.size

    fun isSelected(key: String): Boolean = items.any { it.key == key }

    fun toggle(item: SelectedTrack) {
        val index = items.indexOfFirst { it.key == item.key }
        if (index >= 0) items.removeAt(index) else items.add(item)
    }

    fun clear() = items.clear()

    /** What tapping a row does: plays it normally, or toggles it while selecting. */
    fun onTap(item: SelectedTrack, otherwise: () -> Unit): () -> Unit =
        { if (isActive) toggle(item) else otherwise() }

    /** What a long press does: opens the row's menu normally, or toggles it while selecting. */
    fun onLongPress(item: SelectedTrack, otherwise: (() -> Unit)?): (() -> Unit)? =
        if (isActive) {
            { toggle(item) }
        } else {
            otherwise
        }

    /** For [com.wander.android.ui.components.TrackRow]'s `selected`: null outside selection mode. */
    fun stateOf(key: String): Boolean? = if (isActive) isSelected(key) else null
}

@Composable
fun rememberTrackSelectionState(): TrackSelectionState = remember { TrackSelectionState() }
