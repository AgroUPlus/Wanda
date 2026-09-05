package com.wander.android.data.repository

import android.util.Log
import com.wander.android.BuildConfig
import com.wander.android.core.audio.fingerprint.AudioEmbedder
import com.wander.android.core.audio.fingerprint.AudioFormat
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
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
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
                    centroid = AudioEmbedder.pack(summaryOf(flat)),
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
                    val segments = entity.vector.size / AudioEmbedder.EMBED_DIM
                    if (segments == 0) continue
                    val summary = summaryOf(SegmentVectors(entity.vector, segments))
                    withContext(Dispatchers.IO) {
                        embeddingDao.setCentroid(entity.trackId, AudioEmbedder.pack(summary))
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
            bytesPerSegment = AudioEmbedder.EMBED_DIM,
            segmentHopMs = SEGMENT_HOP_MS,
            coverageToleranceMs = COVERAGE_TOLERANCE_MS
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
        /**
         * The mean cosine along the winning alignment — see [score].
         *
         * Carried but not yet acted on. It is a strictly sharper discriminator than [similarity]:
         * on 120 excerpts of a real index it left the true track at 1.000 while dropping the best
         * impostor from 0.511 to 0.404. Acting on it means new thresholds, and [MIN_SIMILARITY]
         * was tuned against real room captures rather than clean excerpts — so it is logged first,
         * on real captures, and gated afterwards.
         */
        val alignment: Float = 0f
    )

    /**
     * Writes the clip's own embedding beside the clip, in debug builds only.
     *
     * `MicRecorder.dumpForDiagnosis` already keeps `capture/last-listen.f32`, the exact float
     * array the matcher was handed, for the reason that a failed recognition is otherwise
     * unarguable from the outside. This is the other half of the same idea and answers a question
     * that has no other answer: whether the desktop indexer computes the *same vectors* for the
     * same audio.
     *
     * It has to, or nothing works — a track measured on a laptop is compared against a clip
     * measured on the phone, and a difference in quantisation or in where the segment boundaries
     * fall does not degrade the match, it destroys it while leaving both sides looking correct in
     * isolation. With this file and `capture/last-listen.f32`, `tools/check_parity.py` can feed
     * the identical input to `core/embedder.py` and compare, which is the only way to see it.
     */
    private fun dumpQueryForParity(query: SegmentVectors) {
        runCatching {
            val dir = java.io.File(context.filesDir, "capture").apply { mkdirs() }
            // Byte for byte what a stored `vector` BLOB holds, so the desktop side can compare
            // against its own output with nothing in between to get wrong.
            java.io.File(dir, "last-listen-embedding.i8").writeBytes(
                query.values.copyOf(query.segments * AudioEmbedder.EMBED_DIM)
            )
        }
    }

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
        if (BuildConfig.DEBUG) dumpQueryForParity(query)

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
            "at ${"%.3f".format(best.similarity)} (aligned ${"%.3f".format(best.alignment)}), " +
            "runner-up ${competitor?.trackId ?: "none"} at ${"%.3f".format(runnerUp)} " +
            "(aligned ${"%.3f".format(competitor?.alignment ?: 0f)})")
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
     * **One** ranks every track by the best cosine between the clip's mean vector and any of the
     * track's stored [TrackEmbeddingEntity.centroid] chunk means — a megabyte read and a few
     * thousand multiply-adds. The top [SHORTLIST] go on.
     *
     * **Two** reads those rows and scores them with [COARSE_SEGMENTS] evenly spaced clip segments
     * instead of all of them, keeping the top [COARSE_KEEP]. The rows are read once and serve both
     * this pass and the next, so this buys arithmetic and costs no I/O.
     *
     * **Three** scores the survivors with the whole clip, which is what the thresholds judge.
     *
     * ## Why each cut is safe
     *
     * Both were measured against this user's own index rather than reasoned about, and the second
     * measurement corrected the first. A chunk mean is a lossy summary and can rank the right
     * track below the cut: over 400 six-second excerpts of the **full-length** index (median 3.0
     * minutes a track), top-32 recall was 99.75% and the worst true-track rank was 33 — so the
     * shortlist of 32 that a 60-second index had justified was dropping roughly one clip in four
     * hundred, invisibly. [SHORTLIST] is now three times that worst case.
     *
     * That margin is why chunking exists at all. The same measurement against a single whole-track
     * mean gave a worst rank of **524** — a shortlist deep enough to be safe would have had to
     * open a third of the library, which is the cost the shortlist is here to avoid.
     *
     * The coarse pass is the cheaper of the two cuts to justify: over 300 excerpts, three clip
     * segments put the true track no lower than **rank 1** of a 128-track shortlist. [COARSE_KEEP]
     * is twenty-four.
     *
     * The failure mode of either being too tight is a miss, not a wrong answer: the last pass
     * still applies the full threshold and margin to what it is given, so a cut that drops the
     * right track produces "no match" — which is exactly what a song that is genuinely not in the
     * library produces, and exactly why a cut that is too tight is invisible from the outside.
     *
     * Rows with no centroid yet are shortlisted unconditionally — see [TrackEmbeddingDao.centroids].
     */
    private suspend fun shortlistAndScore(query: SegmentVectors): List<Match> {
        val clipMean = AudioEmbedder.pack(arrayOf(meanOf(query)))

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
            // The best chunk, not the average of them: the clip is somewhere in this track, and a
            // track that matches its bridge exactly should not be discounted for the four minutes
            // that surround it.
            var best = -1f
            for (c in 0 until chunks) {
                val s = dot(clipMean, 0, summary, c * AudioEmbedder.EMBED_DIM)
                if (s > best) best = s
            }
            ranked += row.trackId to best
        }
        ranked.sortByDescending { it.second }

        val shortlist = LinkedHashSet<String>(unmeasured)
        for (i in 0 until minOf(SHORTLIST, ranked.size)) shortlist += ranked[i].first

        // Read once. Both remaining passes walk these same bytes — the BLOB *is* the working
        // array, stored and compared in the same form — so re-reading for the second would be
        // paying the expensive part twice to save the cheap one.
        val candidates = ArrayList<SegmentVectors>(shortlist.size)
        val candidateIds = ArrayList<String>(shortlist.size)
        for (ids in shortlist.chunked(FETCH_CHUNK)) {
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

        // Scored into an array first, then sorted by index. `sortedByDescending { score(...) }`
        // reads like one score per candidate and is not: the selector is invoked on every
        // *comparison*, so ninety-six candidates cost some seven hundred scorings and the cheap
        // pass becomes several times more expensive than the expensive one it feeds.
        val coarse = coarseQuery(query)
        val coarseScores = FloatArray(candidates.size) {
            score(coarse, candidates[it], candidateIds[it]).similarity
        }
        val survivors = candidates.indices
            .sortedByDescending { coarseScores[it] }
            .take(COARSE_KEEP)

        val scored = survivors.mapTo(ArrayList(survivors.size)) {
            score(query, candidates[it], candidateIds[it])
        }
        scored.sortByDescending { it.similarity }
        Log.i(
            TAG,
            "Shortlisted ${shortlist.size} of ${centroids.size} tracks, " +
                "scored ${scored.size} in full" +
                if (unmeasured.isEmpty()) "" else " (${unmeasured.size} without a centroid yet)"
        )
        return scored
    }

    companion object {
        private const val TAG = "EmbeddingMatch"

        /**
         * The mean of [vectors]' segments, L2-normalised back onto the unit sphere.
         *
         * Normalised because everything else here is: the comparison is a dot product standing in
         * for a cosine, and a mean of unit vectors is not itself one — its length says how much
         * the segments agree, which would otherwise leak into every score as a bias towards
         * monotonous tracks. A mean of exactly zero cannot be normalised and is left as is; it
         * matches nothing, which is the correct behaviour for a track with no coherent content.
         */
        internal fun meanOf(vectors: SegmentVectors, from: Int = 0, until: Int = vectors.segments): FloatArray {
            val mean = FloatArray(AudioEmbedder.EMBED_DIM)
            for (i in from until until) {
                val base = i * AudioEmbedder.EMBED_DIM
                for (d in 0 until AudioEmbedder.EMBED_DIM) {
                    mean[d] += vectors.values[base + d] / AudioEmbedder.QUANT_SCALE
                }
            }
            var norm = 0f
            for (x in mean) norm += x * x
            norm = kotlin.math.sqrt(norm)
            if (norm > 0f) for (d in mean.indices) mean[d] /= norm
            return mean
        }

        /**
         * One mean per [SUMMARY_CHUNK_SEGMENTS] of the track, as the search index.
         *
         * A trailing part-chunk is folded into the one before it rather than kept: a summary over
         * three seconds is dominated by whatever happens to be in those three seconds, and it
         * would be compared against the same threshold as a summary over thirty.
         */
        internal fun summaryOf(vectors: SegmentVectors): Array<FloatArray> {
            if (vectors.segments <= 0) return emptyArray()
            val chunks = maxOf(1, vectors.segments / SUMMARY_CHUNK_SEGMENTS)
            return Array(chunks) { c ->
                val from = c * SUMMARY_CHUNK_SEGMENTS
                val until = if (c == chunks - 1) vectors.segments else from + SUMMARY_CHUNK_SEGMENTS
                meanOf(vectors, from, until)
            }
        }

        /**
         * One track's score, and where in it the clip started.
         *
         * ## The score
         *
         * The mean over clip segments of the best cosine against any segment of the track — high
         * only when the clip's whole sequence has a counterpart somewhere in the track, which a
         * coincidental timbre match on one segment cannot fake. Unchanged, because
         * [MIN_SIMILARITY] and [MIN_MARGIN] are tuned against this quantity.
         *
         * ## The position
         *
         * A different question, and it used to be answered by the single best-matching track
         * segment for clip segment 0 — one `argmax`, unchecked, deciding the number shown to the
         * user, so a repeated chorus could put it anywhere in the song.
         *
         * A clip is a *contiguous* run: if it starts `k` segments into the track then clip segment
         * `i` belongs at track segment `i + k`, for every `i` at once. So the answer is the offset
         * whose whole diagonal agrees best, which no single segment can outvote. Measured over 120
         * excerpts of this library its error was zero segments — median and worst alike.
         *
         * It costs no extra arithmetic: every cosine the score already computes belongs to exactly
         * one diagonal, and is added to it on the way past.
         *
         * That alignment is also a markedly better *discriminator* — on the same measurement it
         * drops the best impostor from 0.511 to 0.404, widening the separation from +0.489 to
         * +0.596. It is deliberately not used for acceptance here: that is a threshold change, and
         * the thresholds were tuned on real room captures rather than on clean excerpts, so it
         * wants its own tuning pass against the same.
         */
        /**
         * [COARSE_SEGMENTS] of [query], evenly spaced — the cheap pass's query.
         *
         * A copy rather than a stride, because [score]'s inner loop indexes segments contiguously
         * and teaching it to stride would slow the expensive pass to speed up the cheap one.
         */
        internal fun coarseQuery(query: SegmentVectors): SegmentVectors {
            val n = minOf(COARSE_SEGMENTS, query.segments)
            if (n == query.segments) return query
            val out = ByteArray(n * AudioEmbedder.EMBED_DIM)
            for (i in 0 until n) {
                val from = (i * (query.segments - 1) / (n - 1)) * AudioEmbedder.EMBED_DIM
                query.values.copyInto(out, i * AudioEmbedder.EMBED_DIM, from, from + AudioEmbedder.EMBED_DIM)
            }
            return SegmentVectors(out, n)
        }

        internal fun score(query: SegmentVectors, track: SegmentVectors, trackId: String): Match {
            var total = 0f
            // Only offsets that fit the clip entirely inside the track. A partial overlap scores
            // fewer segments and would win on nothing but being shorter.
            val offsets = track.segments - query.segments + 1
            val diagonal = FloatArray(maxOf(1, offsets))

            for (qi in 0 until query.segments) {
                var best = -1f
                val qBase = qi * AudioEmbedder.EMBED_DIM
                for (j in 0 until track.segments) {
                    val s = dot(query.values, qBase, track.values, j * AudioEmbedder.EMBED_DIM)
                    if (s > best) best = s
                    val offset = j - qi
                    if (offset >= 0 && offset < offsets) diagonal[offset] += s
                }
                total += best
            }

            var alignBest = Float.NEGATIVE_INFINITY
            var alignOffset = 0
            for (k in diagonal.indices) {
                if (diagonal[k] > alignBest) {
                    alignBest = diagonal[k]
                    alignOffset = k
                }
            }

            return Match(
                trackId = trackId,
                similarity = total / query.segments,
                positionSeconds =
                    (alignOffset * AudioEmbedder.HOP_SAMPLES) / AudioFormat.SAMPLE_RATE,
                alignment = if (diagonal.isEmpty()) 0f else alignBest / query.segments
            )
        }

        /**
         * Cosine of two unit vectors, addressed by offset into flat byte arrays.
         *
         * The inner loop of the whole feature — tens of millions of iterations per match — so it
         * takes indices rather than slices: a `copyOfRange` per comparison would allocate more than
         * the arithmetic costs. Both sides are L2-normalised before quantising, so the dot product
         * *is* the cosine once the two scales are divided out.
         *
         * Accumulated in `Int`. Each product is at most 127*127 and there are 128 of them, so the
         * sum cannot exceed ~2.1M and is exact — no float rounding enters the comparison at all,
         * which is why quantising costs so much less accuracy than the component precision alone
         * would suggest.
         */
        private fun dot(a: ByteArray, aFrom: Int, b: ByteArray, bFrom: Int): Float {
            var s = 0
            for (i in 0 until AudioEmbedder.EMBED_DIM) s += a[aFrom + i] * b[bFrom + i]
            return s / QUANT_SQUARED
        }

        /**
         * How many tracks the summary stage passes on to the full comparison.
         *
         * Every one of these costs a row read, which is most of what a match spends its time on
         * once the whole-table scan is gone — so this is the number that decides how fast
         * recognition is, and it wants to be as small as it can safely be.
         *
         * It was 32, measured against an index in which every track was 60 seconds and summarised
         * to a single chunk. Re-measured against the same library indexed **whole** — a median of
         * 3.0 minutes and five chunks a track — over 400 excerpts: top-32 recall 99.75%, top-64
         * 100%, worst true-track rank **33**. One in four hundred fell outside the old depth, and
         * a track outside it is simply never found; that is what "it works on some songs and not
         * others" looks like from the outside.
         *
         * 96 is three times the worst rank measured, and those excerpts are clean — a clip from a
         * room is noisier than any of them, so the tail is wider in practice than it is here. The
         * cost of being generous is a few hundred milliseconds; the cost of being tight is a
         * silent miss that looks exactly like a song not being in the library.
         */
        const val SHORTLIST = 96

        /** Divides both operands' fixed-point scales back out of an int8 dot product. */
        private const val QUANT_SQUARED = AudioEmbedder.QUANT_SCALE * AudioEmbedder.QUANT_SCALE

        /** Track ids per `IN (...)`, kept clear of SQLite's 999-parameter limit. */
        private const val FETCH_CHUNK = 100

        /**
         * Clip segments the coarse pass uses, evenly spaced across the clip.
         *
         * Spread rather than taken from the front: three consecutive half-seconds describe one
         * moment and a track that happens to contain a similar moment survives on it, while three
         * spread across six seconds have to agree about a span, which is most of what the full
         * pass is checking anyway.
         */
        private const val COARSE_SEGMENTS = 3

        /** How many survive the coarse pass. Measured worst rank there was 1; this is 24. */
        private const val COARSE_KEEP = 24

        /** Rows given a summary per indexer run. See [backfillCentroids]. */
        private const val CENTROID_BACKFILL_LIMIT = 2_000

        /**
         * Segments per summary chunk — 60 at a 0.5 s hop, so thirty seconds.
         *
         * Chosen by measurement, not by feel. Against this library's index, one summary per track
         * (a whole-track mean) gave a worst true-track rank of 524; at thirty seconds it was 2,
         * and going finer than that bought nothing while costing linearly more to store and scan.
         */
        const val SUMMARY_CHUNK_SEGMENTS = 60

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


