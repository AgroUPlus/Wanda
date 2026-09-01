package com.wander.android.core.audio.features

import com.wander.android.core.audio.fingerprint.AudioFormat
import com.wander.android.core.audio.fingerprint.Fft
import javax.inject.Inject
import kotlin.math.log2
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Measures the six numbers of [AcousticFeatures] from decoded PCM.
 *
 * Runs on the samples the fingerprint indexer has already decoded, and adds one spectrogram pass
 * to a job that was doing one anyway. Decoding is the expensive part of indexing a library; the
 * arithmetic here is not, which is the only reason this can be afforded over a whole collection.
 *
 * Everything is measured at [AudioFormat]'s 8 kHz, so brightness means "where the weight sits
 * below 4 kHz" rather than across the whole audible band. That is a real limit and it is the right
 * trade: a second decode at full rate would double the cost of indexing to sharpen an axis that
 * only has to separate a dull mix from a bright one.
 */
class FeatureExtractor @Inject constructor() {

    fun extract(samples: FloatArray): AcousticFeatures? {
        if (samples.size < AudioFormat.FRAME_SIZE * MIN_FRAMES) return null

        val frames = spectrogram(samples) ?: return null
        val onsets = onsetEnvelope(frames)
        val (bpm, pulse) = tempoOf(onsets)
        val chroma = chromaOf(frames)
        val (pitchClass, tonality) = dominantKey(chroma)
        val (keyX, keyY) = AcousticFeatures.keyPoint(pitchClass, tonality)

        return AcousticFeatures(
            tempo = AcousticFeatures.normaliseTempo(bpm),
            energy = energyOf(samples),
            brightness = brightnessOf(frames),
            danceability = pulse,
            keyX = keyX,
            keyY = keyY
        ).takeIf { it.isUsable }
    }

    /** Magnitude spectra, one row per hop. The shared shape with the fingerprinter is deliberate. */
    private fun spectrogram(samples: FloatArray): Array<FloatArray>? {
        val size = AudioFormat.FRAME_SIZE
        val hop = AudioFormat.HOP_SIZE
        val count = (samples.size - size) / hop
        if (count < MIN_FRAMES) return null

        val real = FloatArray(size)
        val imag = FloatArray(size)
        // Hann, so a frame's edges do not read as a broadband click the centroid would follow.
        val window = FloatArray(size) { i ->
            (0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (size - 1))).toFloat()
        }

