package com.wander.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil3.request.ImageRequest
import coil3.request.allowRgb565
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Size
import kotlin.math.roundToInt

/**
 * The decode sizes artwork is allowed to have. Every request rounds up to one of these, so covers
 * shown at slightly different sizes share a decode instead of each holding their own bitmap.
 *
 * The steps are ~1.35× apart rather than 2×. A coarse ladder rounded a 140 dp card (420 px at 3×)
 * all the way up to 768 px — 3.3× the pixels actually drawn, 2.4 MB a card — so a single Home
 * shelf could evict the rest of the memory cache and force a re-decode on every scroll back.
 */
private val SizeBuckets = intArrayOf(96, 128, 192, 256, 384, 512, 768, 1024)

/**
 * At or below this, RGB565 halves the memory and is indistinguishable on opaque album art. Only
 * the full-screen player cover decodes above it, where banding in a gradient could show.
 */
private const val Rgb565Ceiling = 512

internal fun bucketFor(pixels: Int): Int =
    SizeBuckets.firstOrNull { it >= pixels } ?: SizeBuckets.last()

private fun nextBucketUp(bucket: Int): Int? =
    SizeBuckets.firstOrNull { it > bucket }

internal fun memoryCacheKeyFor(url: String, bucket: Int): String = "$url@$bucket"

/**
 * Builds the request for a piece of album art at [sizeDp].
 *
 * Sizing is explicit rather than measured from the layout node so the decode starts on the first
 * composition instead of after layout, and so the bucket — not the exact node size — is what ends
 * up in the cache key. [crossfade] is opt-in: list thumbnails must not animate.
 */
@Composable
internal fun rememberArtworkRequest(
    url: String,
    sizeDp: Dp,
    crossfade: Boolean
): ImageRequest {
    val context = LocalContext.current
    val density = LocalDensity.current
    val bucket = remember(sizeDp, density) {
        bucketFor(with(density) { sizeDp.toPx() }.roundToInt())
    }

    return remember(url, bucket, crossfade, context) {
        ImageRequest.Builder(context)
            .data(url)
            .size(Size(bucket, bucket))
            // EXACT, not INEXACT: inexact only subsamples by powers of two, so a 1500 px cover
            // asked for at 512 came back at 750 px. The bucket has to be a real ceiling for the
            // memory budget above to mean anything.
            .precision(Precision.EXACT)
            .allowRgb565(bucket <= Rgb565Ceiling)
            .crossfade(crossfade)
            .memoryCacheKey(memoryCacheKeyFor(url, bucket))
            // If a larger copy of this cover is already decoded — the player art while the
            // mini player is on screen, say — show it immediately rather than a blank slot.
            .apply {
                nextBucketUp(bucket)?.let { larger ->
                    placeholderMemoryCacheKey(memoryCacheKeyFor(url, larger))
                }
            }
            .build()
    }
}
