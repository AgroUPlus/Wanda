package com.wander.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp

/** How round a grouped row's corners become while it is pressed — the Material Expressive press morph. */
private val PressedRadius = 28.dp

/**
 * Whether a finger is down on a row. Shared between [trackPress], which sets it, and [shape], which
 * reads it, so any row in a group can round off under the finger without its own click handler
 * having to know.
 */
@Stable
internal class PressMorph {
    var pressed by mutableStateOf(false)
        internal set

    /**
     * The row's shape for its place in the group: [groupedItemShape] at rest, every corner rounded
     * to [PressedRadius] while pressed, moving on the theme's spatial spring.
     */
    @Composable
    fun shape(index: Int, count: Int): RoundedCornerShape {
        val round by animateFloatAsState(
            targetValue = if (pressed) 1f else 0f,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "pressMorph"
        )
        val top = lerp(if (index == 0) GroupedOuterRadius else GroupedInnerRadius, PressedRadius, round)
        val bottom = lerp(if (index == count - 1) GroupedOuterRadius else GroupedInnerRadius, PressedRadius, round)
        return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    }
}

@Composable
internal fun rememberPressMorph(): PressMorph = remember { PressMorph() }

/**
 * Feeds [morph] from touches on this row. It listens before the row's own children do and consumes
 * nothing, so clicks, long presses, swipes and scrolling behave exactly as before. A touch that
 * turns into a scroll or drag stops counting as a press.
 */
internal fun Modifier.trackPress(morph: PressMorph): Modifier = pointerInput(morph) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        morph.pressed = true
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes
                .firstOrNull { it.id == down.id } ?: break
            if (!change.pressed || (change.position - down.position).getDistance() > slop) break
        }
        morph.pressed = false
    }
}
