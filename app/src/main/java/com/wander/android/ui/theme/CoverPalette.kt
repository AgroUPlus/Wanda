package com.wander.android.ui.theme

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
    val safeBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
    } else {
        bitmap
    }
    val palette = try {
        Palette.from(safeBitmap)
            .maximumColorCount(16)
            .generate()
    } catch (_: IllegalStateException) {
        // The bitmap was recycled under us — Coil's memory cache can let go of it at any time.
        // A missing seed colour just keeps the default theme.
        if (safeBitmap != bitmap) safeBitmap.recycle()
        return null
    } catch (_: IllegalArgumentException) {
        // An empty or zero-sized bitmap, which Palette cannot sample.
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

/** What the outermost [CoverTintedTheme] started from, for the ones nested inside it. */
private class TintRoot(val untinted: ColorScheme, val seed: Color?, val amoled: Boolean)

private val LocalTintRoot = staticCompositionLocalOf<TintRoot?> { null }

/**
 * Wraps [content] in a [MaterialExpressiveTheme] whose colour scheme smoothly transitions to
 * one seeded from [seedColor] whenever the playing track changes.
 *
 * Nested inside another (the Jam screen and the player sit inside the app's), it tints from the
 * same untinted scheme rather than [base]: tinting a tinted scheme again doubled the wash there,
 * which is why those screens once looked so much more alive than everything else. With no seed
 * of its own it keeps the outer one's, and it stays AMOLED black where the outer one is.
 */
@Composable
fun CoverTintedTheme(
    seedColor: Color?,
    base: ColorScheme,
    dark: Boolean,
    amoled: Boolean,
    content: @Composable () -> Unit,
) {
    val root = LocalTintRoot.current
    val untinted = root?.untinted ?: base
    val ownOrOuterSeed = seedColor ?: root?.seed
    val black = amoled || root?.amoled == true
    val motion = MaterialTheme.motionScheme
    val seed by animateColorAsState(
        ownOrOuterSeed ?: untinted.primary,
        motion.slowEffectsSpec(),
        label = "coverSeed",
    )
    val strength by animateFloatAsState(
        if (ownOrOuterSeed != null) 1f else 0f,
        motion.slowEffectsSpec(),
        label = "coverTintStrength",
    )

    val scheme = untinted.tintedByCover(seed, strength, dark)

    MaterialExpressiveTheme(
        colorScheme  = if (black && dark) scheme.toAmoled() else scheme,
        motionScheme = motion,
        shapes       = WandaShapes,
        typography   = WandaTypography,
    ) {
        CompositionLocalProvider(LocalTintRoot provides TintRoot(untinted, ownOrOuterSeed, black), content = content)
    }
}
