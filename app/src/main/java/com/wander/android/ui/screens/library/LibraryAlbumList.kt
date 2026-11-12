package com.wander.android.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.CARD_ARTWORK_CORNER
import com.wander.android.ui.components.FastScroller
import com.wander.android.ui.components.HERO_ARTWORK_CORNER
import com.wander.android.ui.components.ScrollToTopOnReselect
import com.wander.android.ui.components.albumArtKey
import com.wander.android.ui.components.groupedListItem
import com.wander.android.ui.components.nestedListInset
import com.wander.android.ui.components.rememberPressScale
import com.wander.android.ui.components.rememberShelfArtworkShape
import com.wander.android.ui.components.rememberShelfEntranceScale
import com.wander.android.ui.components.scrollingTitle
import com.wander.android.ui.components.sharedArtwork

/**
 * The Albums tab as rows rather than covers: the grid's albums, in the same order, each a grouped
 * tile with its cover, title, "artist · year · tracks" and a ⋮ for the sheet a long press opens.
 *
 * No Recent row above it — that row is the grid's way of showing covers off; a list is for reading
 * down. Its callers show [AlbumGrid]'s empty state when there are no albums at all.
 */
@Composable
internal fun AlbumList(
    albums: List<UnifiedAlbum>,
    contentPadding: PaddingValues,
    onOpenAlbum: (String) -> Unit,
    onAlbumLongPress: (UnifiedAlbum) -> Unit,
    letterFor: ((UnifiedAlbum) -> String?)?,
    order: Any?
) {
    val listState = rememberLazyListState()
    ScrollToTopOnReselect(listState)
    val starts = rememberLetterStarts(albums, order, letterFor)
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = contentPadding.nestedListInset(),
            modifier = Modifier
                .fillMaxSize()
                .padding(end = if (starts != null) LetterRailGutter else 0.dp)
        ) {
            itemsIndexed(albums, key = { _, album -> album.id }, contentType = { _, _ -> "album" }) { index, album ->
                AlbumRow(
                    album = album,
                    index = index,
                    onClick = { onOpenAlbum(album.id) },
                    onMore = { onAlbumLongPress(album) },
                    modifier = Modifier
                        .animateItem()
                        .groupedListItem(index, albums.size)
                )
            }
        }
        val railModifier = Modifier
            .align(Alignment.TopEnd)
            .padding(contentPadding.nestedListInset())
            .padding(vertical = 8.dp)
        if (starts != null) {
            LetterRail(
                starts = starts,
                letterAt = {
                    albums.getOrNull(listState.firstVisibleItemIndex)?.let { railLetter(letterFor?.invoke(it)) }
                },
                onJump = { listState.scrollToItem(it) },
                modifier = railModifier
            )
        } else {
            FastScroller(
                totalItems = albums.size,
                firstVisibleItem = { listState.firstVisibleItemIndex },
                isScrollInProgress = { listState.isScrollInProgress },
                onJumpTo = { listState.scrollToItem(it) },
                modifier = railModifier
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumRow(
    album: UnifiedAlbum,
    index: Int,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by rememberPressScale(interactionSource, label = "albumRowPress")
    val entranceScale = rememberShelfEntranceScale(index)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .scale(pressScale * entranceScale)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClickLabel = stringResource(R.string.common_more_options),
                onLongClick = onMore
            )
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
    ) {
        Artwork(
            url = album.coverArtUrl,
            contentDescription = null,
            sizeDp = 52.dp,
            shape = rememberShelfArtworkShape(isPressed),
            modifier = Modifier
                .sharedArtwork(albumArtKey(album.id), CARD_ARTWORK_CORNER, HERO_ARTWORK_CORNER)
                .size(52.dp)
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.scrollingTitle()
            )
            Text(
                text = album.subtitle(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        LibraryMoreButton(onMore)
    }
}

/** "Artist · 2013 · 9 tracks", skipping whatever the source did not provide. */
@Composable
private fun UnifiedAlbum.subtitle(): String = listOfNotNull(
    artist.takeIf { it.isNotBlank() },
    year?.toString(),
    songCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.collection_track_count, it, it) }
).joinToString(" · ")
