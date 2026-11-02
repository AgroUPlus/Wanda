package com.wander.android.core.playback

import com.wander.android.data.model.HLS_LIVE_FORMAT
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsLiveTest {
    private fun track(source: SourceType, live: Boolean, format: String? = null) = UnifiedTrack(
        id = "id",
        source = source,
        title = "t",
        artist = "a",
        format = format,
        isLive = live
    )

    @Test
    fun aRecordingIsNeverHls() {
        assertFalse(track(SourceType.YTMUSIC, live = false).isHlsLive())
    }

    @Test
    fun aYouTubeLivestreamIsHls() {
        assertTrue(track(SourceType.YTMUSIC, live = true).isHlsLive())
    }

    @Test
    fun aRadioStationIsHlsOnlyWhenItSaysSo() {
        assertTrue(track(SourceType.NAVIDROME, live = true, format = HLS_LIVE_FORMAT).isHlsLive())
        assertFalse(track(SourceType.NAVIDROME, live = true).isHlsLive())
    }
}
