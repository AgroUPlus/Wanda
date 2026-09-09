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
     * A track's length: what its metadata claims, or what its embedding measured.
     *
     * The fallback is not a guess. Tracks are embedded whole at a fixed half-second hop, so the
     * segment count *is* a measurement of the decoded audio — and one that does not depend on a
     * backend having filled the field in.
     */
    private fun durationOf(declaredMs: Long, segments: Int): Long =
        if (declaredMs > 0L) declaredMs else segments.toLong() * SEGMENT_HOP_MS

    /**
     * Finds track ids holding the same recording as [trackId], best first.
     */
    suspend fun matchesFor(trackId: String): List<Match> = withContext(Dispatchers.Default) {
        val targetTrack = withContext(Dispatchers.IO) { trackDao.getTrackById(trackId) } ?: return@withContext emptyList()

        val targetEntity = withContext(Dispatchers.IO) {
            embeddingDao.getForTrack(trackId, AudioEmbedder.MODEL_NAME, AudioEmbedder.EMBEDDER_VERSION)
        } ?: return@withContext emptyList()

        val targetVectors = AudioEmbedder.unpack(targetEntity.vector)
        if (targetVectors.isEmpty()) return@withContext emptyList()

        // A track whose metadata carries no duration used to stop here, and that is not a rare
        // shape — YouTube Music rows routinely arrive without one. It made those tracks
        // undetectable as duplicates in either direction, and a duplicate nobody knows about is
        // what stops the *original* from being recognised: it scores within MIN_MARGIN of it and
        // the match is rejected as ambiguous. Measured on a real library, a flawless six-second
        // excerpt of one track led its own unlinked copy by 0.018 against a 0.04 margin.
        //
        // The embedding is a better duration than the metadata anyway: it was measured from the
        // audio that actually decoded, at a known half-second per segment.
        val targetDuration = durationOf(targetTrack.durationMs, targetVectors.size)
        if (targetDuration <= 0L) return@withContext emptyList()

        val minDuration = targetDuration - durationToleranceMs
        val maxDuration = targetDuration + durationToleranceMs

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

    /**
     * Finds local tracks holding the same recording as an embedding from somewhere else.
     *
     * The inverse of [matchesFor]: that one starts from a track this device already has, this one
     * starts from a catalogue entry it has just been handed. Same three steps at the same
     * thresholds — duration, mean, sequence — because "the same recording" has to mean one thing.
     *
     * [durationMs] is the publisher's, so the duration gate is symmetric with theirs.
     */
    suspend fun matchesForEmbedding(
        vectors: Array<FloatArray>,
        durationMs: Long
    ): List<Match> = withContext(Dispatchers.Default) {
        if (vectors.isEmpty() || durationMs <= 0L) return@withContext emptyList()

        val candidateIds = withContext(Dispatchers.IO) {
            trackDao.getCandidateIdsByDuration(
                "",
                durationMs - durationToleranceMs,
                durationMs + durationToleranceMs
            )
        }
        if (candidateIds.isEmpty()) return@withContext emptyList()

        val candidateEntities = withContext(Dispatchers.IO) {
            embeddingDao.getForTracks(candidateIds, AudioEmbedder.MODEL_NAME, AudioEmbedder.EMBEDDER_VERSION)
        }
        if (candidateEntities.isEmpty()) return@withContext emptyList()

        val incomingMean = meanVector(vectors)

        val matches = mutableListOf<Match>()
        for (candidate in candidateEntities) {
            val candidateVectors = AudioEmbedder.unpack(candidate.vector)
            if (candidateVectors.isEmpty()) continue
            if (dot(incomingMean, meanVector(candidateVectors)) < meanSimThreshold) continue

            val sim = sequenceSimilarity(vectors, candidateVectors)
            if (sim >= matchThreshold) matches += Match(candidate.trackId, sim.toDouble())
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

    /**
     * Links every duplicate in the library, for tracks nothing has asked about yet.
     *
     * `FingerprintIndexWorker` records links for each track *it* measures, which is enough while
     * the phone is the only thing that measures anything. It is not enough once a desktop indexer
     * writes the embeddings: those tracks arrive fingerprinted and unlinked, and an unlinked
     * duplicate is worse than no duplicate at all — `EmbeddingRepository.findCompetitor` skips a
     * runner-up it knows to be the same recording, and cannot skip one nobody told it about, so
     * the pair defeat each other on [EmbeddingRepository.MIN_MARGIN] and *neither* is ever
     * recognised. Measured on a real library: a flawless excerpt of one track led its own copy by
     * 0.018 against a 0.04 margin, so no recording however good could have identified it.
     *
     * Bounded per run, and cheap per track: the duration gate means each one compares against a
     * handful of candidates rather than the library. Tracks are taken oldest-measured first so a
     * partial run makes steady progress instead of revisiting the same head each time.
     *
     * Returns how many tracks were examined.
     */
    suspend fun linkDuplicates(
        linkRepository: RecordingLinkRepository,
        secureStorage: com.wander.android.core.security.SecureStorage,
        limit: Int = LINK_BACKFILL_LIMIT
    ): Int = withContext(Dispatchers.Default) {
        val after = secureStorage.duplicateScanCursor
        val pending = withContext(Dispatchers.IO) {
            embeddingDao.idsAfter(
                AudioEmbedder.MODEL_NAME, AudioEmbedder.EMBEDDER_VERSION, after, limit
            )
        }
        if (pending.isEmpty()) {
            // The sweep reached the end. Reset rather than latch: the library gains tracks, and
            // whether two of them are the same recording is a question that comes back.
            if (after.isNotEmpty()) secureStorage.duplicateScanCursor = ""
            return@withContext 0
        }

        var linked = 0
        for (trackId in pending) {
            val matches = matchesFor(trackId)
            if (matches.isNotEmpty()) {
                linkRepository.record(trackId, matches)
                linked++
            }
        }
        secureStorage.duplicateScanCursor = pending.last()
        android.util.Log.i(TAG, "Duplicate link pass: examined ${pending.size}, linked $linked")
        pending.size
    }

    companion object {
        private const val TAG = "RecordingIdentity"

        /** Milliseconds of audio each stored segment advances. See `AudioEmbedder.HOP_SAMPLES`. */
        private const val SEGMENT_HOP_MS = 500L

        /** Tracks examined for duplicates per run. See [linkDuplicates]. */
        private const val LINK_BACKFILL_LIMIT = 300
    }
}
