package com.wander.android.core.work

import com.wander.android.data.sources.agro.AgroArtistRelease
import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseTriageTest {

    private fun release(id: String, at: Long, artistId: String = "a1", artist: String = "Artist") =
        AgroArtistRelease(id, artistId, artist, title = "T$id", album = null, updatedAt = at)

    @Test
    fun `a short page resumes after its newest row`() {
        val page = listOf(release("1", 10), release("2", 12))
        assertEquals(12L, ReleaseTriage.nextWatermark(page, pageSize = 100))
    }

    @Test
    fun `a full page that spans seconds steps back to re-read its last second`() {
        val page = listOf(release("1", 10), release("2", 12))
        assertEquals(11L, ReleaseTriage.nextWatermark(page, pageSize = 2))
    }

    @Test
    fun `a full page all in one second moves on`() {
        val page = listOf(release("1", 12), release("2", 12))
        assertEquals(12L, ReleaseTriage.nextWatermark(page, pageSize = 2))
    }

    @Test
    fun `only artists already primed are announced`() {
        val releases = listOf(release("1", 10, artistId = "old"), release("2", 10, artistId = "new"))
        assertEquals(listOf("1"), ReleaseTriage.announceable(releases, primed = setOf("old")).map { it.recordingId })
    }

    @Test
    fun `nothing is announced before any artist is primed`() {
        assertEquals(emptyList<AgroArtistRelease>(), ReleaseTriage.announceable(listOf(release("1", 10)), emptySet()))
    }

    @Test
    fun `an artist without an id is keyed by name`() {
        assertEquals("artist", ReleaseTriage.artistKey(release("1", 10, artistId = "", artist = " Artist ")))
    }

    @Test
    fun `blank and repeated recording ids are dropped`() {
        val releases = listOf(release("", 10), release("1", 10), release("1", 11))
        assertEquals(listOf("1"), ReleaseTriage.usable(releases).map { it.recordingId })
    }
}
