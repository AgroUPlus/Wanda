package com.wander.android.ui.screens.library

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.SkeletonCard
import com.wander.android.ui.components.listInset

private const val ALBUMS_PAGE_SIZE = 24
private const val PAGE_PREFETCH_DISTANCE = 4
private const val MIN_RECENT_ALBUMS = 4

@Composable
internal fun AlbumGrid(
    albums: List<UnifiedAlbum>,
    recentAlbums: List<UnifiedAlbum>,
    contentPadding: PaddingValues,
    onOpenAlbum: (String) -> Unit,
    onAlbumLongPress: (UnifiedAlbum) -> Unit = {}
) {
    if (albums.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                title = "No albums yet",
                message = "Albums appear once a connected source has been browsed at least once."
            )
        }
        return
    }

    var albumPageSize by remember(albums) { mutableIntStateOf(ALBUMS_PAGE_SIZE) }
    val visibleAlbums = remember(albums, albumPageSize) { albums.take(albumPageSize) }
    val hasMoreAlbums = visibleAlbums.size < albums.size
    val gridState = rememberLazyGridState()
    // Only the count of recents gates the row. It used to also require `albums.size >
    // recentAlbums.size`, which meant a library no larger than the recent limit — every library
    // during its first sync — never saw the row at all, and the row reappeared later for no
    // reason the user could see. The row repeating albums the grid also shows is the point of it.
    val showsRecentRow = recentAlbums.size >= MIN_RECENT_ALBUMS
    val headerCount = if (showsRecentRow) 3 else 0

    // Paging is driven by where the grid has actually been scrolled to, not by an item composing.
    // Keying an effect inside the item lambda on the page size it mutates chains straight through
    // the whole list on the first frame, which is not pagination at all.
    LaunchedEffect(gridState, albums, headerCount) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible ->
                if (lastVisible - headerCount >= albumPageSize - PAGE_PREFETCH_DISTANCE) {
                    albumPageSize = (albumPageSize + ALBUMS_PAGE_SIZE).coerceAtMost(albums.size)
                }
            }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 156.dp),
        contentPadding = contentPadding.listInset(),
        modifier = Modifier.fillMaxSize()
    ) {
        if (showsRecentRow) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "recent_header") {
                Text(
                    text = "Recent",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "recent_row") {
                // No overscroll here: this row lives inside `LibraryScreen`'s `HorizontalPager`, and
                // the default stretch/glow overscroll effect swallows a horizontal drag at this
                // row's own scroll bounds before it can bubble up to the pager — a swipe that starts
                // (or ends) over the Recent row could never turn into a page change, even sitting
                // dead on the first or last album. Nested scrolling still hands the pager unconsumed
                // delta with the effect off; it just no longer eats the delta that would be zero
                // anyway once the row can't scroll further.
                CompositionLocalProvider(LocalOverscrollFactory provides null) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        itemsIndexed(recentAlbums, key = { _, album -> "recent_${album.id}" }) { index, album ->
                            AlbumCard(
                                album = album,
                                index = index,
                                onClick = { onOpenAlbum(album.id) },
                                onLongClick = { onAlbumLongPress(album) },
                                artworkSize = 132.dp,
                                // A `LazyRow` gives its items infinite width along the scroll axis,
                                // so without an explicit width `Artwork`'s own `fillMaxWidth()` was
                                // a no-op (Compose special-cases `fillMaxWidth` under an infinite
                                // constraint as doing nothing) and the title never had a bounded box
                                // to overflow against — it just sat there, uncropped and unscrolled,
                                // for anything long enough to need either. Same width and artwork
                                // size the artist page's own album row already uses.
                                modifier = Modifier.width(148.dp)
                            )
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "all_header") {
                Text(
                    text = "All albums",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        itemsIndexed(visibleAlbums, key = { _, album -> album.id }) { index, album ->
            AlbumCard(
                album = album,
                index = index,
                onClick = { onOpenAlbum(album.id) },
                onLongClick = { onAlbumLongPress(album) }
            )
        }
        if (hasMoreAlbums) {
            items(count = 4, key = { "skeleton_album_$it" }) {
                SkeletonCard()
            }
        }
    }
}
