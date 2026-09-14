package com.wander.android.ui.components.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Opens the queue on an upward drag, from anywhere on the player.
 *
 * The queue used to have a handle at the foot of the player to pull on. A handle is one small
 * target for a gesture the whole surface can afford, so the surface affords it instead: anywhere
 * on the player, drag up.
 *
 * ## Why this is written by hand rather than with `draggable`
 *
 * The player sheet is itself a vertical drag surface — dragging *down* collapses it back to the
 * docked strip (see `PlayerSheet`). A child that took every vertical gesture would eat that, and
 * the player would become impossible to close by dragging.
 *
 * So this consumes **only upward** movement. A gesture that resolves downwards is left entirely
 * untouched, including the events already seen, so the sheet's own `draggable` picks it up and
 * collapses exactly as before. That selectivity is not expressible with `draggable`, which commits
 * to an axis rather than to a direction.
 *
 * Horizontal gestures are likewise released: the cover's swipe-to-skip lives on the same pixels,
 * and a drag that is mostly sideways belongs to it.
 */
internal fun Modifier.swipeUpToOpenQueue(
    enabled: Boolean,
    onOpen: () -> Unit
): Modifier = if (!enabled) this else pointerInput(onOpen) {
    val threshold = QueueOpenDistance.toPx()

    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var travelY = 0f
        var travelX = 0f

        while (true) {
            val event = awaitPointerEvent()
            val change: PointerInputChange = event.changes.firstOrNull() ?: break
            if (!change.pressed) break

            val delta = change.positionChange()
            travelY += delta.y
            travelX += delta.x

            // Sideways gestures belong to the artwork's skip swipe, downward ones to the sheet's
            // collapse. Either way this gesture is over — and nothing has been consumed, so
            // whichever of them owns it has seen the whole thing.
            if (abs(travelX) > abs(travelY)) break
            if (travelY > 0f) break

            // Consumed only once the drag is unambiguously this gesture, so a tap or a short
            // upward wobble still reaches the controls underneath.
            if (-travelY >= threshold) {
                change.consume()
                onOpen()
                break
            }
        }
    }
}

/** Far enough not to fire on a stray upward wobble while tapping a control. */
private val QueueOpenDistance = 56.dp
