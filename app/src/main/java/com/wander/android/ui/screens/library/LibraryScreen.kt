package com.wander.android.ui.screens.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.AddToPlaylistHost
import com.wander.android.ui.components.ExpressiveRefreshIndicator
import com.wander.android.ui.components.SourceFilterChips
import com.wander.android.ui.components.TrackActionsSheet
import com.wander.android.ui.components.headerInset

@Composable
fun LibraryScreen(
    contentPadding: PaddingValues,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPlaylist: (String) -> Unit = {},
    onOpenImport: () -> Unit = {},
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
            onOpenArtist = track.artist
                .takeIf { it.isNotBlank() }
                ?.let { artist -> { onOpenArtist(artist, track.artistId) } },
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
            },
            onDeleteDownload = if (tab == LibraryTab.DOWNLOADS || track.isDownloaded) {
                { viewModel.deleteDownloadedTrack(track) }
            } else null
        )
    }
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val likedTracks by viewModel.likedTracks.collectAsStateWithLifecycle()
    val downloadedTracks by viewModel.downloadedTracks.collectAsStateWithLifecycle()
    val historyTracks by viewModel.historyTracks.collectAsStateWithLifecycle()
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)
        ) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f)
            )
            // Where Home keeps Settings. History is a log rather than a collection, so it reads
            // better as one thing you can go and look at than as a sixth tab competing with them.
            IconButton(onClick = onOpenHistory) {
                Icon(Icons.Rounded.History, contentDescription = "Listening history")
            }
        }

        // The expressive indicator, and the reason it is worth spelling out: the stock one is a bar
        // spanning the whole tab, which on a row of six clipped labels read as a highlight on a
        // column rather than as a marker under a word. `matchContentSize` shrinks it to the label
        // it belongs to, and a rounded shape makes it a pill instead of a rule — so the selected
        // tab is legible from its silhouette before any text is read.
        PrimaryTabRow(
            selectedTabIndex = selectedPage,
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(selectedPage, matchContentSize = true),
                    width = Dp.Unspecified,
                    height = 3.dp,
                    shape = MaterialTheme.shapes.extraSmall
                )
            }
        ) {
            LibraryTab.entries.forEach { entry ->
                Tab(
                    selected = LibraryTab.entries.indexOf(entry) == selectedPage,
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
                    LibraryTab.PLAYLISTS ->
                        PlaylistList(playlists, contentPadding, viewModel, addToPlaylist, onOpenPlaylist, onOpenImport)
                    LibraryTab.LIKED ->
                        TrackList(likedTracks, pageTab, isRefreshing, contentPadding, viewModel) { actionsFor = it }
                    LibraryTab.DOWNLOADS ->
                        TrackList(downloadedTracks, pageTab, isRefreshing, contentPadding, viewModel) { actionsFor = it }
                    LibraryTab.TRACKS -> Column(modifier = Modifier.fillMaxSize()) {
                        if (viewModel.availableSources.size > 1) {
                            SourceFilterChips(
                                sources = viewModel.availableSources,
                                selected = sourceFilter,
                                onSelect = viewModel::selectSource,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        TrackList(tracks, pageTab, isRefreshing, contentPadding, viewModel) { actionsFor = it }
                    }
                }
            }
        }
    }
}
