package com.wander.android

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.DownloadMarks
import com.wander.android.data.repository.ManualDownloadRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which tracks may be downloaded by hand, and how a row reads its download state. */
class ManualDownloadRulesTest {

    private fun track(id: String, source: SourceType = SourceType.NAVIDROME, isLive: Boolean = false, downloaded: Boolean = false) =
        UnifiedTrack(id = id, source = source, title = "t", artist = "a", isLive = isLive, isDownloaded = downloaded)

    @Test
    fun `a streamed track can be downloaded`() {
        assertTrue(ManualDownloadRules.canDownload(track("navidrome:1")))
        assertTrue(ManualDownloadRules.canDownload(track("ytm:1", SourceType.YTMUSIC)))
        assertTrue(ManualDownloadRules.canDownload(track("podcast:1", SourceType.PODCAST)))
    }

    @Test
    fun `local files, placeholders, livestreams and one-shot tracks cannot`() {
        assertFalse(ManualDownloadRules.canDownload(track("local:1", SourceType.LOCAL)))
        assertFalse(ManualDownloadRules.canDownload(track("unresolved:1", SourceType.UNRESOLVED)))
        assertFalse(ManualDownloadRules.canDownload(track("ytm:live", SourceType.YTMUSIC, isLive = true)))
        assertFalse(ManualDownloadRules.canDownload(track("relay:abc", SourceType.LOCAL)))
        assertFalse(ManualDownloadRules.canDownload(track("p2p:abc", SourceType.NAVIDROME)))
    }

    @Test
    fun `a queued id stops being queued once it is downloaded`() {
        val marks = DownloadMarks.of(downloaded = setOf("a"), manual = setOf("a", "b"))
        assertEquals(setOf("b"), marks.queued)
        assertTrue(marks.isDownloaded(track("a")))
        assertFalse(marks.isQueued(track("a")))
        assertTrue(marks.isQueued(track("b")))
    }

    @Test
    fun `the track's own flag counts as downloaded even outside the marks`() {
        val marks = DownloadMarks.of(downloaded = emptySet(), manual = setOf("a"))
        assertTrue(marks.isDownloaded(track("a", downloaded = true)))
        assertFalse(marks.isQueued(track("a", downloaded = true)))
    }

    @Test
    fun `a download can be removed, a local file never`() {
        val marks = DownloadMarks.of(downloaded = setOf("navidrome:1", "local:1"), manual = setOf("navidrome:2"))
        assertTrue(ManualDownloadRules.canRemove(track("navidrome:1"), marks))
        assertTrue(ManualDownloadRules.canRemove(track("navidrome:2"), marks))
        assertFalse(ManualDownloadRules.canRemove(track("navidrome:3"), marks))
        assertFalse(ManualDownloadRules.canRemove(track("local:1", SourceType.LOCAL), marks))
    }
}
