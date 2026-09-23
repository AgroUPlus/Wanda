package com.wander.android.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** How much content blurs at full strength. */
private val MaxBlurRadius = 28.dp

/** How dark the pre-API-31 scrim fallback gets at full strength. */
private const val MaxScrimAlpha = 0.5f

/**
 * Whether back gestures blur what they reveal — the Look & feel toggle, provided once by the
 * shell. Every caller of [backdropBlur] reads it, so switching it off drops the effect everywhere.
 */
val LocalBackBlurEnabled = compositionLocalOf { true }

/**
 * Blurs (or, below API 31, just dims) whatever this is applied to by [amount], 0 (sharp) to 1
 * (fully blurred) — read at draw time, so a moving amount redraws without recomposing.
 *
 * Callers pick the amount so it is strongest while something covers this content and clears as
 * that thing leaves: a back swipe, a drag or a settle animation all read the same way, starting
 * blurred and sharpening continuously rather than snapping when the gesture commits. At 0 the
 * effect is dropped entirely, so an idle screen pays nothing for it.
 *
 * `RenderEffect.createBlurEffect` needs API 31; minSdk here is 26, so 26-30 falls back to a plain
 * scrim instead of silently doing nothing.
 */
fun Modifier.backdropBlur(enabled: Boolean, amount: () -> Float): Modifier = when {
    !enabled -> this
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> graphicsLayer {
        val a = amount().coerceIn(0f, 1f)
        renderEffect = if (a > 0f) {
            val radius = MaxBlurRadius.toPx() * a
            RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        } else {
            null
        }
    }
    else -> drawWithContent {
        drawContent()
        val a = amount().coerceIn(0f, 1f)
        if (a > 0f) drawRect(Color.Black.copy(alpha = a * MaxScrimAlpha))
    }
}

/**
 * Everything currently asking for the app behind it to blur — open sheets, the lyrics — so the
 * shell can blur once, by the strongest of them, rather than each overlay reaching into a tree it
 * isn't part of. Sheets and dialogs are their own windows, but composition locals still reach
 * them, which is what lets them register here.
 */
@Stable
class BackdropBlurController {
    private val sources = mutableStateListOf<() -> Float>()

    fun amount(): Float = sources.maxOfOrNull { it() } ?: 0f

    internal fun add(source: () -> Float) = sources.add(source)
    internal fun remove(source: () -> Float) = sources.remove(source)
}

val LocalBackdropBlur = staticCompositionLocalOf<BackdropBlurController?> { null }

/** Registers [amount] with the shell's [BackdropBlurController] for as long as this is composed. */
@Composable
fun BackdropBlurSource(amount: () -> Float) {
    val controller = LocalBackdropBlur.current ?: return
    val current by rememberUpdatedState(amount)
    DisposableEffect(controller) {
        val source = { current() }
        controller.add(source)
        onDispose { controller.remove(source) }
    }
}

/**
 * Blurs an animated destination while it is still arriving — fully at the start of its enter,
 * sharp once visible. A predictive back seeks that enter with the finger, so the screen being
 * revealed sharpens exactly as far as the swipe has gone.
 */
@Composable
fun Modifier.enteringBlur(scope: AnimatedVisibilityScope): Modifier {
    val blur = scope.transition.animateFloat(label = "enteringBlur") { state ->
        if (state == EnterExitState.PreEnter) 1f else 0f
    }
    return backdropBlur(LocalBackBlurEnabled.current) { blur.value }
}
