package com.wander.android.ui.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.rubberBand
import com.wander.android.ui.components.rememberHaptics
import kotlinx.coroutines.launch

/** Past this many pixels, or this fling velocity, the drag counts as a skip. */
internal const val DistanceThreshold = 120f
private const val VelocityThreshold = 800f

/** Drags track at 70% of the finger so the gesture feels weighted rather than loose. */
private const val DragResistance = 0.7f

/** How far the cover can be pulled toward a side that has no track to skip to. */
private val EdgeStretch = 40.dp

/** Far enough that the full player's cover is clear of any realistic screen width. */
internal const val FullExitDistance = 1200f

/** The docked strip only slides its text a little, so it has far less distance to travel. */
internal const val DockedExitDistance = 220f

/**
 * The live horizontal drag, hoisted out of the modifier that produces it.
 *
 * It used to be a private `Animatable` inside `rememberSwipeToChangeTrack`, which meant only the
 * node the modifier was applied to could move. The cover art is not that node — it is
 * [MorphingArtwork], drawn once for both layouts and positioned from measured anchors — so
 * nothing could follow the finger except by moving the box that *reports* those anchors, and
 * nothing at all could be drawn either side of it. Hoisting the state is what lets the artwork
 * layer translate itself and peek at the neighbouring covers.
 */
@Stable
internal class TrackSwipeState {

    /**
     * Signed pixels the content is currently dragged by.
     *
     * **Read only inside `layout`/`graphicsLayer` lambdas.** It changes every frame of a gesture,
     * and reading it in composition scope would recompose the whole player each of those frames —
     * the constraint the rest of this package is built around.
     */
    internal val offsetX = Animatable(0f)

    /**
     * Whether a gesture is in flight, including its settle animation.
     *
     * Unlike [offsetX] this is safe to read in composition: it flips at most twice per gesture, so
     * it can decide what is *composed* — specifically, whether the neighbouring covers exist at
     * all — without costing a per-frame recomposition.
     */
    var isSwiping by mutableStateOf(false)
        internal set

    /**
     * The filmstrip pitch: one cover plus the gap to the next, in pixels.
     *
     * Written from [MorphingArtwork]'s layout lambda, which is the only place that knows how big
     * the cover currently is, and read when a swipe commits. Plain `var`, not state: it is only
     * ever read inside layout/gesture code, never in composition.
     */
    internal var stepPx: Float = 0f

    /**
     * The cover to show *instead of* the playing track's, from the moment a skip commits until the
     * player actually reports the new track.
     *
     * Skipping is asynchronous — the gesture ends here, but the new track arrives from Media3 a
     * frame or more later. Without this the sequence was: fly the outgoing cover off, snap the
     * carriage back, and spring the *same, still-current* cover back into the slot, only for the
     * artwork to change underneath it once the player caught up. That is the "it moves twice, shows
     * the current one, then the next one" bug.
     *
     * Holding the incoming URL here lets the commit land the filmstrip in its final position in one
     * movement: the peek cover that slid into the slot simply stays there, pixel for pixel, and the
     * hand-off back to playback state is invisible whenever it happens.
     */
    var pendingArtworkUrl by mutableStateOf<String?>(null)
        internal set

    /**
     * Whether the drag has already crossed [DistanceThreshold] this gesture — plain `var`, like
     * [stepPx]: only ever read and written from inside the drag callbacks, never composition, so a
     * tick fires once per crossing instead of once per frame past it.
     */
    internal var thresholdCrossed: Boolean = false

    /** Drops [pendingArtworkUrl] once playback has caught up with the skip. */
    internal fun clearPending() {
        pendingArtworkUrl = null
    }
}

@Composable
internal fun rememberTrackSwipeState(): TrackSwipeState = remember { TrackSwipeState() }

/**
 * Horizontal drag-to-skip, shared by the docked strip and the full player.
 *
 * Only the horizontal axis is claimed, so the sheet's own vertical drag is unaffected. Nothing is
 * translated here — the consumer decides what follows [state], which is what keeps the docked
 * strip's own surface still while its contents move.
 *
 * @param exitDistance how far the outgoing content is carried before the track is swapped.
 */
