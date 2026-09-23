package com.wander.android.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.data.model.EpisodeItem
import com.wander.android.data.model.EpisodeState
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.components.groupedListItem
import com.wander.android.ui.components.listInset
import com.wander.android.ui.components.rememberShelfEntranceScale
import com.wander.android.ui.screens.home.HorizontalTrackCard
import com.wander.android.ui.screens.home.SectionTitle

/**
 * Saved and heard episodes, grouped by show, with what is half-heard pulled up front.
 *
 * Continue listening leads because it is the one thing a podcast listener opens the app for; the
 * chips below it only narrow the show list, never that row.
 */
@Composable
internal fun LibraryPodcastsPage(
    contentPadding: PaddingValues,
    onToggleLike: (UnifiedTrack) -> Unit,
    onLongPress: (UnifiedTrack) -> Unit,
    viewModel: PodcastsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.hasNoEpisodes) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                title = stringResource(R.string.podcasts_empty_title),
                message = stringResource(R.string.podcasts_empty_message),
                icon = Icons.Rounded.Podcasts
            )
        }
        return
    }

    LazyColumn(contentPadding = contentPadding.listInset(), modifier = Modifier.fillMaxSize()) {
        if (state.continueListening.isNotEmpty()) {
            continueListening(state.continueListening, viewModel::play, onLongPress)
        }
        item(key = "episode-filters", contentType = "chips") {
            EpisodeFilterChips(
                selected = state.filter,
                onSelect = viewModel::selectFilter,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        if (state.shows.isEmpty()) {
            item(key = "episode-filter-empty", contentType = "empty") {
                Text(
                    text = stringResource(R.string.podcasts_filter_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                )
            }
        }
        state.shows.forEach { show ->
            item(key = "show-${show.name}", contentType = "show-title") {
                SectionTitle(show.name)
            }
            itemsIndexed(
                items = show.episodes,
                key = { _, item -> "episode-${show.name}-${item.track.id}" },
                contentType = { _, _ -> "episode" }
            ) { index, item ->
                EpisodeRow(
                    item = item,
                    onPlay = { viewModel.play(item.track) },
                    onToggleLike = { onToggleLike(item.track) },
                    onLongPress = { onLongPress(item.track) },
                    modifier = Modifier
                        .animateItem()
                        .scale(rememberShelfEntranceScale(index))
                        .groupedListItem(index, show.episodes.size)
                )
            }
        }
    }
}

private fun LazyListScope.continueListening(
    episodes: List<EpisodeItem>,
    onPlay: (UnifiedTrack) -> Unit,
    onLongPress: (UnifiedTrack) -> Unit
) {
    item(key = "continue-title", contentType = "section-title") {
        SectionTitle(stringResource(R.string.continue_listening))
    }
    item(key = "continue-row", contentType = "carousel") {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
        ) {
            itemsIndexed(episodes, key = { _, item -> item.track.id }) { index, item ->
                HorizontalTrackCard(
                    track = item.track,
                    index = index,
                    onPlay = { onPlay(item.track) },
                    onLongPress = { onLongPress(item.track) },
                    progress = item.fraction
                )
            }
        }
    }
}

/**
 * A [TrackRow] with its listening state under it: a bar while in progress, a label while
 * unplayed, nothing once finished — a finished episode needs no more of your attention.
 */
@Composable
private fun EpisodeRow(
    item: EpisodeItem,
    onPlay: () -> Unit,
    onToggleLike: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        TrackRow(track = item.track, onPlay = onPlay, onToggleLike = onToggleLike, onLongPress = onLongPress)
        when (item.state) {
            EpisodeState.IN_PROGRESS -> LinearProgressIndicator(
                progress = { item.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
            )
            EpisodeState.UNPLAYED -> Text(
                text = stringResource(R.string.podcasts_unplayed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
            )
            EpisodeState.FINISHED -> Unit
        }
    }
}
