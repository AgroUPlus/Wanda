package com.wander.android.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.TrackRow

/**
 * Every scroll position Home hangs on to, held above the `LazyColumn`.
 *
 * `rememberLazyListState()` called inside a lazy `item {}` is disposed the moment that shelf
 * scrolls off, so it neither preserved the horizontal position nor avoided reallocating the
 * state on the way back. Keyed by shelf id for the same reason.
 */
internal class HomeShelfStates {
    val carousels = mutableMapOf<String, LazyListState>()
    val grids = mutableMapOf<String, LazyGridState>()
    private val pagerPages = mutableMapOf<String, Int>()

    /**
     * The page a pager shelf was left on. Only the index is cached, not the [PagerState] itself:
     * a state built in one composition cannot be handed to a later one, and a shelf's page count
     * grows under it as the network fills the shelf in.
     */
    @Composable
    fun pager(sectionId: String, pageCount: Int): PagerState {
        val state = rememberPagerState(
            initialPage = pagerPages[sectionId]?.coerceAtMost(pageCount - 1)?.coerceAtLeast(0) ?: 0,
            pageCount = { pageCount }
        )
        LaunchedEffect(state) {
            snapshotFlow { state.currentPage }.collect { pagerPages[sectionId] = it }
        }
        return state
    }
}

/**
 * One shelf. Split out of the screen body so a new shelf costs a [HomeSection] and nothing else.
 */
internal fun LazyListScope.homeSection(
    section: HomeSection,
    viewModel: HomeViewModel,
    states: HomeShelfStates,
    onLongPress: (UnifiedTrack) -> Unit
) {
    item(key = "${section.id}-title", contentType = "section-title") {
        if (section.style == HomeSectionStyle.TRACK_PAGER) {
            // The lead shelf is the one worth starting from a tap, so it carries the affordance.
            SectionTitle(
                text = section.title,
                action = {
                    OutlinedButton(onClick = { viewModel.play(section.tracks, 0) }) {
                        Text("Play all")
                    }
                }
            )
        } else {
            SectionTitle(section.title)
        }
    }

    when (section.style) {
        HomeSectionStyle.MIX_CAROUSEL -> item(
            key = "${section.id}-row",
            contentType = "mix-carousel"
        ) {
            LazyRow(
                state = states.carousels.getOrPut(section.id) { LazyListState() },
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
                state = states.carousels.getOrPut(section.id) { LazyListState() },
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
                        onPlay = { viewModel.play(section.tracks, index) },
                        onLongPress = { onLongPress(track) }
                    )
                }
            }
        }

        HomeSectionStyle.LARGE_GRID -> item(
            key = "${section.id}-row",
            contentType = "large-grid"
        ) {
            LargeCardShelf(
                tracks = section.tracks,
                sectionId = section.id,
                gridState = states.grids.getOrPut(section.id) { LazyGridState() },
                onPlay = { index -> viewModel.play(section.tracks, index) },
                onLongPress = onLongPress
            )
        }

        HomeSectionStyle.TRACK_PAGER -> item(
            key = "${section.id}-pager",
            contentType = "track-pager"
        ) {
            TrackPagerShelf(
                tracks = section.tracks,
                pagerState = states.pager(section.id, pageCountFor(section.tracks.size)),
                onPlay = { index -> viewModel.play(section.tracks, index) },
                onLongPress = onLongPress,
                onToggleLike = viewModel::toggleLike
            )
        }

        HomeSectionStyle.TRACK_LIST -> itemsIndexed(
            items = section.tracks,
            key = { _, track -> "${section.id}-${track.id}" },
            contentType = { _, _ -> "track-row" }
        ) { index, track ->
            TrackRow(
                track = track,
                onPlay = { viewModel.play(section.tracks, index) },
                onToggleLike = { viewModel.toggleLike(track) },
                onLongPress = { onLongPress(track) }
            )
        }
    }
}

@Composable
internal fun SectionTitle(text: String, action: (@Composable () -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f)
        )
        action?.invoke()
    }
}
