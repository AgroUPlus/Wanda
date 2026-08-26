package com.wander.android.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.ui.components.AddToPlaylistHost
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.ExpressiveRefreshIndicator
import com.wander.android.ui.components.NewPlaylistDialog
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

    val addToPlaylist = AddToPlaylistHost()

    actionsFor?.let { track ->
        TrackActionsSheet(
            track = track,
            isLiked = track.isLiked,
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
            },
            onAddToPlaylist = if (addToPlaylist.canAdd(track)) {
                { addToPlaylist.open(track) }
            } else {
                null
            }
        )
    }
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val likedTracks by viewModel.likedTracks.collectAsStateWithLifecycle()
    val downloadedTracks by viewModel.downloadedTracks.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val recentAlbums by viewModel.recentAlbums.collectAsStateWithLifecycle()
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

        HorizontalPager(
            state = pagerState,
            key = { LibraryTab.entries[it] },
            modifier = Modifier
                .weight(1f)
                .padding(top = 4.dp)
        ) { page ->
            // `refresh()` and `isRefreshing` already existed on the ViewModel with nothing driving
            // them — the library could only be refreshed by leaving and coming back.
            val refreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                state = refreshState,
                indicator = {
                    ExpressiveRefreshIndicator(
                        isRefreshing = isRefreshing,
                        state = refreshState,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                },
                modifier = Modifier.fillMaxSize()
            ) {
                when (val pageTab = LibraryTab.entries[page]) {
                    LibraryTab.ALBUMS ->
                        AlbumGrid(albums, recentAlbums, contentPadding, onOpenAlbum)
                    LibraryTab.PLAYLISTS -> PlaylistList(playlists, contentPadding, viewModel)
                    LibraryTab.LIKED -> TrackList(likedTracks, pageTab, contentPadding, viewModel) { actionsFor = it }
                    LibraryTab.DOWNLOADS -> TrackList(downloadedTracks, pageTab, contentPadding, viewModel) { actionsFor = it }
                    LibraryTab.TRACKS -> Column(modifier = Modifier.fillMaxSize()) {
                        if (viewModel.availableSources.size > 1) {
                            SourceFilterChips(
                                sources = viewModel.availableSources,
                                selected = sourceFilter,
                                onSelect = viewModel::selectSource,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        TrackList(tracks, pageTab, contentPadding, viewModel) { actionsFor = it }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumGrid(
    albums: List<com.wander.android.data.model.UnifiedAlbum>,
    recentAlbums: List<com.wander.android.data.model.UnifiedAlbum>,
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
        // The one thing an alphabetical grid cannot tell you: what turned up lately. Only shown
        // once there is enough of a library for "recent" to mean something — with eight records
        // on the device the row would just be the grid again, in a different order.
        if (recentAlbums.size >= MinRecentAlbums && albums.size > recentAlbums.size) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "recent_header") {
                Text(
                    text = "Recently added",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "recent_row") {
                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                    items(recentAlbums, key = { "recent_" + it.id }) { album ->
                        AlbumCard(
                            album = album,
                            onClick = { onOpenAlbum(album.id) },
                            artworkSize = 140.dp,
                            modifier = Modifier.width(156.dp)
                        )
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "all_header") {
                Text(
                    text = "All albums",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
                )
            }
        }

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
    var naming by remember { mutableStateOf(false) }

    if (naming) {
        NewPlaylistDialog(
            onConfirm = { name ->
                naming = false
                viewModel.createPlaylist(name)
            },
            onDismiss = { naming = false }
        )
    }

    // The empty case used to return early, which meant a source that *can* make playlists offered
    // no way to make the first one — the only state in which you most need it.
    if (playlists.isEmpty()) {
        Centered {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(
                    title = "No playlists",
                    message = "Playlists from Navidrome, YouTube Music and the Internet Archive " +
                        "appear here once those sources are connected."
                )
                if (viewModel.canCreatePlaylists) {
                    Button(
                        onClick = { naming = true },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Text("New playlist", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        return
    }

    LazyColumn(contentPadding = contentPadding.listInset(), modifier = Modifier.fillMaxSize()) {
        if (viewModel.canCreatePlaylists) {
            item(key = "new_playlist", contentType = "action") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { naming = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "New playlist",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 20.dp)
                    )
                }
            }
        }
        items(playlists, key = { it.id }, contentType = { "playlist" }) { playlist ->
            PlaylistRow(
                playlist = playlist,
                onPlay = { viewModel.openPlaylist(playlist) },
                onShare = { viewModel.sharePlaylist(playlist) }
                    .takeIf { viewModel.canShare(playlist.source) }
            )
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

/** Below this the row is not telling you anything the grid underneath it does not. */
private const val MinRecentAlbums = 4
