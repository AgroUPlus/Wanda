package com.wander.android.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * A predictive back swipe on an in-screen page (a thread, a genre, an importer step), as the page
 * shrinking into a rounded card that leans away from the swiped edge ([backMorph]).
 *
 * A committed swipe leaves [progress] where the finger took it, so the page's own exit animation
 * carries on from the card instead of jumping back to full size first. An abandoned one springs
 * back. Each new swipe starts whole.
 */
@Stable
class PredictiveBackState {
    internal val swipe = Animatable(0f)

    /** The swipe's progress, eased the same way as every other back gesture ([backResistance]). */
    val progress: Float get() = backResistance(swipe.value)

    /** Which way the last swipe leans the page; see [backLean]. */
    var lean by mutableFloatStateOf(0f)
        internal set

    /** A finger is on the gesture right now. False once it commits or is abandoned. */
    var swiping by mutableStateOf(false)
        internal set
}

/**
 * Registers a predictive back handler for as long as [enabled], calling [onBack] when the swipe
 * commits. Handlers composed later (a page's own) take the gesture first, the platform's rule.
 */
@Composable
fun rememberPredictiveBack(enabled: Boolean, onBack: () -> Unit): PredictiveBackState {
    val state = remember { PredictiveBackState() }
    PredictiveBack(state, enabled, onBack)
    return state
}

/**
 * The handler half of [rememberPredictiveBack], for a caller that needs [state] before the point
 * where the handler should be registered (handlers registered later win).
 */
@Composable
fun PredictiveBack(state: PredictiveBackState, enabled: Boolean, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val settleSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val currentOnBack by rememberUpdatedState(onBack)
    // Whatever the last committed swipe left, the next page opens whole.
    LaunchedEffect(enabled) { if (enabled && !state.swiping) state.swipe.snapTo(0f) }
    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { event ->
                state.swiping = true
                state.lean = backLean(event.swipeEdge)
                state.swipe.snapTo(event.progress)
            }
            state.swiping = false
            currentOnBack()
        } catch (_: CancellationException) {
            state.swiping = false
            // This coroutine is already cancelled, so the settle runs on the composition's scope.
            scope.launch { state.swipe.animateTo(0f, settleSpec) }
        }
    }
}

/** [backMorph] driven by a [PredictiveBackState]. */
fun Modifier.backMorph(state: PredictiveBackState): Modifier = backMorph({ state.progress }, { state.lean })

/**
 * A full-size surface giving way to what is under it: as [morph] goes 0 to 1 it shrinks into a
 * rounded, shadowed card and leans toward the side the swipe pulls it ([lean]). It stays opaque
 * the whole way, so it never reads as a ghost of itself over the page coming back.
 */
fun Modifier.backMorph(morph: () -> Float, lean: () -> Float): Modifier = graphicsLayer {
    val amount = morph().coerceIn(0f, 1f)
    val scale = lerp(1f, MORPH_SCALE, amount)
    scaleX = scale
    scaleY = scale
    translationX = lean() * amount * size.width * MORPH_SHIFT
    if (amount > 0f) {
        shape = RoundedCornerShape(MORPH_CORNER * amount)
        clip = true
        shadowElevation = MORPH_ELEVATION.toPx() * amount
    } else {
        shape = RectangleShape
        clip = false
        shadowElevation = 0f
    }
}

/**
 * Which way a swipe from [swipeEdge] pulls a surface: toward the middle, so away from the left
 * edge is right (1) and away from the right edge is left (-1). A swipe from no edge does not lean.
 */
fun backLean(swipeEdge: Int): Float = when (swipeEdge) {
    BackEventCompat.EDGE_LEFT -> 1f
    BackEventCompat.EDGE_RIGHT -> -1f
    else -> 0f
}

/** How small a surface gets at the far end of its morph. */
private const val MORPH_SCALE = 0.85f

/** How far a surface leans, as a share of its width, at the far end of its morph. */
private const val MORPH_SHIFT = 0.06f

/** A surface's corner radius at the far end of its morph. */
private val MORPH_CORNER = 32.dp

/** How high the card lifts off what is under it at the far end of its morph. */
private val MORPH_ELEVATION = 8.dp
