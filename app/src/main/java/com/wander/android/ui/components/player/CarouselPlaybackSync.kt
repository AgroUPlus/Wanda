package com.wander.android.ui.components.player

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

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
internal fun CarouselPlaybackSync(
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

    // Every programmatic slide goes through this one queue, one at a time, and only the newest
    // request waits its turn: however fast the presses come, the carousel ends on the last cover
    // asked for. A slide is never cut short by the next one. `animateScrollToItem` works out its
    // distance from the nearest cover, not from where the carousel actually is, so a slide begun
    // mid-slide falls short by whatever fraction was left and leaves a cover part-way across.
    val slideRequests = remember { Channel<Pair<Long, Int>>(Channel.CONFLATED) }
    val requestSeq = remember { longArrayOf(0L) }
    // True from a request until its slide has run to the end or a finger has taken over. It is what
    // tells a finger on the carousel apart from one of these slides, both of which read as "scrolling".
    // State rather than a flag, so the settle effect below re-reads it the moment it clears.
    var sliding by remember { mutableStateOf(false) }
    val lastSlideEnd = remember { longArrayOf(0L) }
    val slideTo = { item: Int ->
        sliding = true
        slideRequests.trySend(++requestSeq[0] to item)
        Unit
    }
    LaunchedEffect(state) {
        for ((seq, item) in slideRequests) {
            try {
                state.animateScrollToItem(item, settleSpec)
                // Lands the cover exactly, whatever the animation left over: a no-op when it
                // already is, and a small correction rather than a cover stuck part-way when not.
                state.scrollToItem(item)
                lastSlideEnd[0] = System.currentTimeMillis()
            } catch (e: CancellationException) {
                // This effect ending is a real cancellation; anything else is a finger taking the
                // scroll over, which the swipe then settles by itself.
                currentCoroutineContext().ensureActive()
            }
            // Unless a newer request arrived as this one finished, and is still queued.
            if (seq == requestSeq[0]) sliding = false
        }
    }
    val fingerDown = { state.isScrollInProgress && !sliding }

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
        // Not while one of our own slides is pending either: between a cancelled slide and the one
        // replacing it the scroll reads as stopped on some cover in between, which is no swipe
        // result and must not send the player back to it.
        snapshotFlow { if (state.isScrollInProgress || sliding) NotSettled else state.currentItem }
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
    // still for a moment, slide to the nearest one. A stop right after one of our own completed
    // slides is skipped, so this never triggers itself; a stop left by a cancelled slide is not.
    // Nothing is healed while a slide is pending, or it would replace the cover that was asked for.
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .filter { !it }
            .collectLatest {
                delay(HealDelayMs)
                val ownStop = System.currentTimeMillis() - lastSlideEnd[0] < OwnSlideGraceMs
                if (!ownStop && !sliding && !state.isScrollInProgress) slideTo(state.currentItem)
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
        // carousel on the first cover it was heading for. Not gated on `currentItem` either: a
        // slide cut short by the next press can leave the carousel past halfway to this cover, so
        // it already reads as current while still resting between two.
        if (currentIndex in 0 until itemCount && !fingerDown()) {
            slideTo(currentIndex)
        }
    }
}

private const val NotSettled = -1
private const val EchoTimeoutMs = 2_000L
private const val HealDelayMs = 120L
private const val OwnSlideGraceMs = 300L
