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

    private fun track(segments: Int): SegmentVectors {
        val flat = FloatArray(segments * AudioEmbedder.EMBED_DIM)
        for (i in 0 until segments) unit().copyInto(flat, i * AudioEmbedder.EMBED_DIM)
        return SegmentVectors(flat, segments)
    }

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
        assertEquals(1f, match.similarity, 1e-4f)
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

    /** Big-endian, segment-major, and the flat form the matcher walks agrees with the boxed one. */
    @Test
    fun `packing round-trips through both unpack shapes`() {
        val vectors = Array(5) { unit() }
        val blob = AudioEmbedder.pack(vectors)
        assertEquals(5 * AudioEmbedder.EMBED_DIM * 4, blob.size)

        val boxed = AudioEmbedder.unpack(blob)
        assertEquals(5, boxed.size)
        for (i in vectors.indices) assertArrayEquals(vectors[i], boxed[i], 0f)

        val flat = FloatArray(5 * AudioEmbedder.EMBED_DIM)
        assertEquals(5, AudioEmbedder.unpackInto(blob, flat))
        assertArrayEquals(AudioEmbedder.flatten(vectors).values, flat, 0f)
    }

    /** A buffer bigger than the row is the normal case — it is reused across tracks of every length. */
    @Test
    fun `unpackInto reports the row's segments, not the buffer's capacity`() {
        val blob = AudioEmbedder.pack(Array(3) { unit() })
        val oversized = FloatArray(120 * AudioEmbedder.EMBED_DIM)
        assertEquals(3, AudioEmbedder.unpackInto(blob, oversized))
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

    /** The 512-byte BLOB the shortlist reads back has to be the vector that was written. */
    @Test
    fun `a centroid round-trips through its blob`() {
        val mean = EmbeddingRepository.meanOf(track(segments = 40))
        val blob = AudioEmbedder.packVector(mean)
        assertEquals(AudioEmbedder.EMBED_DIM * 4, blob.size)

        val read = FloatArray(AudioEmbedder.EMBED_DIM)
        AudioEmbedder.unpackInto(blob, read)
        assertArrayEquals(mean, read, 0f)
    }
}
