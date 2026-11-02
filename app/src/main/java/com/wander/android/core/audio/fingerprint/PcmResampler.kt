package com.wander.android.core.audio.fingerprint

import kotlin.math.min

/**
 * A growable `FloatArray`.
 *
 * `ArrayList<Float>` is the obvious thing to reach for and is the wrong one here: it stores boxed
 * `java.lang.Float` objects, and the decoder appends one per sample. Kotlin has no primitive list
 * in its standard library, so this is a few lines rather than a dependency.
 */
internal class FloatBuffer(initialCapacity: Int) {

    private var data = FloatArray(initialCapacity.coerceAtLeast(1))

    var size = 0
        private set

    val isEmpty: Boolean get() = size == 0

    fun add(value: Float) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = value
    }

    /** Drops everything from [newSize] on. */
    fun truncate(newSize: Int) {
        if (newSize < size) size = newSize.coerceAtLeast(0)
    }

    /**
     * A right-sized array the caller keeps for the life of the fingerprint.
     *
     * Hands back the buffer itself when it happens to be exactly full — the common case when it
     * was sized from the track's declared duration. Copying there would double the peak footprint
     * of a decode for no purpose, and the buffer has no other owner.
     */
    fun toFloatArray(): FloatArray = if (size == data.size) data else data.copyOf(size)
}

/** Linear resampling to [AudioFormat.SAMPLE_RATE]. */
internal object Resampler {

    /**
     * Linear interpolation, not a windowed-sinc filter.
     *
     * Downsampling this way aliases anything above the new Nyquist back into the band. That is a
     * real defect and it is tolerable here for one reason: *both* sides of a match go through this
     * same arithmetic — the microphone through this function, the decoder through
     * [StreamingResampler], which produces the identical samples — so the artefacts land in the
     * same places in the file and in the microphone clip, and the peaks still line up.
     */
    fun toFingerprintRate(samples: FloatArray, sourceRate: Int): FloatArray {
        if (sourceRate == AudioFormat.SAMPLE_RATE) return samples
        val ratio = sourceRate.toDouble() / AudioFormat.SAMPLE_RATE
        val outputSize = (samples.size / ratio).toInt()
        if (outputSize <= 0) return FloatArray(0)
        return FloatArray(outputSize) { i ->
            val position = i * ratio
            val index = position.toInt()
            val fraction = (position - index).toFloat()
            val a = samples[index]
            val b = samples[min(index + 1, samples.lastIndex)]
            a + (b - a) * fraction
        }
    }
}

/**
 * [Resampler.toFingerprintRate], one input sample at a time, appending to [out].
 *
 * What lets the decoder throw source-rate audio away as it arrives. Collecting the whole decode at
 * 44.1 kHz first and resampling at the end held 5.5 times the samples the fingerprint keeps — at
 * the twelve-minute embedding bound, 127 MB of floats per track before a single one was used.
 *
 * Sample for sample equal to the batch function, which is what keeps the decoder and the
 * microphone agreeing (`StreamingResamplerTest` holds it to that). Output *i* interpolates input
 * `floor(i * ratio)` and the one after; it is written as soon as that second sample arrives, and
 * [finish] settles the tail — the samples the batch version clamps to the last input, and the
 * one or two written past the batch version's `floor(n / ratio)` length.
 */
internal class StreamingResampler(sourceRate: Int, private val out: FloatBuffer) {

    private val passThrough = sourceRate == AudioFormat.SAMPLE_RATE
    private val ratio = sourceRate.toDouble() / AudioFormat.SAMPLE_RATE
    private val start = out.size
    private var count = 0L
    private var previous = 0f
    private var nextOut = 0L

    fun push(sample: Float) {
        if (passThrough) {
            out.add(sample)
            return
        }
        if (count > 0) {
            // Every output whose left neighbour is the previous sample: this one is its right.
            val left = count - 1
            while (true) {
                val position = nextOut * ratio
                val index = position.toLong()
                if (index != left) break
                val fraction = (position - index).toFloat()
                out.add(previous + (sample - previous) * fraction)
                nextOut++
            }
        }
        previous = sample
        count++
    }

    /** Ends the stream. [push] must not be called afterwards. */
    fun finish() {
        if (passThrough) return
        val outputSize = (count / ratio).toLong()
        // Outputs left over all sit on the last input, where the batch version clamps the right
        // neighbour to the left one and the interpolation collapses to that sample.
        while (nextOut < outputSize) {
            out.add(previous)
            nextOut++
        }
        out.truncate((start + outputSize).toInt())
    }
}
