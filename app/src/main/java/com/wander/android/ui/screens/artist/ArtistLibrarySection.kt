package com.wander.android.ui.screens.artist

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.components.groupedListItem
import com.wander.android.ui.components.rememberShelfEntranceScale

/**
 * The songs by this artist the user has liked, ahead of everything the backends say about them.
 *
 * Read from the library rather than from any backend page, so it is there offline and it is the
 * user's own answer to "what do I like by them" — which is why a liked song may also appear under
 * Top songs: the two shelves answer different questions.
 */
internal fun LazyListScope.artistLibrarySection(
    likedTracks: List<UnifiedTrack>,
    showAll: Boolean,
    onToggleShowAll: () -> Unit,
    onPlay: (Int) -> Unit,
    onLongPress: (UnifiedTrack) -> Unit
) {
    if (likedTracks.isEmpty()) return
    val shown = if (showAll) likedTracks else likedTracks.take(TopSongsPreview)
    item(key = "liked-title", contentType = "section-title") {
        ArtistSectionTitle(
            text = stringResource(R.string.artist_in_your_library),
            action = if (likedTracks.size > TopSongsPreview) {
                stringResource(if (showAll) R.string.artist_show_less else R.string.artist_show_all)
            } else {
                null
            },
            actionSelected = showAll,
            onAction = onToggleShowAll
        )
    }
    // `shown` is a prefix of `likedTracks`, so the row index is the index into the queue played.
    itemsIndexed(
        items = shown,
        key = { _, track -> "liked-${track.id}" },
        contentType = { _, _ -> "track" }
    ) { index, track ->
        TrackRow(
            track = track,
            onPlay = { onPlay(index) },
            onLongPress = { onLongPress(track) },
            modifier = Modifier
                .animateItem()
                .scale(rememberShelfEntranceScale(index))
                .groupedListItem(index, shown.size)
        )
    }
}
