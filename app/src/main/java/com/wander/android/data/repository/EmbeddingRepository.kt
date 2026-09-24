package com.wander.android.data.repository

import android.content.Context
import android.util.Log
import com.wander.android.core.audio.fingerprint.AudioEmbedder
import com.wander.android.core.audio.fingerprint.EmbeddingModelManager
import com.wander.android.core.audio.fingerprint.SegmentVectors
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.dao.TrackEmbeddingDao
import com.wander.android.core.database.entity.TrackEmbeddingEntity
import com.wander.android.data.model.UnifiedTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the neural audio-fingerprint index: when a track's embedding is computed, where it is
 * kept, and how a microphone clip is matched against the collection.
 */
@Singleton
class EmbeddingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val embeddingDao: TrackEmbeddingDao,
    private val embedder: AudioEmbedder,
    private val trackDao: TrackDao,
    private val recordingRules: RecordingRulesRepository
) {
    private val matcher = EmbeddingMatcher(context, embeddingDao, embedder, trackDao, recordingRules)

    val indexedTrackCount: Flow<Int> =
        embeddingDao.indexedTrackCountFlow(AudioEmbedder.MODEL_NAME, AudioEmbedder.EMBEDDER_VERSION)

    val modelReady: Flow<Boolean> =
        embedder.modelState.map { it is EmbeddingModelManager.State.Ready }

    /** Computes [samples]'s embedding sequence and stores it. A track that embeds to nothing is skipped. */
    suspend fun index(trackId: String, samples: FloatArray) = withContext(Dispatchers.Default) {
        if (!embedder.isAvailable()) return@withContext
        val vectors = embedder.embed(samples)
        if (vectors.isEmpty()) return@withContext
        val flat = AudioEmbedder.flatten(vectors)
        withContext(Dispatchers.IO) {
            embeddingDao.upsert(
                TrackEmbeddingEntity(
                    trackId = trackId,
                    vector = AudioEmbedder.pack(vectors),
                    centroid = AudioEmbedder.pack(EmbeddingScorer.summaryOf(flat)),
                    dim = AudioEmbedder.EMBED_DIM,
                    model = AudioEmbedder.MODEL_NAME,
                    version = AudioEmbedder.EMBEDDER_VERSION,
                    computedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Gives centroids to rows written before the column existed, oldest shape first.
     */
    suspend fun backfillCentroids(limit: Int = EmbeddingScorer.CENTROID_BACKFILL_LIMIT): Int =
        withContext(Dispatchers.Default) {
            val missing = withContext(Dispatchers.IO) {
                embeddingDao.centroids(AudioEmbedder.MODEL_NAME, AudioEmbedder.EMBEDDER_VERSION)
                    .filter { it.centroid == null }
                    .take(limit)
                    .map { it.trackId }
            }
            if (missing.isEmpty()) return@withContext 0

            var filled = 0
            for (ids in missing.chunked(EmbeddingScorer.FETCH_CHUNK)) {
                val rows = withContext(Dispatchers.IO) {
                    embeddingDao.getForTracks(
                        ids, AudioEmbedder.MODEL_NAME, AudioEmbedder.EMBEDDER_VERSION
                    )
                }
                for (entity in rows) {
                    val segments = entity.vector.size / AudioEmbedder.EMBED_DIM
                    if (segments == 0) continue
                    val summary = EmbeddingScorer.summaryOf(SegmentVectors(entity.vector, segments))
                    withContext(Dispatchers.IO) {
                        embeddingDao.setCentroid(entity.trackId, AudioEmbedder.pack(summary))
                    }
                    filled++
                }
            }
            Log.i(TAG, "Backfilled $filled centroid(s)")
            filled
        }

    suspend fun needingIndex(limit: Int): Set<String> = withContext(Dispatchers.IO) {
        if (!embedder.isAvailable()) return@withContext emptySet()
        embeddingDao.needingIndex(
            model = AudioEmbedder.MODEL_NAME,
            version = AudioEmbedder.EMBEDDER_VERSION,
            limit = limit,
            bytesPerSegment = AudioEmbedder.EMBED_DIM,
            segmentHopMs = EmbeddingScorer.SEGMENT_HOP_MS,
            coverageToleranceMs = EmbeddingScorer.COVERAGE_TOLERANCE_MS
        ).toSet()
    }

    suspend fun prune() = withContext(Dispatchers.IO) {
        embeddingDao.prune(AudioEmbedder.MODEL_NAME, AudioEmbedder.EMBEDDER_VERSION)
    }

    suspend fun clear() = withContext(Dispatchers.IO) { embeddingDao.clear() }

    /** One track the clip resembled, best first. */
    data class Match(
        val trackId: String,
        val similarity: Float,
        val positionSeconds: Int,
        val alignment: Float = 0f
    )

    suspend fun match(
        samples: FloatArray,
        minSimilarity: Float = MIN_SIMILARITY,
        minMargin: Float = MIN_MARGIN
    ): Match? = matcher.match(samples, minSimilarity, minMargin)

    companion object {
        private const val TAG = "EmbeddingMatch"

        const val SHORTLIST = EmbeddingScorer.SHORTLIST
        const val SUMMARY_CHUNK_SEGMENTS = EmbeddingScorer.SUMMARY_CHUNK_SEGMENTS
        const val SEGMENT_HOP_MS = EmbeddingScorer.SEGMENT_HOP_MS
        const val COVERAGE_TOLERANCE_MS = EmbeddingScorer.COVERAGE_TOLERANCE_MS
        const val MIN_SIMILARITY = EmbeddingScorer.MIN_SIMILARITY
        const val MIN_MARGIN = EmbeddingScorer.MIN_MARGIN
        const val EARLY_MIN_SIMILARITY = EmbeddingScorer.EARLY_MIN_SIMILARITY
        const val EARLY_MIN_MARGIN = EmbeddingScorer.EARLY_MIN_MARGIN

        fun decide(bestSimilarity: Float, runnerUpSimilarity: Float): Boolean =
            EmbeddingScorer.decide(bestSimilarity, runnerUpSimilarity)

        fun meanOf(vectors: SegmentVectors, from: Int = 0, until: Int = vectors.segments): FloatArray =
            EmbeddingScorer.meanOf(vectors, from, until)

        fun summaryOf(vectors: SegmentVectors): Array<FloatArray> =
            EmbeddingScorer.summaryOf(vectors)

        fun coarseQuery(query: SegmentVectors): SegmentVectors =
            EmbeddingScorer.coarseQuery(query)

        fun score(query: SegmentVectors, track: SegmentVectors, trackId: String): Match =
            EmbeddingScorer.score(query, track, trackId)

        suspend fun findCompetitor(
            bestUnified: UnifiedTrack?,
            candidates: List<Match>,
            rules: RecordingRules = RecordingRules.NONE,
            resolveTrack: suspend (String) -> UnifiedTrack?
        ): Match? = EmbeddingScorer.findCompetitor(bestUnified, candidates, rules, resolveTrack)
    }
}
