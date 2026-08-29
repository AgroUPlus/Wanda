package com.wander.android

import com.wander.android.data.repository.SplitSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A pin is asked about by whichever of the two rows happens to be in hand, so the pair cannot have
 * two representations. Getting this wrong would make an override silently apply in one direction
 * only — the failure that looks exactly like the bug it was created to fix.
 */
class SplitSetTest {

    @Test
    fun `a pin holds whichever way round it is asked`() {
        val splits = SplitSet.of(listOf("ytm:1" to "navidrome:1"))

        assertTrue(splits.isApart("ytm:1", "navidrome:1"))
        assertTrue(splits.isApart("navidrome:1", "ytm:1"))
    }

    @Test
    fun `the same pair given both ways round is one pin`() {
        val splits = SplitSet.of(
            listOf("ytm:1" to "navidrome:1", "navidrome:1" to "ytm:1")
        )

        assertEquals(1, splits.size)
    }

    @Test
    fun `an empty set splits nothing`() {
        assertFalse(SplitSet.EMPTY.isApart("ytm:1", "navidrome:1"))
        assertEquals(0, SplitSet.EMPTY.size)
    }

    @Test
    fun `an unrelated pin leaves other pairs alone`() {
        val splits = SplitSet.of(listOf("ytm:1" to "navidrome:1"))

        assertFalse(splits.isApart("ytm:1", "local:1"))
    }
}
