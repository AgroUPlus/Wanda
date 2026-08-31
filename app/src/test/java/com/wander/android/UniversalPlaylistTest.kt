package com.wander.android

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.model.isPlayableOffline
import com.wander.android.data.repository.TrackDeduplicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalPlaylistTest {

    private fun track(
        source: SourceType,
        id: String,
        title: String = "Let Down",
        artist: String = "Radiohead",
        durationMs: Long = 299_000L,
        isDownloaded: Boolean = false,
        streamUri: String? = null
    ) = UnifiedTrack(
        id = id,
        source = source,
        title = title,
        artist = artist,
        durationMs = durationMs,
        isDownloaded = isDownloaded,
        streamUri = streamUri
    )

    @Test
    fun `playlist resolves to downloaded local file when available`() {
        val ytmTrack = track(
            source = SourceType.YTMUSIC,
            id = "ytm:letdown123",
            title = "Let Down",
            artist = "Radiohead",
            isDownloaded = false
        )
        assertFalse(ytmTrack.isPlayableOffline())

        val downloadedLocalTrack = track(
            source = SourceType.LOCAL,
            id = "local:letdown_file",
            title = "Let Down",
            artist = "Radiohead",
            isDownloaded = true,
            streamUri = "/storage/emulated/0/Music/Radiohead - Let Down.flac"
        )
        assertTrue(downloadedLocalTrack.isPlayableOffline())

        // Simulating playlist best-rendition resolution
        val downloadedTracks = listOf(downloadedLocalTrack)
        val resolved = if (ytmTrack.isPlayableOffline()) {
            ytmTrack
        } else {
            downloadedTracks.firstOrNull { TrackDeduplicator.isSameRecording(ytmTrack, it) } ?: ytmTrack
        }

        assertEquals(downloadedLocalTrack.id, resolved.id)
        assertTrue(resolved.isPlayableOffline())
    }

    @Test
    fun `playlist keeps streaming track when no offline copy exists`() {
        val ytmTrack = track(
            source = SourceType.YTMUSIC,
            id = "ytm:noidle",
            title = "Idioteque",
            artist = "Radiohead"
        )
        val downloadedTracks = emptyList<UnifiedTrack>()

        val resolved = if (ytmTrack.isPlayableOffline()) {
            ytmTrack
        } else {
            downloadedTracks.firstOrNull { TrackDeduplicator.isSameRecording(ytmTrack, it) } ?: ytmTrack
        }

        assertEquals(ytmTrack.id, resolved.id)
        assertEquals(SourceType.YTMUSIC, resolved.source)
    }
}
