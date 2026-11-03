package com.wander.android.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.wander.android.data.model.LibrarySort
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.FastScroller
import com.wander.android.ui.components.ScrollToTopOnReselect
import com.wander.android.ui.components.SkeletonRow
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.components.fastScrollLetter
import com.wander.android.ui.components.groupedListItem
import com.wander.android.ui.components.nestedListInset
import com.wander.android.ui.components.rememberShelfEntranceScale
import com.wander.android.ui.components.selection.SelectedTrack
import com.wander.android.ui.components.selection.TrackSelectionState

private const val LOADING_ROWS = 8

/**
 * The library tab, paged from the database.
 *
 * Separate from [TrackList] rather than replacing it, because the two lists have different
 * problems. Liked and Downloads are tens of rows held in memory anyway and their in-composition
 * windowing is fine. The library is a thousand rows that were re-read and re-mapped in full on
 * every write to `tracks` — a like, a play count, a sync — and no amount of windowing the *display*
 * fixed that, because the cost was upstream of the display.
 *
 * Paged with placeholders, so the list is its full length from the first page and the
 * [FastScroller] can jump anywhere in it; a row whose page has not loaded yet is a skeleton.
 */
@Composable
internal fun PagedTrackList(
    tracks: LazyPagingItems<UnifiedTrack>,
    tab: LibraryTab,
    isSyncing: Boolean,
    contentPadding: PaddingValues,
    onPlay: (UnifiedTrack) -> Unit,
    onToggleLike: (UnifiedTrack) -> Unit,
    onLongPress: (UnifiedTrack) -> Unit,
    selection: TrackSelectionState,
    letterFor: ((UnifiedTrack) -> String?)? = null
) {
    if (tracks.itemCount == 0) {
        // `refresh is Loading` as well as the caller's flag: a paged list is empty for a moment on
        // every filter change while the first page loads, and "your library is empty" shown in that
        // gap is a lie that lasts just long enough to be read.
        if (isSyncing || tracks.loadState.refresh is LoadState.Loading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(contentPadding.nestedListInset())
            ) {
                repeat(LOADING_ROWS) {
                    SkeletonRow(leadingSize = 48.dp, leadingShape = MaterialTheme.shapes.extraSmall)
                }
            }
            return
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(title = emptyTitleFor(tab), message = emptyMessageFor(tab))
        }
        return
    }

    val listState = rememberLazyListState()
    ScrollToTopOnReselect(listState)
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = contentPadding.nestedListInset(),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                count = tracks.itemCount,
                key = tracks.itemKey { it.id },
                contentType = tracks.itemContentType { "track" }
            ) { index ->
                val position = Modifier.groupedListItem(index, tracks.itemCount)
                // A placeholder: this row's page is still loading, typically right after a jump.
                val track = tracks[index]
                if (track == null) {
                    SkeletonRow(
                        leadingSize = 48.dp,
                        leadingShape = MaterialTheme.shapes.extraSmall,
                        modifier = position
                    )
                    return@items
                }
                val entranceScale = rememberShelfEntranceScale(index)
                val item = SelectedTrack(track.id, track)
                TrackRow(
                    track = track,
                    // The queue is built from the database on tap, not from what the screen holds:
                    // paging means the screen holds only the pages scrolled through, and playing
                    // the fortieth track must still queue the rest of the library after it.
                    onPlay = selection.onTap(item) { onPlay(track) },
                    onToggleLike = { onToggleLike(track) },
                    onLongPress = selection.onLongPress(item) { onLongPress(track) },
                    selected = selection.stateOf(item.key),
                    modifier = Modifier.scale(entranceScale).then(position)
                )
            }
        }
        FastScroller(
            totalItems = tracks.itemCount,
            firstVisibleItem = { listState.firstVisibleItemIndex },
            isScrollInProgress = { listState.isScrollInProgress },
            onJumpTo = { listState.scrollToItem(it) },
            // `peek`, not `get`: naming the letter must not itself load the page.
            labelFor = letterFor?.let { letter -> { index -> tracks.peek(index)?.let(letter) } },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(contentPadding.nestedListInset())
                .padding(vertical = 8.dp)
        )
    }
}

/** The letter a track is filed under in [sort], or null for an order that is not alphabetical. */
internal fun trackLetter(sort: LibrarySort?): ((UnifiedTrack) -> String?)? = when (sort) {
    LibrarySort.TITLE -> { track -> fastScrollLetter(track.title) }
    LibrarySort.ARTIST -> { track -> fastScrollLetter(track.artist) }
    LibrarySort.ALBUM -> { track -> fastScrollLetter(track.album) }
    else -> null
}

/** The Tracks tab: [PagedTrackList] under its [LibraryListToolbar]. */
@Composable
internal fun AllTracksPage(
    tracks: LazyPagingItems<UnifiedTrack>,
    isSyncing: Boolean,
    contentPadding: PaddingValues,
    viewModel: LibraryViewModel,
    browse: LibraryBrowseViewModel,
    onLongPress: (UnifiedTrack) -> Unit,
    selection: TrackSelectionState
) {
    val sorts by browse.sorts.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        LibraryListToolbar(LibraryTab.TRACKS, browse)
        PagedTrackList(
            tracks, LibraryTab.TRACKS, isSyncing, contentPadding,
            onPlay = browse::playFromLibrary,
            onToggleLike = viewModel::toggleLike,
            onLongPress = onLongPress,
            selection = selection,
            letterFor = trackLetter(sorts[LibraryTab.TRACKS])
        )
    }
}
