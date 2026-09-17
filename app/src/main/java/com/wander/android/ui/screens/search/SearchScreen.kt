package com.wander.android.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.AddToPlaylistHost
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.SearchKindToggle
import com.wander.android.ui.components.SourceToggleChips
import com.wander.android.ui.components.TrackActionsSheet
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.listInset
import com.wander.android.ui.components.rememberShelfEntranceScale

@Composable
fun SearchScreen(
    contentPadding: PaddingValues,
    onOpenArtist: (String, String?) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val availableSources by viewModel.availableSources.collectAsStateWithLifecycle()
    val selectedSources by viewModel.selectedSources.collectAsStateWithLifecycle()
    val kind by viewModel.kind.collectAsStateWithLifecycle()
    var actionsFor by remember { mutableStateOf<UnifiedTrack?>(null) }
    var isLyricsExpanded by rememberSaveable(query) { mutableStateOf(false) }

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
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
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
            // `listInset()`; anything centred has to be lifted by the same amount or it centres
            // in a space that runs underneath them, and reads as sitting too low.
            val centred = Modifier.padding(bottom = contentPadding.calculateBottomPadding())

            when {
                state.isSearching -> LoadingIndicator(modifier = centred)

                !state.hasQuery -> EmptyState(
                    title = stringResource(R.string.search_search_everything_once),
                    message = stringResource(R.string.search_navidrome_youtube_music_music_device),
                    modifier = centred
                )

                state.results.isEmpty() && state.lyricMatches.isEmpty() -> EmptyState(
                    title = stringResource(R.string.search_no_matches),
                    message = stringResource(R.string.search_nothing_found_connected_sources, query),
                    modifier = centred
                )

                else -> LazyColumn(
                    contentPadding = contentPadding.listInset(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (state.lyricMatches.isNotEmpty()) {
                        val displayedLyrics = if (isLyricsExpanded) state.lyricMatches else state.lyricMatches.take(5)
                        item(key = "header_lyrics") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.search_matched_lyrics),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (state.lyricMatches.size > 5) {
                                    TextButton(
                                        onClick = { isLyricsExpanded = !isLyricsExpanded },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Text(
                                            text = if (isLyricsExpanded) "Show less" else "See all (${state.lyricMatches.size})",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                        itemsIndexed(
                            items = displayedLyrics,
                            key = { _, match -> "lyric_${match.track.id}" },
                            contentType = { _, _ -> "lyric_match" }
                        ) { index, match ->
                            val entranceScale = rememberShelfEntranceScale(index)
                            com.wander.android.ui.components.LyricMatchRow(
                                match = match,
                                searchQuery = query,
                                onPlay = { viewModel.playTrackAtTimestamp(match.track, match.timestampMs) },
                                onLongPress = { actionsFor = match.track },
                                modifier = Modifier.animateItem().scale(entranceScale)
                            )
                        }
                        if (state.lyricMatches.size > 5) {
                            item(key = "footer_lyrics_toggle") {
                                TextButton(
                                    onClick = { isLyricsExpanded = !isLyricsExpanded },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .animateItem()
                                ) {
                                    Text(
                                        text = if (isLyricsExpanded) "Show fewer" else "Show ${state.lyricMatches.size - 5} more lyric matches",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                        if (state.results.isNotEmpty()) {
                            item(key = "header_tracks") {
                                Text(
                                    text = stringResource(R.string.search_songs),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .padding(top = 16.dp, bottom = 8.dp)
                                        .animateItem()
                                )
                            }
                        }
                    }

                    itemsIndexed(
                        items = state.results,
                        key = { _, track -> track.id },
                        contentType = { _, _ -> "track" }
                    ) { index, track ->
                        val entranceScale = rememberShelfEntranceScale(index)
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
                            onPlay = { viewModel.startRadio(track) },
                            onLongPress = { actionsFor = track },
                            modifier = Modifier.animateItem().scale(entranceScale)
                        )
                    }
                }
            }
        }
    }
}
