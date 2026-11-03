package com.wander.android.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** The pace of a held skip button: 10 s a beat to start, quickening, and capped. */
class HoldSeekStepsTest {

    @Test
    fun `starts at ten seconds a beat`() {
        assertEquals(10_000L, HoldSeekSteps.stepMs(0))
        assertEquals(10_000L, HoldSeekSteps.stepMs(5))
    }

    @Test
    fun `quickens the longer it is held`() {
        assertEquals(20_000L, HoldSeekSteps.stepMs(6))
        assertEquals(30_000L, HoldSeekSteps.stepMs(12))
    }

    @Test
    fun `never passes forty seconds a beat`() {
        assertEquals(40_000L, HoldSeekSteps.stepMs(18))
        assertEquals(40_000L, HoldSeekSteps.stepMs(500))
    }
}
