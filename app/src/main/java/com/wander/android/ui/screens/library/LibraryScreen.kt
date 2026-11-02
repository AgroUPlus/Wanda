package com.wander.android.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.AddToPlaylistHost
import com.wander.android.ui.components.ExpressiveRefreshIndicator
import com.wander.android.ui.components.headerInset

@Composable
fun LibraryScreen(
    contentPadding: PaddingValues,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPlaylist: (String) -> Unit = {},
    onOpenImport: () -> Unit = {},
    onOpenSubscriptions: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
    browse: LibraryBrowseViewModel = hiltViewModel()
) {
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    // The same instance the Podcasts page resolves: both sit under this destination.
    val podcastsViewModel: PodcastsViewModel = hiltViewModel()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    var actionsFor by remember { mutableStateOf<UnifiedTrack?>(null) }
    var albumActionsFor by remember { mutableStateOf<UnifiedAlbum?>(null) }

    val addToPlaylist = AddToPlaylistHost()

    albumActionsFor?.let { album ->
        LibraryAlbumActions(album, viewModel, addToPlaylist, onOpenArtist) { albumActionsFor = null }
    }
    actionsFor?.let { track ->
        LibraryTrackActions(track, tab, viewModel, podcastsViewModel, addToPlaylist, onOpenArtist, onOpenAlbum) {
            actionsFor = null
        }
    }
    val pageActions = remember(onOpenAlbum, onOpenArtist, onOpenPlaylist, onOpenImport, onOpenSubscriptions) {
        LibraryPageActions(
            onOpenAlbum = onOpenAlbum,
            onOpenArtist = onOpenArtist,
            onOpenPlaylist = onOpenPlaylist,
            onOpenImport = onOpenImport,
            onOpenSubscriptions = onOpenSubscriptions,
            onTrackLongPress = { actionsFor = it },
            onAlbumLongPress = { albumActionsFor = it }
        )
    }
    val likedTracks by browse.likedTracks.collectAsStateWithLifecycle()
    val downloadedTracks by browse.downloadedTracks.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(
        initialPage = tabs.indexOf(tab).coerceAtLeast(0),
        pageCount = { tabs.size }
    )

    // The pager is the source of truth while a swipe is in flight; the ViewModel catches up once
    // it settles. Driving it the other way during a drag would fight the user's finger.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            tabs.getOrNull(page)?.let(viewModel::selectTab)
        }
    }
    LaunchedEffect(tab, tabs) {
        // A section that was hidden has no page; the pager keeps whatever page it is on.
        val target = tabs.indexOf(tab)
        if (target >= 0 && target != pagerState.currentPage) pagerState.animateScrollToPage(target)
    }

    // Read through derivedStateOf: pagerState.currentPage changes on every frame of a swipe, and
    // reading it directly recomposed the title, tab row and chip row for each of those frames.
    val selectedPage by remember { derivedStateOf { pagerState.currentPage } }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Top inset here rather than on the lists, so the title and tabs clear the status bar.
                .padding(contentPadding.headerInset())
        ) {
            LibraryTitleRow(onOpenHistory)
            LibraryTabRow(tabs, selectedPage, viewModel::selectTab)

            HorizontalPager(
                state = pagerState,
                key = { tabs[it] },
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 4.dp)
            ) { page ->
                // `refresh()` and `isRefreshing` already existed on the ViewModel with nothing
                // driving them — the library could only be refreshed by leaving and coming back.
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
                    LibraryPage(tabs[page], contentPadding, viewModel, browse, addToPlaylist, pageActions)
                }
            }
        }

        // Outside the Column, inside the Box: the pill floats over the list rather than taking a
        // row at the bottom of it, which is the reason the Box was wrapped around this at all.
        val currentTab = tabs.getOrElse(selectedPage) { tabs.first() }
        val activeTracks = when (currentTab) {
            LibraryTab.LIKED -> likedTracks
            LibraryTab.DOWNLOADS -> downloadedTracks
            else -> emptyList()
        }
        LibraryPlayShufflePill(
            visible = activeTracks.isNotEmpty(),
            onPlayAll = { viewModel.play(activeTracks, 0) },
            onShuffle = { viewModel.play(activeTracks.shuffled(), 0) },
            bottomInset = contentPadding.calculateBottomPadding()
        )
    }
}
