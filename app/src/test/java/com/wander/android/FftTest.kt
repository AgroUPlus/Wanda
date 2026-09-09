package com.wander.android

import com.wander.android.core.audio.fingerprint.AudioFormat
import com.wander.android.core.audio.fingerprint.Fft
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * The transform under `RecordingFingerprinter` and `FeatureExtractor`.
 *
 * What is left of `FingerprinterTest` after the landmark engine was removed: the landmark
 * assertions went with it, but [Fft] still carries both remaining analyses, and a subtly wrong
 * transform has no visible failure mode — it produces plausible numbers that simply never agree
 * with anything.
 */
class FftTest {

    /** A pure tone lands in the bin its frequency belongs to, and not a neighbouring one. */
    @Test
    fun `fft finds the bin of a pure tone`() {
        val size = 1024
        val binWidth = AudioFormat.SAMPLE_RATE.toDouble() / size
        val targetBin = 64
        val frequency = targetBin * binWidth

        val real = FloatArray(size) { i ->
            sin(2.0 * PI * frequency * i / AudioFormat.SAMPLE_RATE).toFloat()
        }
        val imag = FloatArray(size)
        Fft.transform(real, imag)

        var loudest = 0
        var loudestMagnitude = 0.0
        for (bin in 1 until size / 2) {
            val magnitude = real[bin] * real[bin].toDouble() + imag[bin] * imag[bin].toDouble()
            if (magnitude > loudestMagnitude) {
                loudestMagnitude = magnitude
                loudest = bin
            }
        }
        assertEquals(targetBin, loudest)
    }
}
