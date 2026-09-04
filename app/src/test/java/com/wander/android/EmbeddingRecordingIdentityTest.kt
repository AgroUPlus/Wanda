package com.wander.android

import com.wander.android.data.repository.RecordingIdentityRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class EmbeddingRecordingIdentityTest {

    private fun unitVector(dim: Int = 128, seed: Int = 1): FloatArray {
        val v = FloatArray(dim) { i -> ((i + seed) % 7 - 3).toFloat() }
        var norm = 0f
        for (x in v) norm += x * x
        norm = sqrt(norm)
        for (i in v.indices) v[i] /= norm
        return v
    }

    @Test
    fun `sequenceSimilarity on identical sequences is 1_0`() {
        val repo = RecordingIdentityRepository(
            embeddingDao = FakeTrackEmbeddingDao(),
            trackDao = FakeTrackDao()
        )
        val seqA = Array(5) { unitVector(128, it) }
        val seqB = Array(5) { unitVector(128, it) }

        val sim = repo.sequenceSimilarity(seqA, seqB)
        assertEquals(1.0f, sim, 1e-4f)
    }

    @Test
    fun `sequenceSimilarity on distinct random sequences is low`() {
        val repo = RecordingIdentityRepository(
            embeddingDao = FakeTrackEmbeddingDao(),
            trackDao = FakeTrackDao()
        )
        // Two completely different sets of orthogonal-ish vectors
        val seqA = Array(5) { i ->
            FloatArray(128) { if (it == i) 1.0f else 0.0f }
        }
        val seqB = Array(5) { i ->
            FloatArray(128) { if (it == i + 10) 1.0f else 0.0f }
        }

        val sim = repo.sequenceSimilarity(seqA, seqB)
        assertTrue("Expected similarity < 0.2f, got $sim", sim < 0.2f)
    }

    @Test
    fun `meanVector normalizes properly`() {
        val repo = RecordingIdentityRepository(
            embeddingDao = FakeTrackEmbeddingDao(),
            trackDao = FakeTrackDao()
        )
        val seq = Array(4) { unitVector(128, it) }
        val mean = repo.meanVector(seq)

        var norm = 0f
        for (x in mean) norm += x * x
        norm = sqrt(norm)
        assertEquals(1.0f, norm, 1e-4f)
    }

    private class FakeTrackEmbeddingDao : com.wander.android.core.database.dao.TrackEmbeddingDao {
        override suspend fun getAll(model: String, version: Int) = emptyList<com.wander.android.core.database.entity.TrackEmbeddingEntity>()
        override suspend fun getForTrack(trackId: String, model: String, version: Int) = null
        override suspend fun getForTracks(trackIds: List<String>, model: String, version: Int) = emptyList<com.wander.android.core.database.entity.TrackEmbeddingEntity>()
        override fun indexedTrackCountFlow(model: String, version: Int) = kotlinx.coroutines.flow.emptyFlow<Int>()
        override fun indexedTrackIdsFlow(model: String, version: Int) = kotlinx.coroutines.flow.emptyFlow<List<String>>()
        override suspend fun upsert(embedding: com.wander.android.core.database.entity.TrackEmbeddingEntity) {}
        override suspend fun needingIndex(model: String, version: Int, limit: Int) = emptyList<String>()
        override suspend fun prune(model: String, version: Int) {}
        override suspend fun clear() {}
    }

    private class FakeTrackDao : com.wander.android.core.database.dao.TrackDao {
        override fun getAllTracksFlow() = kotlinx.coroutines.flow.emptyFlow<List<com.wander.android.core.database.entity.TrackEntity>>()
        override fun pagedTracks(): androidx.paging.PagingSource<Int, com.wander.android.core.database.entity.TrackEntity> = throw NotImplementedError()
        override fun pagedTracksBySource(source: com.wander.android.data.model.SourceType): androidx.paging.PagingSource<Int, com.wander.android.core.database.entity.TrackEntity> = throw NotImplementedError()
        override suspend fun libraryTrackIds() = emptyList<String>()
        override suspend fun libraryTrackIdsBySource(source: com.wander.android.data.model.SourceType) = emptyList<String>()
        override suspend fun getAllTracksOnce() = emptyList<com.wander.android.core.database.entity.TrackEntity>()
        override fun getTracksBySourceFlow(source: com.wander.android.data.model.SourceType) = kotlinx.coroutines.flow.emptyFlow<List<com.wander.android.core.database.entity.TrackEntity>>()
        override fun getLikedTracksFlow() = kotlinx.coroutines.flow.emptyFlow<List<com.wander.android.core.database.entity.TrackEntity>>()
        override fun getLikedTrackIdsFlow() = kotlinx.coroutines.flow.emptyFlow<List<String>>()
        override fun getDownloadedTracksFlow() = kotlinx.coroutines.flow.emptyFlow<List<com.wander.android.core.database.entity.TrackEntity>>()
        override suspend fun getOfflineTracksOnce() = emptyList<com.wander.android.core.database.entity.TrackEntity>()
        override fun getTracksByAlbumFlow(albumId: String) = kotlinx.coroutines.flow.emptyFlow<List<com.wander.android.core.database.entity.TrackEntity>>()
        override suspend fun getTrackById(id: String): com.wander.android.core.database.entity.TrackEntity? = null
        override suspend fun getCandidateIdsByDuration(excludingId: String, minDurationMs: Long, maxDurationMs: Long) = emptyList<String>()
        override suspend fun getTracksInAlbum(albumId: String) = emptyList<com.wander.android.core.database.entity.TrackEntity>()
        override suspend fun getTracksInSource(source: com.wander.android.data.model.SourceType) = emptyList<com.wander.android.core.database.entity.TrackEntity>()
        override fun getTracksByArtistFlow(artist: String) = kotlinx.coroutines.flow.emptyFlow<List<com.wander.android.core.database.entity.TrackEntity>>()
        override suspend fun searchTracksOnce(query: String) = emptyList<com.wander.android.core.database.entity.TrackEntity>()
        override fun getTracksBySourceAndTitleFlow(source: com.wander.android.data.model.SourceType, query: String) = kotlinx.coroutines.flow.emptyFlow<List<com.wander.android.core.database.entity.TrackEntity>>()
        override suspend fun insert(track: com.wander.android.core.database.entity.TrackEntity) = 0L
        override suspend fun insertAll(tracks: List<com.wander.android.core.database.entity.TrackEntity>) = emptyList<Long>()
        override suspend fun upsert(track: com.wander.android.core.database.entity.TrackEntity) = 0L
        override suspend fun update(track: com.wander.android.core.database.entity.TrackEntity) {}
        override suspend fun updateSourceMetadata(tracks: List<com.wander.android.core.database.entity.TrackSourceFields>) {}
        override suspend fun updateLikeStatus(trackId: String, isLiked: Boolean) {}
        override suspend fun markDownloaded(trackId: String, localFilePath: String) {}
        override suspend fun removeDownloaded(trackId: String) {}
        override suspend fun recordPlay(trackId: String, timestamp: Long) {}
        override suspend fun deleteById(trackId: String) {}
        override suspend fun deleteTracks(trackIds: List<String>) {}
        override suspend fun clearNonLibraryTracks() {}
        override suspend fun clear() {}
        override suspend fun pruneOneShotTracks() {}
    }
}
