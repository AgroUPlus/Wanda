package com.wander.android

import com.wander.android.core.playback.SpeedAndPitch
import com.wander.android.data.repository.playbackFrom
import com.wander.android.data.repository.playbackSuffix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The half of the share-link protocol that can be pinned down without a network or an Android
 * `Uri`.
 *
 * `SHARE_LINKS.md` §3.2 is implemented three times — here, in Agro's `playback_suffix`, and in
 * frwd.top's `rebuiltSearch` — and nothing in any toolchain will notice the three drifting apart.
 * These cases are deliberately the same ones as `agro/src/listen.rs`'s `playback_tests`, so a
 * disagreement between the two shows up as a failing test on one side rather than as a link that
 * plays at the wrong speed on someone else's phone.
 */
class ShareLinkPlaybackTest {

    @Test
    fun `defaults add nothing, so an ordinary share stays an ordinary url`() {
        assertEquals("", playbackSuffix(SpeedAndPitch()))
    }

    @Test
    fun `a changed rate is carried`() {
        assertEquals("&s=1.25&p=0.9", playbackSuffix(SpeedAndPitch(speed = 1.25f, pitch = 0.9f)))
    }

    @Test
    fun `both in range round-trip`() {
        assertEquals(
            SpeedAndPitch(speed = 1.25f, pitch = 0.9f),
            playbackFrom("1.25", "0.9")
        )
    }

    @Test
    fun `one without the other is dropped`() {
        assertNull(playbackFrom("1.25", null))
        assertNull(playbackFrom(null, "0.9"))
    }

    /** Dropped, not clamped: clamping invents an intent the link does not carry. */
    @Test
    fun `out of range is dropped not clamped`() {
        assertNull(playbackFrom("40.0", "1.0"))
        assertNull(playbackFrom("1.0", "0.1"))
    }

    @Test
    fun `the bounds are inclusive`() {
        assertEquals(SpeedAndPitch(speed = 0.5f, pitch = 2.0f), playbackFrom("0.5", "2.0"))
    }

    @Test
    fun `values at the defaults are not a request to change anything`() {
        assertNull(playbackFrom("1.0", "1.0"))
    }

    @Test
    fun `nonsense is not a rate`() {
        assertNull(playbackFrom("fast", "0.9"))
    }
}
