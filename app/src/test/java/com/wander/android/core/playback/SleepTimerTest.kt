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
}
