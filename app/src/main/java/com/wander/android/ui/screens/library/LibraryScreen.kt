package com.wander.android.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.SourceFilterChips
import com.wander.android.ui.components.TrackActionsSheet
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.listInset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    contentPadding: PaddingValues,
    onOpenAlbum: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val sourceFilter by viewModel.sourceFilter.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    var actionsFor by remember { mutableStateOf<com.wander.android.data.model.UnifiedTrack?>(null) }

    actionsFor?.let { track ->
        TrackActionsSheet(
            track = track,
            isLiked = track.isLiked,
            onPlay = { viewModel.play(listOf(track), 0) },
            onPlayNext = { viewModel.playNext(track) },
            onAddToQueue = { viewModel.addToQueue(track) },
            onStartRadio = { viewModel.startRadio(track) },
            onToggleLike = { viewModel.toggleLike(track) },
            onRemove = null,
            onDismiss = { actionsFor = null },
            onShare = if (viewModel.canShare(track)) {
                { viewModel.share(track) }
            } else {
                null
            }
        )
    }
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val likedTracks by viewModel.likedTracks.collectAsStateWithLifecycle()
    val downloadedTracks by viewModel.downloadedTracks.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(
        initialPage = tab.ordinal,
        pageCount = { LibraryTab.entries.size }
    )

    // The pager is the source of truth while a swipe is in flight; the ViewModel catches up once
    // it settles. Driving it the other way during a drag would fight the user's finger.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            viewModel.selectTab(LibraryTab.entries[page])
        }
    }
    LaunchedEffect(tab) {
        if (tab.ordinal != pagerState.currentPage) pagerState.animateScrollToPage(tab.ordinal)
    }

    // Read through derivedStateOf: pagerState.currentPage changes on every frame of a swipe, and
    // reading it directly recomposed the title, tab row and chip row for each of those frames.
    val selectedPage by remember { derivedStateOf { pagerState.currentPage } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Top inset here rather than on the lists, so the title and tabs clear the status bar.
            .padding(contentPadding.headerInset())
    ) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
        )

        PrimaryTabRow(selectedTabIndex = selectedPage) {
            LibraryTab.entries.forEachIndexed { index, entry ->
                Tab(
                    selected = index == selectedPage,
                    onClick = { viewModel.selectTab(entry) },
                    text = { Text(entry.label, maxLines = 1, softWrap = false) }
                )
            }
        }

        // Composed on every page, not just Tracks. Rendering it conditionally *inside* the pager
        // made the list below it jump up and down mid-swipe as pages with and without it scrolled
        // past each other; reserving the row keeps every page's content at the same offset.
        val filtersApply = LibraryTab.entries[selectedPage] == LibraryTab.TRACKS
        SourceFilterChips(
            sources = viewModel.availableSources,
            selected = sourceFilter,
            onSelect = viewModel::selectSource,
            enabled = filtersApply,
            modifier = Modifier
                .padding(vertical = 12.dp)
                .graphicsLayer { alpha = if (filtersApply) 1f else 0f }
        )

        HorizontalPager(
            state = pagerState,
            key = { LibraryTab.entries[it] },
            modifier = Modifier.weight(1f)
        ) { page ->
            // `refresh()` and `isRefreshing` already existed on the ViewModel with nothing driving
            // them — the library could only be refreshed by leaving and coming back.
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    when (val pageTab = LibraryTab.entries[page]) {
                        LibraryTab.ALBUMS -> AlbumGrid(albums, contentPadding, onOpenAlbum)
                        LibraryTab.PLAYLISTS -> PlaylistList(playlists, contentPadding, viewModel)
                        LibraryTab.LIKED -> TrackList(likedTracks, pageTab, contentPadding, viewModel) { actionsFor = it }
                        LibraryTab.DOWNLOADS ->
                            TrackList(downloadedTracks, pageTab, contentPadding, viewModel) { actionsFor = it }
                        LibraryTab.TRACKS -> TrackList(tracks, pageTab, contentPadding, viewModel) { actionsFor = it }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumGrid(
    albums: List<com.wander.android.data.model.UnifiedAlbum>,
    contentPadding: PaddingValues,
    onOpenAlbum: (String) -> Unit
) {
    if (albums.isEmpty()) {
        Centered {
            EmptyState(
                title = "No albums yet",
                message = "Albums appear once a connected source has been browsed at least once."
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        contentPadding = contentPadding.listInset(),
        modifier = Modifier.fillMaxSize()
    ) {
        items(albums, key = { it.id }, contentType = { "album" }) { album ->
            // Opens the record rather than immediately playing it. Tapping an album to see what
            // is on it is at least as common as tapping it to hear it, and the page has a Play
            // button right at the top for the other case.
            AlbumCard(album = album, onClick = { onOpenAlbum(album.id) })
        }
    }
}

@Composable
private fun PlaylistList(
    playlists: List<com.wander.android.data.model.UnifiedPlaylist>,
    contentPadding: PaddingValues,
    viewModel: LibraryViewModel
) {
    if (playlists.isEmpty()) {
        Centered {
            EmptyState(
                title = "No playlists",
                message = "Playlists from Navidrome, YouTube Music and the Internet Archive " +
                    "appear here once those sources are connected."
            )
        }
        return
    }
    LazyColumn(contentPadding = contentPadding.listInset(), modifier = Modifier.fillMaxSize()) {
        items(playlists, key = { it.id }, contentType = { "playlist" }) { playlist ->
            PlaylistRow(playlist = playlist, onPlay = { viewModel.openPlaylist(playlist) })
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<com.wander.android.data.model.UnifiedTrack>,
    tab: LibraryTab,
    contentPadding: PaddingValues,
    viewModel: LibraryViewModel,
    onLongPress: (com.wander.android.data.model.UnifiedTrack) -> Unit
) {
    if (tracks.isEmpty()) {
        Centered {
            EmptyState(title = emptyTitleFor(tab), message = emptyMessageFor(tab))
        }
        return
    }
    LazyColumn(contentPadding = contentPadding.listInset(), modifier = Modifier.fillMaxSize()) {
        itemsIndexed(
            items = tracks,
            key = { _, track -> track.id },
            contentType = { _, _ -> "track" }
        ) { index, track ->
            TrackRow(
                track = track,
                onPlay = { viewModel.play(tracks, index) },
                onToggleLike = { viewModel.toggleLike(track) },
                onLongPress = { onLongPress(track) }
            )
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
