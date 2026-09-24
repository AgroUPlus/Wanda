package com.wander.android.ui.components.player

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Eased 0→1 ramp between [from] and [to], flat outside them. */
internal fun smoothStep(value: Float, from: Float, to: Float): Float {
    val t = ((value - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * How much of the overlay survives a horizontal drag.
 */
internal fun swipeFade(offsetX: Float): Float =
    1f - smoothStep(abs(offsetX), SwipeFadeStart, DistanceThreshold)

/**
 * How far open the player must be before an upward drag means "queue" rather than "open me".
 */
internal const val QueueGestureArmed = 0.98f

internal const val SwipeFadeStart = 12f

/** How far up a drag has to go to open a jam's queue screen. */
internal val JamQueueOpenDistance = 56.dp

/**
 * Takes every pointer event and gives nothing to the content beneath.
 */
internal fun Modifier.swallowPointerInput(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}
