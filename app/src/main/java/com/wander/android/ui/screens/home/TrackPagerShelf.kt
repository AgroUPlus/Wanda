package com.wander.android.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.TrackRow

/** How many rows one page of a [HomeSectionStyle.TRACK_PAGER] shelf holds. */
const val RowsPerPage = 4

/**
 * The lead shelf: full-width rows dealt into pages you swipe sideways through.
 *
 * A carousel of small cards shows a dozen tracks the user cannot read. Four legible rows at a
 * time, with the next page peeking past the edge to say there is more, reads far better for the
 * shelf at the top of the screen — and reuses [TrackRow], so a track offers the same affordances
 * here as everywhere else in the app.
 */
@Composable
internal fun TrackPagerShelf(
    tracks: List<UnifiedTrack>,
    pagerState: PagerState,
    onPlay: (Int) -> Unit,
    onLongPress: (UnifiedTrack) -> Unit,
    onToggleLike: (UnifiedTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    HorizontalPager(
        state = pagerState,
        // The peek is the whole point of paging rather than listing: a hard edge at the screen
        // boundary reads as the end of the shelf.
        contentPadding = PaddingValues(horizontal = 12.dp),
        pageSpacing = 8.dp,
        modifier = modifier.fillMaxWidth()
    ) { page ->
        val first = page * RowsPerPage
        val last = minOf(first + RowsPerPage, tracks.size)

        Column(modifier = Modifier.fillMaxWidth()) {
            for (index in first until last) {
                val track = tracks[index]
                TrackRow(
                    track = track,
                    onPlay = { onPlay(index) },
                    onToggleLike = { onToggleLike(track) },
                    onLongPress = { onLongPress(track) }
                )
            }
            // A short final page would otherwise shrink the pager's height as you reach it,
            // dragging the rest of Home up under your thumb mid-swipe.
            repeat(RowsPerPage - (last - first)) {
                Spacer(modifier = Modifier.height(TrackRowHeight))
            }
        }
    }
}

/** Number of pages [tracks] fills, at [RowsPerPage] each. */
internal fun pageCountFor(trackCount: Int): Int =
    (trackCount + RowsPerPage - 1) / RowsPerPage

/**
 * `TrackRow`'s laid-out height: its 52.dp artwork plus 8.dp of padding above and below. A
 * constant rather than a measurement, because the only thing it pads out is an empty tail slot.
 */
private val TrackRowHeight = 68.dp
