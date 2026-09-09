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
        override suspend fun computedSince(after: Long, model: String, version: Int, limit: Int) =
            emptyList<com.wander.android.core.database.entity.TrackEmbeddingEntity>()
        override suspend fun needingIndex(model: String, version: Int, limit: Int) = emptyList<String>()
        override suspend fun prune(model: String, version: Int) {}
        override suspend fun clear() {}
    }

    /**
     * A stub, not a fake: every method answers empty. The test drives
     * [RecordingIdentityRepository] through the embedding path, which reads none of these — the
     * DAO is here only because the constructor requires one.
     *
     * Regenerated from the interface. Kotlin forbids default values on an override, so the
     * defaults `TrackDao` declares are dropped here.
     */
    private class FakeTrackDao : com.wander.android.core.database.dao.TrackDao {
        override fun getAllTracksFlow(): kotlinx.coroutines.flow.Flow<List<com.wander.android.core.database.entity.TrackEntity>> = kotlinx.coroutines.flow.emptyFlow()
        override fun pagedTracks(): androidx.paging.PagingSource<Int, com.wander.android.core.database.entity.TrackEntity> = throw NotImplementedError()
        override fun pagedTracksBySource(source: com.wander.android.data.model.SourceType): androidx.paging.PagingSource<Int, com.wander.android.core.database.entity.TrackEntity> = throw NotImplementedError()
        override suspend fun libraryTrackIds(): List<String> = emptyList()
        override suspend fun libraryTrackIdsBySource(source: com.wander.android.data.model.SourceType): List<String> = emptyList()
        override suspend fun getAllTracksOnce(): List<com.wander.android.core.database.entity.TrackEntity> = emptyList()
        override fun getTracksBySourceFlow(source: com.wander.android.data.model.SourceType): kotlinx.coroutines.flow.Flow<List<com.wander.android.core.database.entity.TrackEntity>> = kotlinx.coroutines.flow.emptyFlow()
        override fun getLikedTracksFlow(): kotlinx.coroutines.flow.Flow<List<com.wander.android.core.database.entity.TrackEntity>> = kotlinx.coroutines.flow.emptyFlow()
        override fun getLikedTrackIdsFlow(): kotlinx.coroutines.flow.Flow<List<String>> = kotlinx.coroutines.flow.emptyFlow()
        override fun getDownloadedTracksFlow(): kotlinx.coroutines.flow.Flow<List<com.wander.android.core.database.entity.TrackEntity>> = kotlinx.coroutines.flow.emptyFlow()
        override suspend fun getOfflineTracksOnce(): List<com.wander.android.core.database.entity.TrackEntity> = emptyList()
        override fun getTracksByAlbumFlow(albumId: String): kotlinx.coroutines.flow.Flow<List<com.wander.android.core.database.entity.TrackEntity>> = kotlinx.coroutines.flow.emptyFlow()
        override suspend fun getTrackById(id: String): com.wander.android.core.database.entity.TrackEntity? = null
        override suspend fun getCandidateIdsByDuration(excludingId: String, minDurationMs: Long, maxDurationMs: Long): List<String> = emptyList()
        override suspend fun getTracksInAlbum(albumId: String): List<com.wander.android.core.database.entity.TrackEntity> = emptyList()
        override suspend fun getTracksInSource(source: com.wander.android.data.model.SourceType): List<com.wander.android.core.database.entity.TrackEntity> = emptyList()
        override fun getTracksByArtistFlow(artist: String): kotlinx.coroutines.flow.Flow<List<com.wander.android.core.database.entity.TrackEntity>> = kotlinx.coroutines.flow.emptyFlow()
        override suspend fun getTracksByArtistOnce(artist: String): List<com.wander.android.core.database.entity.TrackEntity> = emptyList()
        override suspend fun getLikedTracksOnce(): List<com.wander.android.core.database.entity.TrackEntity> = emptyList()
        override suspend fun searchTracks(query: String, limit: Int): List<com.wander.android.core.database.entity.TrackEntity> = emptyList()
        override suspend fun searchTracksInSource(source: com.wander.android.data.model.SourceType, query: String, limit: Int): List<com.wander.android.core.database.entity.TrackEntity> = emptyList()
        override suspend fun getRandomTracksInSource(source: com.wander.android.data.model.SourceType, limit: Int): List<com.wander.android.core.database.entity.TrackEntity> = emptyList()
        override suspend fun getRecentlyAddedInSource(source: com.wander.android.data.model.SourceType, limit: Int): List<com.wander.android.core.database.entity.TrackEntity> = emptyList()
        override suspend fun getRecentlyAddedTracks(limit: Int): List<com.wander.android.core.database.entity.TrackEntity> = emptyList()
        override suspend fun markLive(trackId: String) {}
        override fun observeRecentlyAddedAlbumIds(limit: Int): kotlinx.coroutines.flow.Flow<List<String>> = kotlinx.coroutines.flow.emptyFlow()
        override suspend fun getRecentlyPlayedTracks(limit: Int): List<com.wander.android.core.database.entity.TrackEntity> = emptyList()
        override suspend fun getLikedTracksList(limit: Int): List<com.wander.android.core.database.entity.TrackEntity> = emptyList()
        override suspend fun getTopPlayedTracks(limit: Int): List<com.wander.android.core.database.entity.TrackEntity> = emptyList()
        override suspend fun getPlayedTracksOnce(): List<com.wander.android.core.database.entity.TrackEntity> = emptyList()
        override suspend fun getForgottenFavorites(thresholdTimestamp: Long, limit: Int): List<com.wander.android.core.database.entity.TrackEntity> = emptyList()
        override suspend fun getNeverPlayedTracks(limit: Int): List<com.wander.android.core.database.entity.TrackEntity> = emptyList()
        override suspend fun getLikedNotDownloaded(limit: Int): List<com.wander.android.core.database.entity.TrackEntity> = emptyList()
        override suspend fun deleteOneShotTrackRows(): Int = 0
        override suspend fun insertNewTracks(tracks: List<com.wander.android.core.database.entity.TrackEntity>): List<Long> = emptyList()
        override suspend fun updateSourceFields(fields: List<com.wander.android.core.database.entity.TrackSourceFields>) {}
        override suspend fun markAsLibrary(trackIds: List<String>) {}
        override suspend fun setLiked(trackId: String, isLiked: Boolean) {}
        override suspend fun setDisplayMetadata(trackId: String, title: String, artist: String, album: String?) {}
        override suspend fun getFingerprintableTracks(): List<com.wander.android.core.database.entity.TrackEntity> = emptyList()
        override fun getFingerprintableTracksFlow(): kotlinx.coroutines.flow.Flow<List<com.wander.android.core.database.entity.TrackEntity>> = kotlinx.coroutines.flow.emptyFlow()
        override suspend fun findLocalOrDownloadedCandidates(title: String, limit: Int): List<com.wander.android.core.database.entity.TrackEntity> = emptyList()
        override suspend fun findNavidromeCandidates(title: String, limit: Int): List<com.wander.android.core.database.entity.TrackEntity> = emptyList()
        override suspend fun setDownloaded(trackId: String, isDownloaded: Boolean, localPath: String?) {}
        override suspend fun incrementPlayCount(trackId: String, timestamp: Long) {}
        override suspend fun getUnhashedLocalTracks(limit: Int): List<com.wander.android.core.database.entity.TrackEntity> = emptyList()
        override suspend fun getUnhashedDownloads(limit: Int): List<com.wander.android.core.database.entity.TrackEntity> = emptyList()
        override suspend fun fillMissingDuration(trackId: String, durationMs: Long) {}
        override suspend fun setContentHash(trackId: String, hash: String) {}
        override suspend fun getUnsyncedLocalTracks(limit: Int): List<com.wander.android.core.database.entity.TrackEntity> = emptyList()
        override suspend fun markSynced(trackId: String, timestamp: Long) {}
        override suspend fun getSyncedLocalTracks(): List<com.wander.android.core.database.entity.TrackEntity> = emptyList()
        override fun countPendingUploadFlow(): kotlinx.coroutines.flow.Flow<Int> = kotlinx.coroutines.flow.emptyFlow()
        override fun countSyncedFlow(): kotlinx.coroutines.flow.Flow<Int> = kotlinx.coroutines.flow.emptyFlow()
        override fun countLocalFlow(): kotlinx.coroutines.flow.Flow<Int> = kotlinx.coroutines.flow.emptyFlow()
        override suspend fun localContentHashesNotIn(keepIds: List<String>): List<String> = emptyList()
        override suspend fun deleteLocalTracksNotIn(keepIds: List<String>): Int = 0
        override suspend fun clearBySource(source: com.wander.android.data.model.SourceType) {}
        override suspend fun getTracksByIds(ids: List<String>): List<com.wander.android.core.database.entity.TrackEntity> = emptyList()
        override suspend fun findByContentHash(hash: String): com.wander.android.core.database.entity.TrackEntity? = null
    }
}
