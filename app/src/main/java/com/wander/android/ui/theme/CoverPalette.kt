package com.wander.android.ui.theme

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowRgb565
import coil3.toBitmap

// ---------------------------------------------------------------------------
// CompositionLocal
// ---------------------------------------------------------------------------

/**
 * A seed [Color] derived from the currently-playing cover art. Null while no art is loaded,
 * which tells consumers to fall back to the app-wide scheme.
 *
 * Scoped to the Now Playing surface only — nothing outside that surface reads it. The value
 * is null by default so callers that don't wrap themselves in [CoverTintedTheme] are safe.
 */
val LocalCoverSeedColor = compositionLocalOf<Color?> { null }

// ---------------------------------------------------------------------------
// Bitmap loading + colour extraction
// ---------------------------------------------------------------------------

/**
 * Loads [url] through Coil at a small size (128 px — enough for palette accuracy, cheap to
 * decode) and extracts a seed colour using [Palette]. Returns null until the image arrives or
 * if the palette is empty (solid-black cover, etc.).
 *
 * The result is stable across recompositions for the same URL; a new URL resets to null then
 * resolves to the new cover's colour.
 */
@Composable
fun rememberCoverSeedColor(url: String?): Color? {
    val context = LocalContext.current
    var seedColor by remember(url) { mutableStateOf<Color?>(null) }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            seedColor = null
            return@LaunchedEffect
        }
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(128)          // palette accuracy is fine at 128 px
            .allowRgb565(false) // palette needs full RGB
            .build()
        val result = loader.execute(request)
        if (result is SuccessResult) {
            val bitmap = result.image.toBitmap()
            seedColor = extractSeedColor(bitmap)
        }
        loader.shutdown()
    }

    return seedColor
}

/**
 * Extracts a dominant seed colour from [bitmap] using [Palette], picking in priority order:
 * vibrant → muted → dominant. Returns null if the palette is empty.
 */
fun extractSeedColor(bitmap: Bitmap): Color? {
    val palette = Palette.from(bitmap)
        .maximumColorCount(16)
        .generate()
    val argb = palette.getVibrantColor(
        palette.getMutedColor(
            palette.getDominantColor(0)
        )
    )
    return if (argb == 0) null else Color(argb)
}

// ---------------------------------------------------------------------------
// Scheme derivation
// ---------------------------------------------------------------------------

/**
 * Derives a full M3 [ColorScheme] seeded from [seed], respecting [dark] and [amoled].
 *
 * Uses tonal blending — the result harmonises with the cover art without being a direct pixel
 * sample: the accent is tinted by the art colour, not lifted verbatim from it.
 *
 * When [seed] is null the function returns null so callers can fall back to the app scheme.
 */
@Stable
fun coverColorScheme(seed: Color?, dark: Boolean, amoled: Boolean): ColorScheme? {
    seed ?: return null

    val scheme = if (dark) {
        darkColorScheme(
            primary              = seed.harmonise(0.6f, dark = true),
            onPrimary            = Color.Black,
            primaryContainer     = seed.harmonise(0.25f, dark = true),
            onPrimaryContainer   = seed.harmonise(0.9f, dark = true),
            secondary            = seed.harmonise(0.4f, dark = true),
            onSecondary          = Color.Black,
            secondaryContainer   = seed.harmonise(0.2f, dark = true),
            onSecondaryContainer = seed.harmonise(0.85f, dark = true),
        )
    } else {
        lightColorScheme(
            primary              = seed.harmonise(0.5f, dark = false),
            onPrimary            = Color.White,
            primaryContainer     = seed.harmonise(0.9f, dark = false),
            onPrimaryContainer   = seed.harmonise(0.1f, dark = false),
            secondary            = seed.harmonise(0.4f, dark = false),
            onSecondary          = Color.White,
            secondaryContainer   = seed.harmonise(0.85f, dark = false),
            onSecondaryContainer = seed.harmonise(0.15f, dark = false),
        )
    }

    return if (amoled && dark) scheme.toAmoled() else scheme
}

/**
 * Blends [this] colour toward white (light) or black (dark) by [factor] to produce a tonal
 * variant. Gives a convincing Material-ish result without the full MCU library.
 */
private fun Color.harmonise(factor: Float, dark: Boolean): Color {
    val target = if (dark) Color.White else Color.Black
    return Color(
        red   = red   + (target.red   - red)   * factor,
        green = green + (target.green - green) * factor,
        blue  = blue  + (target.blue  - blue)  * factor,
        alpha = 1f
    )
}

/**
 * Pins the darkest surfaces to true black for OLED panels.
 * Internal so [Theme.kt] can also call it from [WanderTheme].
 */
internal fun ColorScheme.toAmoled(): ColorScheme = copy(
    background              = Color.Black,
    surface                 = Color.Black,
    surfaceDim              = Color.Black,
    surfaceContainerLowest  = Color.Black,
    surfaceContainerLow     = Color(0xFF0A0A0A),
    surfaceContainer        = Color(0xFF121212),
    surfaceContainerHigh    = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222)
)

// ---------------------------------------------------------------------------
// Animated scoped theme
// ---------------------------------------------------------------------------

/**
 * Wraps [content] in a [MaterialExpressiveTheme] whose colour scheme smoothly transitions to
 * one seeded from [seedColor] whenever the playing track changes.
 *
 * - When [seedColor] is null the [base] scheme is used unchanged.
 * - The transition is a 600 ms ease-out so it follows the cover crossfade.
 * - Only primary/secondary roles are tinted — neutral surfaces stay neutral so the cover
 *   itself remains the visual focus, not the chrome.
 */
@Composable
fun CoverTintedTheme(
    seedColor: Color?,
    base: ColorScheme,
    dark: Boolean,
    amoled: Boolean,
    content: @Composable () -> Unit,
) {
    val target = remember(seedColor, dark, amoled) {
        coverColorScheme(seedColor, dark, amoled) ?: base
    }

    val primary              by animateColorAsState(target.primary,              tween(600), label = "primary")
    val onPrimary            by animateColorAsState(target.onPrimary,            tween(600), label = "onPrimary")
    val primaryContainer     by animateColorAsState(target.primaryContainer,     tween(600), label = "primaryContainer")
    val onPrimaryContainer   by animateColorAsState(target.onPrimaryContainer,   tween(600), label = "onPrimaryContainer")
    val secondary            by animateColorAsState(target.secondary,            tween(600), label = "secondary")
    val onSecondary          by animateColorAsState(target.onSecondary,          tween(600), label = "onSecondary")
    val secondaryContainer   by animateColorAsState(target.secondaryContainer,   tween(600), label = "secondaryContainer")
    val onSecondaryContainer by animateColorAsState(target.onSecondaryContainer, tween(600), label = "onSecondaryContainer")

    MaterialExpressiveTheme(
        colorScheme = base.copy(
            primary              = primary,
            onPrimary            = onPrimary,
            primaryContainer     = primaryContainer,
            onPrimaryContainer   = onPrimaryContainer,
            secondary            = secondary,
            onSecondary          = onSecondary,
            secondaryContainer   = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
        ),
        motionScheme = MotionScheme.expressive(),
        shapes       = WandaShapes,
        typography   = WandaTypography,
        content      = content,
    )
}
