package com.wander.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp

/** How round a grouped row's corners become while it is pressed — the Material Expressive press morph. */
private val PressedRadius = 28.dp

/** How far a pressed row's content shrinks. The same as the cards and track rows shrink on their own. */
private const val PressedScale = 0.94f

/** Whether a finger is down on a row. Set by [trackPress], read by the [PressMorph] built on it. */
@Stable
internal class PressTracker {
    var pressed by mutableStateOf(false)
        internal set
}

/**
 * One press animation for a grouped row: its corners round off ([shape]) and its content shrinks
 * ([pressShrink]) on the same spring, started the instant a finger lands, so the two read as one
 * motion however quick the tap.
 */
@Stable
internal class PressMorph internal constructor(
    internal val tracker: PressTracker,
    private val progress: State<Float>
) {
    /** The row's shape for its place in the group: [groupedItemShape] at rest, fully rounded at full press. */
    fun shape(index: Int, count: Int): RoundedCornerShape {
        val round = progress.value
        val top = lerp(if (index == 0) GroupedOuterRadius else GroupedInnerRadius, PressedRadius, round)
        val bottom = lerp(if (index == count - 1) GroupedOuterRadius else GroupedInnerRadius, PressedRadius, round)
        return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    }

    internal val scale: Float get() = 1f - (1f - PressedScale) * progress.value
}

@Composable
internal fun rememberPressMorph(): PressMorph {
    val tracker = remember { PressTracker() }
    val progress = animateFloatAsState(
        targetValue = if (tracker.pressed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "pressMorph"
    )
    return remember(tracker, progress) { PressMorph(tracker, progress) }
}

/** Shrinks what is inside a pressed row, in step with its corners. Apply to the row's content. */
internal fun Modifier.pressShrink(morph: PressMorph): Modifier = graphicsLayer {
    val scale = morph.scale
    scaleX = scale
    scaleY = scale
}

/**
 * How long a touch on a list item holds before it shows it is pressed — the same beat a click waits
 * before showing its own press feedback. In a list, a finger that lands to scroll should not make
 * every row it crosses round off and shrink.
 */
internal const val ListPressDelayMillis = 100L

/**
 * Feeds [morph] from touches on this row. It listens before the row's own children do and consumes
 * nothing, so clicks, long presses, swipes and scrolling behave exactly as before. A touch that
 * turns into a scroll or drag stops counting as a press.
 *
 * With a [delayMillis] the press only shows once the finger has held that long, so a tap or a
 * scroll that starts sooner shows nothing. Settings and buttons use none: they respond at once.
 */
internal fun Modifier.trackPress(morph: PressMorph, delayMillis: Long = 0L): Modifier = pointerInput(morph, delayMillis) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)

        // True once the finger lifts or slides off, false while it is still holding.
        suspend fun AwaitPointerEventScope.released(): Boolean {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes
                .firstOrNull { it.id == down.id } ?: return true
            return !change.pressed || (change.position - down.position).getDistance() > slop
        }

        val endedEarly = if (delayMillis > 0) {
            withTimeoutOrNull(delayMillis) {
                while (!released()) Unit
                true
            }
        } else {
            null
        }
        if (endedEarly == null) {
            morph.tracker.pressed = true
            while (!released()) Unit
            morph.tracker.pressed = false
        }
    }
}
