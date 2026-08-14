package com.wander.android.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.TrackRow

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Held above the LazyColumn, keyed by shelf. `rememberLazyListState()` called inside a lazy
    // `item {}` is disposed the moment that shelf scrolls off, so it neither preserved the
    // horizontal position nor avoided reallocating the state on the way back.
    val carouselStates = remember { mutableMapOf<String, LazyListState>() }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> LoadingIndicator(modifier = Modifier.align(Alignment.Center))

            state.isEmpty -> EmptyState(
                title = "Nothing to play yet",
                message = "Connect Navidrome or YouTube Music in Settings, or grant access to " +
                    "music stored on this device.",
                actionLabel = "Open Settings",
                onAction = onOpenSettings,
                modifier = Modifier.align(Alignment.Center)
            )

            else -> LazyColumn(
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "header", contentType = "header") {
                    HomeHeader(greeting = state.greeting, onOpenSettings = onOpenSettings)
                }

                state.sections.forEach { section ->
                    homeSection(section, viewModel, carouselStates)
                }
            }
        }
    }
}

/**
 * One shelf. Split out of the screen body so a new shelf costs a [HomeSection] and nothing else.
 */
private fun LazyListScope.homeSection(
    section: HomeSection,
    viewModel: HomeViewModel,
    carouselStates: MutableMap<String, LazyListState>
) {
    item(key = "${section.id}-title", contentType = "section-title") {
        SectionTitle(section.title)
    }

    when (section.style) {
        HomeSectionStyle.MIX_CAROUSEL -> item(
            key = "${section.id}-row",
            contentType = "mix-carousel"
        ) {
            LazyRow(
                state = carouselStates.getOrPut(section.id) { LazyListState() },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                itemsIndexed(
                    items = section.mixes,
                    key = { _, mix -> "${section.id}-${mix.id}" },
                    contentType = { _, _ -> "mix-card" }
                ) { _, mix ->
                    SmartMixCard(mix = mix, onPlay = { viewModel.playMix(mix) })
                }
            }
        }

        HomeSectionStyle.TRACK_CAROUSEL -> item(
            key = "${section.id}-row",
            contentType = "carousel"
        ) {
            LazyRow(
                state = carouselStates.getOrPut(section.id) { LazyListState() },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                // Indexed, so playing a card does not linear-scan the shelf for the track and
                // does not capture the whole list in a lambda that changes every recomposition.
                itemsIndexed(
                    items = section.tracks,
                    key = { _, track -> "${section.id}-${track.id}" },
                    contentType = { _, _ -> "track-card" }
                ) { index, track ->
                    HorizontalTrackCard(
                        track = track,
                        onPlay = { viewModel.play(section.tracks, index) }
                    )
                }
            }
        }

        HomeSectionStyle.TRACK_LIST -> itemsIndexed(
            items = section.tracks,
            key = { _, track -> "${section.id}-${track.id}" },
            contentType = { _, _ -> "track-row" }
        ) { index, track ->
            TrackRow(
                track = track,
                onPlay = { viewModel.play(section.tracks, index) },
                onToggleLike = { viewModel.toggleLike(track) }
            )
        }
    }
}

@Composable
private fun HomeHeader(greeting: String, onOpenSettings: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp)
    ) {
        // The greeting is the header. The app's own name told the user nothing they didn't
        // already know from having opened it.
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings")
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
    )
}
