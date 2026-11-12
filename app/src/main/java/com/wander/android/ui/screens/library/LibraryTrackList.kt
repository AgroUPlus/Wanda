package com.wander.android.ui.screens.library

import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.rememberLazyListState
import com.wander.android.R
import com.wander.android.ui.components.ScrollToTopOnReselect
import com.wander.android.ui.components.groupedListItem
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.SkeletonRow
import com.wander.android.ui.components.nestedListInset
import com.wander.android.ui.components.rememberShelfEntranceScale
import com.wander.android.ui.components.selection.SelectedTrack
import com.wander.android.ui.components.selection.TrackSelectionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val TRACKS_PAGE_SIZE = 40
private const val SKELETON_ROWS = 8
private const val WINDOW_WAIT_MS = 500L

@Composable
internal fun TrackList(
    tracks: List<UnifiedTrack>,
    tab: LibraryTab,
    isSyncing: Boolean,
    contentPadding: PaddingValues,
    viewModel: LibraryViewModel,
    onLongPress: (UnifiedTrack) -> Unit,
    selection: TrackSelectionState,
    /** The letter a track is filed under in an alphabetical order, for the [LetterRail]. */
    letterFor: ((UnifiedTrack) -> String?)? = null,
    /** What [letterFor] was made from, so the rail re-reads the list when the order changes. */
    order: Any? = null
) {
    if (tracks.isEmpty()) {
        TrackListEmpty(tab, isSyncing, contentPadding)
        return
    }

    var pageSize by remember(tracks) { mutableIntStateOf(TRACKS_PAGE_SIZE) }
    val visibleTracks = remember(tracks, pageSize) { tracks.take(pageSize) }
    val hasMore = visibleTracks.size < tracks.size

    val listState = rememberLazyListState()
    ScrollToTopOnReselect(listState)
    val starts = rememberLetterStarts(tracks, order, letterFor)
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = contentPadding.nestedListInset(),
            modifier = Modifier
                .fillMaxSize()
                .padding(end = if (starts != null) LetterRailGutter else 0.dp)
        ) {
            trackItems(
                tracks = tracks,
                visibleTracks = visibleTracks,
                hasMore = hasMore,
                pageSize = pageSize,
                onNearEnd = { pageSize = (pageSize + TRACKS_PAGE_SIZE).coerceAtMost(tracks.size) },
                viewModel = viewModel,
                selection = selection,
                onLongPress = onLongPress
            )
        }
        if (starts != null) {
            val letterOf = letterFor
            LetterRail(
                starts = starts,
                letterAt = {
                    tracks.getOrNull(listState.firstVisibleItemIndex)?.let { railLetter(letterOf?.invoke(it)) }
                },
                onJump = { index ->
                    // The list only holds the window scrolled through so far; widen it to the target
                    // first, and wait for those rows to be in the list before jumping to one of them.
                    if (index >= pageSize) {
                        pageSize = (index + TRACKS_PAGE_SIZE).coerceAtMost(tracks.size)
                        withTimeoutOrNull(WINDOW_WAIT_MS) {
                            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > index }
                        }
                    }
                    listState.scrollToItem(index)
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(contentPadding.nestedListInset())
                    .padding(vertical = 8.dp)
            )
        }
    }
}

/** Skeleton rows while the first sync is still running, the tab's empty state once it is not. */
@Composable
private fun TrackListEmpty(tab: LibraryTab, isSyncing: Boolean, contentPadding: PaddingValues) {
    if (isSyncing) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding.nestedListInset())
        ) {
            repeat(SKELETON_ROWS) {
                SkeletonRow(leadingSize = 48.dp, leadingShape = MaterialTheme.shapes.extraSmall)
            }
        }
        return
    }
    Centered {
        EmptyState(title = emptyTitleFor(tab), message = emptyMessageFor(tab))
    }
}

/**
 * The [visibleTracks] window of [tracks], asking for the next page through [onNearEnd] as the
 * last rows come into view, with skeleton rows below while [hasMore] remain.
 */
private fun LazyListScope.trackItems(
    tracks: List<UnifiedTrack>,
    visibleTracks: List<UnifiedTrack>,
    hasMore: Boolean,
    pageSize: Int,
    onNearEnd: () -> Unit,
    viewModel: LibraryViewModel,
    selection: TrackSelectionState,
    onLongPress: (UnifiedTrack) -> Unit
) {
    itemsIndexed(
        items = visibleTracks,
        key = { _, track -> track.id },
        contentType = { _, _ -> "track" }
    ) { index, track ->
        if (index >= visibleTracks.size - 6 && hasMore) {
            LaunchedEffect(pageSize) { onNearEnd() }
        }
        val entranceScale = rememberShelfEntranceScale(index)
        val item = SelectedTrack(track.id, track)
        LibraryTrackRow(
            track = track,
            onPlay = selection.onTap(item) { viewModel.play(tracks, index) },
            onLongPress = selection.onLongPress(item) { onLongPress(track) },
            onMore = { onLongPress(track) },
            selected = selection.stateOf(item.key),
            modifier = Modifier
                .animateItem()
                .scale(entranceScale)
                .groupedListItem(index, visibleTracks.size)
        )
    }
    if (hasMore) {
        items(count = 3, key = { "track_skeleton_$it" }, contentType = { "skeleton" }) {
            SkeletonRow(leadingSize = 48.dp, leadingShape = MaterialTheme.shapes.extraSmall)
        }
    }
}

@StringRes
internal fun emptyTitleFor(tab: LibraryTab): Int = when (tab) {
    LibraryTab.LIKED -> R.string.library_liked_empty_title
    LibraryTab.DOWNLOADS -> R.string.library_downloads_empty_title
    LibraryTab.GENRES -> R.string.library_genre_empty_title
    else -> R.string.library_tracks_empty_title
}

@StringRes
internal fun emptyMessageFor(tab: LibraryTab): Int = when (tab) {
    LibraryTab.LIKED -> R.string.library_liked_empty_message
    LibraryTab.DOWNLOADS -> R.string.library_downloads_empty_message
    LibraryTab.GENRES -> R.string.library_genre_empty_message
    else -> R.string.library_tracks_empty_message
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
