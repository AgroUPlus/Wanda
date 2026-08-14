package com.wander.android

import com.wander.android.ui.components.bucketFor
import com.wander.android.ui.components.memoryCacheKeyFor
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.pow

/**
 * Artwork decodes are bucketed so the same cover shown at slightly different sizes shares one
 * bitmap. Without this, a 52 dp row and a 56 dp row each held their own decode of the same image
 * and scrolling Home — which shows two sizes at once — re-decoded constantly.
 */
class ArtworkBucketTest {

    @Test
    fun `nearby sizes collapse onto one bucket`() {
        assertEquals(bucketFor(140), bucketFor(160))
        assertEquals(bucketFor(96), bucketFor(50))
    }

    @Test
    fun `bucket is never smaller than the requested size`() {
        listOf(1, 95, 96, 97, 300, 767, 768).forEach { requested ->
            assert(bucketFor(requested) >= requested) {
                "bucket ${bucketFor(requested)} is smaller than requested $requested"
            }
        }
    }

    @Test
    fun `oversized requests clamp to the largest bucket rather than growing unbounded`() {
        assertEquals(bucketFor(4000), bucketFor(100_000))
    }

    /**
     * The reason the ladder is fine-grained. A coarse one rounded a 140 dp card (420 px at 3×)
     * up to 768 px — 3.3× the pixels drawn — and one Home shelf then evicted the memory cache.
     */
    @Test
    fun `no request is rounded up to more than twice the pixels it needs`() {
        val realSizes = listOf(
            144, // 48 dp mini player at 3x
            156, // 52 dp track row at 3x
            420, // 140 dp home card at 3x
            480, // 160 dp album grid cell at 3x
            1080 // 360 dp player cover at 3x
        )
        realSizes.forEach { needed ->
            val wastedPixelRatio = bucketFor(needed).toDouble().pow(2) / needed.toDouble().pow(2)
            assert(wastedPixelRatio <= 2.0) {
                "$needed px decodes at ${bucketFor(needed)} px — ${"%.1f".format(wastedPixelRatio)}x the pixels"
            }
        }
    }

    @Test
    fun `distinct buckets produce distinct cache keys for the same url`() {
        val url = "https://example.test/cover.jpg"
        assertEquals("$url@96", memoryCacheKeyFor(url, 96))
        assert(memoryCacheKeyFor(url, 96) != memoryCacheKeyFor(url, 768))
    }
}
