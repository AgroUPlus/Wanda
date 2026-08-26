package com.wander.android.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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

/**
 * Two rows of oversized cards that scroll sideways as one block.
 *
 * The counterweight to [HorizontalTrackCard]'s 140.dp row: where a carousel skims, this shelf
 * commits screen to a handful of things worth returning to, and the artwork is large enough to
 * recognise a record by.
 */
@Composable
internal fun LargeCardShelf(
    tracks: List<UnifiedTrack>,
    sectionId: String,
    gridState: LazyGridState,
    onPlay: (Int) -> Unit,
    onLongPress: (UnifiedTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyHorizontalGrid(
        rows = GridCells.Fixed(GridRows),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        // The grid has no intrinsic height, so it has to be told: two cells plus the gap between.
        modifier = modifier.height(CardHeight * GridRows + 16.dp)
    ) {
        itemsIndexed(
            items = tracks,
            key = { _, track -> "$sectionId-${track.id}" },
            contentType = { _, _ -> "large-card" }
        ) { index, track ->
            LargeTrackCard(
                track = track,
                onPlay = { onPlay(index) },
                onLongPress = { onLongPress(track) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LargeTrackCard(
    track: UnifiedTrack,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    enabled: Boolean = track.isPlayableNow()
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interactionSource, label = "largeCardPress")

    Column(
        modifier = Modifier
            .width(CardWidth)
            .scale(scale)
            .graphicsLayer { alpha = if (enabled) 1f else DisabledAlpha }
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
            sizeDp = CardWidth,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.size(CardWidth)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = track.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

private const val GridRows = 2

/** Material's disabled-content opacity, matching `TrackRow`. */
private const val DisabledAlpha = 0.38f

private val CardWidth = 168.dp

/** The card's artwork plus the two caption lines under it. */
private val CardHeight = 236.dp
