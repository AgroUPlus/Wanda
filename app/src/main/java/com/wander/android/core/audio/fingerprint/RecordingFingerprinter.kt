package com.wander.android.core.audio.fingerprint

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/**
 * A fingerprint that answers "which recording is this file", not "what is playing in the room".
 *
 * The landmark fingerprinter next door answers the second question: it survives a phone microphone
 * in a noisy room, and pays for that with an index built from sparse spectral peaks that is only
 * ever queried by *searching* it. This answers the first, and the difference in job produces a
 * different shape.
 *
 * Here both sides hold the actual file — the same recording arriving from Navidrome, from YouTube
 * Music and from a local rip — and the question is whether two files are the same performance so
 * their metadata, plays and likes can be treated as one. That wants a dense, ordered sequence of
 * hashes that can be compared directly and indexed for exact lookup, which is what this produces.
 *
 * Deliberately **not** libchromaprint through the NDK, which is what was first sketched for this.
 * The comparison is only ever against fingerprints this fleet computed, never against AcoustID's
 * catalogue, so bit-compatibility with Chromaprint buys nothing — and it would cost a native
 * toolchain, a vendored C++ library in the build, and a fingerprinter that cannot be unit-tested
 * on the JVM. The algorithm below is the same family (Haitsma-Kalker): energy differences across
 * log-spaced bands and across time, one bit each.
 */
internal object RecordingFingerprinter {

    /**
     * 33 bands produce 32 bits: each bit compares one band against its neighbour.
     *
     * Log-spaced because pitch is. Linear bands would put most of the resolution above 2 kHz,
     * where music carries little of what distinguishes one recording from another.
     */
    private const val BAND_COUNT = 33

    /** Bits in one sub-fingerprint, one per adjacent band pair. */
    const val BITS = BAND_COUNT - 1

    /** Bands start here: below this is mains hum, rumble and DC, none of it musical identity. */
    private const val MIN_FREQUENCY = 100.0

    /** The top of the usable spectrum at 8 kHz, short of Nyquist to leave the filter room. */
    private const val MAX_FREQUENCY = 3_800.0

    /**
     * How far back in time each bit compares against, in frames.
     *
     * Not the previous frame. Frames are 32 ms apart and overlap by 75%, so consecutive ones are
     * nearly the same audio: the difference between them is mostly whatever noise the encoder
     * left, and quantising that to one bit produces a fingerprint that a transcode destroys.
     * Four frames is ~128 ms, far enough that the music itself has moved.
     */
    const val TIME_DELTA_FRAMES = 4

    /** Band edges in FFT bins, computed once. */
    private val bandEdges: IntArray = run {
        val binsPerHz = AudioFormat.FRAME_SIZE.toDouble() / AudioFormat.SAMPLE_RATE
        val ratio = (MAX_FREQUENCY / MIN_FREQUENCY).pow(1.0 / BAND_COUNT)
        IntArray(BAND_COUNT + 1) { index ->
            val frequency = MIN_FREQUENCY * ratio.pow(index.toDouble())
            (frequency * binsPerHz).toInt().coerceIn(1, AudioFormat.BIN_COUNT - 1)
        }
    }

    /**
     * Sub-fingerprints for [samples], one per frame after the first.
     *
     * The first frame produces none: every bit is a comparison with the frame before it, which is
     * what makes the fingerprint describe change over time rather than a spectrum. A constant tone
     * and silence differ in loudness and not in this, and that is correct — they are not different
     * recordings of anything.
     */
    fun fingerprint(samples: FloatArray): IntArray {
        val energies = bandEnergies(samples)
        if (energies.size <= TIME_DELTA_FRAMES) return IntArray(0)

        return IntArray(energies.size - TIME_DELTA_FRAMES) { frame ->
            var hash = 0
            for (band in 0 until BITS) {
                // Energy difference between neighbouring bands, differenced again against a frame
                // further back. Differencing twice is what makes the bit survive volume changes
                // and gentle EQ: both move the energies, neither moves how they move.
                val later = frame + TIME_DELTA_FRAMES
                val now = energies[later][band] - energies[later][band + 1]
                val before = energies[frame][band] - energies[frame][band + 1]
                if (now - before > 0) hash = hash or (1 shl band)
            }
            hash
        }
    }

