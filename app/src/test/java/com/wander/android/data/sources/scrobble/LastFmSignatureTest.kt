package com.wander.android.data.sources.scrobble

import com.wander.android.core.database.dao.PendingScrobble
import org.junit.Assert.assertEquals
import org.junit.Test

/** Expected digests computed independently, from the recipe at last.fm/api/desktopauth. */
class LastFmSignatureTest {

    @Test
    fun theDocumentedExampleSignsAsDocumented() {
        val params = mapOf("api_key" to "XXX", "method" to "auth.getSession", "token" to "XX")
        assertEquals("c63723f9c552a68f71a2c393284e7b1a", LastFmSignature.sign(params, "mysecret"))
    }

    @Test
    fun formatIsLeftOutOrderIsByNameAndTextIsUtf8() {
        val params = mapOf(
            "track[0]" to "Jóga",
            "format" to "json",
            "timestamp[0]" to "100",
            "artist[0]" to "Björk",
            "api_key" to "K"
        )
        assertEquals("925d7eeed68d555506339aaf600b8676", LastFmSignature.sign(params, "sec"))
    }

    @Test
    fun aPlayStartedWhenItsCountedMomentSaysItDid() {
        fun play(durationMs: Long) = PendingScrobble(1, 1_000_000L, "t", "a", null, null, durationMs)
        // A 4-minute track counts at three quarters: three minutes in.
        assertEquals((1_000_000L - 180_000L) / 1000, play(240_000L).startedAtSeconds())
        // A 20-minute one at the four-minute cap.
        assertEquals((1_000_000L - 240_000L) / 1000, play(1_200_000L).startedAtSeconds())
        // Unknown length: the thirty seconds the play rule falls back to.
        assertEquals((1_000_000L - 30_000L) / 1000, play(0L).startedAtSeconds())
    }
}
