package com.wander.android.ui.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Past this many pixels, or this fling velocity, the drag counts as a skip. */
private const val DistanceThreshold = 120f
private const val VelocityThreshold = 800f

/** Drags track at 70% of the finger so the gesture feels weighted rather than loose. */
private const val DragResistance = 0.7f

/** Far enough that the content is fully clear of any realistic player width. */
private const val ExitDistance = 1200f

/**
 * Horizontal drag-to-skip, shared by the docked strip and the full player.
 *
 * Only the horizontal axis is claimed, so the sheet's own vertical drag is unaffected.
 *
 * @param translateContent whether the content follows the finger. The full player's cover should
 *   (dragging a big image that does not move feels dead), but the docked strip should **not** —
 *   sliding the whole bar around over the navigation bar looks broken. When false the gesture and
 *   its thresholds are unchanged; only the visual translation is dropped.
 */
@Composable
internal fun rememberSwipeToChangeTrack(
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    translateContent: Boolean = true
): Modifier {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    return Modifier
        .then(
            if (translateContent) {
                Modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }
            } else {
                Modifier
            }
        )
        .draggable(
            orientation = Orientation.Horizontal,
            state = rememberDraggableState { delta ->
                scope.launch { offsetX.snapTo(offsetX.value + delta * DragResistance) }
            },
            onDragStopped = { velocity ->
                scope.launch {
                    val offset = offsetX.value
                    val skipNext = offset < -DistanceThreshold || velocity < -VelocityThreshold
                    val skipPrevious = offset > DistanceThreshold || velocity > VelocityThreshold

                    if (skipNext || skipPrevious) {
                        if (translateContent) {
                            // Carry the outgoing content off, swap, bring the new one in from the
                            // far side.
                            val exit = if (skipNext) -ExitDistance else ExitDistance
                            offsetX.animateTo(exit, tween(durationMillis = 140))
                            if (skipNext) onNext() else onPrevious()
                            offsetX.snapTo(-exit)
                        } else {
                            // Nothing is moving, so an exit animation would only delay the skip.
                            if (skipNext) onNext() else onPrevious()
                            offsetX.snapTo(0f)
                            return@launch
                        }
                    }

                    offsetX.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
            }
        )
}
