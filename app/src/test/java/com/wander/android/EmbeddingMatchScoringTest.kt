package com.wander.android

import com.wander.android.core.audio.fingerprint.AudioEmbedder
import com.wander.android.core.audio.fingerprint.SegmentVectors
import com.wander.android.data.repository.EmbeddingRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Where in a track a clip came from, and the packing the answer is read out of.
 *
 * Both had a failure nobody could see. The position was the single best-matching track segment for
 * clip segment 0 — one comparison, unchecked, deciding the number shown to the user — and the
 * packing is big-endian to match a desktop indexer, which a little-endian read would turn into
 * plausible noise rather than an error.
 */
class EmbeddingMatchScoringTest {

    private val random = Random(seed = 7)

    /** A unit vector, as the model emits. Cosine is only the dot product when both sides are one. */
    private fun unit(): FloatArray {
        val v = FloatArray(AudioEmbedder.EMBED_DIM) { random.nextFloat() * 2f - 1f }
        val norm = sqrt(v.fold(0f) { acc, x -> acc + x * x })
        return FloatArray(v.size) { v[it] / norm }
    }

    private fun track(segments: Int): SegmentVectors =
        AudioEmbedder.flatten(Array(segments) { unit() })

    /** The clip as it appears `at` segments into `track`. */
    private fun excerpt(track: SegmentVectors, at: Int, segments: Int): SegmentVectors {
        val from = at * AudioEmbedder.EMBED_DIM
        return SegmentVectors(
            track.values.copyOfRange(from, from + segments * AudioEmbedder.EMBED_DIM),
            segments
        )
    }

    @Test
    fun `a clip is placed where it was taken from, not at the start`() {
        val song = track(segments = 240)                      // two minutes
        val clip = excerpt(song, at = 200, segments = 11)      // 100 s in, a six-second clip

        val match = EmbeddingRepository.score(clip, song, "t")

        // 200 segments at a 0.5 s hop.
        assertEquals(100, match.positionSeconds)
        // Not exactly 1.0: quantising moves each component by up to half a step, which perturbs
        // the vector's norm, so a clip's cosine against its own source can land fractionally
        // either side of unity. The measured drift on real vectors is under 0.002 — an order of
        // magnitude below the 0.04 margin any decision is made on.
        assertEquals(1f, match.similarity, 0.002f)
    }

    /**
     * The failure the vote exists to survive.
     *
     * A repeated chorus puts a near-identical half-second somewhere else in the track, so one clip
     * segment's best match lands in the wrong place. Under the old `argmax` of segment 0 that one
     * segment *was* the answer; here it is outvoted by the ten that agree.
     */
    @Test
    fun `one segment matching the wrong place does not move the position`() {
        val song = track(segments = 240)
        val clip = excerpt(song, at = 120, segments = 11)
        // Segment 0 of the clip is also, exactly, segment 4 of the song — an earlier chorus.
        val dim = AudioEmbedder.EMBED_DIM
        clip.values.copyInto(song.values, 4 * dim, 0, dim)

        val match = EmbeddingRepository.score(clip, song, "t")

        assertEquals(60, match.positionSeconds)
    }

    /** A clip that starts before the indexed audio implies a negative offset; a position cannot be. */
    @Test
    fun `a position is never negative`() {
        val song = track(segments = 240)
        val clip = excerpt(song, at = 0, segments = 11)
        assertTrue(EmbeddingRepository.score(clip, song, "t").positionSeconds >= 0)
    }

    /**
     * One byte a component, segment-major, and a round-trip that stays inside the quantiser's step.
     *
     * The BLOB is the working array — a match walks the bytes SQLite hands it — so `pack` and
     * `flatten` producing anything different would not be a rounding difference, it would be two
     * incompatible alphabets that both look plausible.
     */
    @Test
    fun `packing is one byte per component and round-trips within a quantisation step`() {
        val vectors = Array(5) { unit() }
        val blob = AudioEmbedder.pack(vectors)
        assertEquals(5 * AudioEmbedder.EMBED_DIM, blob.size)

        val read = AudioEmbedder.unpack(blob)
        assertEquals(5, read.size)
        // Half a step of 1/255, which is the most rounding to nearest can cost.
        for (i in vectors.indices) assertArrayEquals(vectors[i], read[i], 0.5f / 255f)

        assertArrayEquals(blob, AudioEmbedder.flatten(vectors).values)
    }

    /**
     * Nothing in a real vector comes near clipping, and the guard holds if anything ever does.
     *
     * Measured over 162,447 stored segment vectors the largest component was 0.433; the scale
     * clips at 0.498. A component beyond that must saturate rather than wrap, because
     * `(0.6 * 255).toInt().toByte()` is -103 — a large positive number stored as a large negative
     * one, which is the kind of failure that produces confident nonsense.
     */
    @Test
    fun `an out-of-range component saturates rather than wrapping`() {
        assertEquals(127.toByte(), AudioEmbedder.quantise(0.9f))
        assertEquals((-127).toByte(), AudioEmbedder.quantise(-0.9f))
        assertEquals(0.toByte(), AudioEmbedder.quantise(0f))
        // The measured extreme, comfortably inside the range.
        assertEquals(110.toByte(), AudioEmbedder.quantise(0.433f))
    }

