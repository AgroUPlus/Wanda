package com.wander.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 Expressive. `MotionScheme.expressive()` is what makes every built-in component
 * animate with the springy, overshooting motion the design system calls for — screens should
 * pull their own animation specs from `MaterialTheme.motionScheme` rather than hand-rolling
 * spring constants. That's also what makes [reduceMotion] cheap: swapping the whole scheme out
 * for [NoMotionScheme] here covers nearly every animation in the app in one place, rather than
 * needing a check in each of them — see [LocalReducedMotion] for the couple of exceptions that
 * hand-roll a spec and check it directly.
 */
@Composable
fun WanderTheme(
    dynamicColor: Boolean = true,
    amoledBlack: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** The in-app "Reduce motion" setting. Or-ed with the phone's own accessibility setting. */
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val supportsMonet = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val effectiveReduceMotion = reduceMotion || rememberSystemAnimationsDisabled()

    val scheme = when {
        dynamicColor && supportsMonet && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && supportsMonet -> dynamicLightColorScheme(context)
        darkTheme -> WandaDarkScheme
        else -> WandaLightScheme
    }

    MaterialExpressiveTheme(
        colorScheme = if (darkTheme && amoledBlack) scheme.toAmoled() else scheme,
        motionScheme = if (effectiveReduceMotion) NoMotionScheme else MotionScheme.expressive(),
        shapes = WandaShapes,
        typography = WandaTypography
    ) {
        CompositionLocalProvider(LocalReducedMotion provides effectiveReduceMotion, content = content)
    }
}
