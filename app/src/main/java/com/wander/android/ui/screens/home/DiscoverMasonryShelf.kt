package com.wander.android.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyHorizontalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.isPlayableNow
import com.wander.android.ui.components.rememberPressScale
import com.wander.android.ui.components.rememberShelfArtworkShape
import com.wander.android.ui.components.rememberShelfEntranceScale
import com.wander.android.ui.components.scrollingTitle
import com.wander.android.ui.components.instantPress

/**
 * Two even rows, scrolling sideways. For "Discover" — unranked, mixed-provenance suggestions where
 * a strict single row implies an ordering the shelf doesn't actually have.
 *
 * This used to stagger every third card taller, for a broken-grid masonry look. In practice that
 * read as covers that just did not match — a shelf full of mismatched artwork sizes looks like a
 * bug even when it is not one, and it *was* one everywhere a card's row happened to be the odd
 * one out beside a taller neighbour. Uniform cards read as a deliberate grid instead.
 */
@Composable
internal fun DiscoverMasonryShelf(
    tracks: List<UnifiedTrack>,
    sectionId: String,
    onPlay: (Int) -> Unit,
    onLongPress: (UnifiedTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyHorizontalStaggeredGrid(
        rows = StaggeredGridCells.Fixed(GridRows),
        state = rememberLazyStaggeredGridState(),
        horizontalItemSpacing = 12.dp,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = modifier.height(ShelfHeight)
    ) {
        itemsIndexed(
            items = tracks,
            key = { _, track -> "$sectionId-${track.id}" },
            contentType = { _, _ -> "masonry-card" }
        ) { index, track ->
            MasonryCard(
                track = track,
                index = index,
                onPlay = { onPlay(index) },
                onLongPress = { onLongPress(track) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MasonryCard(
    track: UnifiedTrack,
    index: Int,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    enabled: Boolean = track.isPlayableNow()
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by rememberPressScale(interactionSource, label = "masonryCardPress")
    val entranceScale = rememberShelfEntranceScale(index)
    val artworkShape = rememberShelfArtworkShape(isPressed)
    val artworkSize = RegularArtworkSize

    Column(
        modifier = Modifier
            .width(CardWidth)
            .scale(scale * entranceScale)
            .graphicsLayer { alpha = if (enabled) 1f else DisabledAlpha }
            .instantPress(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { if (enabled) onPlay() },
                onLongClick = onLongPress
            )
    ) {
        Artwork(
            url = track.artworkUrl,
            contentDescription = track.title,
            sizeDp = artworkSize,
            shape = artworkShape,
            modifier = Modifier
                .width(CardWidth)
                .height(artworkSize)
        )
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.scrollingTitle()
        )
        if (track.artist.isNotBlank()) {
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.scrollingTitle()
            )
        }
    }
}

private const val GridRows = 2

/** Material's disabled-content opacity, matching `TrackRow`. */
private const val DisabledAlpha = 0.38f

private val CardWidth = 120.dp
private val RegularArtworkSize = 120.dp

/** One card's cover plus its two caption lines, twice over (two rows), plus the row gap. */
private val ShelfHeight = 258.dp
