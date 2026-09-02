package com.wander.android

import com.wander.android.core.playback.PreloadDecision
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What may be fetched before anybody has asked to hear it.
 *
 * Worth a test rather than a code review, because the expensive mistake here is silent and
 * expensive: preloading a one-shot stream consumes the transfer, and the real playback is then
 * answered `409` by a relay session that has already served its only half. That exact failure took
 * a long session to track down once, from the wrong end.
 */
class PreloadDecisionTest {

    @Test
    fun `an ordinary track is preloaded`() {
        assertTrue(PreloadDecision.canPreload("navidrome:tr-42", isLive = false))
        assertTrue(PreloadDecision.canPreload("ytm:IGQH1FS89jE", isLive = false))
    }

    @Test
    fun `a borrowed transfer is never preloaded`() {
        // Reading it is spending it: the peer and the relay each serve these exactly once.
        assertFalse(PreloadDecision.canPreload("relay:cd02d863", isLive = false))
        assertFalse(PreloadDecision.canPreload("p2p:cd02d863", isLive = false))
    }

    @Test
    fun `a livestream is never preloaded`() {
        // There is no beginning to fetch early, and the edge fetched now is not the edge later.
        assertFalse(PreloadDecision.canPreload("ytm:live1", isLive = true))
    }

    @Test
    fun `nothing queued means nothing to preload`() {
        assertFalse(PreloadDecision.canPreload(null, isLive = false))
        assertFalse(PreloadDecision.canPreload("", isLive = false))
    }
}
