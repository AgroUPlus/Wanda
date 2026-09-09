package com.wander.android

import com.wander.android.core.database.dao.LyricSearchResult
import com.wander.android.core.database.dao.TrackLyricsDao
import com.wander.android.core.database.entity.TrackLyricsEntity
import com.wander.android.core.network.HttpClientFactory
import com.wander.android.data.repository.LyricsRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsParserTest {

    private val fakeDao = object : TrackLyricsDao {
        override suspend fun getLyricsForTrack(trackId: String): TrackLyricsEntity? = null
        override suspend fun findLyricsForTrackOrMetadata(trackId: String, title: String, artist: String): TrackLyricsEntity? = null
        override suspend fun insertLyrics(lyrics: TrackLyricsEntity) {}
        override suspend fun insertFts(trackId: String, plainLyrics: String) {}
        override suspend fun deleteFts(trackId: String) {}
        override suspend fun searchTracksByLyrics(query: String, limit: Int): List<LyricSearchResult> = emptyList()
        override suspend fun countLyrics(): Int = 0
    }

    @Test
    fun testLrcParserAccurateTimestamps() {
        val repo = LyricsRepository(emptySet(), fakeDao, HttpClientFactory.ktorClient)
        val sampleLrc = """
            [00:12.50]Line one of song
            [01:04.20]Chorus starts here
            [02:30.00]Final outro
        """.trimIndent()

        val lines = repo.parseLrc(sampleLrc)
        assertEquals(3, lines.size)

        // 00:12.50 -> 12500ms
        assertEquals(12500L, lines[0].timestampMs)
        assertEquals("Line one of song", lines[0].text)

        // 01:04.20 -> 64200ms
        assertEquals(64200L, lines[1].timestampMs)
        assertEquals("Chorus starts here", lines[1].text)

        // 02:30.00 -> 150000ms
        assertEquals(150000L, lines[2].timestampMs)
        assertEquals("Final outro", lines[2].text)
    }

    @Test
    fun testCachedLyricsResolution() = kotlinx.coroutines.runBlocking {
        val cachedDao = object : TrackLyricsDao {
            override suspend fun getLyricsForTrack(trackId: String): TrackLyricsEntity =
                TrackLyricsEntity(
                    trackId = trackId,
                    plainLyrics = "Line one\nLine two",
                    syncedLyrics = "[00:10.00]Line one\n[00:20.00]Line two",
                    source = "LOCAL_CACHE"
                )
            override suspend fun findLyricsForTrackOrMetadata(trackId: String, title: String, artist: String): TrackLyricsEntity? =
                getLyricsForTrack(trackId)
            override suspend fun insertLyrics(lyrics: TrackLyricsEntity) {}
            override suspend fun insertFts(trackId: String, plainLyrics: String) {}
            override suspend fun deleteFts(trackId: String) {}
            override suspend fun searchTracksByLyrics(query: String, limit: Int): List<LyricSearchResult> = emptyList()
            override suspend fun countLyrics(): Int = 1
        }
        val repo = LyricsRepository(emptySet(), cachedDao, HttpClientFactory.ktorClient)
        val data = repo.getLyrics("track_123", "Title", "Artist")
        org.junit.Assert.assertNotNull(data)
        assertEquals(true, data?.isSynced)
        assertEquals("LOCAL_CACHE", data?.source)
        assertEquals(2, data?.lines?.size)
        assertEquals(10000L, data?.lines?.get(0)?.timestampMs)
    }
}
