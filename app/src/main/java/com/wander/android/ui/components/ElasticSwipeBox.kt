package com.wander.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** Which way a row is being swiped, in reading order. */
enum class SwipeSide { START, END }

/** How far the rubber band lets a row travel, as a fraction of its width. */
private const val StretchLimit = 0.55f

/** How far (of the width) the row has to be pulled before letting go triggers the action. */
private const val TriggerFraction = 0.3f

/** A flick this fast (px/s) toward an action fires it however short the pull. */
private const val FlingVelocity = 1500f

/** A little bounce back past rest on release — the feel of the system back arrow letting go. */
private val ReturnSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow
)

/**
 * A row that can be swiped sideways with rubber-band resistance: it tracks the finger closely at
 * first and stiffens the further it is pulled ([rubberBand]), ticks once when the pull passes
 * the point of no return, and on release either fires the action or springs back with a small
 * bounce. Both actions are shortcuts that leave the row in place; removing a row is
 * [androidx.compose.material3.SwipeToDismissBox]'s job.
 *
 * [background] is drawn behind the row and told which side is showing and how far toward the
 * trigger point the pull has got (0..1), so it can grow into the action rather than just appear.
 */
@Composable
fun ElasticSwipeBox(
    onSwipeStart: (() -> Unit)?,
    onSwipeEnd: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    background: @Composable BoxScope.(side: SwipeSide?, progress: Float) -> Unit = { _, _ -> },
    content: @Composable BoxScope.() -> Unit
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var raw by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(0) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val currentStart by rememberUpdatedState(onSwipeStart)
    val currentEnd by rememberUpdatedState(onSwipeEnd)

    fun sideOf(value: Float): SwipeSide? = when {
        value == 0f -> null
        (value > 0f) != rtl -> SwipeSide.START
        else -> SwipeSide.END
    }

    fun actionFor(side: SwipeSide?) = when (side) {
        SwipeSide.START -> currentStart
        SwipeSide.END -> currentEnd
        null -> null
    }

    val dragState = rememberDraggableState { delta ->
        val limit = width * StretchLimit
        val next = raw + delta
        // A side with no action does not move at all, rather than stretching toward nothing.
        if (actionFor(sideOf(next)) == null) {
            raw = 0f
        } else {
            val trigger = width * TriggerFraction
            val before = abs(rubberBand(raw, limit)) >= trigger
            raw = next
            val after = abs(rubberBand(raw, limit)) >= trigger
            if (before != after) haptics.heldDown()
        }
        scope.launch { offset.snapTo(rubberBand(raw, limit)) }
    }

    Box(modifier = modifier.onSizeChanged { width = it.width }) {
        val side = sideOf(offset.value)
        val progress = if (width == 0) 0f else (abs(offset.value) / (width * TriggerFraction)).coerceIn(0f, 1f)
        Box(modifier = Modifier.matchParentSize()) { background(side, progress) }
        Box(
            modifier = Modifier
                .graphicsLayer { translationX = offset.value }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    onDragStopped = { velocity ->
                        // From the finger's own travel, not `offset.value`: the drawn offset is
                        // snapped in launched coroutines and trails a fast swipe, so a flick that
                        // had already ticked past the trigger was read as short of it and bounced
                        // back instead of firing.
                        val released = rubberBand(raw, width * StretchLimit)
                        raw = 0f
                        offset.snapTo(released)
                        val action = actionFor(sideOf(released))
                        val flung = abs(velocity) > FlingVelocity && sign(velocity) == sign(released)
                        val triggered = action != null &&
                            (abs(released) >= width * TriggerFraction || flung)
                        if (triggered) {
                            haptics.confirmed()
                            action?.invoke()
                        }
                        offset.animateTo(0f, ReturnSpring, velocity)
                    }
                ),
            content = content
        )
    }
}
