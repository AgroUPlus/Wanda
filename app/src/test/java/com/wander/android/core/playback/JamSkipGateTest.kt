package com.wander.android.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JamSkipGateTest {
    @Test
    fun outsideAJamASkipIsNotAVoteAndTheCallerSkipsNormally() {
        val gate = JamSkipGate()

        assertFalse(gate.active.value)
        assertFalse(gate.tryVote())
    }

    @Test
    fun insideAJamASkipCastsExactlyOneVote() {
        val gate = JamSkipGate()
        var votes = 0
        gate.setVoter { votes++ }

        assertTrue(gate.active.value)
        assertTrue(gate.tryVote())
        assertEquals(1, votes)
    }

    @Test
    fun endingTheJamRestoresNormalSkipping() {
        val gate = JamSkipGate()
        var votes = 0
        gate.setVoter { votes++ }
        gate.setVoter(null)

        assertFalse(gate.active.value)
        assertFalse(gate.tryVote())
        assertEquals(0, votes)
    }
}
