package com.wander.android.ui.screens.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.MoodPresets
import com.wander.android.ui.components.AddToPlaylistHost
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.SearchKindToggle
import com.wander.android.ui.components.SourceToggleChips
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.nestedListInset
import com.wander.android.ui.components.rememberShelfEntranceScale
import com.wander.android.ui.components.selection.SelectedTrack
import com.wander.android.ui.components.selection.TrackSelectionBar
import com.wander.android.ui.components.selection.TrackSwipeBox
import com.wander.android.ui.components.selection.rememberTrackSelectionState
import com.wander.android.ui.screens.home.MoodMatrixViewModel

@Composable
fun SearchScreen(
    contentPadding: PaddingValues,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
    historyViewModel: SearchHistoryViewModel = hiltViewModel(),
    moodViewModel: MoodMatrixViewModel = hiltViewModel(),
    shortcutsViewModel: SearchShortcutsViewModel = hiltViewModel()
) {
    val moodState by moodViewModel.state.collectAsStateWithLifecycle()
    val shortcutTracks by shortcutsViewModel.tracks.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val recentSearches by historyViewModel.recent.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val availableSources by viewModel.availableSources.collectAsStateWithLifecycle()
    val selectedSources by viewModel.selectedSources.collectAsStateWithLifecycle()
    val kind by viewModel.kind.collectAsStateWithLifecycle()
    var actionsFor by remember { mutableStateOf<UnifiedTrack?>(null) }
    var albumActionsFor by remember { mutableStateOf<UnifiedAlbum?>(null) }
    var isLyricsExpanded by rememberSaveable(query) { mutableStateOf(false) }

    val addToPlaylist = AddToPlaylistHost()

    val selection = rememberTrackSelectionState()
    // A new search is a new list; whatever was selected in the last one is not in it.
    LaunchedEffect(query) { selection.clear() }

    actionsFor?.let { track ->
        SearchTrackActions(
            track, viewModel, addToPlaylist, onOpenArtist, onOpenAlbum,
            onSelect = { selection.toggle(SelectedTrack(track.id, track)) }
                .takeIf { state.results.any { it.id == track.id } },
            onDismiss = { actionsFor = null }
        )
    }

    albumActionsFor?.let { album ->
        SearchAlbumActions(
            album,
            onOpenArtist = { name, id ->
                viewModel.rememberQuery()
                onOpenArtist(name, id)
            },
            onDismiss = { albumActionsFor = null }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding.headerInset())
        ) {
            // The field itself is in the dock at the bottom of the app — one search box, always in the
            // same place, whether or not you are on this screen. What stays here is everything that
            // shapes the search rather than states it.
            SearchKindToggle(
                selected = kind,
                onSelect = viewModel::selectKind,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)
            )

            if (availableSources.size > 1) {
                SourceToggleChips(
                    sources = availableSources,
                    selected = selectedSources,
                    onToggle = viewModel::toggleSource,
                    onSelectAll = viewModel::selectAllSources,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            if (!state.isSearching && !state.isFailure && state.unansweredSources.isNotEmpty()) {
                UnansweredSourcesNote(
                    sources = state.unansweredSources,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)
                )
            }

            // fillMaxWidth is load-bearing: without it the Box wraps its content, so it was only as
            // wide as the spinner and `Center` had nothing to centre within — the indicator sat at
            // the Column's start edge.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // The bottom inset carries the player and nav bar. The list gets it through
                // `nestedListInset()`; anything centred has to be lifted by the same amount or it centres
                // in a space that runs underneath them, and reads as sitting too low.
                val centred = Modifier.padding(bottom = contentPadding.calculateBottomPadding())

                when {
                    state.isSearching -> LoadingIndicator(modifier = centred)

                    // The field, not `hasQuery`: the state trails typing by the debounce, and the
                    // history must not flash up under a query that is already there.
                    query.isBlank() -> SearchStartPage(
                        recentSearches = recentSearches,
                        shortcuts = SearchShortcut.entries.filter { it in shortcutTracks },
                        moods = MoodPresets,
                        playingMoodKey = moodState.playingKey,
                        contentPadding = contentPadding.nestedListInset(),
                        onSearch = historyViewModel::search,
                        onRemoveSearch = historyViewModel::remove,
                        onClearSearches = historyViewModel::clear,
                        onPlayShortcut = shortcutsViewModel::play,
                        onPlayMood = moodViewModel::playMood
                    )

                    !state.hasQuery -> EmptyState(
                        title = R.string.search_search_everything_once,
                        message = R.string.search_navidrome_youtube_music_music_device,
                        modifier = centred
                    )

                    state.isFailure -> EmptyState(
                        title = R.string.search_failed_title,
                        message = R.string.search_failed_message,
                        icon = Icons.Rounded.CloudOff,
                        actionLabel = R.string.search_retry,
                        onAction = viewModel::retry,
                        modifier = centred
                    )

                    state.isEmpty -> EmptyState(
                        title = stringResource(R.string.search_no_matches),
                        message = stringResource(R.string.search_nothing_found_connected_sources, query),
                        modifier = centred
                    )

                    else -> LazyColumn(
                        contentPadding = contentPadding.nestedListInset(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        artistResultsSection(state.artists) { name, id ->
                            viewModel.rememberQuery()
                            onOpenArtist(name, id)
                        }
                        albumResultsSection(state.albums, onLongPressAlbum = { albumActionsFor = it }) { albumId ->
                            viewModel.rememberQuery()
                            onOpenAlbum(albumId)
                        }
                        lyricMatchesSection(
                            matches = state.lyricMatches,
                            query = query,
                            expanded = isLyricsExpanded,
                            onToggleExpanded = { isLyricsExpanded = !isLyricsExpanded },
                            onPlay = { match -> viewModel.playTrackAtTimestamp(match.track, match.timestampMs) },
                            onLongPress = { track -> actionsFor = track }
                        )
                        val hasOtherSections = state.lyricMatches.isNotEmpty() ||
                            state.albums.isNotEmpty() || state.artists.isNotEmpty()
                        if (hasOtherSections && state.results.isNotEmpty()) {
                            searchSectionHeader(key = "header_tracks", title = R.string.search_songs)
                        }

                        itemsIndexed(
                            items = state.results,
                            key = { _, track -> track.id },
                            contentType = { _, _ -> "track" }
                        ) { index, track ->
                            val entranceScale = rememberShelfEntranceScale(index)
                            val item = SelectedTrack(track.id, track)
                            TrackSwipeBox(
                                track = track,
                                enabled = !selection.isActive,
                                modifier = Modifier.animateItem().scale(entranceScale)
                            ) {
                                TrackRow(
                                    track = track,
                                    // A radio from the tapped song, not the results list.
                                    //
                                    // The list is everything matching a string: searching "Signals" and
                                    // pressing one of them queued every other song with "Signals" in its
                                    // name, by unrelated artists, in relevance order. That is a spelling
                                    // coincidence, not a running order. A station seeded from the track
                                    // plays it first and then what actually sounds like it, across every
                                    // configured source — see `PlaybackCoordinator.startRadio`.
                                    onPlay = selection.onTap(item) { viewModel.startRadio(track) },
                                    onLongPress = selection.onLongPress(item) { actionsFor = track },
                                    selected = selection.stateOf(item.key)
                                )
                            }
                        }
                    }
                }
            }
        }

        TrackSelectionBar(
            selection = selection,
            addToPlaylist = addToPlaylist,
            topInset = contentPadding.calculateTopPadding(),
            modifier = Modifier.align(Alignment.TopStart)
        )
    }
}
