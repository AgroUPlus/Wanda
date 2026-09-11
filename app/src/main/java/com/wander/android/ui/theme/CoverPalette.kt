package com.wander.android.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.allowRgb565
import coil3.request.bitmapConfig
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
 * Disallows hardware bitmaps in the Coil request and converts hardware bitmaps to software
 * copies if needed, preventing `IllegalStateException: pixel access is not supported on Config#HARDWARE`.
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
            .size(128)                             // palette accuracy is fine at 128 px
            .allowHardware(false)                  // Palette requires software pixels
            .allowRgb565(false)                    // palette needs full RGB
            .bitmapConfig(Bitmap.Config.ARGB_8888) // explicit software config
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
 *
 * Safely converts [Config#HARDWARE] bitmaps to software bitmaps to avoid crashes on Android 8+.
 */
fun extractSeedColor(bitmap: Bitmap): Color? {
    val safeBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
    } else {
        bitmap
    }
    val palette = try {
        Palette.from(safeBitmap)
            .maximumColorCount(16)
            .generate()
    } catch (_: Throwable) {
        if (safeBitmap != bitmap) safeBitmap.recycle()
        return null
    }
    val argb = palette.getVibrantColor(
        palette.getMutedColor(
            palette.getDominantColor(0)
        )
    )
    if (safeBitmap != bitmap) {
        safeBitmap.recycle()
    }
    return if (argb == 0) null else Color(argb)
}

// ---------------------------------------------------------------------------
// Scheme derivation
// ---------------------------------------------------------------------------

/**
 * Returns [this] scheme re-coloured around [seed], by [strength] (0 = untouched, 1 = fully
 * applied), respecting [dark].
 *
 * Two intensities, on purpose:
 *
 * - **Accent roles** (primary/secondary/tertiary and their containers) are *derived* from the
 *   seed by tonal blending, so buttons, active states and highlights take the cover's colour.
 * - **Neutral roles** (surfaces, background, outlines, `onSurfaceVariant`) are only *washed*
 *   toward it by a few percent. That is what carries the tint across the rest of the interface —
 *   text, dividers, sheets, cards — without turning the chrome into a second album cover. The
 *   cover stays the focus.
 *
 * Tinting only the accents was the earlier behaviour, and it left everything drawn with a neutral
 * role — which is most of the player — visibly unaffected.
 */
@Stable
fun ColorScheme.tintedByCover(seed: Color, strength: Float, dark: Boolean): ColorScheme {
    if (strength <= 0f) return this

    // Blends toward the seed's tonal variant: an accent that reads as the cover's colour.
    fun accent(from: Color, factor: Float) = lerp(from, seed.harmonise(factor, dark), strength)
    // Barely moves: a wash of the seed over a neutral.
    fun wash(from: Color, amount: Float) = lerp(from, seed, amount * strength)

    val onAccent = if (dark) Color.Black else Color.White
    return copy(
        primary              = accent(primary,              if (dark) 0.60f else 0.50f),
        onPrimary            = lerp(onPrimary, onAccent, strength),
        primaryContainer     = accent(primaryContainer,     if (dark) 0.25f else 0.90f),
        onPrimaryContainer   = accent(onPrimaryContainer,   if (dark) 0.90f else 0.10f),
        secondary            = accent(secondary,            0.40f),
        onSecondary          = lerp(onSecondary, onAccent, strength),
        secondaryContainer   = accent(secondaryContainer,   if (dark) 0.20f else 0.85f),
        onSecondaryContainer = accent(onSecondaryContainer, if (dark) 0.85f else 0.15f),
        tertiary             = accent(tertiary,             if (dark) 0.50f else 0.60f),
        onTertiary           = lerp(onTertiary, onAccent, strength),
        tertiaryContainer    = accent(tertiaryContainer,    if (dark) 0.30f else 0.88f),
        onTertiaryContainer  = accent(onTertiaryContainer,  if (dark) 0.88f else 0.12f),

        background              = wash(background,              0.06f),
        onBackground            = wash(onBackground,            0.05f),
        surface                 = wash(surface,                 0.06f),
        onSurface               = wash(onSurface,               0.05f),
        surfaceVariant          = wash(surfaceVariant,          0.10f),
        onSurfaceVariant        = wash(onSurfaceVariant,        0.12f),
        surfaceDim              = wash(surfaceDim,              0.08f),
        surfaceBright           = wash(surfaceBright,           0.08f),
        surfaceContainerLowest  = wash(surfaceContainerLowest,  0.08f),
        surfaceContainerLow     = wash(surfaceContainerLow,     0.08f),
        surfaceContainer        = wash(surfaceContainer,        0.08f),
        surfaceContainerHigh    = wash(surfaceContainerHigh,    0.08f),
        surfaceContainerHighest = wash(surfaceContainerHighest, 0.08f),
        outline                 = wash(outline,                 0.14f),
        outlineVariant          = wash(outlineVariant,          0.12f),
        surfaceTint             = accent(surfaceTint,           if (dark) 0.60f else 0.50f),
    )
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
 * The seed itself is animated, and the scheme derived from the animated value, rather than each
 * role being animated separately — one 600 ms ease-out (matching the cover crossfade) then covers
 * every role [tintedByCover] touches, and adding a role later needs no second edit here.
 *
 * [strength] falls to 0 when [seedColor] is null, so turning the setting off or losing the art
 * fades back to [base] instead of cutting.
 *
 * When [amoled] and [dark], the darkest surfaces are pinned back to true black afterwards: the
 * wash must not lift an OLED panel off black.
 */
@Composable
fun CoverTintedTheme(
    seedColor: Color?,
    base: ColorScheme,
    dark: Boolean,
    amoled: Boolean,
    content: @Composable () -> Unit,
) {
    val seed by animateColorAsState(
        seedColor ?: base.primary,
        tween(600),
        label = "coverSeed",
    )
    val strength by animateFloatAsState(
        if (seedColor != null) 1f else 0f,
        tween(600),
        label = "coverTintStrength",
    )

    val scheme = base.tintedByCover(seed, strength, dark)

    MaterialExpressiveTheme(
        colorScheme  = if (amoled && dark) scheme.toAmoled() else scheme,
        motionScheme = MotionScheme.expressive(),
        shapes       = WandaShapes,
        typography   = WandaTypography,
        content      = content,
    )
}
