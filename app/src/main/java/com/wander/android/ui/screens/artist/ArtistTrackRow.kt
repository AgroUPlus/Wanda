package com.wander.android.ui.screens.artist

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.components.groupedListItem
import com.wander.android.ui.components.rememberShelfEntranceScale
import com.wander.android.ui.components.selection.TrackSwipeBox

/**
 * One song in a track bucket of the artist page: row [index] of a grouped card of [count], with the
 * swipe shortcuts on. Always on — the artist page has no multi-select for a swipe to compete with.
 */
@Composable
internal fun LazyItemScope.ArtistTrackRow(
    track: UnifiedTrack,
    index: Int,
    count: Int,
    onPlay: () -> Unit,
    onLongPress: () -> Unit
) {
    TrackSwipeBox(
        track = track,
        enabled = true,
        modifier = Modifier
            .animateItem()
            .scale(rememberShelfEntranceScale(index))
            .groupedListItem(index, count)
    ) {
        TrackRow(track = track, onPlay = onPlay, onLongPress = onLongPress)
    }
}
