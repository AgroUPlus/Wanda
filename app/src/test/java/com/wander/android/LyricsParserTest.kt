package com.wander.android

import com.wander.android.core.database.dao.LyricSearchResult
import com.wander.android.core.database.dao.TrackLyricsDao
import com.wander.android.core.database.entity.TrackLyricsEntity
import com.wander.android.core.network.HttpClientFactory
import com.wander.android.data.model.LyricsState
import com.wander.android.data.repository.LyricsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsParserTest {

    private val fakeDao = object : TrackLyricsDao {
        override suspend fun getLyricsForTrack(trackId: String): TrackLyricsEntity? = null
        override suspend fun findLyricsForTrackOrMetadata(trackId: String, title: String, artist: String): TrackLyricsEntity? = null
        override suspend fun insertLyrics(lyrics: TrackLyricsEntity) {}
        override suspend fun insertFts(trackId: String, plainLyrics: String) {}
        override suspend fun deleteFts(trackId: String) {}
        override suspend fun deleteLyrics(trackId: String) {}
        override suspend fun searchTracksByLyrics(query: String, limit: Int): List<LyricSearchResult> = emptyList()
        override suspend fun countLyrics(): Int = 0
        override fun countFromCatalogueFlow(): Flow<Int> = flowOf(0)
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
            override suspend fun deleteLyrics(trackId: String) {}
            override suspend fun searchTracksByLyrics(query: String, limit: Int): List<LyricSearchResult> = emptyList()
            override suspend fun countLyrics(): Int = 1
            override fun countFromCatalogueFlow(): Flow<Int> = flowOf(0)
        }
        val repo = LyricsRepository(emptySet(), cachedDao, HttpClientFactory.ktorClient)
        val state = repo.getLyrics("track_123", "Title", "Artist")
        val data = (state as? LyricsState.Present)?.lyrics
        org.junit.Assert.assertNotNull(data)
        assertEquals(true, data?.isSynced)
        assertEquals("LOCAL_CACHE", data?.source)
        assertEquals(2, data?.lines?.size)
        assertEquals(10000L, data?.lines?.get(0)?.timestampMs)
    }

    @Test
    fun testEnhancedLrcWordTimestamps() {
        val repo = LyricsRepository(emptySet(), fakeDao, HttpClientFactory.ktorClient)
        val enhancedLrc = """
            [00:10.00]<00:10.00>Never <00:10.50>gonna <00:11.00>give <00:11.50>you <00:12.00>up
        """.trimIndent()

        val lines = repo.parseLrc(enhancedLrc)
        assertEquals(1, lines.size)
        val line = lines[0]
        assertEquals("Never gonna give you up", line.text)
        assertEquals(5, line.words.size)
        assertEquals("Never", line.words[0].text)
        assertEquals(10000L, line.words[0].startMs)
        assertEquals(10500L, line.words[0].endMs)
        assertEquals("up", line.words[4].text)
        assertEquals(12000L, line.words[4].startMs)
    }

    @Test
    fun testResolveWordsInterpolation() {
        val sampleLrc = "[00:10.00]Hello world here"
        val lines = com.wander.android.data.repository.LrcParser.parse(sampleLrc)
        assertEquals(1, lines.size)
        val words = com.wander.android.data.repository.LrcParser.resolveWords(lines[0], nextLineTimestampMs = 13000L)
        assertEquals(3, words.size)
        assertEquals("Hello", words[0].text)
        assertEquals(10000L, words[0].startMs)
        assertEquals("world", words[1].text)
        assertEquals("here", words[2].text)
        // Words complete when vocal finishes, leaving pause before next line at 13000ms
        org.junit.Assert.assertTrue(words[2].endMs in 11000L..13000L)
    }

    @Test
    fun testLyricsSyncType() {
        val unsynced = com.wander.android.data.model.LyricsData("1", isSynced = false)
        assertEquals(com.wander.android.data.model.LyricsSyncType.NONE, unsynced.syncType)

        val lineSynced = com.wander.android.data.model.LyricsData(
            "2",
            isSynced = true,
            lines = listOf(com.wander.android.data.model.LyricLine(1000L, "Line 1"))
        )
        assertEquals(com.wander.android.data.model.LyricsSyncType.LINE_SYNCED, lineSynced.syncType)

        val wordSynced = com.wander.android.data.model.LyricsData(
            "3",
            isSynced = true,
            lines = listOf(
                com.wander.android.data.model.LyricLine(
                    1000L,
                    "Line 1",
                    words = listOf(com.wander.android.data.model.LyricWord("Line", 1000L, 1500L))
                )
            )
        )
        assertEquals(com.wander.android.data.model.LyricsSyncType.WORD_SYNCED, wordSynced.syncType)
    }
}