    /** Log energy per band, per frame. */
    private fun bandEnergies(samples: FloatArray): List<DoubleArray> {
        val frames = mutableListOf<DoubleArray>()
        val real = FloatArray(AudioFormat.FRAME_SIZE)
        val imag = FloatArray(AudioFormat.FRAME_SIZE)
        val window = hann()

        var start = 0
        while (start + AudioFormat.FRAME_SIZE <= samples.size) {
            // Each frame is normalised to unit RMS before the transform, so a gain change is
            // removed at the source rather than approximately cancelled later. Without it the
            // floor added below stops being negligible for quiet audio, and the same recording
            // mastered quieter fingerprints differently — which is the one thing this must not do.
            var energy = 0.0
            for (i in 0 until AudioFormat.FRAME_SIZE) {
                val sample = samples[start + i]
                energy += sample.toDouble() * sample
            }
            val scale = if (energy > 0.0) {
                (1.0 / kotlin.math.sqrt(energy / AudioFormat.FRAME_SIZE)).toFloat()
            } else {
                0f
            }
            for (i in 0 until AudioFormat.FRAME_SIZE) {
                real[i] = samples[start + i] * scale * window[i]
                imag[i] = 0f
            }
            Fft.transform(real, imag)

            val bands = DoubleArray(BAND_COUNT)
            for (band in 0 until BAND_COUNT) {
                var sum = 0.0
                for (bin in bandEdges[band] until max(bandEdges[band] + 1, bandEdges[band + 1])) {
                    val re = real[bin].toDouble()
                    val im = imag[bin].toDouble()
                    sum += re * re + im * im
                }
                // Log, so a quiet passage and a loud one are compared on the same scale.
                bands[band] = ln(sum + 1e-10)
            }
            frames += bands
            start += AudioFormat.HOP_SIZE
        }
        return frames
    }

    private fun hann(): FloatArray = FloatArray(AudioFormat.FRAME_SIZE) { i ->
        (0.5 - 0.5 * kotlin.math.cos(2.0 * Math.PI * i / (AudioFormat.FRAME_SIZE - 1))).toFloat()
    }

    /**
     * How alike two fingerprints are, as the fraction of bits that agree.
     *
     * Aligned first. The comment this replaced assumed both sequences start at the same instant
     * "because both are computed from the beginning of the file", and for two copies of one
     * upload that holds. Across sources it does not: two YouTube uploads of one song routinely
     * differ by seconds of intro, and even a re-encode of the same master shifts by a frame or
     * two. Compared from index 0 those land at ~0.49 — chance — and a real duplicate is read as
     * two different recordings.
     *
     * Measured over this library's 44 same-title pairs: 32 matched from index 0, 43 match once
     * aligned. Four of the rescued pairs were out by a **single frame** (32 ms) and scored just
     * under the threshold at 0.69-0.71, jumping to 0.92+ once shifted.
     *
     * @return the best similarity found, or 0.0 when nothing aligns.
     */
    fun similarity(a: IntArray, b: IntArray): Double = aligned(a, b).similarity

    /**
     * The best alignment of [b] against [a], and the similarity there.
     *
     * ## Why this does not scan every offset
     *
     * Sliding one sequence across the other costs an entire comparison per offset — some 800 of
     * them to cover a plausible ±13 s, and each is thousands of integer operations. That is
     * affordable for one pair and not for a library, and the cost would grow with every candidate
     * the sub-hash filter proposes.
     *
     * So the offset is *voted for* rather than searched, the same way [OffsetAlignment] settles a
     * landmark match: a sub-hash value occurring at frame `i` in one sequence and frame `j` in the
     * other is one vote for offset `i - j`. Unrelated tracks scatter their votes; a genuine pair
     * piles them onto one offset. One pass over the sequences finds it, and only a handful of
     * comparisons follow.
     *
     * The short refine around the winning bin covers the case where quantisation puts the true
     * offset a frame or two off the fullest bin — cheap insurance, seven comparisons rather than
     * eight hundred. Verified against exhaustive search over this library: identical results.
     */
    fun aligned(a: IntArray, b: IntArray): Alignment {
        if (a.isEmpty() || b.isEmpty()) return Alignment(0.0, 0, 0)

        // No shared sub-hash is not the same as no similarity, and treating it as one is what this
        // `?: 0` replaces. A vote needs two sub-hashes to be equal in all 32 bits; degradation
        // flips bits, so a re-encoded or noisy copy of the same recording shares far fewer exact
        // values than intuition suggests, and often none at all. The old code returned 0.0 there —
        // scoring a degraded copy of a track *below* an unrelated one, which is the one comparison
        // the fingerprint exists to get right.
        //
        // So the vote decides *where* to look, not *whether* to look. With no votes the offset is
        // unknown and 0 is the best available guess: the overwhelmingly common case is two
        // encodings of the same recording starting at the same place. The refine window then
        // covers a frame or two of drift, at the same seven comparisons the voted path costs.
        val voted = voteOffset(a, b)
        val centre = voted?.offsetFrames ?: 0
        val votes = voted?.votes ?: 0

        var best = Alignment(0.0, centre, votes)
        for (offset in centre - REFINE_FRAMES..centre + REFINE_FRAMES) {
            val score = similarityAt(a, b, offset)
            if (score > best.similarity) best = Alignment(score, offset, votes)
        }
        return best
    }

