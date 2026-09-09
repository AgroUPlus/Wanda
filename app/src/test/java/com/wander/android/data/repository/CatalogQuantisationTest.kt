package com.wander.android.data.repository

import com.wander.android.data.repository.CatalogSyncRepository.Companion.quantiseToHex
import com.wander.android.data.repository.CatalogSyncRepository.Companion.unpackHex
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wire format the catalogue trades in.
 *
 * Embeddings are stored as float32 and sent as int8, which is the only lossy step in the whole
 * trade. What has to survive it is the comparison: a track published by one device must still
 * match the same track on another, at the same threshold both ends use to decide.
 */
class CatalogQuantisationTest {

    private val dim = 128

    /** A 32-bit finaliser, so one changed input bit changes half the output bits. */
    private fun mix(x: Int): Int {
        var h = x
        h = h xor (h ushr 16); h *= 0x7feb352d.toInt()
        h = h xor (h ushr 15); h *= 0x846ca68b.toInt()
        h = h xor (h ushr 16)
        return h
    }

    /** L2-normalised vectors, as `AudioEmbedder` produces them. */
    private fun embedding(seed: Int, segments: Int = 40): Array<FloatArray> =
        Array(segments) { segment ->
            val v = FloatArray(dim) { d ->
                (mix(seed xor mix(segment xor mix(d))).toFloat() / Int.MAX_VALUE) * 0.5f
            }
            val norm = sqrt(v.fold(0f) { acc, x -> acc + x * x })
            FloatArray(dim) { v[it] / norm }
        }

    private fun cosineSequence(a: Array<FloatArray>, b: Array<FloatArray>): Float {
        var total = 0f
        for (i in a.indices) {
            var s = 0f
            for (d in 0 until dim) s += a[i][d] * b[i][d]
            total += s
        }
        return total / a.size
    }

    @Test
    fun quantisingToInt8DoesNotCostAMatch() {
        val original = embedding(1)
        val roundTripped = unpackHex(quantiseToHex(original), dim)!!

        // The client's own match threshold. Below this, a published track would stop being
        // recognisable as itself once it came back.
        assertTrue(
            "int8 round trip scored ${cosineSequence(original, roundTripped)}",
            cosineSequence(original, roundTripped) >= 0.88f
        )
    }

    @Test
    fun theRoundTripPreservesShape() {
        val original = embedding(2, segments = 7)
        val roundTripped = unpackHex(quantiseToHex(original), dim)!!

        assertEquals(7, roundTripped.size)
        assertEquals(dim, roundTripped[0].size)
    }

    @Test
    fun aBlobThatIsNotWholeVectorsIsRefused() {
        // A client that changed `dim` without changing its packing. Better to drop the entry than
        // to reinterpret somebody else's bytes as vectors.
        val hex = quantiseToHex(embedding(3, segments = 2))
        assertNull(unpackHex(hex.dropLast(2), dim))
        assertNull(unpackHex(hex, dim = 0))
        assertNull(unpackHex("", dim))
    }

    @Test
    fun negativeValuesSurviveTheSignedRoundTrip() {
        // The byte is signed and the hex is not. Getting this wrong turns every negative component
        // positive, which no threshold would catch — the vectors would simply stop matching.
        val vectors = arrayOf(FloatArray(dim) { if (it % 2 == 0) 0.5f else -0.5f })
        val roundTripped = unpackHex(quantiseToHex(vectors), dim)!!

        assertTrue("expected a negative component", roundTripped[0][1] < 0f)
        assertEquals(0.5f, roundTripped[0][0], 0.01f)
        assertEquals(-0.5f, roundTripped[0][1], 0.01f)
    }
}
