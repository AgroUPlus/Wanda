package com.wander.android.data.repository

import android.util.Log
import com.wander.android.core.audio.fingerprint.AudioEmbedder
import com.wander.android.core.audio.fingerprint.AudioFormat
import com.wander.android.core.audio.fingerprint.OffsetAlignment
import com.wander.android.core.audio.fingerprint.SegmentVectors
import com.wander.android.core.audio.fingerprint.EmbeddingModelManager
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.dao.TrackEmbeddingDao
import com.wander.android.core.database.entity.TrackEmbeddingEntity
import com.wander.android.data.model.UnifiedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    /**
     * Whether the model this index is built with is on the device at all.
     *
     * Separate from [indexedTrackCount] being zero, and the difference matters to the person
     * holding the phone: an empty index fills itself in the background, a missing model never
     * will until they ask for the download.
     */
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
                    // Computed here, once, rather than derived at query time: it is the index a
                    // match is shortlisted by, and re-deriving it would mean reading exactly the
                    // vectors the shortlist exists to avoid reading.
                    centroid = AudioEmbedder.packVector(meanOf(flat)),
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
     *
     * Called by the indexer rather than by a match, so the cost of reading those rows once is paid
     * in the background instead of on the first tap after an upgrade. Until a row is done it is
     * shortlisted unconditionally, so this is a performance backfill and never a correctness one.
     * Returns how many were filled in.
     */
    suspend fun backfillCentroids(limit: Int = CENTROID_BACKFILL_LIMIT): Int =
        withContext(Dispatchers.Default) {
            val missing = withContext(Dispatchers.IO) {
                embeddingDao.centroids(AudioEmbedder.MODEL_NAME, AudioEmbedder.EMBEDDER_VERSION)
                    .filter { it.centroid == null }
                    .take(limit)
                    .map { it.trackId }
            }
            if (missing.isEmpty()) return@withContext 0

            var filled = 0
            for (ids in missing.chunked(FETCH_CHUNK)) {
                val rows = withContext(Dispatchers.IO) {
                    embeddingDao.getForTracks(
                        ids, AudioEmbedder.MODEL_NAME, AudioEmbedder.EMBEDDER_VERSION
                    )
                }
                for (entity in rows) {
                    val segments = entity.vector.size / (AudioEmbedder.EMBED_DIM * 4)
                    if (segments == 0) continue
                    val flat = FloatArray(segments * AudioEmbedder.EMBED_DIM)
                    AudioEmbedder.unpackInto(entity.vector, flat)
                    val mean = meanOf(SegmentVectors(flat, segments))
                    withContext(Dispatchers.IO) {
                        embeddingDao.setCentroid(entity.trackId, AudioEmbedder.packVector(mean))
                    }
                    filled++
                }
            }
            Log.i(TAG, "Backfilled $filled centroid(s)")
            filled
        }

    /**
     * Which of this device's tracks still need an embedding under the current model.
     *
     * Includes tracks that have one covering only part of their length — see
     * [TrackEmbeddingDao.needingIndex]. Every row written before indexing read past the first
     * minute is such a track, so this list is long the first time it is asked after that change
     * and then settles.
     */
    suspend fun needingIndex(limit: Int): Set<String> = withContext(Dispatchers.IO) {
        if (!embedder.isAvailable()) return@withContext emptySet()
        embeddingDao.needingIndex(
            model = AudioEmbedder.MODEL_NAME,
            version = AudioEmbedder.EMBEDDER_VERSION,
            limit = limit,
            bytesPerSegment = AudioEmbedder.EMBED_DIM * 4,
            segmentHopMs = SEGMENT_HOP_MS,
            coverageToleranceMs = COVERAGE_TOLERANCE_MS
        ).toSet()
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
    suspend fun match(
        samples: FloatArray,
        minSimilarity: Float = MIN_SIMILARITY,
        minMargin: Float = MIN_MARGIN
    ): Match? = withContext(Dispatchers.Default) {
        if (!embedder.isAvailable()) return@withContext null
        val query = AudioEmbedder.flatten(embedder.embed(samples))
        if (query.segments == 0) return@withContext null

        val ranked = shortlistAndScore(query)
        if (ranked.isEmpty()) return@withContext null

        val best = ranked.first()
        if (best.similarity < minSimilarity) {
            Log.i(TAG, "Embedding pass: ${ranked.size} candidates, best ${best.trackId} " +
                "at ${"%.3f".format(best.similarity)} below floor $minSimilarity")
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
        if (best.similarity - runnerUp < minMargin) null else best
    }

    /**
     * The shortlist, then the real comparison — best first.
     *
     * ## Why there are two stages
     *
     * Comparing a clip against every segment of every track meant opening the whole table on every
     * attempt. Measured on a 1384-track library: **18.6 s** to read 84 MB of BLOBs out of SQLite,
     * **23.8 s** to turn those bytes into floats, and **8.0 s** of actual arithmetic. Making the
     * arithmetic faster was solving the smallest of the three problems; the answer is to stop
     * reading the other 1300 tracks.
     *
     * Stage one ranks every track by the cosine between the clip's mean vector and the track's
     * stored [TrackEmbeddingEntity.centroid] — 700 KB read and ~180,000 multiply-adds, milliseconds.
     * Stage two opens only the top [SHORTLIST] and does the full segment-level comparison there.
     *
     * ## Why the shortlist is safe
     *
     * A centroid is a lossy summary and could in principle rank the right track below the cut.
     * Measured against this user's own 1382-track index, over 400 random six-second excerpts: the
     * true track ranked **first** for 94.5% of them, inside the top 10 for 99.5%, and inside the
     * top 80 for **100%** — worst observed rank 45. [SHORTLIST] is set well above that worst case.
     *
     * The failure mode if it ever is not deep enough is a miss, not a wrong answer: stage two
     * still applies the full threshold and margin to what it is given, so a shortlist that drops
     * the right track produces "no match", which is what the user would have been told by a
     * genuine failure anyway.
     *
     * Rows with no centroid yet are shortlisted unconditionally — see [TrackEmbeddingDao.centroids].
     */
    private suspend fun shortlistAndScore(query: SegmentVectors): List<Match> {
        val clipMean = meanOf(query)

        val centroids = withContext(Dispatchers.IO) {
            embeddingDao.centroids(AudioEmbedder.MODEL_NAME, AudioEmbedder.EMBEDDER_VERSION)
        }
        if (centroids.isEmpty()) return emptyList()

        val unmeasured = ArrayList<String>()
        val ranked = ArrayList<Pair<String, Float>>(centroids.size)
        val centroid = FloatArray(AudioEmbedder.EMBED_DIM)
        for (row in centroids) {
            val packed = row.centroid
            if (packed == null || packed.size != AudioEmbedder.EMBED_DIM * 4) {
                unmeasured += row.trackId
                continue
            }
            AudioEmbedder.unpackInto(packed, centroid)
            ranked += row.trackId to dot(clipMean, 0, centroid, 0)
        }
        ranked.sortByDescending { it.second }

        val shortlist = LinkedHashSet<String>(unmeasured)
        for (i in 0 until minOf(SHORTLIST, ranked.size)) shortlist += ranked[i].first

        val scored = ArrayList<Match>(shortlist.size)
        var track = FloatArray(INITIAL_TRACK_BUFFER * AudioEmbedder.EMBED_DIM)
        for (ids in shortlist.chunked(FETCH_CHUNK)) {
            val rows = withContext(Dispatchers.IO) {
                embeddingDao.getForTracks(
                    ids, AudioEmbedder.MODEL_NAME, AudioEmbedder.EMBEDDER_VERSION
                )
            }
            for (entity in rows) {
                val segments = entity.vector.size / (AudioEmbedder.EMBED_DIM * 4)
                if (segments == 0) continue
                if (track.size < segments * AudioEmbedder.EMBED_DIM) {
                    track = FloatArray(segments * AudioEmbedder.EMBED_DIM)
                }
                AudioEmbedder.unpackInto(entity.vector, track)
                scored += score(query, SegmentVectors(track, segments), entity.trackId)
            }
        }
        scored.sortByDescending { it.similarity }
        Log.i(
            TAG,
            "Shortlisted ${shortlist.size} of ${centroids.size} tracks" +
                if (unmeasured.isEmpty()) "" else " (${unmeasured.size} without a centroid yet)"
        )
        return scored
    }

    companion object {
        private const val TAG = "EmbeddingMatch"

        /**
         * One track's score, and where in it the clip started.
         *
         * The similarity is unchanged: the mean over clip segments of the best cosine against any
         * segment of the track. The **position** is not. It used to be the single best-matching track
         * segment for clip segment 0 — one `argmax` out of one comparison, with nothing to check it
         * against, so a single noisy half-second decided the number the user was shown.
         *
         * Every clip segment now votes. If the clip really starts `k` segments into the track then
         * segment `i` of the clip matches segment `i + k` of the track, so each segment's best match
         * implies an offset, and the offset they agree on is the answer — the same reasoning
         * [OffsetAlignment] applies to landmarks, and it tolerates individual segments that match the
         * wrong place because a repeated chorus put a near-identical half-second elsewhere.
         */
        /**
         * The mean of [vectors]' segments, L2-normalised back onto the unit sphere.
         *
         * Normalised because everything else here is: the comparison is a dot product standing in
         * for a cosine, and a mean of unit vectors is not itself one — its length says how much
         * the segments agree, which would otherwise leak into every score as a bias towards
         * monotonous tracks. A mean of exactly zero cannot be normalised and is left as is; it
         * matches nothing, which is the correct behaviour for a track with no coherent content.
         */
        internal fun meanOf(vectors: SegmentVectors): FloatArray {
            val mean = FloatArray(AudioEmbedder.EMBED_DIM)
            for (i in 0 until vectors.segments) {
                val base = i * AudioEmbedder.EMBED_DIM
                for (d in 0 until AudioEmbedder.EMBED_DIM) mean[d] += vectors.values[base + d]
            }
            var norm = 0f
            for (x in mean) norm += x * x
            norm = kotlin.math.sqrt(norm)
            if (norm > 0f) for (d in mean.indices) mean[d] /= norm
            return mean
        }

        internal fun score(query: SegmentVectors, track: SegmentVectors, trackId: String): Match {
            var total = 0f
            val votes = HashMap<Int, Int>(query.segments * 2)
            for (qi in 0 until query.segments) {
                var best = -1f
                var bestJ = 0
                val qBase = qi * AudioEmbedder.EMBED_DIM
                for (j in 0 until track.segments) {
                    val s = dot(query.values, qBase, track.values, j * AudioEmbedder.EMBED_DIM)
                    if (s > best) {
                        best = s
                        bestJ = j
                    }
                }
                total += best
                val offset = bestJ - qi
                votes[offset] = (votes[offset] ?: 0) + 1
            }
            // Negative offsets are real — the clip can start before the indexed audio does — but a
            // position is a place in a file and cannot be one, so it clamps rather than being dropped.
            val offset = OffsetAlignment.best(votes)?.offsetFrames ?: 0
            return Match(
                trackId = trackId,
                similarity = total / query.segments,
                positionSeconds =
                    (offset.coerceAtLeast(0) * AudioEmbedder.HOP_SAMPLES) / AudioFormat.SAMPLE_RATE
            )
        }

        /**
         * Cosine of two unit vectors, addressed by offset into flat arrays.
         *
         * The inner loop of the whole feature — a few hundred million iterations per match — so it
         * takes indices rather than slices: a `copyOfRange` per comparison would allocate more than
         * the arithmetic costs. Both sides are L2-normalised on the way in, so the dot product *is*
         * the cosine and no division is needed.
         */
        private fun dot(a: FloatArray, aFrom: Int, b: FloatArray, bFrom: Int): Float {
            var s = 0f
            for (i in 0 until AudioEmbedder.EMBED_DIM) s += a[aFrom + i] * b[bFrom + i]
            return s
        }

        /**
         * How many tracks the centroid stage passes on to the full comparison.
         *
         * The worst true-track rank measured over 400 excerpts of this library's own index was 45,
         * and top-80 recall was 100%. Set above that with room to spare: overshooting costs a few
         * milliseconds per extra track, while undershooting costs a match.
         */
        const val SHORTLIST = 128

        /** Track ids per `IN (...)`, kept clear of SQLite's 999-parameter limit. */
        private const val FETCH_CHUNK = 100

        /** Rows given a centroid per indexer run. See [backfillCentroids]. */
        private const val CENTROID_BACKFILL_LIMIT = 2_000

        /** Segments the track buffer starts at — a 60 s track, grown in place for longer ones. */
        private const val INITIAL_TRACK_BUFFER = 120

        /** Milliseconds of track each stored segment advances — `HOP_SAMPLES` at the sample rate. */
        const val SEGMENT_HOP_MS =
            AudioEmbedder.HOP_SAMPLES * 1_000 / AudioFormat.SAMPLE_RATE

        /**
         * How far short of a track's declared duration its vectors may stop and still count.
         *
         * Covers the half-second the segmentation rounds off, plus the routine disagreement
         * between the duration in a container's header and what actually decodes out of it —
         * neither of which is unfinished indexing, and both of which would otherwise re-queue a
         * finished track on every sweep for ever.
         */
        const val COVERAGE_TOLERANCE_MS = 5_000

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

        /**
         * The bar a *partial* clip has to clear to end the recording early.
         *
         * [MIN_SIMILARITY] and [MIN_MARGIN] were tuned on six seconds of audio. Two seconds is
         * three or four segments, and a mean over three segments is a far noisier estimate than a
         * mean over eleven — the same threshold applied to it accepts things it should not, which
         * is the one failure a recogniser must not trade speed for. Less evidence, higher bar; a
         * clip that does not clear it simply keeps recording and is judged normally at the end.
         */
        const val EARLY_MIN_SIMILARITY = 0.70f
        const val EARLY_MIN_MARGIN = 0.08f

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


