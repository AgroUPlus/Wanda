package com.wander.android.data.repository

import com.wander.android.core.audio.fingerprint.AudioEmbedder
import com.wander.android.core.database.dao.Centroid
import com.wander.android.core.database.dao.TrackEmbeddingDao
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The indexed tracks whose stored audio embedding sits nearest a seed's — "Sonic radio".
 *
 * Reads the per-track centroids the fingerprint index already keeps (512 bytes a track, the same
 * one-shot read a recognition pass makes) rather than the full segment vectors, so it costs a few
 * tens of milliseconds on a large library and never decodes audio.
 *
 * This *supplies* candidates, unlike [SmartRadioBuilder]'s acoustic features, which only order
 * them. That is safe here because it is one source among several: the backends' own radios still
 * reach past the library, and this only adds the library's closest-sounding tracks to the pool.
 * A seed that was never indexed has no centroid and contributes nothing — that is an absence of
 * data, not a reason to pick something else.
 */
internal class SonicNeighbours(private val embeddingDao: TrackEmbeddingDao) {

    /** Up to [limit] track ids nearest [seedId], closest first; empty when the seed is unindexed. */
    suspend fun nearest(seedId: String, limit: Int): List<String> {
        val centroids = withContext(Dispatchers.IO) {
            embeddingDao.centroids(AudioEmbedder.MODEL_NAME, AudioEmbedder.EMBEDDER_VERSION)
        }
        val seed = centroids.firstOrNull { it.trackId == seedId }?.centroid ?: return emptyList()
        val others = centroids.filter { it.trackId != seedId }
        return withContext(Dispatchers.Default) { rank(seed, others, limit) }
    }

    internal companion object {

        /**
         * [others] by cosine to [seed], best first, skipping any row whose centroid has not been
         * backfilled yet.
         *
         * A centroid is one mean vector per thirty seconds of track; each is folded to a single
         * whole-track mean before comparing, so a long track and a short one are compared on the
         * same footing rather than on how many chunks each happens to have.
         */
        fun rank(seed: ByteArray, others: List<Centroid>, limit: Int): List<String> {
            val target = meanUnit(seed) ?: return emptyList()
            return others
                .mapNotNull { row ->
                    val summary = row.centroid ?: return@mapNotNull null
                    val vector = meanUnit(summary) ?: return@mapNotNull null
                    row.trackId to dot(target, vector)
                }
                .sortedByDescending { it.second }
                .take(limit)
                .map { it.first }
        }

        /** The chunks of [blob] averaged into one unit vector, or null when there is nothing to average. */
        private fun meanUnit(blob: ByteArray): FloatArray? {
            val chunks = blob.size / AudioEmbedder.EMBED_DIM
            if (chunks == 0) return null
            val mean = FloatArray(AudioEmbedder.EMBED_DIM)
            for (c in 0 until chunks) {
                val base = c * AudioEmbedder.EMBED_DIM
                for (d in 0 until AudioEmbedder.EMBED_DIM) mean[d] += blob[base + d].toFloat()
            }
            val norm = sqrt(mean.fold(0f) { sum, v -> sum + v * v })
            if (norm == 0f) return null
            for (d in mean.indices) mean[d] /= norm
            return mean
        }

        private fun dot(a: FloatArray, b: FloatArray): Float {
            var sum = 0f
            for (i in a.indices) sum += a[i] * b[i]
            return sum
        }
    }
}
