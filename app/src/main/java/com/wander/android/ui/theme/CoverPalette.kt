package com.wander.android.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
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

private val seedColorCache = java.util.Collections.synchronizedMap(
    object : java.util.LinkedHashMap<String, Color>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Color>?): Boolean = size > 24
    }
)

/**
 * Loads [url] through Coil at a small size (128 px — enough for palette accuracy, cheap to
 * decode) and extracts a seed colour using [Palette]. Returns null until the image arrives or
 * if the palette is empty (solid-black cover, etc.).
 *
 * Caches extracted seeds in an LRU memory cache so when the player is expanded, the colour scheme
 * applies instantly without falling back to default theme during the decode.
 */
@Composable
fun rememberCoverSeedColor(url: String?): Color? {
    val context = LocalContext.current
    var seedColor by remember { mutableStateOf(url?.let { seedColorCache[it] }) }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            seedColor = null
            return@LaunchedEffect
        }
        val cached = seedColorCache[url]
        if (cached != null) {
            seedColor = cached
            return@LaunchedEffect
        }
        val loader = SingletonImageLoader.get(context)
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(128)
            .allowHardware(false)
            .allowRgb565(false)
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            .build()
        val result = loader.execute(request)
        if (result is SuccessResult) {
            val bitmap = result.image.toBitmap()
            val extracted = extractSeedColor(bitmap)
            if (extracted != null) {
                seedColorCache[url] = extracted
                seedColor = extracted
            }
        }
    }

    val animated by animateColorAsState(
        targetValue = seedColor ?: Color.Transparent,
        animationSpec = tween(SeedTweenMs),
        label = "coverSeed"
    )
    return if (seedColor == null) null else animated
}

/** Long enough to read as a crossfade, short enough to have finished before the cover settles. */
private const val SeedTweenMs = 450

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
// Animated scoped theme
// ---------------------------------------------------------------------------

/**
 * Wraps [content] in a [MaterialExpressiveTheme] whose colour scheme smoothly transitions to
 * one seeded from [seedColor] whenever the playing track changes.
 */
@Composable
fun CoverTintedTheme(
    seedColor: Color?,
    base: ColorScheme,
    dark: Boolean,
    amoled: Boolean,
    content: @Composable () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val seed by animateColorAsState(
        seedColor ?: base.primary,
        motion.slowEffectsSpec(),
        label = "coverSeed",
    )
    val strength by animateFloatAsState(
        if (seedColor != null) 1f else 0f,
        motion.slowEffectsSpec(),
        label = "coverTintStrength",
    )

    val scheme = base.tintedByCover(seed, strength, dark)

    MaterialExpressiveTheme(
        colorScheme  = if (amoled && dark) scheme.toAmoled() else scheme,
        motionScheme = motion,
        shapes       = WandaShapes,
        typography   = WandaTypography,
        content      = content,
    )
}
