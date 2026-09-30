package com.wander.android.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
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
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.AddToPlaylistController
import com.wander.android.ui.components.AddToPlaylistHost
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.GroupedInnerRadius
import com.wander.android.ui.components.GroupedItemGap
import com.wander.android.ui.components.GroupedOuterRadius
import com.wander.android.ui.components.SkeletonRow
import com.wander.android.ui.components.TrackActionsSheet
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.listInset
import com.wander.android.ui.components.rememberShelfEntranceScale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val SKELETON_ROWS = 8
private val PlayTime: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val plays = viewModel.plays.collectAsLazyPagingItems()
    val zone = remember { ZoneId.systemDefault() }
    var actionsFor by remember { mutableStateOf<UnifiedTrack?>(null) }

    val addToPlaylist = AddToPlaylistHost()

    actionsFor?.let { track ->
        HistoryTrackActions(track, viewModel, addToPlaylist, onOpenArtist) { actionsFor = null }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(contentPadding.headerInset())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
            Text(stringResource(R.string.library_history), style = MaterialTheme.typography.headlineLarge)
        }

        if (plays.itemCount == 0) {
            HistoryPlaceholder(plays, contentPadding)
        } else {
            LazyColumn(
                contentPadding = contentPadding.listInset(),
                modifier = Modifier.fillMaxSize()
            ) {
                historyItems(plays, zone, viewModel) { actionsFor = it }
                if (plays.loadState.append is LoadState.Loading) {
                    items(count = 3, key = { "history_skeleton_$it" }, contentType = { "skeleton" }) {
                        SkeletonRow(leadingSize = 48.dp, leadingShape = MaterialTheme.shapes.extraSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTrackActions(
    track: UnifiedTrack,
    viewModel: HistoryViewModel,
    addToPlaylist: AddToPlaylistController,
    onOpenArtist: (String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    TrackActionsSheet(
        track = track,
        isLiked = track.isLiked,
        onPlayNext = { viewModel.playNext(track) },
        onAddToQueue = { viewModel.addToQueue(track) },
        onStartRadio = { viewModel.startRadio(track) },
        onToggleLike = { viewModel.toggleLike(track) },
        onRemove = null,
        onOpenArtist = track.artist
            .takeIf { it.isNotBlank() }
            ?.let { artist -> { onOpenArtist(artist, track.artistId) } },
        onDismiss = onDismiss,
        onShare = null,
        onAddToPlaylist = if (addToPlaylist.canAdd(track)) {
            { addToPlaylist.open(track) }
        } else {
            null
        }
    )
}

/** What shows while the log has no rows yet: skeletons while the first page loads, else the empty state. */
@Composable
private fun HistoryPlaceholder(plays: LazyPagingItems<HistoryTrack>, contentPadding: PaddingValues) {
    if (plays.loadState.refresh is LoadState.Loading) {
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding.listInset())) {
            repeat(SKELETON_ROWS) {
                SkeletonRow(leadingSize = 48.dp, leadingShape = MaterialTheme.shapes.extraSmall)
            }
        }
        return
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            title = stringResource(R.string.library_nothing_played_yet),
            message = stringResource(R.string.library_everything_play_turns_up_here)
        )
    }
}

private fun LazyListScope.historyItems(
    plays: LazyPagingItems<HistoryTrack>,
    zone: ZoneId,
    viewModel: HistoryViewModel,
    onLongPress: (UnifiedTrack) -> Unit
) {
    items(
        count = plays.itemCount,
        key = plays.itemKey { it.historyId },
        contentType = plays.itemContentType { "play" }
    ) { index ->
        val entry = plays[index] ?: return@items
        HistoryEntry(plays, index, entry, zone, viewModel, onLongPress)
    }
}

@Composable
private fun HistoryEntry(
    plays: LazyPagingItems<HistoryTrack>,
    index: Int,
    entry: HistoryTrack,
    zone: ZoneId,
    viewModel: HistoryViewModel,
    onLongPress: (UnifiedTrack) -> Unit
) {
    val previous = if (index > 0) plays.peek(index - 1) else null
    val next = if (index < plays.itemCount - 1) plays.peek(index + 1) else null
    val isFirstOfDay = previous == null || !sameDay(previous.playedAt, entry.playedAt, zone)
    val isLastOfDay = next == null || !sameDay(next.playedAt, entry.playedAt, zone)

    val entranceScale = rememberShelfEntranceScale(index)
    Column(modifier = Modifier.scale(entranceScale)) {
        if (isFirstOfDay) {
            HistoryDateDivider(dayMillis = entry.playedAt, zone = zone)
        }
        HistoryRow(
            entry = entry,
            isFirstOfDay = isFirstOfDay,
            isLastOfDay = isLastOfDay,
            onPlay = {
                // Queues from what is currently loaded, not the whole log: a play log
                // has no natural "everything after this" the way an album does, so the
                // queue is exactly the window already on screen, same as before paging.
                val loaded = plays.itemSnapshotList.filterNotNull()
                val playIndex = loaded.indexOfFirst { it.historyId == entry.historyId }
                viewModel.play(loaded.map { it.track }, playIndex.coerceAtLeast(0))
            },
            onToggleLike = { viewModel.toggleLike(entry.track) },
            onLongPress = { onLongPress(entry.track) },
            zone = zone
        )
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryTrack,
    isFirstOfDay: Boolean,
    isLastOfDay: Boolean,
    onPlay: () -> Unit,
    onToggleLike: () -> Unit,
    onLongPress: () -> Unit,
    zone: ZoneId
) {
    TrackRow(
        track = entry.track,
        onPlay = onPlay,
        onToggleLike = onToggleLike,
        onLongPress = onLongPress,
        trailingLabel = Instant.ofEpochMilli(entry.playedAt).atZone(zone).toLocalTime().format(PlayTime),
        modifier = Modifier.dayGroupedItem(isFirstOfDay, isLastOfDay)
    )
}

/**
 * The grouped-card look ([groupedListItem][com.wander.android.ui.components.groupedListItem]),
 * driven by day-group edges instead of list position — a day's plays are the "card" here, not the
 * whole page, and a page boundary mid-day must not draw a seam paging alone did not create.
 */
@Composable
private fun Modifier.dayGroupedItem(isFirstOfDay: Boolean, isLastOfDay: Boolean): Modifier {
    val top = if (isFirstOfDay) GroupedOuterRadius else GroupedInnerRadius
    val bottom = if (isLastOfDay) GroupedOuterRadius else GroupedInnerRadius
    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    return this
        .padding(horizontal = 16.dp)
        .padding(top = if (isFirstOfDay) 0.dp else GroupedItemGap)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainer)
}
