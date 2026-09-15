package com.wander.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.toSize
import com.wander.android.ui.theme.LocalReducedMotion
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Backs a row of mutually-exclusive chips or toggles with one background that glides to whichever
 * item is selected, instead of each item snapping its own highlight on and off — the same idea as
 * a tab row's indicator, generalised to any row of selectable items.
 *
 * Items report their own bounds via [recordHighlightBounds] once laid out, so this owns no
 * knowledge of how many there are or how they're arranged — only which key is currently selected.
 */
internal class TravelingHighlightState {
    internal val bounds = mutableStateMapOf<Any, Pair<Offset, Size>>()
}

@Composable
internal fun rememberTravelingHighlightState(): TravelingHighlightState =
    remember { TravelingHighlightState() }

/** Reports this item's bounds, relative to the container [TravelingHighlight] shares them in. */
internal fun Modifier.recordHighlightBounds(state: TravelingHighlightState, key: Any): Modifier =
    onGloballyPositioned { coordinates ->
        state.bounds[key] = coordinates.positionInParent() to coordinates.size.toSize()
    }

/**
 * The pill itself. Place it as a sibling of the row it highlights, inside the same container the
 * items measured their bounds against (a shared [Box] is the usual shape) — and, if that container
 * scrolls, inside the scrolling content too, so the pill moves with it for free.
 */
@Composable
internal fun TravelingHighlight(
    state: TravelingHighlightState,
    selectedKey: Any?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge
) {
    val target = selectedKey?.let { state.bounds[it] }
    val density = LocalDensity.current

    val offsetX = remember { Animatable(target?.first?.x ?: 0f) }
    val offsetY = remember { Animatable(target?.first?.y ?: 0f) }
    val width = remember { Animatable(target?.second?.width ?: 0f) }
    val height = remember { Animatable(target?.second?.height ?: 0f) }
    var hasPlaced by remember { mutableStateOf(target != null) }

    val highlightAlpha by animateFloatAsState(
        targetValue = if (target != null) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "highlightAlpha"
    )

    // Critically damped — no bounce, no overshoot past the target. A springier spec reads fine for
    // a single value in isolation, but four of them (offset x/y, width, height) each overshooting
    // and settling on their own schedule is what made this box arrive at the new chip's *position*
    // looking like it still had the old chip's *size* for a beat. `dampingRatio = 1f` means each
    // dimension arrives once and stays, so all four land together.
    val travelSpec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    val scope = rememberCoroutineScope()
    // Hand-rolled spring, not `MaterialTheme.motionScheme` — see the note on `travelSpec` — so
    // reduce-motion is checked directly here rather than covered by the theme's scheme swap.
    val reduceMotion = LocalReducedMotion.current

    LaunchedEffect(target) {
        val (position, size) = target ?: return@LaunchedEffect
        if (!hasPlaced || reduceMotion) {
            // First time this key has ever had bounds: appear there directly rather than
            // travelling in from wherever the Animatables happened to start. Reduced motion takes
            // the same path every time — the pill jumps straight to the selection rather than
            // gliding to it.
            hasPlaced = true
            offsetX.snapTo(position.x)
            offsetY.snapTo(position.y)
            width.snapTo(size.width)
            height.snapTo(size.height)
        } else {
            // All four launched together rather than awaited in sequence: `animateTo` suspends
            // until its animation finishes, so awaiting them one after another moved the pill to
            // its new *position* — fully — before its *size* ever started catching up, visibly
            // sitting at the wrong width for a beat. They now settle in the same frame.
            scope.launch { offsetX.animateTo(position.x, travelSpec) }
            scope.launch { offsetY.animateTo(position.y, travelSpec) }
            scope.launch { width.animateTo(size.width, travelSpec) }
            scope.launch { height.animateTo(size.height, travelSpec) }
        }
    }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
            .width(with(density) { width.value.toDp() })
            .height(with(density) { height.value.toDp() })
            .alpha(highlightAlpha)
            .background(MaterialTheme.colorScheme.secondaryContainer, shape)
    )
}
