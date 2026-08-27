package com.wander.android

import com.wander.android.core.playback.LiveRejoinBudget
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The budget exists to tell two identical-looking failures apart: a stream that has gone off the
 * air, and a healthy one on a phone that went through a tunnel. Only the spacing separates them.
 */
class LiveRejoinBudgetTest {

    private var clock = 0L
    private fun budget() = LiveRejoinBudget(maxPerBurst = 3, burstWindowMs = 60_000L) { clock }

    @Test
    fun `allows a short burst then gives up`() {
        val budget = budget()
        repeat(3) { assertTrue(budget.allow("live")) }
        assertFalse(budget.allow("live"))
    }

    /**
     * The case a lifetime counter got wrong: radio left on for an afternoon drops out now and
     * then, and every one of those is a new incident rather than a retry loop.
     */
    @Test
    fun `an isolated failure later is a new burst`() {
        val budget = budget()
        repeat(3) { assertTrue(budget.allow("live")) }
        assertFalse(budget.allow("live"))

        clock += 60_001L
        assertTrue(budget.allow("live"))
        assertTrue(budget.allow("live"))
    }

    @Test
    fun `a failure just inside the window keeps counting`() {
        val budget = budget()
        assertTrue(budget.allow("live"))
        clock += 59_000L
        assertTrue(budget.allow("live"))
        clock += 1_000L
        assertTrue(budget.allow("live"))
        assertFalse(budget.allow("live"))
    }

    @Test
    fun `stations have their own budgets`() {
        val budget = budget()
        repeat(3) { assertTrue(budget.allow("one")) }
        assertFalse(budget.allow("one"))
        assertTrue(budget.allow("two"))
    }
}
