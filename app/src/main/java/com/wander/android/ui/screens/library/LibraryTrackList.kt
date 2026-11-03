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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.SkeletonRow
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.components.nestedListInset
import com.wander.android.ui.components.rememberShelfEntranceScale
import com.wander.android.ui.components.selection.SelectedTrack
import com.wander.android.ui.components.selection.TrackSelectionState

private const val TRACKS_PAGE_SIZE = 40
private const val SKELETON_ROWS = 8

@Composable
internal fun TrackList(
    tracks: List<UnifiedTrack>,
    tab: LibraryTab,
    isSyncing: Boolean,
    contentPadding: PaddingValues,
    viewModel: LibraryViewModel,
    onLongPress: (UnifiedTrack) -> Unit,
    selection: TrackSelectionState
) {
    if (tracks.isEmpty()) {
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
        return
    }

    var pageSize by remember(tracks) { mutableIntStateOf(TRACKS_PAGE_SIZE) }
    val visibleTracks = remember(tracks, pageSize) { tracks.take(pageSize) }
    val hasMore = visibleTracks.size < tracks.size

    val listState = rememberLazyListState()
    ScrollToTopOnReselect(listState)
    LazyColumn(
        state = listState,
        contentPadding = contentPadding.nestedListInset(),
        modifier = Modifier.fillMaxSize()
    ) {
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
            val entranceScale = rememberShelfEntranceScale(index)
            val item = SelectedTrack(track.id, track)
            TrackRow(
                track = track,
                onPlay = selection.onTap(item) { viewModel.play(tracks, index) },
                onToggleLike = { viewModel.toggleLike(track) },
                onLongPress = selection.onLongPress(item) { onLongPress(track) },
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
