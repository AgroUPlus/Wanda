package com.wander.android
import com.wander.android.core.audio.visualizer.AudioFftProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class AudioFftProcessorTest {

    @Test
    fun testProcessorInactiveProducesNoOutput() {
        val processor = AudioFftProcessor()
        processor.isVisualizerActive = false

        val samples = ShortArray(512) { (sin(it * 0.1) * 30000).toInt().toShort() }
        processor.processPcmSamples(samples, samples.size)

        // Initial default is all zeros
        val spectrum = processor.spectrumFlow.value
        assertEquals(64, spectrum.size)
        assertTrue(spectrum.all { it == 0.0f })
    }

    @Test
    fun testProcessorHandlesEmptyBufferGracefully() {
        val processor = AudioFftProcessor()
        processor.isVisualizerActive = true

        processor.processPcmSamples(ShortArray(0), 0)
        assertEquals(64, processor.spectrumFlow.value.size)
        assertEquals(128, processor.waveformFlow.value.size)
    }

    @Test
    fun testProcessorComputesSpectrumAndWaveform() {
        val processor = AudioFftProcessor()
        processor.isVisualizerActive = true

        // Generate 440Hz test sine wave sampled at 44.1kHz (period ~100 samples)
        val samples = ShortArray(512) { i ->
            (sin(2.0 * Math.PI * 440.0 * i / 44100.0) * 20000).toInt().toShort()
        }

        processor.processPcmSamples(samples, samples.size)

        val waveform = processor.waveformFlow.value
        val spectrum = processor.spectrumFlow.value

        assertEquals(128, waveform.size)
        assertEquals(64, spectrum.size)

        // Waveform should contain non-zero normalized values
        assertTrue(waveform.any { it != 0.0f })
        // Spectrum should contain non-zero magnitude in lower frequency bands
        assertTrue(spectrum.any { it > 0.0f })
    }
}
