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
            trackDao = FakeTrackDao.create()
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
            trackDao = FakeTrackDao.create()
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
            trackDao = FakeTrackDao.create()
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

    private class FakeTrackDao {
        companion object {
            fun create(): com.wander.android.core.database.dao.TrackDao {
                val clazz = com.wander.android.core.database.dao.TrackDao::class.java
                return java.lang.reflect.Proxy.newProxyInstance(
                    clazz.classLoader,
                    arrayOf(clazz)
                ) { _, method, _ ->
                    when (method.returnType) {
                        Boolean::class.javaPrimitiveType -> false
                        Int::class.javaPrimitiveType -> 0
                        Long::class.javaPrimitiveType -> 0L
                        List::class.java -> emptyList<Any>()
                        else -> null
                    }
                } as com.wander.android.core.database.dao.TrackDao
            }
        }
    }
}
