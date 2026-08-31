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
