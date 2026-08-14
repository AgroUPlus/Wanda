package com.wander.android.core.audio.visualizer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class VisualizerMode(val displayName: String) {
    AURORA("Aurora Ribbon"),
    EMBERS("Embers & Flames"),
    BLOOM("Bloom Rings"),
    OSCILLOSCOPE("Oscilloscope"),
    WATERFALL("Spectrogram Waterfall"),
    OFF("Disabled")
}

class AudioFftProcessor {

    private val bandCount = 64
    private val fftSize = 512
    private val halfFft = fftSize / 2

    // Precomputed tables
    private val hammingWindow = FloatArray(fftSize) { i ->
        (0.54 - 0.46 * cos(2.0 * Math.PI * i / (fftSize - 1))).toFloat()
    }
    private val cosTable = FloatArray(halfFft) { i ->
        cos(-2.0 * Math.PI * i / fftSize).toFloat()
    }
    private val sinTable = FloatArray(halfFft) { i ->
        sin(-2.0 * Math.PI * i / fftSize).toFloat()
    }
    private val bitReversedIndices = IntArray(fftSize) { i ->
        Integer.reverse(i) ushr (32 - Integer.numberOfTrailingZeros(fftSize))
    }

    // Reusable buffers (zero allocation on audio thread)
    private val realBuffer = FloatArray(fftSize)
    private val imagBuffer = FloatArray(fftSize)
    private val waveformBuffer = FloatArray(128)
    private val smoothedBands = FloatArray(bandCount)

    private val _spectrumFlow = MutableStateFlow(FloatArray(bandCount))
    val spectrumFlow: StateFlow<FloatArray> = _spectrumFlow.asStateFlow()

    private val _waveformFlow = MutableStateFlow(FloatArray(128))
    val waveformFlow: StateFlow<FloatArray> = _waveformFlow.asStateFlow()

    var isVisualizerActive: Boolean = false

    private var lastDispatchTimeMs: Long = 0L

    /**
     * Process raw PCM 16-bit audio samples into frequency bands using fast Radix-2 FFT.
     */
    fun processPcmSamples(samples: ShortArray, length: Int) {
        if (!isVisualizerActive || length <= 0) return

        val now = System.currentTimeMillis()
        // Throttle dispatch to max ~60 FPS (16ms) to avoid flooding the UI thread
        val shouldDispatch = (now - lastDispatchTimeMs) >= MIN_DISPATCH_INTERVAL_MS

        // 1. Time-domain waveform (128 samples)
        val step = (length / 128).coerceAtLeast(1)
        for (i in 0 until 128) {
            val sampleIdx = (i * step).coerceAtMost(length - 1)
            waveformBuffer[i] = samples[sampleIdx] / 32768.0f
        }

        // 2. Prepare FFT input with windowing and bit reversal
        val copyCount = length.coerceAtMost(fftSize)
        for (i in 0 until fftSize) {
            val revIdx = bitReversedIndices[i]
            if (revIdx < copyCount) {
                realBuffer[i] = (samples[revIdx] / 32768.0f) * hammingWindow[revIdx]
            } else {
                realBuffer[i] = 0.0f
            }
            imagBuffer[i] = 0.0f
        }

        // 3. In-place Cooley-Tukey Radix-2 FFT
        var stageSize = 2
        while (stageSize <= fftSize) {
            val halfStage = stageSize / 2
            val tableStep = fftSize / stageSize

            for (k in 0 until fftSize step stageSize) {
                for (j in 0 until halfStage) {
                    val tableIdx = j * tableStep
                    val c = cosTable[tableIdx]
                    val s = sinTable[tableIdx]

                    val idx1 = k + j
                    val idx2 = idx1 + halfStage

                    val tr = c * realBuffer[idx2] - s * imagBuffer[idx2]
                    val ti = s * realBuffer[idx2] + c * imagBuffer[idx2]

                    realBuffer[idx2] = realBuffer[idx1] - tr
                    imagBuffer[idx2] = imagBuffer[idx1] - ti
                    realBuffer[idx1] += tr
                    imagBuffer[idx1] += ti
                }
            }
            stageSize *= 2
        }

        // 4. Map halfFft (256) bins into bandCount (64) logarithmic/linear output bands
        val binRatio = halfFft.toFloat() / bandCount
        for (k in 0 until bandCount) {
            val startBin = (k * binRatio).toInt().coerceIn(0, halfFft - 1)
            val endBin = ((k + 1) * binRatio).toInt().coerceIn(startBin + 1, halfFft)

            var maxMag = 0.0f
            for (bin in startBin until endBin) {
                val r = realBuffer[bin]
                val im = imagBuffer[bin]
                val mag = sqrt(r * r + im * im) * 2.0f
                if (mag > maxMag) maxMag = mag
            }

            val bandVal = maxMag.coerceIn(0.0f, 1.0f)
            // Smooth decay: attack fast (0.6), decay slow (0.85)
            if (bandVal > smoothedBands[k]) {
                smoothedBands[k] = smoothedBands[k] * 0.4f + bandVal * 0.6f
            } else {
                smoothedBands[k] = smoothedBands[k] * 0.85f + bandVal * 0.15f
            }
        }

        if (shouldDispatch) {
            lastDispatchTimeMs = now
            _waveformFlow.value = waveformBuffer.copyOf()
            _spectrumFlow.value = smoothedBands.copyOf()
        }
    }

    private companion object {
        const val MIN_DISPATCH_INTERVAL_MS = 16L // ~60 FPS cap
    }
}
