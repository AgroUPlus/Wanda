package com.wander.android.ui.screens.welcome

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import com.wander.android.ui.theme.LocalReducedMotion
import kotlin.math.abs

/**
 * A small, obviously-fake player with a fingertip demonstrating one gesture on a loop.
 *
 * It is a drawing, not a live player: no artwork, no state, nothing wired up. That is deliberate —
 * a real player here would need a track to show, and setup happens before there is one. But the
 * skeleton it draws is the *real* player's own skeleton — cover at the same width fraction and
 * corner shape as [com.wander.android.ui.screens.player.NowPlayingScreen], title/artist rows, a
 * seek bar, a prev/play/next row — not an arbitrary set of bars, so the gesture demonstrated here
 * visibly lands on the part of the real screen it actually belongs to.
 *
 * Height-capped rather than let its `aspectRatio` grow with the page's width: uncapped, a
 * near-full-width square-ish mock plus the headline, subtitle, chip row and caption around it can
 * run past a single viewport on a compact phone, so this step would need to be scrolled through
 * instead of watched.
 *
 * Honours reduced motion by holding the cursor still at the gesture's end position with its trail
 * drawn, rather than looping. An animation that exists to be watched is exactly the kind the
 * setting is asking not to be shown.
 */
@Composable
internal fun GesturePreview(
    gesture: PlayerGesture,
    modifier: Modifier = Modifier
) {
    val reduced = LocalReducedMotion.current
    val progress by if (reduced) {
        remember { mutableFloatStateOf(1f) }
    } else {
        rememberInfiniteTransition(label = "gestureLoop").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(LoopMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "gestureProgress"
        )
    }

    val cursor = cursorAt(gesture.motion, progress)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(PhoneAspect)
            .heightIn(max = MaxPreviewHeight)
    ) {
        PhoneMock(highlight = gesture.motion, modifier = Modifier.fillMaxSize())
        Cursor(cursor, showTrail = reduced, modifier = Modifier.fillMaxSize())
    }
}

/** Where the fingertip is, how hard it is pressing, and how far a tap ripple has spread. */
private data class CursorState(
    val xFraction: Float,
    val yFraction: Float,
    val press: Float,
    val ripple: Float,
    val alpha: Float
)

/**
 * The fingertip's position for [motion] at [progress].
 *
 * Fractions of the mock's own size rather than dp, so the demonstration scales with whatever width
 * the step is given instead of drifting off a narrow screen.
 */
private fun cursorAt(motion: GestureMotion, progress: Float): CursorState = when (motion) {
    GestureMotion.SWIPE_UP -> travel(progress, fromY = 0.30f, toY = -0.12f)
    GestureMotion.SWIPE_DOWN -> travel(progress, fromY = -0.06f, toY = 0.34f)
    GestureMotion.SWIPE_SIDEWAYS -> travel(progress, fromX = 0.26f, toX = -0.26f, y = -0.10f)
    GestureMotion.TAP -> {
        // Down, up, and a ripple that outlives the press — the shape of a tap read at a glance.
        val down = progress in TapStart..TapEnd
        CursorState(
            xFraction = 0f,
            yFraction = -0.10f,
            press = if (down) 1f else 0f,
            ripple = ((progress - TapStart) / RippleSpan).coerceIn(0f, 1f),
            alpha = 1f
        )
    }
    GestureMotion.HOLD -> CursorState(
        xFraction = 0f,
        yFraction = -0.10f,
        // Presses early and stays down: the ring below fills for as long as it is held, which is
        // the only thing that distinguishes this from a tap.
        press = (progress / HoldRampSpan).coerceIn(0f, 1f),
        ripple = 0f,
        alpha = 1f
    )
}

/** A straight drag, fading in as the finger lands and out as it lifts. */
private fun travel(
    progress: Float,
    fromX: Float = 0f,
    toX: Float = 0f,
    fromY: Float = 0f,
    toY: Float = 0f,
    y: Float? = null
): CursorState {
    // Eased so the drag accelerates and settles rather than sliding at a constant rate, and held
    // at each end for a beat so the loop reads as repetitions rather than one continuous circle.
    val t = ((progress - TravelStart) / TravelSpan).coerceIn(0f, 1f)
    val eased = t * t * (3f - 2f * t)
    return CursorState(
        xFraction = fromX + (toX - fromX) * eased,
        yFraction = y ?: (fromY + (toY - fromY) * eased),
        press = if (progress in TravelStart..(TravelStart + TravelSpan)) 1f else 0.35f,
        ripple = 0f,
        alpha = 1f - (abs(progress - 0.5f) / 0.5f).coerceIn(0f, 1f) * FadeAtEnds
    )
}

/** The fingertip itself: a soft disc, a press ring, and a tap ripple. */
@Composable
private fun Cursor(
    state: CursorState,
    showTrail: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        // The ripple, drawn under the fingertip so it reads as coming out from beneath it.
        if (state.ripple > 0f && state.ripple < 1f) {
            Box(
                modifier = Modifier
                    .fractionOffset(state.xFraction, state.yFraction)
                    .size(CursorSize)
                    .graphicsLayer {
                        val spread = 1f + state.ripple * RippleGrowth
                        scaleX = spread
                        scaleY = spread
                        alpha = (1f - state.ripple) * RippleAlpha
                    }
                    .clip(CircleShape)
                    .background(accent)
            )
        }

        Box(
            modifier = Modifier
                .fractionOffset(state.xFraction, state.yFraction)
                .size(CursorSize)
                .graphicsLayer {
                    // Presses *into* the screen: smaller and more opaque, the way a fingertip
                    // flattens against glass.
                    val squash = 1f - state.press * PressSquash
                    scaleX = squash
                    scaleY = squash
                    alpha = state.alpha
                }
                .clip(CircleShape)
                .background(accent.copy(alpha = if (showTrail) 0.75f else 0.55f + state.press * 0.35f))
        )
    }
}

/** Offsets by a fraction of the parent's own size, which `Modifier.offset` cannot express. */
private fun Modifier.fractionOffset(x: Float, y: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        placeable.place(
            (constraints.maxWidth * x).toInt(),
            (constraints.maxHeight * y).toInt()
        )
    }
}

private const val LoopMillis = 2400
private const val PhoneAspect = 0.72f
private val MaxPreviewHeight = 260.dp
private const val FadeAtEnds = 0.85f

// Where in the loop a drag runs, leaving a beat of stillness either side of it.
private const val TravelStart = 0.18f
private const val TravelSpan = 0.52f

// A tap: down briefly, with the ripple outlasting it.
private const val TapStart = 0.30f
private const val TapEnd = 0.42f
private const val RippleSpan = 0.40f
private const val RippleGrowth = 2.2f
private const val RippleAlpha = 0.45f

/** How much of the loop the hold spends pressing down before it is simply held. */
private const val HoldRampSpan = 0.35f
private const val PressSquash = 0.22f

private val CursorSize = 26.dp
