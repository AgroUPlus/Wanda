package com.wander.android

import androidx.media3.common.MimeTypes
import com.wander.android.core.playback.streamCacheKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StreamCacheKeyTest {

    /**
     * The bug this exists for: the key used to default to the resolved URI, and a Navidrome URL
     * carries a fresh auth salt per request. Every play of the same file wrote a new cache entry
     * and never read one back.
     */
    @Test
    fun sameTrackAndEncodingKeyTheSameAcrossResolves() {
        assertEquals(
            streamCacheKey("navidrome:42", "audio/*", 320),
            streamCacheKey("navidrome:42", "audio/*", 320)
        )
    }

    @Test
    fun differentTracksDoNotShareAKey() {
        assertNotEquals(
            streamCacheKey("navidrome:42", "audio/*", 320),
            streamCacheKey("navidrome:43", "audio/*", 320)
        )
    }

    /**
     * A YouTube track can resolve to a different rendition on a later play. Keying on the encoding
     * as well as the id makes that a cache miss rather than webm bytes served as mp4.
     */
    @Test
    fun differentEncodingsOfOneTrackDoNotShareAKey() {
        val webm = streamCacheKey("yt:abc", "audio/webm", 160)
        assertNotEquals(webm, streamCacheKey("yt:abc", "audio/mp4", 160))
        assertNotEquals(webm, streamCacheKey("yt:abc", "audio/webm", 256))
        assertNotEquals(webm, streamCacheKey("yt:abc", MimeTypes.APPLICATION_M3U8, 0))
    }
}
