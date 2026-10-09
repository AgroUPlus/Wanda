package com.wander.android

import com.wander.android.data.repository.estimateHostPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class ListenAlongHostPositionTest {
    @Test
    fun playingHostHasMovedOnByTheFrameAge() {
        assertEquals(13_500L, estimateHostPosition(10_000L, isPlaying = true, ageMs = 3_500L))
    }

    @Test
    fun pausedHostHasNotMoved() {
        assertEquals(10_000L, estimateHostPosition(10_000L, isPlaying = false, ageMs = 3_500L))
    }

    @Test
    fun negativeAgeIsIgnored() {
        assertEquals(10_000L, estimateHostPosition(10_000L, isPlaying = true, ageMs = -50L))
    }
}
