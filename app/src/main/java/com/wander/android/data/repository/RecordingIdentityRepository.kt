package com.wander.android.data.repository

import com.wander.android.core.audio.fingerprint.AudioEmbedder
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.dao.TrackEmbeddingDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Which tracks are the same recording, decided using neural audio embeddings.
 *
 * Replaces legacy Haitsma-Kalker sub-hashes with 128-d neural embeddings (wanda_embedder.tflite).
 *
 * Matching uses a two-stage filter:
 * 1. Candidates within +/- 3000ms duration tolerance are proposed.
 * 2. Mean-vector dot product filters out dissimilar songs (>= 0.75).
 * 3. Full symmetric segment similarity decides (>= 0.88).
 */
@Singleton
class RecordingIdentityRepository @Inject constructor(
    private val embeddingDao: TrackEmbeddingDao,
    private val trackDao: TrackDao
) {
    /**
     * How alike two sequences must be to be called one recording.
     *
     * Two transfers/encodings of the same recording score >= 0.88 (typically >= 0.95).
     * Unrelated tracks, covers, or different arrangements sit well below 0.70.
     */
    private val matchThreshold = 0.88

    /**
     * Tolerance in duration to propose duplicate candidates.
     * Aligned with [TrackDeduplicator.DURATION_TOLERANCE_MS].
     */
    private val durationToleranceMs = 3_000L

    /**
     * Minimum dot product between normalized mean vectors to proceed to sequence comparison.
     */
    private val meanSimThreshold = 0.75f

    data class Match(val trackId: String, val similarity: Double)

    /**
     * Finds track ids holding the same recording as [trackId], best first.
     */
    suspend fun matchesFor(trackId: String): List<Match> = withContext(Dispatchers.Default) {
        val targetTrack = withContext(Dispatchers.IO) { trackDao.getTrackById(trackId) } ?: return@withContext emptyList()
        if (targetTrack.durationMs <= 0L) return@withContext emptyList()

        val targetEntity = withContext(Dispatchers.IO) {
            embeddingDao.getForTrack(trackId, AudioEmbedder.MODEL_NAME, AudioEmbedder.EMBEDDER_VERSION)
        } ?: return@withContext emptyList()

        val targetVectors = AudioEmbedder.unpack(targetEntity.vector)
        if (targetVectors.isEmpty()) return@withContext emptyList()

        val minDuration = targetTrack.durationMs - durationToleranceMs
        val maxDuration = targetTrack.durationMs + durationToleranceMs

        val candidateIds = withContext(Dispatchers.IO) {
            trackDao.getCandidateIdsByDuration(trackId, minDuration, maxDuration)
        }
        if (candidateIds.isEmpty()) return@withContext emptyList()

        val candidateEntities = withContext(Dispatchers.IO) {
            embeddingDao.getForTracks(candidateIds, AudioEmbedder.MODEL_NAME, AudioEmbedder.EMBEDDER_VERSION)
        }
        if (candidateEntities.isEmpty()) return@withContext emptyList()

        val targetMean = meanVector(targetVectors)

        val matches = mutableListOf<Match>()
        for (candidate in candidateEntities) {
            val candidateVectors = AudioEmbedder.unpack(candidate.vector)
            if (candidateVectors.isEmpty()) continue

            val candidateMean = meanVector(candidateVectors)
            val meanSim = dot(targetMean, candidateMean)
            if (meanSim < meanSimThreshold) continue

            val sim = sequenceSimilarity(targetVectors, candidateVectors)
            if (sim >= matchThreshold) {
                matches += Match(candidate.trackId, sim.toDouble())
            }
        }

        matches.sortedByDescending { it.similarity }
    }

    /** True if [trackId] already has a computed embedding. */
    suspend fun isIndexed(trackId: String): Boolean = withContext(Dispatchers.IO) {
        embeddingDao.getForTrack(trackId, AudioEmbedder.MODEL_NAME, AudioEmbedder.EMBEDDER_VERSION) != null
    }

    /**
     * Computes symmetric sequence similarity between two embedding sequences.
     * Score is the average of best cosine matches in both directions.
     */
    internal fun sequenceSimilarity(a: Array<FloatArray>, b: Array<FloatArray>): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f

        var sumA = 0f
        for (va in a) {
            var best = -1f
            for (vb in b) {
                val d = dot(va, vb)
                if (d > best) best = d
            }
            sumA += best
        }
        val meanA = sumA / a.size

        var sumB = 0f
        for (vb in b) {
            var best = -1f
            for (va in a) {
                val d = dot(va, vb)
                if (d > best) best = d
            }
            sumB += best
        }
        val meanB = sumB / b.size

        return (meanA + meanB) / 2f
    }

    internal fun meanVector(vectors: Array<FloatArray>): FloatArray {
        val dim = vectors[0].size
        val mean = FloatArray(dim)
        for (v in vectors) {
            for (i in 0 until dim) {
                mean[i] += v[i]
            }
        }
        var normSq = 0f
        for (i in 0 until dim) {
            mean[i] /= vectors.size
            normSq += mean[i] * mean[i]
        }
        val norm = sqrt(normSq)
        if (norm > 0f) {
            for (i in 0 until dim) {
                mean[i] /= norm
            }
        }
        return mean
    }

    private fun dot(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in a.indices) s += a[i] * b[i]
        return s
    }
}
