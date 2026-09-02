package com.wander.android.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.SkeletonRow
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.components.listInset

private const val TRACKS_PAGE_SIZE = 40
private const val SKELETON_ROWS = 8

@Composable
internal fun TrackList(
    tracks: List<UnifiedTrack>,
    tab: LibraryTab,
    isRefreshing: Boolean,
    contentPadding: PaddingValues,
    viewModel: LibraryViewModel,
    onLongPress: (UnifiedTrack) -> Unit
) {
    if (tracks.isEmpty()) {
        if (isRefreshing) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding.listInset())
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
        return
    }

    var pageSize by remember(tracks) { mutableIntStateOf(TRACKS_PAGE_SIZE) }
    val visibleTracks = remember(tracks, pageSize) { tracks.take(pageSize) }
    val hasMore = visibleTracks.size < tracks.size

    LazyColumn(contentPadding = contentPadding.listInset(), modifier = Modifier.fillMaxSize()) {
        itemsIndexed(
            items = visibleTracks,
            key = { _, track -> track.id },
            contentType = { _, _ -> "track" }
        ) { index, track ->
            if (index >= visibleTracks.size - 6 && hasMore) {
                LaunchedEffect(pageSize) {
                    pageSize = (pageSize + TRACKS_PAGE_SIZE).coerceAtMost(tracks.size)
                }
            }
            TrackRow(
                track = track,
                onPlay = { viewModel.play(tracks, index) },
                onToggleLike = { viewModel.toggleLike(track) },
                onLongPress = { onLongPress(track) }
            )
        }
        if (hasMore) {
            items(count = 3, key = { "track_skeleton_$it" }, contentType = { "skeleton" }) {
                SkeletonRow(leadingSize = 48.dp, leadingShape = MaterialTheme.shapes.extraSmall)
            }
        }
    }
}

/**
 * The library tab, paged from the database.
 *
 * Separate from [TrackList] rather than replacing it, because the two lists have different
 * problems. Liked and Downloads are tens of rows held in memory anyway and their in-composition
 * windowing is fine. The library is a thousand rows that were re-read and re-mapped in full on
 * every write to `tracks` — a like, a play count, a sync — and no amount of windowing the *display*
 * fixed that, because the cost was upstream of the display.
 */
@Composable
internal fun PagedTrackList(
    tracks: LazyPagingItems<UnifiedTrack>,
    tab: LibraryTab,
    isRefreshing: Boolean,
    contentPadding: PaddingValues,
    viewModel: LibraryViewModel,
    onLongPress: (UnifiedTrack) -> Unit
) {
    if (tracks.itemCount == 0) {
        // `refresh is Loading` as well as the caller's flag: a paged list is empty for a moment on
        // every filter change while the first page loads, and "your library is empty" shown in that
        // gap is a lie that lasts just long enough to be read.
        if (isRefreshing || tracks.loadState.refresh is LoadState.Loading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(contentPadding.listInset())
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
        return
    }

    LazyColumn(contentPadding = contentPadding.listInset(), modifier = Modifier.fillMaxSize()) {
        items(
            count = tracks.itemCount,
            key = tracks.itemKey { it.id },
            contentType = tracks.itemContentType { "track" }
        ) { index ->
            // Null while the page holding this row loads. It should not happen with placeholders
            // disabled, but the API is nullable and this is the app's busiest list to crash on.
            val track = tracks[index] ?: return@items
            TrackRow(
                track = track,
                // The queue is built from the database on tap, not from what the screen holds:
                // paging means the screen holds only the pages scrolled through, and playing the
                // fortieth track must still queue the whole library after it.
                onPlay = { viewModel.playFromLibrary(track) },
                onToggleLike = { viewModel.toggleLike(track) },
                onLongPress = { onLongPress(track) }
            )
        }
        if (tracks.loadState.append is LoadState.Loading) {
            items(count = 3, key = { "track_skeleton_$it" }, contentType = { "skeleton" }) {
                SkeletonRow(leadingSize = 48.dp, leadingShape = MaterialTheme.shapes.extraSmall)
            }
        }
    }
}

private fun emptyTitleFor(tab: LibraryTab) = when (tab) {
    LibraryTab.LIKED -> "Nothing liked yet"
    LibraryTab.DOWNLOADS -> "Nothing saved offline"
    else -> "Your library is empty"
}

private fun emptyMessageFor(tab: LibraryTab) = when (tab) {
    LibraryTab.LIKED -> "Tap the heart on any track to keep it here."
    LibraryTab.DOWNLOADS ->
        "Liked tracks download automatically on Wi-Fi while your phone is charging."
    else -> "Connect a source in Settings, or grant access to music on this device."
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
