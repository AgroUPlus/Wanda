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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.ui.components.SearchKindToggle
import com.wander.android.ui.components.AddToPlaylistHost
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.SourceToggleChips
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.TrackActionsSheet
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.listInset

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
                    title = "Search everything at once",
                    message = "Navidrome, YouTube Music, the Internet Archive and music on this " +
                        "device, in one list.",
                    modifier = centred
                )

                state.results.isEmpty() && state.lyricMatches.isEmpty() -> EmptyState(
                    title = "No matches",
                    message = "Nothing found for \"$query\" in your connected sources.",
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
                                    text = "Matched in lyrics",
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
                        items(
                            items = displayedLyrics,
                            key = { "lyric_${it.track.id}" },
                            contentType = { "lyric_match" }
                        ) { match ->
                            com.wander.android.ui.components.LyricMatchRow(
                                match = match,
                                onPlay = { viewModel.playTrackAtTimestamp(match.track, match.timestampMs) },
                                onToggleLike = { viewModel.toggleLike(match.track) },
                                onLongPress = { actionsFor = match.track }
                            )
                        }
                        if (state.lyricMatches.size > 5) {
                            item(key = "footer_lyrics_toggle") {
                                TextButton(
                                    onClick = { isLyricsExpanded = !isLyricsExpanded },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
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
                                    text = "Songs",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .padding(top = 16.dp, bottom = 8.dp)
                                )
                            }
                        }
                    }

                    itemsIndexed(
                        items = state.results,
                        key = { _, track -> track.id },
                        contentType = { _, _ -> "track" }
                    ) { index, track ->
                        TrackRow(
                            track = track,
                            onPlay = { viewModel.play(state.results, index) },
                            onToggleLike = { viewModel.toggleLike(track) },
                            onLongPress = { actionsFor = track }
                        )
                    }
                }
            }
        }
    }
}