@Composable
internal fun Modifier.swipeToChangeTrack(
    state: TrackSwipeState,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    nextArtworkUrl: String?,
    previousArtworkUrl: String?,
    canNext: Boolean = true,
    canPrevious: Boolean = true,
    exitDistance: Float = FullExitDistance
): Modifier {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    // Read here, in composition, and captured by the gesture coroutines below — which run inside
    // `draggable` and cannot reach the theme themselves.
    //
    // Two specs, because the two settles mean different things. A completed swipe *hands over* to
    // the neighbouring cover and must arrive exactly, so it takes the non-overshooting effects
    // spec — a spring would carry the artwork past its slot and back, and the swap underneath it
    // would show. An abandoned swipe springs back, because overshoot is precisely what says the
    // gesture did not take.
    val handoffSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val settleSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    // The finger's own travel, separate from what is drawn: toward a side with no track the cover
    // follows it on a rubber band ([rubberBand]) instead of stopping dead, then bounces back.
    var fingerX by remember { mutableFloatStateOf(0f) }
    val edgeStretchPx = with(LocalDensity.current) { EdgeStretch.toPx() }

    return draggable(
        orientation = Orientation.Horizontal,
        enabled = canNext || canPrevious,
        onDragStarted = { fingerX = state.offsetX.value },
        state = rememberDraggableState { delta ->
            val current = state.offsetX.value
            fingerX += delta * DragResistance
            val blocked = (fingerX > 0f && !canPrevious) || (fingerX < 0f && !canNext)
            val clamped = if (blocked) rubberBand(fingerX, edgeStretchPx) else fingerX
            if (clamped != current) {
                state.isSwiping = true
                scope.launch { state.offsetX.snapTo(clamped) }
            }
            // A tick right as the drag crosses into "this would skip if you let go" — the same
            // boundary `onDragStopped` commits on — so releasing carries no surprise.
            val crossed = !blocked && kotlin.math.abs(clamped) >= DistanceThreshold
            if (crossed != state.thresholdCrossed) {
                state.thresholdCrossed = crossed
                if (crossed) haptics.tick()
            }
        },
        onDragStopped = { velocity ->
            scope.launch {
                try {
                    val offset = state.offsetX.value
                    val skipNext = (offset < -DistanceThreshold || velocity < -VelocityThreshold) && canNext
                    val skipPrevious = (offset > DistanceThreshold || velocity > VelocityThreshold) && canPrevious

                    if (skipNext || skipPrevious) {
                        // Slide the whole filmstrip by exactly one slot, and stop.
                        //
                        // Exactly one step where the peek covers have reported one: carrying the
                        // cover further than the neighbour is spaced meant the incoming cover
                        // arrived from two slots away, which read as skipping two tracks.
                        // [exitDistance] is the fallback for content with no filmstrip — the docked
                        // strip's text.
                        val travel = state.stepPx.takeIf { it > 0f } ?: exitDistance
                        state.offsetX.animateTo(
                            targetValue = if (skipNext) -travel else travel,
                            animationSpec = handoffSpec
                        )

                        // The neighbour is now sitting exactly in the slot. Adopt its cover and
                        // re-zero the carriage in the same breath: the two cancel out, so nothing
                        // moves, and the strip is already in its final state before the player has
                        // even been told to skip.
                        state.pendingArtworkUrl =
                            if (skipNext) nextArtworkUrl else previousArtworkUrl
                        state.offsetX.snapTo(0f)

                        if (skipNext) onNext() else onPrevious()
                    } else {
                        // Same mild bounce as the sheet itself — an abandoned swipe springs back
                        // rather than gliding, which is what says the gesture did not take.
                        state.offsetX.animateTo(
                            targetValue = 0f,
                            animationSpec = settleSpec
                        )
                    }
                } finally {
                    // In a `finally` because a second gesture starting mid-settle cancels this
                    // coroutine, and leaving the flag set would strand the peek covers on screen.
                    state.isSwiping = false
                    state.thresholdCrossed = false
                }
            }
        }
    )
}
