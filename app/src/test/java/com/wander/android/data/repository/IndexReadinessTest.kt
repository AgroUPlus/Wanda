package com.wander.android.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The three things the Listen sheet can say, and when.
 *
 * This exists because of a bug it would have caught. The sheet's count read `fingerprints`, a
 * table that lost its writer when the neural embedder took over recognition, so it was zero on
 * every device for ever — and the sheet said "nothing is indexed yet" while Settings counted a
 * full library from `track_embeddings` and recognition matched happily against it. Nothing
 * asserted that the number shown and the number matched against were the same number.
 */
class IndexReadinessTest {

    @Test
    fun `a missing model outranks an empty index`() {
        // Both are "nothing to match against", but only one of them is fixed by waiting, so the
        // model has to win — telling someone to wait for a download they never started is the
        // failure this ordering prevents.
        assertEquals(IndexReadiness.ModelMissing, IndexReadiness.of(modelReady = false, indexedTrackCount = 0))
        assertEquals(IndexReadiness.ModelMissing, IndexReadiness.of(modelReady = false, indexedTrackCount = 337))
    }

    @Test
    fun `the model is present and the index is still filling`() {
        assertEquals(IndexReadiness.Empty, IndexReadiness.of(modelReady = true, indexedTrackCount = 0))
    }

    @Test
    fun `a non-empty index carries its count to the sheet`() {
        assertEquals(
            IndexReadiness.Ready(337),
            IndexReadiness.of(modelReady = true, indexedTrackCount = 337)
        )
        assertEquals(IndexReadiness.Ready(1), IndexReadiness.of(modelReady = true, indexedTrackCount = 1))
    }

    /** A negative count is not a count. Room cannot produce one, but `Ready(-1)` would render. */
    @Test
    fun `a nonsensical count reads as empty rather than as a negative library`() {
        assertEquals(IndexReadiness.Empty, IndexReadiness.of(modelReady = true, indexedTrackCount = -1))
    }
}