    /**
     * The shortlist's premise: a track's centroid is close to the centroid of a clip taken from it,
     * and further from an unrelated track's.
     *
     * The recall this rests on was measured against the real 1382-track index rather than asserted
     * here — 100% of 400 excerpts inside the top 80, worst rank 45. What a unit test can pin is
     * that the summary is computed the way that measurement assumed: a normalised mean, which
     * cannot be checked by eye once it is a 512-byte BLOB.
     */
    @Test
    fun `a clip's centroid is nearer its own track than a stranger's`() {
        val song = track(segments = 240)
        val other = track(segments = 240)
        val clip = excerpt(song, at = 100, segments = 11)

        val q = EmbeddingRepository.meanOf(clip)
        val mine = EmbeddingRepository.meanOf(song)
        val theirs = EmbeddingRepository.meanOf(other)

        fun cosine(a: FloatArray, b: FloatArray) = a.indices.fold(0f) { acc, i -> acc + a[i] * b[i] }
        assertTrue(cosine(q, mine) > cosine(q, theirs))
    }

    /** A centroid is a unit vector, or a dot product against it is not a cosine. */
    @Test
    fun `a centroid is normalised`() {
        val mean = EmbeddingRepository.meanOf(track(segments = 32))
        val norm = sqrt(mean.fold(0f) { acc, x -> acc + x * x })
        assertEquals(1f, norm, 1e-4f)
    }

    /**
     * The position must survive a chorus that repeats verbatim later in the song.
     *
     * The clip's own segments are duplicated at another offset, so several of them individually
     * match the wrong place perfectly. Only the diagonal distinguishes the two, and it is the
     * whole reason this is not an argmax.
     */
    @Test
    fun `a verbatim repeat elsewhere does not move the position`() {
        val song = track(segments = 300)
        val clip = excerpt(song, at = 200, segments = 11)
        // The same eleven segments also appear at segment 40.
        clip.values.copyInto(song.values, 40 * AudioEmbedder.EMBED_DIM)

        // Two exact alignments now exist; the earlier one is reported, and either is correct.
        val at = EmbeddingRepository.score(clip, song, "t").positionSeconds
        assertTrue(at == 20 || at == 100)
    }

    /** The BLOB the shortlist reads back has to be the vectors that were written. */
    @Test
    fun `a summary round-trips through its blob`() {
        val song = track(segments = 40)
        val summary = EmbeddingRepository.summaryOf(song)
        val blob = AudioEmbedder.pack(summary)
        assertEquals(summary.size * AudioEmbedder.EMBED_DIM, blob.size)
        assertArrayEquals(AudioEmbedder.flatten(summary).values, blob)
    }

    /**
     * One summary per 30 s, and the tail folded in rather than left as a stub.
     *
     * The chunk length is the whole reason the shortlist can be short: measured over 300 excerpts
     * of a real 1374-track index, a single whole-track mean put the true track as low as rank 524,
     * and one mean per 30 s put it no lower than rank 2.
     */
    @Test
    fun `a track is summarised once per thirty seconds`() {
        // 60 segments is one chunk; 119 (a 60 s track) is still one.
        assertEquals(1, EmbeddingRepository.summaryOf(track(segments = 60)).size)
        assertEquals(1, EmbeddingRepository.summaryOf(track(segments = 119)).size)
        // 360 segments is a three-minute track: six chunks.
        assertEquals(6, EmbeddingRepository.summaryOf(track(segments = 360)).size)
        // A short remainder joins the last chunk instead of becoming a seventh.
        assertEquals(6, EmbeddingRepository.summaryOf(track(segments = 370)).size)
        // Shorter than one chunk still yields one.
        assertEquals(1, EmbeddingRepository.summaryOf(track(segments = 11)).size)
    }

    /** Every chunk summary is a unit vector, or a dot product against it is not a cosine. */
    @Test
    fun `every chunk summary is normalised`() {
        for (chunk in EmbeddingRepository.summaryOf(track(segments = 300))) {
            assertEquals(1f, sqrt(chunk.fold(0f) { acc, x -> acc + x * x }), 1e-4f)
        }
    }

    /**
     * The coarse pass must sample the clip's whole span, not its opening.
     *
     * Three consecutive half-seconds describe one moment, and a track containing a similar moment
     * survives on it; three spread across the clip have to agree about a span. Ends included, so
     * the pass sees how far the clip reaches.
     */
    @Test
    fun `the coarse query spans the clip end to end`() {
        val song = track(segments = 240)
        val clip = excerpt(song, at = 0, segments = 11)
        val coarse = EmbeddingRepository.coarseQuery(clip)

        assertEquals(3, coarse.segments)
        val dim = AudioEmbedder.EMBED_DIM
        // First, middle and last segment of the clip.
        for ((i, source) in listOf(0, 5, 10).withIndex()) {
            assertArrayEquals(
                clip.values.copyOfRange(source * dim, (source + 1) * dim),
                coarse.values.copyOfRange(i * dim, (i + 1) * dim)
            )
        }
    }

    /** A clip already at or below the coarse width is passed through, not padded or truncated. */
    @Test
    fun `a clip shorter than the coarse width is used whole`() {
        val song = track(segments = 240)
        val clip = excerpt(song, at = 0, segments = 2)
        assertEquals(2, EmbeddingRepository.coarseQuery(clip).segments)
    }

    /** The cheap pass has to rank the true track first, or the expensive one never sees it. */
    @Test
    fun `the coarse pass still puts the right track on top`() {
        val right = track(segments = 240)
        val wrong = track(segments = 240)
        val clip = excerpt(right, at = 90, segments = 11)
        val coarse = EmbeddingRepository.coarseQuery(clip)

        assertTrue(
            EmbeddingRepository.score(coarse, right, "right").similarity >
                EmbeddingRepository.score(coarse, wrong, "wrong").similarity
        )
    }
}
