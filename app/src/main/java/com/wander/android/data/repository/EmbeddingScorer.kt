package com.wander.android.data.repository

import com.wander.android.core.audio.fingerprint.AudioEmbedder
import com.wander.android.core.audio.fingerprint.AudioFormat
import com.wander.android.core.audio.fingerprint.SegmentVectors
import com.wander.android.data.model.UnifiedTrack
import kotlin.math.sqrt

/**
 * Pure vector arithmetic and scoring operations for neural audio-fingerprint matching.
 */
internal object EmbeddingScorer {

    /** How many tracks the summary stage passes on to the full comparison. */
    const val SHORTLIST = 96

    /** Divides both operands' fixed-point scales back out of an int8 dot product. */
    const val QUANT_SQUARED = AudioEmbedder.QUANT_SCALE * AudioEmbedder.QUANT_SCALE

    /** Track ids per `IN (...)`, kept clear of SQLite's 999-parameter limit. */
    const val FETCH_CHUNK = 100

    /** Clip segments the coarse pass uses, evenly spaced across the clip. */
    const val COARSE_SEGMENTS = 3

    /** How many survive the coarse pass. Measured worst rank there was 1; this is 24. */
    const val COARSE_KEEP = 24

    /** Rows given a summary per indexer run. */
    const val CENTROID_BACKFILL_LIMIT = 2_000

    /** Segments per summary chunk — 60 at a 0.5 s hop, so thirty seconds. */
    const val SUMMARY_CHUNK_SEGMENTS = 60

    /** Milliseconds of track each stored segment advances — `HOP_SAMPLES` at the sample rate. */
    const val SEGMENT_HOP_MS = AudioEmbedder.HOP_SAMPLES * 1_000 / AudioFormat.SAMPLE_RATE

    /** How far short of a track's declared duration its vectors may stop and still count. */
    const val COVERAGE_TOLERANCE_MS = 5_000

    /** Floor on the mean best-segment cosine. */
    const val MIN_SIMILARITY = 0.55f

    /** How far the winner must lead the runner-up, so two near-identical masters do not flip. */
    const val MIN_MARGIN = 0.04f

    /** The bar a partial clip has to clear to end the recording early. */
    const val EARLY_MIN_SIMILARITY = 0.70f
    const val EARLY_MIN_MARGIN = 0.08f

    fun decide(bestSimilarity: Float, runnerUpSimilarity: Float): Boolean =
        bestSimilarity >= MIN_SIMILARITY && (bestSimilarity - runnerUpSimilarity) >= MIN_MARGIN

    fun meanOf(vectors: SegmentVectors, from: Int = 0, until: Int = vectors.segments): FloatArray {
        val mean = FloatArray(AudioEmbedder.EMBED_DIM)
        for (i in from until until) {
            val base = i * AudioEmbedder.EMBED_DIM
            for (d in 0 until AudioEmbedder.EMBED_DIM) {
                mean[d] += vectors.values[base + d] / AudioEmbedder.QUANT_SCALE
            }
        }
        var norm = 0f
        for (x in mean) norm += x * x
        norm = sqrt(norm)
        if (norm > 0f) for (d in mean.indices) mean[d] /= norm
        return mean
    }

    fun summaryOf(vectors: SegmentVectors): Array<FloatArray> {
        if (vectors.segments <= 0) return emptyArray()
        val chunks = maxOf(1, vectors.segments / SUMMARY_CHUNK_SEGMENTS)
        return Array(chunks) { c ->
            val from = c * SUMMARY_CHUNK_SEGMENTS
            val until = if (c == chunks - 1) vectors.segments else from + SUMMARY_CHUNK_SEGMENTS
            meanOf(vectors, from, until)
        }
    }

    fun coarseQuery(query: SegmentVectors): SegmentVectors {
        val n = minOf(COARSE_SEGMENTS, query.segments)
        if (n == query.segments) return query
        val out = ByteArray(n * AudioEmbedder.EMBED_DIM)
        for (i in 0 until n) {
            val from = (i * (query.segments - 1) / (n - 1)) * AudioEmbedder.EMBED_DIM
            query.values.copyInto(out, i * AudioEmbedder.EMBED_DIM, from, from + AudioEmbedder.EMBED_DIM)
        }
        return SegmentVectors(out, n)
    }

    fun score(query: SegmentVectors, track: SegmentVectors, trackId: String): EmbeddingRepository.Match {
        var total = 0f
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

        return EmbeddingRepository.Match(
            trackId = trackId,
            similarity = total / query.segments,
            positionSeconds = (alignOffset * AudioEmbedder.HOP_SAMPLES) / AudioFormat.SAMPLE_RATE,
            alignment = if (diagonal.isEmpty()) 0f else alignBest / query.segments
        )
    }

    fun dot(a: ByteArray, aFrom: Int, b: ByteArray, bFrom: Int): Float {
        var s = 0
        for (i in 0 until AudioEmbedder.EMBED_DIM) s += a[aFrom + i] * b[bFrom + i]
        return s / QUANT_SQUARED
    }

    suspend fun findCompetitor(
        bestUnified: UnifiedTrack?,
        candidates: List<EmbeddingRepository.Match>,
        rules: RecordingRules = RecordingRules.NONE,
        resolveTrack: suspend (String) -> UnifiedTrack?
    ): EmbeddingRepository.Match? {
        if (bestUnified == null) return candidates.getOrNull(1)
        for (i in 1 until candidates.size) {
            val candidate = candidates[i]
            val track = resolveTrack(candidate.trackId)
            if (track == null || !rules.isSame(bestUnified, track)) {
                return candidate
            }
        }
        return null
    }
}
