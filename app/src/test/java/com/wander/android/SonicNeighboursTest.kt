package com.wander.android

import com.wander.android.core.audio.fingerprint.AudioEmbedder
import com.wander.android.core.database.dao.Centroid
import com.wander.android.data.repository.RadioPool
import com.wander.android.data.repository.SonicNeighbours
import org.junit.Assert.assertEquals
import org.junit.Test

/** How "Sonic radio" reads the fingerprint index, and how its picks are mixed with the rest. */
class SonicNeighboursTest {

    /** A one-chunk centroid pointing mostly along [axis], tilted towards [tilt] by [amount]. */
    private fun centroid(axis: Int, tilt: Int = axis, amount: Int = 0): ByteArray =
        ByteArray(AudioEmbedder.EMBED_DIM).also {
            it[axis] = 100
            if (tilt != axis) it[tilt] = amount.toByte()
        }

    @Test
    fun `nearest centroid ranks first`() {
        val seed = centroid(0)
        val ranked = SonicNeighbours.rank(
            seed,
            listOf(
                Centroid("far", centroid(5)),
                Centroid("near", centroid(0, tilt = 1, amount = 10)),
                Centroid("middle", centroid(0, tilt = 1, amount = 90))
            ),
            limit = 10
        )
        assertEquals(listOf("near", "middle", "far"), ranked)
    }

    @Test
    fun `rows not yet backfilled are skipped, not ranked`() {
        val ranked = SonicNeighbours.rank(
            centroid(0),
            listOf(Centroid("pending", null), Centroid("ready", centroid(0))),
            limit = 10
        )
        assertEquals(listOf("ready"), ranked)
    }

    @Test
    fun `a multi-chunk centroid is compared on its whole-track mean`() {
        val twoChunks = centroid(0) + centroid(0)
        val ranked = SonicNeighbours.rank(
            centroid(0),
            listOf(Centroid("other", centroid(3)), Centroid("long", twoChunks)),
            limit = 1
        )
        assertEquals(listOf("long"), ranked)
    }

    @Test
    fun `round robin takes one from each list in turn`() {
        assertEquals(
            listOf("a1", "b1", "c1", "a2", "c2", "a3"),
            RadioPool.roundRobin(listOf("a1", "a2", "a3"), listOf("b1"), listOf("c1", "c2"))
        )
    }
}
