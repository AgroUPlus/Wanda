package com.wander.android.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Whether motion should be suppressed app-wide — the in-app "Reduce motion" toggle, or-ed with
 * [rememberSystemAnimationsDisabled]. Provided once, by [com.wander.android.ui.theme.WanderTheme],
 * for the handful of animations that don't route through `MaterialTheme.motionScheme` (see
 * [NoMotionScheme]) and so aren't already covered by swapping it out.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * Android has no dedicated "prefers reduced motion" flag the way iOS does. Accessibility settings'
 * "Remove animations" toggle (and the same developer-options animation scale) is backed by
 * [Settings.Global.ANIMATOR_DURATION_SCALE] going to zero — that's the real, documented signal,
 * so it's the one read here rather than guessed at.
 *
 * Registers a [ContentObserver] so flipping the setting and returning to the app updates this
 * live, rather than only on the next cold start.
 */
@Composable
fun rememberSystemAnimationsDisabled(): Boolean {
    val context = LocalContext.current
    val resolver = context.contentResolver

    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    var disabled by remember { mutableStateOf(read()) }

    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                disabled = read()
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }

    return disabled
}

/**
 * Every [MotionScheme] spec, as close to instant as a spec can be. Everything that already pulls
 * its animation spec from `MaterialTheme.motionScheme` — which is nearly everything in this app;
 * see the doc comment on `WanderTheme` — is covered by swapping this in for
 * [MotionScheme.expressive] for free: a switch still flips, a card still presses down and back,
 * just without an eased duration. That is "reduce motion", not "remove feedback".
 *
 * **Not `snap()`.** Predictive back's live preview works by *seeking* into the nav graph's
 * `popEnterTransition`/`popExitTransition` spec at whatever fraction the gesture has reached —
 * `NavTransitions` builds those from this same scheme. `snap()` has no curve to seek into: queried
 * at any fraction it just reports "already at the target", so the preview stopped tracking the
 * finger and the gesture read as broken rather than merely unanimated. A `tween` seeks correctly
 * at any duration, including a practically-imperceptible one — 1 ms is not "some motion left in",
 * it is the shortest duration that is still a real curve rather than a single point.
 *
 * The few animations that hand-roll their own spec instead of reading the theme — `CardMotion.kt`'s
 * entrance pop, `TravelingHighlight.kt`'s glide — don't go through this and check
 * [LocalReducedMotion] directly.
 */
internal object NoMotionScheme : MotionScheme {
    private fun <T> instant(): FiniteAnimationSpec<T> = tween(durationMillis = 1)

    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = instant()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = instant()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = instant()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = instant()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = instant()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = instant()
}
