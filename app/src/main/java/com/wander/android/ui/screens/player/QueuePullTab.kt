package com.wander.android.ui.screens.player

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.rememberHaptics
import kotlin.math.exp
import kotlinx.coroutines.launch

/**
 * The grab handle at the bottom of the player: drag it up and the queue comes with it.
 *
 * The queue used to be reachable only by the button in the top bar, which is a strange place to
 * keep "what is coming next" — the thing it opens rises from the bottom of the screen. This is the
 * gesture for it, and the button stays as the discoverable equivalent.
 *
 * ## Why the movement is not one-to-one
 *
 * The handle does not follow the finger. It follows an exponential approach to [MaxPull], so the
 * first millimetres move nearly one-to-one and the last barely move at all — the further you pull,
 * the harder it pulls back. This is the same shape Android's predictive back uses for its arrow,
 * and it exists to say two things at once: the gesture is live and it is bounded. Tracking the
 * finger exactly would offer to drag the handle to the top of the screen, which is not a thing it
 * can do; a fixed cap with no give would read as a stuck control.
 *
 * Past [OpenFraction] of that give, releasing opens the drawer. The handle springs home either way
 * rather than staying up: the sheet that opens is what continues the movement, so a handle left
 * hanging would be a second thing on screen mid-gesture.
 */
@Composable
internal fun QueuePullTab(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    contentAlpha: () -> Float = { 1f }
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // The resisted offset, in pixels, and the raw distance the finger has actually travelled.
    // Two values rather than one because the mapping between them is not invertible in a way worth
    // keeping: the raw total is what the resistance curve is a function of.
    val pull = remember { Animatable(0f) }
    val rawPull = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

    val maxPullPx = with(density) { MaxPull.toPx() }
    val openAtPx = maxPullPx * OpenFraction

    // Taken from the theme rather than hand-rolled, per the app's motion rules. The slow spatial
    // spec is the bouncy one — the handle should overshoot slightly coming home, which is the
    // "give" the gesture promised being paid back.
    val settleSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()

    val dragState = rememberDraggableState { delta ->
        // Up is negative in Compose's coordinates, and only up means anything here.
        rawPull.floatValue = (rawPull.floatValue - delta).coerceAtLeast(0f)
        scope.launch { pull.snapTo(resist(rawPull.floatValue, maxPullPx)) }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(TabHeight)
            .graphicsLayer { alpha = contentAlpha() }
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStopped = {
                    val opens = pull.value >= openAtPx
                    rawPull.floatValue = 0f
                    if (opens) {
                        haptics.confirmed()
                        onOpen()
                    }
                    pull.animateTo(0f, settleSpec)
                }
            )
            // The gesture is the point, but a gesture alone is unreachable with a screen reader or
            // a switch — so the handle is also an ordinary activatable control.
            .semantics {
                contentDescription = "Open the queue"
                onClick(label = "Open the queue") {
                    onOpen()
                    true
                }
            }
    ) {
        Box(
            modifier = Modifier
                .size(width = HandleWidth, height = HandleHeight)
                .graphicsLayer {
                    // Rises with the pull and stretches as it goes, so the handle reads as being
                    // under tension rather than merely displaced.
                    translationY = -pull.value
                    val strain = (pull.value / maxPullPx).coerceIn(0f, 1f)
                    scaleX = 1f + strain * HandleStretch
                    scaleY = 1f - strain * HandleThin
                }
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(percent = 50)
                )
        )
    }
}

/**
 * How far the handle has moved for a finger that has travelled [raw].
 *
 * An exponential approach to [max]: the derivative is 1 at zero and falls away towards it, so the
 * handle starts by tracking the finger and ends by resisting it, and never passes [max] however
 * hard it is pulled.
 */
private fun resist(raw: Float, max: Float): Float = max * (1f - exp(-raw / max))

/** The give in the handle. Not how far the drawer travels — that is the sheet's own animation. */
private val MaxPull = 72.dp

/** Past this much of [MaxPull], letting go opens the queue. */
private const val OpenFraction = 0.55f

/** Enough of a target to grab without reaching into the transport controls above it. */
private val TabHeight = 28.dp

private val HandleWidth = 40.dp
private val HandleHeight = 4.dp

/** At full pull the handle is this much wider and this much thinner. */
private const val HandleStretch = 0.5f
private const val HandleThin = 0.25f
