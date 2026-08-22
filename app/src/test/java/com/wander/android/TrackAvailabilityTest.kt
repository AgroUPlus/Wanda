package com.wander.android

import com.wander.android.data.model.SearchKind
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.model.isPlayableOffline
import com.wander.android.data.sources.ytmusic.EPISODES_FILTER
import com.wander.android.data.sources.ytmusic.SONGS_FILTER
import com.wander.android.data.sources.ytmusic.VIDEOS_FILTER
import com.wander.android.data.sources.ytmusic.filterParam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackAvailabilityTest {

    private fun track(
        source: SourceType,
        isDownloaded: Boolean = false,
        isCached: Boolean = false
    ) = UnifiedTrack(
        id = "${source.idPrefix}1",
        source = source,
        title = "Title",
        artist = "Artist",
        isDownloaded = isDownloaded,
        isCached = isCached
    )

    @Test
    fun `local tracks always play offline`() {
        assertTrue(track(SourceType.LOCAL).isPlayableOffline())
    }

    @Test
    fun `downloaded remote tracks play offline`() {
        assertTrue(track(SourceType.NAVIDROME, isDownloaded = true).isPlayableOffline())
        assertTrue(track(SourceType.YTMUSIC, isDownloaded = true).isPlayableOffline())
    }

    @Test
    fun `remote tracks that were never downloaded do not play offline`() {
        assertFalse(track(SourceType.NAVIDROME).isPlayableOffline())
        assertFalse(track(SourceType.INTERNET_ARCHIVE).isPlayableOffline())
    }

    /**
     * `isCached` is set only for local files today and does not reflect the Media3 streaming
     * cache, so it must not be mistaken for "available offline" — a partially cached stream is not
     * something playback can promise to finish.
     */
    @Test
    fun `isCached alone does not make a remote track available`() {
        assertFalse(track(SourceType.YTMUSIC, isCached = true).isPlayableOffline())
    }

    @Test
    fun `every search kind maps to its own filter`() {
        assertEquals(SONGS_FILTER, SearchKind.TRACKS.filterParam())
        assertEquals(VIDEOS_FILTER, SearchKind.VIDEOS.filterParam())
        assertEquals(EPISODES_FILTER, SearchKind.EPISODES.filterParam())
        assertEquals(
            SearchKind.entries.size,
            SearchKind.entries.map { it.filterParam() }.distinct().size
        )
    }
}
