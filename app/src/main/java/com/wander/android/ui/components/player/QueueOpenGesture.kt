package com.wander.android.ui.components.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs

/**
 * Pulls the queue up on an upward drag, from anywhere on the player — the drawer follows the finger.
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
@Composable
internal fun Modifier.swipeUpToOpenQueue(
    enabled: Boolean,
    /**
     * Checked at touch-down. False once the drawer is up: from then on the drawer's own drag and
     * its list's nested scroll own the gesture, and claiming it here too would move it twice.
     */
    canStart: () -> Boolean,
    /** Each movement once the gesture is claimed, in px, screen direction (negative is up). */
    onDrag: (dy: Float) -> Unit,
    /** The finger lifted (or the gesture was cancelled), with its vertical velocity in px/s. */
    onRelease: (velocityY: Float) -> Unit
): Modifier {
    // Keyed on nothing that changes per frame: the caller's lambdas are new on every
    // recomposition, and the drawer appearing recomposes the caller at the very start of this
    // drag — a pointerInput keyed on them restarted mid-gesture and left the drawer stranded.
    val currentCanStart by rememberUpdatedState(canStart)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentRelease by rememberUpdatedState(onRelease)
    return if (!enabled) this else pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!currentCanStart()) return@awaitEachGesture
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var travelY = 0f
            var travelX = 0f
            var claimed = false

            while (true) {
                val event = awaitPointerEvent()
                val change: PointerInputChange = event.changes.firstOrNull { it.id == down.id }
                    ?: break.also { if (claimed) currentRelease(0f) }
                tracker.addPosition(change.uptimeMillis, change.position)
                if (!change.pressed) {
                    if (claimed) currentRelease(tracker.calculateVelocity().y)
                    break
                }

                val delta = change.positionChange()
                if (claimed) {
                    change.consume()
                    currentDrag(delta.y)
                    continue
                }

                travelY += delta.y
                travelX += delta.x
                // Sideways gestures belong to the artwork's skip swipe, downward ones to the sheet's
                // collapse. Either way this gesture is over — and nothing has been consumed, so
                // whichever of them owns it has seen the whole thing.
                if (abs(travelX) > abs(travelY)) break
                if (travelY > 0f) break

                // Claimed at touch slop, so a tap still reaches the controls underneath — and from
                // then on every pixel of movement goes to the drawer, which follows the finger.
                if (-travelY >= slop) {
                    claimed = true
                    change.consume()
                    currentDrag(travelY)
                }
            }
        }
    }
}
