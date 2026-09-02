package com.wander.android.core.audio.fingerprint

/**
 * One spectral peak: a moment, and the frequency that dominated it.
 *
 * [frame] counts hops from the start of the audio, not samples — the whole matcher works in frame
 * time, and converting at the edges keeps a sample rate from leaking into the algorithm.
 */
data class Peak(val frame: Int, val bin: Int, val magnitude: Float)

/**
 * A pair of peaks, packed into one integer.
 *
 * A single peak is far too common to identify anything — every recording has energy at 440 Hz. A
 * *pair* with a measured gap between them is rare enough to be worth looking up, which is the
 * whole idea behind landmark fingerprinting: the hash says "this frequency, then that frequency,
 * that long apart", and a song is the set of such statements it makes.
 *
 * The offset is stored beside the hash rather than inside it. Two recordings of the same song
 * agree on the *relative* time between paired peaks and disagree on where in the track the
 * listener started, so the gap belongs in the key and the position does not — the position is what
 * the matcher aligns on afterwards.
 */
@JvmInline
value class Hash(val value: Int)

/** A hash together with where in the audio its anchor peak fell. */
data class Landmark(val hash: Hash, val anchorFrame: Int)

object HashPacking {

    /**
     * Frequency resolution kept in the hash, in bits.
     *
     * Eight, spent on a *logarithmic* axis rather than a linear one. That is the change that
     * matters: pitch is logarithmic, so a fixed number of hertz per code is more than a semitone
     * down in the bass and a twentieth of one up top. The linear version spent almost all of its
     * codes above 2 kHz, where a phone speaker's own colouration moves the peak around, and gave
     * the bass — where the notes are — five distinguishable values.
     */
    private const val FREQ_BITS = 8
    private const val FREQ_LEVELS = (1 shl FREQ_BITS) - 1

    /** Six bits of gap, in frames: up to ~2 seconds between the two peaks of a pair. */
    private const val DELTA_BITS = 6
    const val MAX_DELTA_FRAMES = (1 shl DELTA_BITS) - 1

    /** Below this the pair is two views of one event and says nothing about the gap. */
    const val MIN_DELTA_FRAMES = 1

    private val LOG_TOP = kotlin.math.ln(AudioFormat.BIN_COUNT.toDouble())

    /**
     * A bin as a logarithmic code.
     *
     * Bin 0 has no logarithm and is folded into the first code; nothing musical lives in the first
     * 8 Hz anyway.
     */
    fun quantiseFrequency(bin: Int): Int {
        val safe = if (bin < 1) 1 else bin
        val scaled = kotlin.math.ln(safe.toDouble()) / LOG_TOP * FREQ_LEVELS
        return scaled.toInt().coerceIn(0, FREQ_LEVELS)
    }

    fun pack(anchorBin: Int, targetBin: Int, deltaFrames: Int): Hash = Hash(
        (quantiseFrequency(anchorBin) shl (FREQ_BITS + DELTA_BITS)) or
            (quantiseFrequency(targetBin) shl DELTA_BITS) or
            (deltaFrames and MAX_DELTA_FRAMES)
    )
}
