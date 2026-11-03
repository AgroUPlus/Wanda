package com.wander.android.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.wander.android.R
import com.wander.android.data.model.HistoryTrack
import com.wander.android.ui.components.AddToPlaylistHost
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.SkeletonRow
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.nestedListInset
import com.wander.android.ui.components.rememberShelfEntranceScale
import com.wander.android.ui.components.selection.TrackSelectionBar
import com.wander.android.ui.components.selection.TrackSelectionState
import com.wander.android.ui.components.selection.rememberTrackSelectionState
import java.time.ZoneId

private const val SKELETON_ROWS = 8

/**
 * Everything you have played, newest first, grouped by the day it happened.
 *
 * Its own screen rather than a tab. History is a log, not a collection — you never curate it, and
 * it was taking a sixth of a tab row whose labels were already clipping. Reached from the icon in
 * the Library header, the way Settings is reached from Home.
 *
 * Paged rather than loaded whole: a play log only grows, and reading the last thousand rows in
 * full on every new play is the exact freeze [LibraryTrackList]'s [PagedTrackList] already solved
 * for the library tab — see [HistoryViewModel.plays].
 */
@Composable
internal fun HistoryScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    onOpenAlbum: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val plays = viewModel.plays.collectAsLazyPagingItems()
    val zone = remember { ZoneId.systemDefault() }
    var actionsFor by remember { mutableStateOf<HistoryTrack?>(null) }

    val addToPlaylist = AddToPlaylistHost()
    val selection = rememberTrackSelectionState()

    actionsFor?.let { entry ->
        HistoryTrackActions(
            entry, viewModel, addToPlaylist, onOpenArtist, onOpenAlbum,
            onSelect = { selection.toggle(entry.selectionItem()) }
        ) { actionsFor = null }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding.headerInset())
    ) {
        // The header gives way to the selection's bar while one runs; the Column already clears
        // the status bar, so the bar needs no inset of its own.
        TrackSelectionBar(
            selection = selection,
            addToPlaylist = addToPlaylist,
            topInset = 0.dp,
            extraActions = historySelectionActions(viewModel)
        )
        if (!selection.isActive) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(end = 8.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
                Text(
                    stringResource(R.string.library_history),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.weight(1f)
                )
                if (plays.itemCount > 0) HistoryClearButton(onClear = viewModel::clearAll)
            }
        }

        if (plays.itemCount == 0) {
            HistoryPlaceholder(plays, contentPadding)
        } else {
            LazyColumn(
                contentPadding = contentPadding.nestedListInset(),
                modifier = Modifier.fillMaxSize()
            ) {
                historyItems(plays, zone, viewModel, selection) { actionsFor = it }
                if (plays.loadState.append is LoadState.Loading) {
                    items(count = 3, key = { "history_skeleton_$it" }, contentType = { "skeleton" }) {
                        SkeletonRow(leadingSize = 48.dp, leadingShape = MaterialTheme.shapes.extraSmall)
                    }
                }
            }
        }
    }
}

/** What shows while the log has no rows yet: skeletons while the first page loads, else the empty state. */
@Composable
private fun HistoryPlaceholder(plays: LazyPagingItems<HistoryTrack>, contentPadding: PaddingValues) {
    if (plays.loadState.refresh is LoadState.Loading) {
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding.nestedListInset())) {
            repeat(SKELETON_ROWS) {
                SkeletonRow(leadingSize = 48.dp, leadingShape = MaterialTheme.shapes.extraSmall)
            }
        }
        return
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            title = R.string.library_nothing_played_yet,
            message = R.string.library_everything_play_turns_up_here
        )
    }
}

private fun LazyListScope.historyItems(
    plays: LazyPagingItems<HistoryTrack>,
    zone: ZoneId,
    viewModel: HistoryViewModel,
    selection: TrackSelectionState,
    onLongPress: (HistoryTrack) -> Unit
) {
    items(
        count = plays.itemCount,
        key = plays.itemKey { it.historyId },
        contentType = plays.itemContentType { "play" }
    ) { index ->
        val entry = plays[index] ?: return@items
        HistoryEntry(plays, index, entry, zone, viewModel, selection, onLongPress, Modifier.animateItem())
    }
}

@Composable
private fun HistoryEntry(
    plays: LazyPagingItems<HistoryTrack>,
    index: Int,
    entry: HistoryTrack,
    zone: ZoneId,
    viewModel: HistoryViewModel,
    selection: TrackSelectionState,
    onLongPress: (HistoryTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    val previous = if (index > 0) plays.peek(index - 1) else null
    val next = if (index < plays.itemCount - 1) plays.peek(index + 1) else null
    val isFirstOfDay = previous == null || !sameDay(previous.playedAt, entry.playedAt, zone)
    val isLastOfDay = next == null || !sameDay(next.playedAt, entry.playedAt, zone)

    val entranceScale = rememberShelfEntranceScale(index)
    Column(modifier = modifier.scale(entranceScale)) {
        if (isFirstOfDay) {
            HistoryDateDivider(dayMillis = entry.playedAt, zone = zone)
        }
        val item = entry.selectionItem()
        HistoryRow(
            entry = entry,
            isFirstOfDay = isFirstOfDay,
            isLastOfDay = isLastOfDay,
            onPlay = selection.onTap(item) {
                // Queues from what is currently loaded, not the whole log: a play log
                // has no natural "everything after this" the way an album does, so the
                // queue is exactly the window already on screen, same as before paging.
                val loaded = plays.itemSnapshotList.filterNotNull()
                val playIndex = loaded.indexOfFirst { it.historyId == entry.historyId }
                viewModel.play(loaded.map { it.track }, playIndex.coerceAtLeast(0))
            },
            onToggleLike = { viewModel.toggleLike(entry.track) },
            onLongPress = selection.onLongPress(item) { onLongPress(entry) },
            selected = selection.stateOf(item.key),
            swipeEnabled = !selection.isActive,
            zone = zone
        )
    }
}
