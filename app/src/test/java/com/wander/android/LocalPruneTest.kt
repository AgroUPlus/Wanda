package com.wander.android

import com.wander.android.data.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The id arithmetic behind pruning local tracks whose file has gone.
 *
 * The query itself needs Room, but the part that was wrong is the mapping between MediaStore's
 * numeric ids and the namespaced ids Room stores. Getting that wrong in either direction is
 * silent and severe: too narrow and dead rows survive, too wide and the prune deletes the
 * library.
 */
class LocalPruneTest {

    private fun keepIds(existing: Set<Long>): List<String> =
        existing.map { "${SourceType.LOCAL.idPrefix}$it" }

    @Test
    fun `media store ids become namespaced track ids`() {
        assertEquals(listOf("local:470"), keepIds(setOf(470L)))
    }

    /** The bug this came from: row 470 went, the same song came back as 602. */
    @Test
    fun `a replaced row keeps the new id and drops the old one`() {
        val keep = keepIds(setOf(602L))

        assertTrue("local:602" in keep)
        assertTrue("the dead row would have survived the prune", "local:470" !in keep)
    }

    @Test
    fun `other sources are never in the keep list`() {
        val keep = keepIds(setOf(1L, 2L))

        assertTrue(keep.none { it.startsWith(SourceType.NAVIDROME.idPrefix) })
        assertTrue(keep.none { it.startsWith(SourceType.YTMUSIC.idPrefix) })
    }
}
