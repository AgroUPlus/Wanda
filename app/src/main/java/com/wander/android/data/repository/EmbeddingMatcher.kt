package com.wander.android.data.repository

import android.content.Context
import android.util.Log
import com.wander.android.BuildConfig
import com.wander.android.core.audio.fingerprint.AudioEmbedder
import com.wander.android.core.audio.fingerprint.SegmentVectors
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.dao.TrackEmbeddingDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Handles shortlisting, coarse scoring, and multi-stage neural fingerprint matching against indexed tracks.
 */
internal class EmbeddingMatcher(
    private val context: Context,
    private val embeddingDao: TrackEmbeddingDao,
    private val embedder: AudioEmbedder,
    private val trackDao: TrackDao,
    private val recordingRules: RecordingRulesRepository
) {
    private val tag = "EmbeddingMatch"

    private fun dumpQueryForParity(query: SegmentVectors) {
        runCatching {
            val dir = File(context.filesDir, "capture").apply { mkdirs() }
            File(dir, "last-listen-embedding.i8").writeBytes(
                query.values.copyOf(query.segments * AudioEmbedder.EMBED_DIM)
            )
        }
    }

    suspend fun match(
        samples: FloatArray,
        minSimilarity: Float = EmbeddingScorer.MIN_SIMILARITY,
        minMargin: Float = EmbeddingScorer.MIN_MARGIN
    ): EmbeddingRepository.Match? = withContext(Dispatchers.Default) {
        if (!embedder.isAvailable()) return@withContext null
        val query = AudioEmbedder.flatten(embedder.embed(samples))
        if (query.segments == 0) return@withContext null
        if (BuildConfig.DEBUG) dumpQueryForParity(query)

        val ranked = shortlistAndScore(query)
        if (ranked.isEmpty()) return@withContext null

        val best = ranked.first()
        if (best.similarity < minSimilarity) {
            Log.i(tag, "Embedding pass: ${ranked.size} candidates, best ${best.trackId} " +
                "at ${"%.3f".format(best.similarity)} below floor $minSimilarity")
            return@withContext null
        }

        val bestTrack = withContext(Dispatchers.IO) { trackDao.getTrackById(best.trackId) }
        val competitor = EmbeddingScorer.findCompetitor(
            bestTrack?.toUnifiedTrack(),
            ranked,
            recordingRules.current()
        ) { tid ->
            withContext(Dispatchers.IO) { trackDao.getTrackById(tid)?.toUnifiedTrack() }
        }

        val runnerUp = competitor?.similarity ?: 0f
        Log.i(tag, "Embedding pass: ${ranked.size} candidates, best ${best.trackId} " +
            "at ${"%.3f".format(best.similarity)} (aligned ${"%.3f".format(best.alignment)}), " +
            "runner-up ${competitor?.trackId ?: "none"} at ${"%.3f".format(runnerUp)} " +
            "(aligned ${"%.3f".format(competitor?.alignment ?: 0f)})")
        if (best.similarity - runnerUp < minMargin) null else best
    }

    private suspend fun shortlistAndScore(query: SegmentVectors): List<EmbeddingRepository.Match> {
        val clipMean = AudioEmbedder.pack(arrayOf(EmbeddingScorer.meanOf(query)))

        val centroids = withContext(Dispatchers.IO) {
            embeddingDao.centroids(AudioEmbedder.MODEL_NAME, AudioEmbedder.EMBEDDER_VERSION)
        }
        if (centroids.isEmpty()) return emptyList()

        val unmeasured = ArrayList<String>()
        val ranked = ArrayList<Pair<String, Float>>(centroids.size)
        for (row in centroids) {
            val summary = row.centroid
            if (summary == null || summary.size < AudioEmbedder.EMBED_DIM) {
                unmeasured += row.trackId
                continue
            }
            val chunks = summary.size / AudioEmbedder.EMBED_DIM
            var best = -1f
            for (c in 0 until chunks) {
                val s = EmbeddingScorer.dot(clipMean, 0, summary, c * AudioEmbedder.EMBED_DIM)
                if (s > best) best = s
            }
            ranked += row.trackId to best
        }
        ranked.sortByDescending { it.second }

        val shortlist = LinkedHashSet<String>(unmeasured)
        for (i in 0 until minOf(EmbeddingScorer.SHORTLIST, ranked.size)) {
            shortlist += ranked[i].first
        }

        val candidates = ArrayList<SegmentVectors>(shortlist.size)
        val candidateIds = ArrayList<String>(shortlist.size)
        for (ids in shortlist.chunked(EmbeddingScorer.FETCH_CHUNK)) {
            val rows = withContext(Dispatchers.IO) {
                embeddingDao.getForTracks(
                    ids, AudioEmbedder.MODEL_NAME, AudioEmbedder.EMBEDDER_VERSION
                )
            }
            for (entity in rows) {
                val segments = entity.vector.size / AudioEmbedder.EMBED_DIM
                if (segments == 0) continue
                candidates += SegmentVectors(entity.vector, segments)
                candidateIds += entity.trackId
            }
        }

        val coarse = EmbeddingScorer.coarseQuery(query)
        val coarseScores = FloatArray(candidates.size) {
            EmbeddingScorer.score(coarse, candidates[it], candidateIds[it]).similarity
        }
        val survivors = candidates.indices
            .sortedByDescending { coarseScores[it] }
            .take(EmbeddingScorer.COARSE_KEEP)

        val scored = survivors.mapTo(ArrayList(survivors.size)) {
            EmbeddingScorer.score(query, candidates[it], candidateIds[it])
        }
        scored.sortByDescending { it.similarity }
        Log.i(
            tag,
            "Shortlisted ${shortlist.size} of ${centroids.size} tracks, " +
                "scored ${scored.size} in full" +
                if (unmeasured.isEmpty()) "" else " (${unmeasured.size} without a centroid yet)"
        )
        return scored
    }
}
