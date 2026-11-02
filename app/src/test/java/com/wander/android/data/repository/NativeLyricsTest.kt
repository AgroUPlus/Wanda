package com.wander.android.data.repository

import com.wander.android.core.database.dao.CatalogLyricHit
import com.wander.android.core.database.dao.CatalogRecordingDao
import com.wander.android.core.database.dao.CatalogVector
import com.wander.android.core.database.dao.Centroid
import com.wander.android.core.database.dao.Evictable
import com.wander.android.core.database.dao.LyricSearchResult
import com.wander.android.core.database.dao.TrackLyricsDao
import com.wander.android.core.database.entity.CatalogRecordingEntity
import com.wander.android.core.database.entity.TrackLyricsEntity
import com.wander.android.core.network.HttpClientFactory
import com.wander.android.core.security.FakeSharedPreferences
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.LyricLine
import com.wander.android.data.model.LyricsData
import com.wander.android.data.model.LyricsState
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.IMusicSource
import com.wander.android.data.sources.SourceCapabilities
import com.wander.android.data.sources.StreamInfo
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeLyricsTest {

    private class RecordingDao : TrackLyricsDao {
        val saved = mutableListOf<TrackLyricsEntity>()
        override suspend fun getLyricsForTrack(trackId: String): TrackLyricsEntity? = null
        override suspend fun findLyricsForTrackOrMetadata(
            trackId: String, title: String, artist: String, durationMs: Long?
        ): TrackLyricsEntity? = null
        override suspend fun insertLyrics(lyrics: TrackLyricsEntity) { saved += lyrics }
        override suspend fun insertFts(trackId: String, plainLyrics: String) {}
        override suspend fun deleteFts(trackId: String) {}
        override suspend fun deleteLyrics(trackId: String) {}
        override suspend fun searchTracksByLyrics(query: String, limit: Int): List<LyricSearchResult> = emptyList()
        override suspend fun countLyrics(): Int = saved.size
        override fun countFromCatalogueFlow(): Flow<Int> = flowOf(0)
        override suspend fun trackIdsNamed(title: String, artist: String): List<String> = emptyList()
    }

    private object NoCatalogue : CatalogRecordingDao {
        override suspend fun insert(recording: CatalogRecordingEntity) {}
        override suspend fun insertFts(recordingId: String, plainLyrics: String) {}
        override suspend fun deleteFts(recordingId: String) {}
        override suspend fun byId(recordingId: String): CatalogRecordingEntity? = null
        override suspend fun centroids(model: String, version: Int): List<Centroid> = emptyList()
        override fun countFlow(model: String, version: Int): Flow<Int> = flowOf(0)
        override suspend fun vectors(ids: List<String>, model: String, version: Int): List<CatalogVector> = emptyList()
        override suspend fun searchByLyrics(query: String, limit: Int): List<CatalogLyricHit> = emptyList()
        override suspend fun touch(recordingId: String, at: Long) {}
        override suspend fun vectorBytes(): Long = 0L
        override suspend fun evictionOrder(limit: Int): List<Evictable> = emptyList()
        override suspend fun dropFingerprints(ids: List<String>) {}
    }

    private class FakeSource(private val answer: Result<LyricsData?>) : IMusicSource {
        override val sourceType = SourceType.NAVIDROME
        override val displayName = "Fake"
        override val capabilities = SourceCapabilities(lyrics = true)
        override val isConfigured: StateFlow<Boolean> = MutableStateFlow(true)
        override suspend fun search(query: String): Result<List<UnifiedTrack>> = Result.success(emptyList())
        override suspend fun getStreamInfo(trackId: String): Result<StreamInfo> =
            Result.failure(UnsupportedOperationException())
        override suspend fun getLyrics(trackId: String): Result<LyricsData?> = answer
    }

    private fun repo(dao: TrackLyricsDao, source: IMusicSource): LyricsRepository {
        val storage = SecureStorage(FakeSharedPreferences()).apply { setExternalLyricsEnabled(false) }
        return LyricsRepository(setOf(source), dao, NoCatalogue, HttpClientFactory.ktorClient, storage)
    }

    @Test
    fun syncedNativeLyricsAreCachedAsLrc() = runBlocking {
        val dao = RecordingDao()
        val lines = listOf(LyricLine(1_500L, "One"), LyricLine(62_050L, "Two"))
        val native = LyricsData("navidrome:1", isSynced = true, lines = lines, source = "Navidrome")
        repo(dao, FakeSource(Result.success(native))).getLyrics("navidrome:1", "T", "A")

        val stored = dao.saved.single().syncedLyrics.orEmpty()
        assertEquals("[00:01.500]One\n[01:02.050]Two", stored)
        assertEquals(lines.map { it.timestampMs to it.text }, LrcParser.parse(stored).map { it.timestampMs to it.text })
    }

    @Test
    fun unsyncedNativeLyricsStoreNoLrc() = runBlocking {
        val dao = RecordingDao()
        val native = LyricsData("navidrome:1", isSynced = false, lines = listOf(LyricLine(0L, "One")))
        repo(dao, FakeSource(Result.success(native))).getLyrics("navidrome:1", "T", "A")
        assertNull(dao.saved.single().syncedLyrics)
    }

    @Test
    fun unreachableSourceIsNotReportedAsAbsent() = runBlocking {
        val dao = RecordingDao()
        val state = repo(dao, FakeSource(Result.failure(IOException("down")))).getLyrics("navidrome:1", "T", "A")
        assertEquals(LyricsState.Unreachable, state)
        assertEquals(0, dao.saved.size)
    }

    @Test
    fun sourceWithNoLyricsIsAbsent() = runBlocking {
        val state = repo(RecordingDao(), FakeSource(Result.success(null))).getLyrics("navidrome:1", "T", "A")
        assertEquals(LyricsState.Absent, state)
    }
}
