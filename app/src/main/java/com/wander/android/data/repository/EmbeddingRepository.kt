package com.wander.android.data.repository

import android.util.Log
import com.wander.android.core.audio.fingerprint.AudioEmbedder
import com.wander.android.core.audio.fingerprint.AudioFormat
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.dao.TrackEmbeddingDao
import com.wander.android.core.database.entity.TrackEmbeddingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the neural audio-fingerprint index: when a track's embedding is computed, where it is
 * kept, and how a microphone clip is matched against the collection.
 *
 * The measurement is [AudioEmbedder]'s; this is the equivalent of [RecognitionRepository] for the
 * landmark path — filling the index, and aligning a clip against it — kept separate while the two
 * run side by side.
 */
@Singleton
class EmbeddingRepository @Inject constructor(
    private val embeddingDao: TrackEmbeddingDao,
    private val embedder: AudioEmbedder,
    private val trackDao: TrackDao,
    private val linkRepository: RecordingLinkRepository,
    private val splitRepository: RecordingSplitRepository
) {


    val indexedTrackCount: Flow<Int> =
        embeddingDao.indexedTrackCountFlow(AudioEmbedder.MODEL_NAME, AudioEmbedder.EMBEDDER_VERSION)

    /** Computes [samples]'s embedding sequence and stores it. A track that embeds to nothing is skipped. */
    suspend fun index(trackId: String, samples: FloatArray) = withContext(Dispatchers.Default) {
        if (!embedder.isAvailable()) return@withContext
        val vectors = embedder.embed(samples)
        if (vectors.isEmpty()) return@withContext
        withContext(Dispatchers.IO) {
            embeddingDao.upsert(
                TrackEmbeddingEntity(
                    trackId = trackId,
                    vector = AudioEmbedder.pack(vectors),
                    dim = AudioEmbedder.EMBED_DIM,
                    model = AudioEmbedder.MODEL_NAME,
                    version = AudioEmbedder.EMBEDDER_VERSION,
                    computedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** Which of this device's tracks still need an embedding under the current model. */
    suspend fun needingIndex(limit: Int): Set<String> = withContext(Dispatchers.IO) {
        if (!embedder.isAvailable()) return@withContext emptySet()
        embeddingDao.needingIndex(AudioEmbedder.MODEL_NAME, AudioEmbedder.EMBEDDER_VERSION, limit).toSet()
    }

    suspend fun prune() = withContext(Dispatchers.IO) {
        embeddingDao.prune(AudioEmbedder.MODEL_NAME, AudioEmbedder.EMBEDDER_VERSION)
    }

    suspend fun clear() = withContext(Dispatchers.IO) { embeddingDao.clear() }

    /** One track the clip resembled, best first. */
    data class Match(val trackId: String, val similarity: Float, val positionSeconds: Int)

    /**
     * Ranks every indexed track by how well [samples] resembles it.
     *
     * Every segment of the clip is embedded, then for each track the mean over clip segments of
     * the best cosine similarity to any of that track's segments — high only when the clip's
     * whole sequence has a counterpart somewhere in the track, which a coincidental timbre match
     * on one segment cannot fake. The position is where in the track the clip's first segment
     * landed.
     *
     * Returns null when nothing clears [MIN_SIMILARITY] with a [MIN_MARGIN] lead on the
     * runner-up — the embedding equivalent of [com.wander.android.core.audio.fingerprint.MatchConfidence].
     */
    suspend fun match(samples: FloatArray): Match? = withContext(Dispatchers.Default) {
        if (!embedder.isAvailable()) return@withContext null
        val query = embedder.embed(samples)
        if (query.isEmpty()) return@withContext null

        val stored = withContext(Dispatchers.IO) {
            embeddingDao.getAll(AudioEmbedder.MODEL_NAME, AudioEmbedder.EMBEDDER_VERSION)
        }
        if (stored.isEmpty()) return@withContext null

        val ranked = stored.map { entity ->
            val track = AudioEmbedder.unpack(entity.vector)
            val (similarity, firstBest) = scoreSequence(query, track)
            Match(
                trackId = entity.trackId,
                similarity = similarity,
                positionSeconds = (firstBest * AudioEmbedder.HOP_SAMPLES / AudioFormat.SAMPLE_RATE)
            )
        }.sortedByDescending { it.similarity }

        val best = ranked.first()
        if (best.similarity < MIN_SIMILARITY) {
            Log.i(TAG, "Embedding pass: ${ranked.size} candidates, best ${best.trackId} " +
                "at ${"%.3f".format(best.similarity)} below MIN_SIMILARITY ${MIN_SIMILARITY}")
            return@withContext null
        }

        // Measure margin against the first candidate that is a distinct recording.
        // If the runner-up is the same recording stored under another id (e.g. Navidrome vs YTM),
        // it must not steal the margin and cause a false rejection.
        val bestTrack = withContext(Dispatchers.IO) { trackDao.getTrackById(best.trackId) }
        val splits = splitRepository.splits()
        val links = linkRepository.links()
        val competitor = findCompetitor(bestTrack?.toUnifiedTrack(), ranked, splits, links) { tid ->
            withContext(Dispatchers.IO) { trackDao.getTrackById(tid)?.toUnifiedTrack() }
        }

        val runnerUp = competitor?.similarity ?: 0f
        Log.i(TAG, "Embedding pass: ${ranked.size} candidates, best ${best.trackId} " +
            "at ${"%.3f".format(best.similarity)}, runner-up ${competitor?.trackId ?: "none"} at ${"%.3f".format(runnerUp)}")
        if (best.similarity - runnerUp < MIN_MARGIN) null else best
    }

    internal fun scoreSequence(
        query: Array<FloatArray>,
        track: Array<FloatArray>
    ): Pair<Float, Int> {
        var total = 0f
        var firstBest = 0
        for ((qi, q) in query.withIndex()) {
            var best = -1f
            var bestJ = 0
            for (j in track.indices) {
                val s = dot(q, track[j])
                if (s > best) {
                    best = s
                    bestJ = j
                }
            }
            total += best
            if (qi == 0) firstBest = bestJ
        }
        return Pair(total / query.size, firstBest)
    }

    private fun dot(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    companion object {
        private const val TAG = "EmbeddingMatch"

        /**
         * Floor on the mean best-segment cosine.
         *
         * A degraded copy of the same recording sits well above this against the trained model;
         * an unrelated track from the same artist sits below it. Tune on the duplicate-pair
         * benchmark before this path is made primary.
         */
        const val MIN_SIMILARITY = 0.55f

        /** How far the winner must lead the runner-up, so two near-identical masters do not flip. */
        const val MIN_MARGIN = 0.04f

        /** True if [bestSimilarity] clears the threshold and leads [runnerUpSimilarity] by at least [MIN_MARGIN]. */
        fun decide(bestSimilarity: Float, runnerUpSimilarity: Float): Boolean =
            bestSimilarity >= MIN_SIMILARITY && (bestSimilarity - runnerUpSimilarity) >= MIN_MARGIN

        /**
         * Finds the highest-scoring candidate in [candidates] (after the winner at index 0) that represents
         * a *different* recording than [bestUnified].
         *
         * If the runner-up is the same recording (e.g. Navidrome FLAC vs YouTube Music stream),
         * it must not steal the margin and cause a false rejection of a correct match.
         */
        internal suspend fun findCompetitor(
            bestUnified: UnifiedTrack?,
            candidates: List<Match>,
            splits: SplitSet = SplitSet.EMPTY,
            links: RecordingLinkSet = RecordingLinkSet.EMPTY,
            resolveTrack: suspend (String) -> UnifiedTrack?
        ): Match? {
            if (bestUnified == null) return candidates.getOrNull(1)
            for (i in 1 until candidates.size) {
                val candidate = candidates[i]
                val track = resolveTrack(candidate.trackId)
                if (track == null || !TrackDeduplicator.isSameRecording(bestUnified, track, splits, links)) {
                    return candidate
                }
            }
            return null
        }
    }
}


