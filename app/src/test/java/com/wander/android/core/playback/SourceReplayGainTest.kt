package com.wander.android.core.playback

import com.wander.android.data.model.EXTRA_REPLAYGAIN_ALBUM_DB
import com.wander.android.data.model.EXTRA_REPLAYGAIN_TRACK_DB
import org.junit.Assert.assertEquals
import org.junit.Test

/** ReplayGain an OpenSubsonic server reports, used where the stream's own tags say nothing. */
class SourceReplayGainTest {

    @Test
    fun readsTheSourcesValues() {
        val info = ReplayGainTags.fromSource(
            mapOf(EXTRA_REPLAYGAIN_TRACK_DB to "-6.5", EXTRA_REPLAYGAIN_ALBUM_DB to "-7.25")
        )
        assertEquals(ReplayGainInfo(-6.5f, -7.25f), info)
    }

    @Test
    fun dropsValuesNoTagWouldBeTrustedWith() {
        val info = ReplayGainTags.fromSource(
            mapOf(EXTRA_REPLAYGAIN_TRACK_DB to "-600", EXTRA_REPLAYGAIN_ALBUM_DB to "NaN")
        )
        assertEquals(ReplayGainInfo.NONE, info)
    }

    @Test
    fun streamTagsWinAndTheSourceFillsTheGaps() {
        val merged = ReplayGainInfo(trackGainDb = -3f, albumGainDb = null)
            .orElse(ReplayGainInfo(trackGainDb = -9f, albumGainDb = -4f))
        assertEquals(ReplayGainInfo(-3f, -4f), merged)
    }
}
