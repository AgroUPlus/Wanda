package com.wander.android.ui.components.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.Artwork

/**
 * The full player's cover as an M3 Expressive hero carousel over the queue, clipped to the cover's
 * own frame: only the playing cover shows at rest, and a swipe shrinks it while the previous or
 * next one slides in.
 *
 * The hero item is exactly cover-sized (see the bleed below), which is what lets [MorphingArtwork]
 * hand over to this without a jump.
 *
 * @param onPreviewIndex the queue index the carousel is showing while it differs from what is
 *   playing because of a swipe, or null. Lets the text under the cover follow the gesture instead
 *   of waiting for the player to catch up.
 * @param alpha read in the draw phase, so fading it for lyrics never recomposes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlayerCoverCarousel(
    queue: List<UnifiedTrack>,
    currentIndex: Int,
    onSeekToIndex: (Int) -> Unit,
    onPreviewIndex: (Int?) -> Unit,
    isPlaying: Boolean,
    alpha: () -> Float,
    modifier: Modifier = Modifier
) {
    if (queue.isEmpty()) return

    val state = rememberCarouselState(initialItem = currentIndex.coerceIn(0, queue.lastIndex)) { queue.size }
    CarouselPlaybackSync(state, currentIndex, queue.size, onSeekToIndex, onPreviewIndex)

    // The same zoom-while-playing as the cover [MorphingArtwork] hands over from, inside each
    // cover's own mask so the frame's edges never move.
    val playingZoom by animateFloatAsState(
        targetValue = if (isPlaying) PLAYING_ZOOM else 1f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "carouselPlayingZoom"
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // The hero item is a hair larger than the cover on every side, so it can never come out
        // narrower than it and crop the artwork's edges; the clip below trims it back.
        val itemWidth = maxWidth + ItemBleed * 2
        val itemHeight = maxHeight + ItemBleed * 2
        // Clipped to the cover's own frame: at rest only the playing cover shows, and a swipe
        // shrinks it while the neighbour slides in from beyond the edge.
        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            HorizontalCenteredHeroCarousel(
                state = state,
                maxItemWidth = itemWidth,
                itemSpacing = 0.dp,
                // One cover per swipe, however hard the fling.
                flingBehavior = CarouselDefaults.singleAdvanceFlingBehavior(state),
                // No spare room for slivers or gaps: the carousel pins the first and last hero to
                // the container's edge, so any slack would sit the cover off-centre there, with
                // its neighbour peeking in. The hero then always fills the container exactly.
                minSmallItemWidth = 0.dp,
                maxSmallItemWidth = 0.dp,
                modifier = Modifier
                    .requiredWidth(itemWidth)
                    .requiredHeight(itemHeight)
                    .graphicsLayer { this.alpha = alpha() }
            ) { index ->
                val track = queue.getOrNull(index)
                if (track != null) {
                    CoverItem(track, itemWidth, zoom = { playingZoom })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CarouselItemScope.CoverItem(
    track: UnifiedTrack,
    itemWidth: Dp,
    zoom: () -> Float
) {
    val shape = remember(itemWidth) { SwipeGapShape(carouselItemDrawInfo, CoverCorner, itemWidth) }
    Box(modifier = Modifier.fillMaxSize().maskClip(shape)) {
        Artwork(
            url = track.artworkUrl,
            contentDescription = track.title,
            sizeDp = MorphArtworkSize,
            // `maskClip` above already shapes the item; see TrackCarouselCard.
            shape = RectangleShape,
            crossfade = false,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom()
                    scaleY = zoom()
                }
        )
    }
}

/** Square, like the expanded [MorphingArtwork] it takes over from: the cover runs edge to edge. */
private val CoverCorner = 0.dp

/** How far the hero item overshoots the cover on each side, before the clip trims it. */
private val ItemBleed = 2.dp
