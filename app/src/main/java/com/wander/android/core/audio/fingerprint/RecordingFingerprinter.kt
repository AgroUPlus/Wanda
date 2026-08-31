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
     * Compared over the shorter of the two from the start, because both are computed from the
     * beginning of the file. A pair of encodings of one recording sits close to 1; two different
     * recordings sit near 0.5, which is what random bits give.
     */
    fun similarity(a: IntArray, b: IntArray): Double {
        val length = minOf(a.size, b.size)
        if (length == 0) return 0.0
        var agreeing = 0
        for (i in 0 until length) {
            // No mask: [BITS] is exactly the width of an Int, and `1 shl 32` would wrap to 1
            // and mask everything away — which reads as every pair of fingerprints matching.
            agreeing += BITS - Integer.bitCount(a[i] xor b[i])
        }
        return agreeing.toDouble() / (length.toDouble() * BITS)
    }
}
