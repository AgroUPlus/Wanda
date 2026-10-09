package com.wander.android.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Reports a press to [source] the instant a finger lands.
 *
 * A click inside a scrolling list waits a beat before it shows it is pressed, so a card's own press
 * shrink started late and a tap barely showed it. This puts the press on the source first; the
 * click's own, later press joins it and the source stays pressed until both end. Nothing is
 * consumed, so clicks, long presses and scrolling are untouched, and a touch that turns into a
 * scroll ends the press.
 */
internal fun Modifier.instantPress(source: MutableInteractionSource): Modifier = pointerInput(source) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val press = PressInteraction.Press(down.position)
        source.tryEmit(press)
        var cancelled = false
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes
                .firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            if ((change.position - down.position).getDistance() > slop) {
                cancelled = true
                break
            }
        }
        source.tryEmit(if (cancelled) PressInteraction.Cancel(press) else PressInteraction.Release(press))
    }
}