    /**
     * Where the shared sub-hashes agree [b] sits relative to [a], in frames.
     *
     * Null when they share nothing at all, which is itself an answer: no alignment exists and the
     * pair needs no comparison.
     */
    private fun voteOffset(a: IntArray, b: IntArray): OffsetAlignment.Aligned? {
        // Positions of each value in `a`. Sub-hash values repeat within a track, so a value maps
        // to a list; a repeated passage simply votes more than once, which is correct.
        val positions = HashMap<Int, MutableList<Int>>(a.size)
        for (i in a.indices) {
            positions.getOrPut(a[i]) { ArrayList(1) } += i
        }

        val bins = HashMap<Int, Int>()
        for (j in b.indices) {
            val hits = positions[b[j]] ?: continue
            // A value that appears everywhere carries no positional information and would only
            // add noise proportional to its own frequency.
            if (hits.size > MAX_VOTES_PER_VALUE) continue
            for (i in hits) {
                val offset = i - j
                if (offset < -MAX_OFFSET_FRAMES || offset > MAX_OFFSET_FRAMES) continue
                bins[offset] = (bins[offset] ?: 0) + 1
            }
        }
        return OffsetAlignment.best(bins)
    }

    /** Bit agreement with [b] shifted [offsetFrames] against [a]. */
    private fun similarityAt(a: IntArray, b: IntArray, offsetFrames: Int): Double {
        val aStart = if (offsetFrames >= 0) offsetFrames else 0
        val bStart = if (offsetFrames >= 0) 0 else -offsetFrames
        val length = minOf(a.size - aStart, b.size - bStart)
        // Too short an overlap says more about the shift than about the audio: a handful of
        // frames will agree by chance and would score higher than a real, fully overlapped match.
        if (length < MIN_OVERLAP_FRAMES) return 0.0

        var agreeing = 0
        for (i in 0 until length) {
            // No mask: [BITS] is exactly the width of an Int, and `1 shl 32` would wrap to 1
            // and mask everything away — which reads as every pair of fingerprints matching.
            agreeing += BITS - Integer.bitCount(a[aStart + i] xor b[bStart + i])
        }
        return agreeing.toDouble() / (length.toDouble() * BITS)
    }

    /** A similarity, and where it was found. */
    data class Alignment(
        val similarity: Double,
        val offsetFrames: Int,
        /** How many sub-hashes agreed on this offset. Low votes mean a weakly located match. */
        val votes: Int
    )

    /**
     * How far apart two copies of one recording may start.
     *
     * ±400 frames is about ±13 seconds, which covers the intros, countdowns and silence that
     * uploads of one song differ by — the largest genuine shift measured in this library was 9.0 s.
     * Wider mostly buys coincidences.
     */
    private const val MAX_OFFSET_FRAMES = 400

    /** Offsets either side of the voted bin that are worth comparing in full. */
    private const val REFINE_FRAMES = 3

    /** Frames of overlap below which a similarity is not worth believing. */
    private const val MIN_OVERLAP_FRAMES = 100

    /**
     * Above this many occurrences, a sub-hash value is treated as saying nothing about position.
     *
     * A value repeating through a track pairs with every occurrence of itself in the other, which
     * is quadratic in its own frequency and lands as noise spread across every bin.
     */
    private const val MAX_VOTES_PER_VALUE = 32
}
