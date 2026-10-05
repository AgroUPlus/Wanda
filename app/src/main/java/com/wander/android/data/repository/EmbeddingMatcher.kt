package com.wander.android.data.repository

import android.content.Context
import android.util.Log
import com.wander.android.BuildConfig
import com.wander.android.core.audio.fingerprint.AudioEmbedder
import com.wander.android.core.audio.fingerprint.SegmentVectors
import com.wander.android.core.database.dao.CatalogRecordingDao
import com.wander.android.core.database.dao.Centroid
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
    private val recordingRules: RecordingRulesRepository,
    private val catalogDao: CatalogRecordingDao
) {
    private val tag = "EmbeddingMatch"

    private companion object {
        /** How close a library copy must score to a catalogue twin to be preferred over it. */
        const val OWNED_TIE = 0.03f
    }

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

        val rules = recordingRules.current()
        val bestTrack = named(best.trackId)
        val competitor = EmbeddingScorer.findCompetitor(bestTrack, ranked, rules) { named(it) }

        val runnerUp = competitor?.similarity ?: 0f
        Log.i(tag, "Embedding pass: ${ranked.size} candidates, best ${best.trackId} " +
            "at ${"%.3f".format(best.similarity)} (aligned ${"%.3f".format(best.alignment)}), " +
            "runner-up ${competitor?.trackId ?: "none"} at ${"%.3f".format(runnerUp)} " +
            "(aligned ${"%.3f".format(competitor?.alignment ?: 0f)})")
        if (best.similarity - runnerUp < minMargin) return@withContext null
        // A recording the library holds is the answer to give, not its catalogue twin: it plays
        // from where the user already has it.
        if (!best.trackId.startsWith(CatalogRecordingDao.PREFIX) || bestTrack == null) return@withContext best
        ranked.firstOrNull { candidate ->
            !candidate.trackId.startsWith(CatalogRecordingDao.PREFIX) &&
                best.similarity - candidate.similarity <= OWNED_TIE &&
                named(candidate.trackId)?.let { rules.isSame(bestTrack, it) } == true
        } ?: best
    }

    /** A track or catalogue recording as something comparable, or null once it has gone. */
    private suspend fun named(id: String) = withContext(Dispatchers.IO) {
        if (id.startsWith(CatalogRecordingDao.PREFIX)) {
            catalogDao.byId(id.removePrefix(CatalogRecordingDao.PREFIX))?.toStubTrack()
        } else {
            trackDao.getTrackById(id)?.toUnifiedTrack()
        }
    }

    private suspend fun shortlistAndScore(query: SegmentVectors): List<EmbeddingRepository.Match> {
        val clipMean = AudioEmbedder.pack(arrayOf(EmbeddingScorer.meanOf(query)))

        val centroids = withContext(Dispatchers.IO) {
            embeddingDao.centroids(AudioEmbedder.MODEL_NAME, AudioEmbedder.EMBEDDER_VERSION) +
                catalogDao.centroids(AudioEmbedder.MODEL_NAME, AudioEmbedder.EMBEDDER_VERSION)
        }
        if (centroids.isEmpty()) return emptyList()

        val (unmeasured, ranked) = rankByCentroid(clipMean, centroids)
        val shortlist = LinkedHashSet<String>(unmeasured)
        ranked.take(EmbeddingScorer.SHORTLIST).forEach { shortlist += it.first }

        val (candidates, candidateIds) = loadCandidates(shortlist)
        val scored = scoreCandidates(query, candidates, candidateIds)
        Log.i(
            tag,
            "Shortlisted ${shortlist.size} of ${centroids.size} tracks, " +
                "scored ${scored.size} in full" +
                if (unmeasured.isEmpty()) "" else " (${unmeasured.size} without a centroid yet)"
        )
        return scored
    }

    /** Splits [centroids] into the ids with no usable centroid yet and the rest, best match first. */
    private fun rankByCentroid(clipMean: ByteArray, centroids: List<Centroid>): Pair<List<String>, List<Pair<String, Float>>> {
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
        return unmeasured to ranked
    }

    private suspend fun loadCandidates(shortlist: Set<String>): Pair<List<SegmentVectors>, List<String>> {
        val candidates = ArrayList<SegmentVectors>(shortlist.size)
        val candidateIds = ArrayList<String>(shortlist.size)
        val (recordings, tracks) = shortlist.partition { it.startsWith(CatalogRecordingDao.PREFIX) }
        for (ids in tracks.chunked(EmbeddingScorer.FETCH_CHUNK)) {
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
        for (ids in recordings.map { it.removePrefix(CatalogRecordingDao.PREFIX) }.chunked(EmbeddingScorer.FETCH_CHUNK)) {
            val rows = withContext(Dispatchers.IO) {
                catalogDao.vectors(ids, AudioEmbedder.MODEL_NAME, AudioEmbedder.EMBEDDER_VERSION)
            }
            for (row in rows) {
                val segments = row.vector.size / AudioEmbedder.EMBED_DIM
                if (segments == 0) continue
                candidates += SegmentVectors(row.vector, segments)
                candidateIds += CatalogRecordingDao.PREFIX + row.recordingId
            }
        }
        return candidates to candidateIds
    }

    /** A cheap coarse pass prunes the candidates; only the survivors get the full score. */
    private fun scoreCandidates(
        query: SegmentVectors,
        candidates: List<SegmentVectors>,
        candidateIds: List<String>
    ): List<EmbeddingRepository.Match> {
        // A catalogue recording is stored at half the density, so it is scored against the clip
        // at the same density; otherwise its hop and the clip's would disagree.
        val thinQuery = EmbeddingScorer.decimate(query)
        fun queryFor(id: String) = if (id.startsWith(CatalogRecordingDao.PREFIX)) thinQuery else query
        val coarse = EmbeddingScorer.coarseQuery(query)
        val thinCoarse = EmbeddingScorer.coarseQuery(thinQuery)
        val coarseScores = FloatArray(candidates.size) {
            val id = candidateIds[it]
            EmbeddingScorer.score(if (queryFor(id) === query) coarse else thinCoarse, candidates[it], id).similarity
        }
        val survivors = candidates.indices
            .sortedByDescending { coarseScores[it] }
            .take(EmbeddingScorer.COARSE_KEEP)

        val scored = survivors.mapTo(ArrayList(survivors.size)) {
            val id = candidateIds[it]
            val match = EmbeddingScorer.score(queryFor(id), candidates[it], id)
            // Positions are counted in the stored hop, which is longer for a catalogue recording.
            if (queryFor(id) === query) match
            else match.copy(positionSeconds = match.positionSeconds * EmbeddingScorer.CATALOG_DECIMATION)
        }
        scored.sortByDescending { it.similarity }
        return scored
    }
}
