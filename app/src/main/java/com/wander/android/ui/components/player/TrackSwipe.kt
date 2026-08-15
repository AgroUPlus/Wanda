package com.wander.android.ui.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

/** Past this many pixels, or this fling velocity, the drag counts as a skip. */
private const val DistanceThreshold = 120f
private const val VelocityThreshold = 800f

/** Drags track at 70% of the finger so the gesture feels weighted rather than loose. */
private const val DragResistance = 0.7f

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
    exitDistance: Float = FullExitDistance
): Modifier {
    val scope = rememberCoroutineScope()

    return draggable(
        orientation = Orientation.Horizontal,
        state = rememberDraggableState { delta ->
            state.isSwiping = true
            scope.launch { state.offsetX.snapTo(state.offsetX.value + delta * DragResistance) }
        },
        onDragStopped = { velocity ->
            scope.launch {
                try {
                    val offset = state.offsetX.value
                    val skipNext = offset < -DistanceThreshold || velocity < -VelocityThreshold
                    val skipPrevious = offset > DistanceThreshold || velocity > VelocityThreshold

                    if (skipNext || skipPrevious) {
                        // Carry the outgoing content off, swap, then bring the new one in from
                        // the far side.
                        val exit = if (skipNext) -exitDistance else exitDistance
                        state.offsetX.animateTo(exit, tween(durationMillis = 140))
                        if (skipNext) onNext() else onPrevious()
                        state.offsetX.snapTo(-exit)
                    }

                    state.offsetX.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                } finally {
                    // In a `finally` because a second gesture starting mid-settle cancels this
                    // coroutine, and leaving the flag set would strand the peek covers on screen.
                    state.isSwiping = false
                }
            }
        }
    )
}
