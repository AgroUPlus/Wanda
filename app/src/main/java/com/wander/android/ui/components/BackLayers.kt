package com.wander.android.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.util.lerp

/**
 * A page opened inside a screen — a conversation over the inbox, a genre over the genre list —
 * laid over that screen rather than swapped for it.
 *
 * [base] stays composed underneath, so its scroll and state survive and a back swipe can show it:
 * the page shrinks into a card leaning off the swiped edge ([backMorph]) while the base, dimmed
 * and stepped back behind it, comes forward. Committing carries the page out from that card;
 * abandoning springs it back. Back on the page calls [onBack], which should set [page] to null.
 *
 * While the page fully covers it, the base is not drawn, so an open page costs nothing extra.
 */
@Composable
fun <T : Any> BackLayers(
    page: T?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    base: @Composable () -> Unit,
    content: @Composable (T) -> Unit
) {
    val transition = updateTransition(page, label = "backLayers")
    val motion = MaterialTheme.motionScheme
    val depth by transition.animateFloat(
        transitionSpec = { motion.defaultEffectsSpec() },
        label = "backLayersDepth"
    ) { if (it != null) 1f else 0f }
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = BASE_DIM)
    val open = page != null
    val back = remember { PredictiveBackState() }
    val covered by remember {
        derivedStateOf { transition.targetState != null && !transition.isRunning && back.swipe.value == 0f }
    }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (open) Modifier.clearAndSetSemantics {} else Modifier)
                // Hidden under an opaque page, the base need not be drawn at all.
                .graphicsLayer {
                    alpha = if (covered) 0f else 1f
                    // Stepped back behind the page; a back swipe brings it forward.
                    val scale = lerp(1f, BASE_SCALE, depth * (1f - back.progress))
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            // Hidden under a page, the base's covers must not answer for the page's: one key
            // on screen per destination (see `sharedArtwork`).
            val destination = LocalDestinationScope.current
            CompositionLocalProvider(LocalDestinationScope provides destination.takeUnless { open }, content = base)
        }

        // Registered after the base, so a handler inside the base never outranks the page's.
        PredictiveBack(back, enabled = open, onBack = onBack)
        // The dim is its own layer over the base, so the base's own drawing is untouched.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    val amount = depth * (1f - back.progress * BACK_GIVE)
                    if (amount > 0f) drawRect(scrim, alpha = amount)
                }
        )

        transition.AnimatedContent(
            transitionSpec = { pageTransition(motion) },
            contentKey = { it }
        ) { shown ->
            if (shown != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .backMorph(back)
                        .background(MaterialTheme.colorScheme.background)
                        // The page is opaque; touches must not reach the base through it.
                        .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } }
                ) { content(shown) }
            }
        }
    }
}

/** A page opens out of the base and leaves into it; one page replacing another cross-fades. */
private fun <T : Any> AnimatedContentTransitionScope<T?>.pageTransition(motion: MotionScheme): ContentTransform {
    val spatial = motion.defaultSpatialSpec<Float>()
    val effects = motion.defaultEffectsSpec<Float>()
    return when {
        initialState == null -> (scaleIn(spatial, PAGE_SCALE) + fadeIn(effects)) togetherWith ExitTransition.None
        targetState == null -> EnterTransition.None togetherWith (scaleOut(spatial, PAGE_SCALE) + fadeOut(effects))
        else -> fadeIn(effects) togetherWith fadeOut(effects)
    }
}

/** How dark the base gets under an open page, as the scrim's alpha. */
private const val BASE_DIM = 0.32f

/** Share of the base's dim a full back swipe hands back before it commits. */
private const val BACK_GIVE = 0.5f

/** How far the base steps back under an open page. */
private const val BASE_SCALE = 0.94f

/** The scale a page opens from and leaves to. */
private const val PAGE_SCALE = 0.92f

/**
 * [value] while [live], and the last live value once it stops being: a page on its way out of
 * [BackLayers] keeps showing what it had, rather than going blank under the finger as the state
 * behind it clears.
 */
@Composable
fun <V> rememberLastLive(value: V, live: Boolean): V {
    val holder = remember { LastLive(value) }
    // A plain field, not state: rewriting it never schedules a recomposition of its own.
    if (live) holder.value = value
    return holder.value
}

private class LastLive<V>(var value: V)