        return Array(count) { frame ->
            val offset = frame * hop
            for (i in 0 until size) {
                real[i] = samples[offset + i] * window[i]
                imag[i] = 0f
            }
            Fft.transform(real, imag)
            FloatArray(AudioFormat.BIN_COUNT) { bin ->
                sqrt(real[bin] * real[bin] + imag[bin] * imag[bin])
            }
        }
    }

    /**
     * Spectral flux: how much the spectrum grew since the previous frame.
     *
     * Only rises count. A note ending is not an onset, and counting decay as event would put a
     * beat between every pair of real ones.
     */
    private fun onsetEnvelope(frames: Array<FloatArray>): FloatArray =
        FloatArray(frames.size - 1) { index ->
            var flux = 0f
            val previous = frames[index]
            val current = frames[index + 1]
            for (bin in current.indices) {
                val rise = current[bin] - previous[bin]
                if (rise > 0f) flux += rise
            }
            flux
        }

    /**
     * BPM and pulse strength, by autocorrelating the onset envelope.
     *
     * The lag with the strongest agreement is the beat period. Pulse is that peak measured against
     * the average correlation — a drum machine towers over its neighbours, a rubato piano barely
     * rises above them — which is what [AcousticFeatures.danceability] means here. It is a claim
     * about regularity, not about a genre.
     */
    private fun tempoOf(onsets: FloatArray): Pair<Float, Float> {
        if (onsets.size < MIN_FRAMES) return DEFAULT_BPM to 0f
        val mean = onsets.average().toFloat()
        val centred = FloatArray(onsets.size) { onsets[it] - mean }

        val minLag = lagFor(AcousticFeatures.MAX_BPM)
        val maxLag = lagFor(AcousticFeatures.MIN_BPM).coerceAtMost(onsets.size / 2)
        if (maxLag <= minLag) return DEFAULT_BPM to 0f

        var bestLag = minLag
        var bestScore = Float.NEGATIVE_INFINITY
        var total = 0.0
        for (lag in minLag..maxLag) {
            var score = 0f
            for (i in 0 until centred.size - lag) score += centred[i] * centred[i + lag]
            score /= (centred.size - lag)
            total += score
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }

        val average = (total / (maxLag - minLag + 1)).toFloat()
        val energy = centred.fold(0f) { acc, v -> acc + v * v } / centred.size
        // Normalised against the signal's own variance, so a loud track is not called danceable
        // for being loud.
        val pulse = if (energy <= 0f) 0f else ((bestScore - average) / energy).coerceIn(0f, 1f)
        return bpmFor(bestLag) to pulse
    }

    private fun lagFor(bpm: Float): Int = (60f * AudioFormat.FRAMES_PER_SECOND / bpm).toInt()

    private fun bpmFor(lag: Int): Float = 60f * AudioFormat.FRAMES_PER_SECOND / lag

    /** RMS on a decibel scale, mapped over the range real music occupies. */
    private fun energyOf(samples: FloatArray): Float {
        var sum = 0.0
        for (sample in samples) sum += sample.toDouble() * sample
        val rms = sqrt(sum / samples.size).toFloat()
        if (rms <= 0f) return 0f
        val db = 20f * log10(rms)
        return ((db - QUIET_DB) / (LOUD_DB - QUIET_DB)).coerceIn(0f, 1f)
    }

    /** Spectral centroid, averaged over frames and expressed as a fraction of Nyquist. */
    private fun brightnessOf(frames: Array<FloatArray>): Float {
        var total = 0f
        var counted = 0
        for (frame in frames) {
            var weighted = 0f
            var magnitude = 0f
            for (bin in frame.indices) {
                weighted += bin * frame[bin]
                magnitude += frame[bin]
            }
            if (magnitude > 0f) {
                total += weighted / magnitude
                counted++
            }
        }
        if (counted == 0) return 0f
        return (total / counted / AudioFormat.BIN_COUNT).coerceIn(0f, 1f)
    }

    /** Energy folded onto the twelve pitch classes, ignoring which octave it came from. */
    private fun chromaOf(frames: Array<FloatArray>): FloatArray {
        val chroma = FloatArray(12)
        val binHz = AudioFormat.SAMPLE_RATE.toFloat() / AudioFormat.FRAME_SIZE
        for (frame in frames) {
            for (bin in frame.indices) {
                val frequency = bin * binHz
                // Below the lowest bass note and above where pitch reads as timbre, a bin says
                // nothing about key and only adds noise to the fold.
                if (frequency < MIN_PITCH_HZ || frequency > MAX_PITCH_HZ) continue
                val midi = 69f + 12f * log2(frequency / 440f)
                val pitchClass = ((midi.toInt() % 12) + 12) % 12
                chroma[pitchClass] += frame[bin]
            }
        }
        return chroma
    }

    /**
     * The strongest pitch class, and how much it stands out.
     *
     * Tonality near zero means the twelve classes are near-equal — atonal, percussive or noisy —
     * and pulls the key point toward the origin rather than asserting a key nothing supports.
     */
    private fun dominantKey(chroma: FloatArray): Pair<Int, Float> {
        val total = chroma.sum()
        if (total <= 0f) return 0 to 0f
        val best = chroma.indices.maxBy { chroma[it] }
        val share = chroma[best] / total
        // A flat distribution gives every class 1/12. Anything above that is evidence.
        val tonality = ((share - 1f / 12f) / (1f - 1f / 12f)).coerceIn(0f, 1f)
        return best to (tonality * TONALITY_GAIN).coerceIn(0f, 1f)
    }

    private companion object {
        /** Below this there is not enough music to measure a tempo from. */
        const val MIN_FRAMES = 64

        /** What a track is called when the beat could not be found at all. */
        const val DEFAULT_BPM = 110f

        const val QUIET_DB = -40f
        const val LOUD_DB = -6f

        /** Roughly C2 up to the top of where a fundamental still carries the tune. */
        const val MIN_PITCH_HZ = 65f
        const val MAX_PITCH_HZ = 2_000f

        /**
         * Even a strongly tonal track spreads energy across the scale, so a raw dominant share
         * rarely passes 0.2. Without this the key axis would sit near the origin for everything
         * and contribute nothing.
         */
        const val TONALITY_GAIN = 3.5f
    }
}
