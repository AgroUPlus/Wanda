package com.wander.android.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.isPlayableNow
import com.wander.android.ui.components.rememberPressScale
import com.wander.android.ui.components.rememberShelfArtworkShape
import com.wander.android.ui.components.rememberShelfEntranceScale

/**
 * Covers overlapping like a loose stack of photos rather than sitting edge to edge in a row.
 *
 * For a shelf that is a personal pile rather than a queue — "Your Favourites" — where the point is
 * "here's a bunch of things you like", not "here's an ordered list".
 */
@Composable
internal fun OverlappingStackShelf(
    tracks: List<UnifiedTrack>,
    sectionId: String,
    onPlay: (Int) -> Unit,
    onLongPress: (UnifiedTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        // Negative spacing is what creates the overlap — each card sits partly under the one
        // before it, laid over by the arrangement's own math rather than an offset per item.
        horizontalArrangement = Arrangement.spacedBy(-OverlapAmount),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = modifier
    ) {
        itemsIndexed(
            items = tracks,
            key = { _, track -> "$sectionId-${track.id}" },
            contentType = { _, _ -> "stack-card" }
        ) { index, track ->
            StackCard(
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
private fun StackCard(
    track: UnifiedTrack,
    index: Int,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    enabled: Boolean = track.isPlayableNow()
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by rememberPressScale(interactionSource, label = "stackCardPress")
    val entranceScale = rememberShelfEntranceScale(index)
    val artworkShape = rememberShelfArtworkShape(isPressed)
    // A gentle, alternating tilt is what reads as a loosely fanned stack rather than a strict
    // deck — a uniform rotation would look like one photo repeated, not many.
    val tilt = if (index % 2 == 0) -StackTilt else StackTilt

    Artwork(
        url = track.artworkUrl,
        contentDescription = track.title,
        sizeDp = CardSize,
        shape = artworkShape,
        modifier = Modifier
            .size(CardSize)
            // Later cards sit on top of earlier ones, matching reading order down the stack.
            .zIndex(index.toFloat())
            .graphicsLayer {
                rotationZ = tilt
                alpha = if (enabled) 1f else DisabledAlpha
            }
            .scale(scale * entranceScale)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { if (enabled) onPlay() },
                onLongClick = onLongPress
            )
    )
}

/** Material's disabled-content opacity, matching `TrackRow`. */
private const val DisabledAlpha = 0.38f

private val CardSize = 130.dp
private val OverlapAmount = 44.dp
private const val StackTilt = 4f
