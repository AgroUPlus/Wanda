package com.wander.android.ui.screens.playlist

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.MatchStatus
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.components.selection.SelectedTrack
import com.wander.android.ui.components.selection.SelectionAction
import com.wander.android.ui.components.selection.TrackSelectionState

/** The selection entry for a playlist row: keyed by the entry, carrying its position. */
internal fun PlaylistEntry.selectionItem() = SelectedTrack(key, track, index.toLong())

/** "Remove from playlist" for the selection, while at least one selected row may be removed. */
internal fun playlistSelectionActions(
    playlist: UnifiedPlaylist?,
    selection: TrackSelectionState,
    viewModel: PlaylistViewModel,
    sharedViewModel: SharedPlaylistViewModel
): List<SelectionAction> {
    val current = playlist ?: return emptyList()
    if (selection.selected.none { sharedViewModel.canRemove(current, it.ref.toInt()) }) return emptyList()
    return listOf(
        SelectionAction(Icons.Rounded.Delete, R.string.action_remove_from_playlist) { selected ->
            sharedViewModel.removeAll(current, selected.map { it.ref.toInt() }, viewModel::refresh)
        }
    )
}

/**
 * The playlist's track rows. Split out of [PlaylistScreen] for length.
 *
 * Reordering is off while a selection runs: a drag would move rows out from under the positions
 * the selection holds for "Remove from playlist".
 */
internal fun LazyListScope.playlistTrackRows(
    rows: List<PlaylistEntry>,
    playlist: UnifiedPlaylist?,
    reorder: PlaylistReorder,
    canMove: Boolean,
    isImporting: Boolean,
    viewModel: PlaylistViewModel,
    sharedViewModel: SharedPlaylistViewModel,
    selection: TrackSelectionState,
    onLongPress: (IndexedValue<UnifiedTrack>) -> Unit
) {
    itemsIndexed(
        items = rows,
        key = { _, entry -> entry.key },
        contentType = { _, _ -> "track" }
    ) { position, entry ->
        val index = entry.index
        val track = entry.track
        val unmatched = track.source == SourceType.UNRESOLVED
        // An unmatched track has nothing to play, but can still be taken out of a
        // playlist this account may edit.
        val removable = playlist?.let { sharedViewModel.canRemove(it, index) } == true
        val item = entry.selectionItem()
        PlaylistTrackItem(
            reorder = reorder,
            entry = entry,
            position = position,
            count = rows.size,
            draggable = canMove && !selection.isActive && !sharedViewModel.isPending(index)
        ) { rowModifier ->
            TrackRow(
                track = track,
                onPlay = selection.onTap(item) { viewModel.play(index) },
                onLongPress = selection.onLongPress(item) { onLongPress(IndexedValue(index, track)) }
                    ?.takeUnless { unmatched && !removable },
                trailingLabel = sharedViewModel.addedBy(index)
                    ?.let { stringResource(R.string.shared_playlist_added_by, it) },
                matchStatus = when {
                    !unmatched -> null
                    isImporting -> MatchStatus.MATCHING
                    else -> MatchStatus.NOT_FOUND
                },
                selected = selection.stateOf(item.key),
                modifier = rowModifier
            )
        }
    }
}
