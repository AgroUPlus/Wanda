package com.wander.android.ui.components.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.FingerprintStatus
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.FingerprintBadge
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * The full player's cover as an M3 Expressive hero carousel over the queue, clipped to the cover's
 * own square: only the playing cover shows at rest, and a swipe shrinks it while the previous or
 * next one slides in.
 *
 * Laid out the width of the window and clipped back to the slot, so the hero item has room to be
 * exactly cover-sized. That is what lets [MorphingArtwork] hand over to this without a jump.
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
    fingerprintStatus: FingerprintStatus,
    alpha: () -> Float,
    modifier: Modifier = Modifier
) {
    if (queue.isEmpty()) return

    val state = rememberCarouselState(initialItem = currentIndex.coerceIn(0, queue.lastIndex)) { queue.size }
    CarouselPlaybackSync(state, currentIndex, queue.size, onSeekToIndex, onPreviewIndex)

    // The same settle-on-pause as the cover [MorphingArtwork] hands over from.
    val pauseScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else PausedScale,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "carouselPauseScale"
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // A fixed radius, not a percentage: a percentage shrinks with the item, so the covers
        // sliding in would be less rounded than the one sitting still.
        val cornerShape = RoundedCornerShape(maxWidth * CoverCornerFraction)
        // The hero item is a hair larger than the cover on every side, so it can never come out
        // narrower than it and crop the artwork's edges; the clip below trims it back.
        val itemWidth = maxWidth + ItemBleed * 2
        val itemHeight = maxHeight + ItemBleed * 2
        val containerWidth = itemWidth + (SliverWidth + ItemSpacing) * 2 + 2.dp
        // Clipped to the cover's own rounded square: at rest only the playing cover shows, and a
        // swipe shrinks it while the neighbour slides in from beyond the edge. Rounded, so the
        // neighbour arrives with a curved edge instead of a cut-off square one.
        Box(modifier = Modifier.fillMaxSize().clip(cornerShape)) {
            HorizontalCenteredHeroCarousel(
                state = state,
                maxItemWidth = itemWidth,
                itemSpacing = ItemSpacing,
                // One cover per swipe, however hard the fling.
                flingBehavior = CarouselDefaults.singleAdvanceFlingBehavior(state),
                minSmallItemWidth = SliverWidth,
                maxSmallItemWidth = SliverWidth,
                modifier = Modifier
                    .requiredWidth(containerWidth)
                    .requiredHeight(itemHeight)
                    .graphicsLayer {
                        this.alpha = alpha()
                        scaleX = pauseScale
                        scaleY = pauseScale
                    }
            ) { index ->
                val track = queue.getOrNull(index)
                if (track != null) {
                    CoverItem(track, cornerShape, isCurrent = index == currentIndex, fingerprintStatus)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CarouselItemScope.CoverItem(
    track: UnifiedTrack,
    shape: Shape,
    isCurrent: Boolean,
    fingerprintStatus: FingerprintStatus
) {
    Box(modifier = Modifier.fillMaxSize().maskClip(shape)) {
        Artwork(
            url = track.artworkUrl,
            contentDescription = track.title,
            sizeDp = MorphArtworkSize,
            // `maskClip` above already shapes the item; see FavoritesCarouselShelf.
            shape = RectangleShape,
            crossfade = false,
            modifier = Modifier.fillMaxSize()
        )
        if (isCurrent) {
            FingerprintBadge(
                status = fingerprintStatus,
                modifier = Modifier.align(Alignment.BottomStart).padding(BadgeInset)
            )
        }
    }
}

/**
 * Keeps the carousel and Media3 agreeing about which track is current, in both directions, without
 * either one fighting the other.
 *
 * - **Carousel → player:** only a *settled* item is acted on, so nothing plays or buffers while a
 *   swipe is still moving.
 * - **Player → carousel:** a change nobody here asked for (skip button, a song ending, the queue
 *   drawer) animates the carousel to it.
 *
 * The player reports back every seek this file issues. Those echoes are recognised and ignored;
 * without that, sliding again before the first echo landed would treat it as an outside change and
 * yank the carousel back to the older cover. [requested] holds the seeks still waiting on their
 * echo, oldest first, so an echo also retires any older ones the player coalesced past.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CarouselPlaybackSync(
    state: CarouselState,
    currentIndex: Int,
    itemCount: Int,
    onSeekToIndex: (Int) -> Unit,
    onPreviewIndex: (Int?) -> Unit
) {
    val requested = remember { ArrayDeque<Int>() }
    val latestIndex by rememberUpdatedState(currentIndex)
    val seek by rememberUpdatedState(onSeekToIndex)
    val settleSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    // How many of our own slides are running. A count, not a flag: a restarted effect cancels its
    // predecessor, whose cleanup can land after the successor has already begun. It is what tells
    // a finger on the carousel apart from one of these slides, both of which read as "scrolling".
    val slides = remember { intArrayOf(0) }
    val slideTo: suspend (Int) -> Unit = { item ->
        slides[0]++
        try {
            state.animateScrollToItem(item, settleSpec)
        } finally {
            slides[0]--
        }
    }
    val fingerDown = { state.isScrollInProgress && slides[0] == 0 }

    // What the text under the cover should show: the cover the carousel is on, from the moment a
    // swipe carries it past halfway until the player has caught up. Not for a change the player
    // made itself (a button), where the player is already ahead of the carousel and the text
    // follows it on its own.
    val onPreview by rememberUpdatedState(onPreviewIndex)
    val publishPreview = {
        val shown = state.currentItem
        onPreview(if (shown != latestIndex && (fingerDown() || shown in requested)) shown else null)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.currentItem to state.isScrollInProgress }.collect { publishPreview() }
    }

    LaunchedEffect(state) {
        snapshotFlow { if (state.isScrollInProgress) NotSettled else state.currentItem }
            .filter { it != NotSettled }
            .distinctUntilChanged()
            .collectLatest { settled ->
                if (settled == latestIndex) return@collectLatest
                requested.addLast(settled)
                publishPreview()
                seek(settled)
                // A seek the player never acts on (a Jam follower's, say) would otherwise leave
                // the carousel showing a track that is not playing.
                delay(EchoTimeoutMs)
                requested.clear()
                publishPreview()
                if (!fingerDown() && state.currentItem != latestIndex) slideTo(latestIndex)
            }
    }

    // A scroll cut short (a programmatic slide interrupted by the next touch, a fling from a very
    // fast run of swipes) can leave the carousel resting between two covers. Once things have been
    // still for a moment, slide to the nearest one. The cooldown keeps this one's own motion from
    // triggering another.
    LaunchedEffect(state) {
        var lastHeal = 0L
        snapshotFlow { state.isScrollInProgress }
            .filter { !it }
            .collectLatest {
                delay(HealDelayMs)
                val now = System.currentTimeMillis()
                if (now - lastHeal < HealCooldownMs) return@collectLatest
                lastHeal = now
                slideTo(state.currentItem)
            }
    }

    LaunchedEffect(currentIndex) {
        val echo = requested.indexOf(currentIndex)
        if (echo >= 0) {
            repeat(echo + 1) { requested.removeFirst() }
            publishPreview()
            return@LaunchedEffect
        }
        requested.clear()
        publishPreview()
        // Not while a finger is on it: the swipe wins, and whatever it settles on is what plays.
        // Our own slide in flight does not count, or a run of button presses would freeze the
        // carousel on the first cover it was heading for.
        if (currentIndex in 0 until itemCount && state.currentItem != currentIndex && !fingerDown()) {
            slideTo(currentIndex)
        }
    }
}

private const val NotSettled = -1
private const val EchoTimeoutMs = 2_000L
private const val HealDelayMs = 120L
private const val HealCooldownMs = 500L

/** [MorphShape]'s 12% corner, as a fraction of the cover's width. */
private const val CoverCornerFraction = 0.12f

private val ItemSpacing = 12.dp

/** How far the hero item overshoots the cover on each side, before the clip trims it. */
private val ItemBleed = 2.dp

/** Only sizes the carousel's small items; clipped away at rest, they are never seen. */
private val SliverWidth = 16.dp
