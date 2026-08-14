package com.wander.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 Expressive. `MotionScheme.expressive()` is what makes every built-in component
 * animate with the springy, overshooting motion the design system calls for — screens should
 * pull their own animation specs from `MaterialTheme.motionScheme` rather than hand-rolling
 * spring constants.
 */
@Composable
fun WanderTheme(
    dynamicColor: Boolean = true,
    amoledBlack: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val supportsMonet = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val scheme = when {
        dynamicColor && supportsMonet && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && supportsMonet -> dynamicLightColorScheme(context)
        darkTheme -> WandaDarkScheme
        else -> WandaLightScheme
    }

    MaterialExpressiveTheme(
        colorScheme = if (darkTheme && amoledBlack) scheme.toAmoled() else scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = WandaShapes,
        typography = WandaTypography,
        content = content
    )
}

/**
 * Pins the darkest surfaces to true black. On an OLED panel those pixels are switched off
 * entirely, so a dark UI genuinely costs less battery rather than just looking like it should.
 */
private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222)
)
