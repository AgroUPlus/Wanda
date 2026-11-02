package com.wander.android.core.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepTimerTest {
    @Test
    fun startsOff() {
        assertEquals(SleepTimerState.Off, SleepTimer().state.value)
    }

    @Test
    fun stoppingAtTheEndOfTheItemIsRemembered() {
        val timer = SleepTimer()
        timer.stopAtEndOfItem()

        assertEquals(SleepTimerState.EndOfItem, timer.state.value)
    }

    @Test
    fun cancelReturnsToOffFromAnyMode() {
        val timer = SleepTimer()
        timer.stopAtEndOfItem()
        timer.cancel()

        assertEquals(SleepTimerState.Off, timer.state.value)
    }

    @Test
    fun extendingAddsToTheEndWhileItIsStillAhead() {
        assertEquals(400_000L, extendedEnd(endsAtMs = 100_000L, nowMs = 50_000L, minutes = 5))
    }

    @Test
    fun extendingDuringTheFadeCountsFromNow() {
        assertEquals(420_000L, extendedEnd(endsAtMs = 100_000L, nowMs = 120_000L, minutes = 5))
    }

    @Test
    fun extendingWithoutACountdownChangesNothing() {
        val timer = SleepTimer()
        timer.extend(5)
        assertEquals(SleepTimerState.Off, timer.state.value)
        timer.stopAtEndOfItem()
        timer.extend(5)
        assertEquals(SleepTimerState.EndOfItem, timer.state.value)
    }
}
