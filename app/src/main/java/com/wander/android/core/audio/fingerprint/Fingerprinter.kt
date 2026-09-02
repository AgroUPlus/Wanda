package com.wander.android.core.audio.fingerprint

import javax.inject.Inject
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Turns mono 8 kHz PCM into landmarks. The one place fingerprints are made, for files and for the
 * microphone alike — two implementations would be two chances to disagree, and a fingerprint that
 * disagrees with the index matches nothing while looking like it works.
 *
 * Three steps: a spectrogram, the peaks that survive it, and the pairs those peaks form.
 *
 * The peaks are the point. What a microphone in a room does to music is add noise, tilt the
 * spectrum and lose quiet detail — but the *loudest* partial in a frequency band survives all
 * three, and its position in time and frequency survives them unchanged. So the fingerprint keeps
 * only those positions and throws away the magnitudes that produced them.
 */
class Fingerprinter @Inject constructor() {

    /**
     * Hann window. Without one, a frame's abrupt ends smear energy across the whole spectrum and
     * the peak picking below finds edges rather than notes.
     *
     * Shared across calls, unlike the scratch buffers: it is computed once and only ever read.
     */
    private val window = FloatArray(AudioFormat.FRAME_SIZE) { i ->
        (0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (AudioFormat.FRAME_SIZE - 1))).toFloat()
    }

    /**
     * The full pipeline. [samples] must already be mono at [AudioFormat.SAMPLE_RATE].
     *
     * Returns landmarks in anchor order, which is also frame order — the matcher relies on neither,
     * but a stable order makes an index build reproducible.
     */
    fun fingerprint(samples: FloatArray): List<Landmark> = pair(peaks(samples))

    /**
     * The peaks, as a constellation of local maxima.
     *
     * ## Why not a threshold that decays
     *
     * The previous version kept one threshold per frequency band, set by the last peak that band
     * emitted and decaying from there. It reads well — it is relative to what the band just did,
     * so halving the level of everything leaves the peaks where they were — and it does not work,
     * because it is *stateful*. Two recordings of the same passage arrive with different histories
     * (a different starting instant, a room's noise, a speaker's colouration), the thresholds walk
     * apart, and the two fingerprints stop agreeing about which moments were peaks at all.
     *
     * Measured on a Pixel listening to a phone playing music beside it: of the hashes in the file's
     * fingerprint for those six seconds, the microphone's fingerprint shared **14.7%**. The track
     * being played came 11th against an index of two dozen tracks including itself.
     *
     * A local maximum has no history. A point is a peak if nothing within [NEIGHBOUR_FRAMES] in
     * time and [NEIGHBOUR_BINS] in frequency is louder, which is a question about the music and
     * not about what happened a moment ago — so both recordings answer it the same way. It is what
     * Shazam's constellation map is, and the comment this replaces dismissed it for a reason that
     * does not survive being tested: sustained notes are not dropped, because a sustained note is
     * still a ridge with a maximum along it.
     *
     * ## Whitening
     *
     * The per-bin mean is subtracted first. A speaker and a microphone each impose a fixed tilt on
     * the spectrum, and the tilt is exactly what a comparison of absolute magnitudes across bins
     * mistakes for content. Removing each bin's own average over the clip removes the tilt and
     * leaves what varies, which is the music.
     *
     * The strongest [PEAKS_PER_SECOND] survive. A density target rather than a magnitude threshold,
     * because how loud a peak has to be to be interesting depends entirely on the recording.
     */
    fun peaks(samples: FloatArray): List<Peak> {
        val frameCount = (samples.size - AudioFormat.FRAME_SIZE) / AudioFormat.HOP_SIZE + 1
        if (frameCount <= 0) return emptyList()

        // Per call, not per instance: one `Fingerprinter` is shared by the indexing worker and the
        // listening sheet, and instance-level scratch was a data race that silently corrupted both
        // fingerprints whenever an index run overlapped somebody tapping the note.
        val real = FloatArray(AudioFormat.FRAME_SIZE)
        val imag = FloatArray(AudioFormat.FRAME_SIZE)
        val spectrogram = Array(frameCount) { FloatArray(AudioFormat.BIN_COUNT) }

        for (frame in 0 until frameCount) {
            val start = frame * AudioFormat.HOP_SIZE
            for (i in 0 until AudioFormat.FRAME_SIZE) {
                real[i] = samples[start + i] * window[i]
                imag[i] = 0f
            }
            Fft.transform(real, imag)
            val row = spectrogram[frame]
            for (bin in 0 until AudioFormat.BIN_COUNT) {
                // Log magnitude: loudness is perceived logarithmically, and on a linear scale a
                // quiet passage produces no peaks worth the name. The epsilon keeps silence finite.
                val power = real[bin] * real[bin] + imag[bin] * imag[bin]
                row[bin] = ln(sqrt(power) + 1e-9f)
            }
        }

        // Whitening: each bin loses its own average over the clip.
        for (bin in 0 until AudioFormat.BIN_COUNT) {
            var sum = 0.0
            for (frame in 0 until frameCount) sum += spectrogram[frame][bin]
            val mean = (sum / frameCount).toFloat()
            for (frame in 0 until frameCount) spectrogram[frame][bin] -= mean
        }

        // A separable maximum filter, not a scan of the whole neighbourhood per point. The direct
        // form is `frames x bins x (2dt+1) x (2df+1)` comparisons — around 128 million for a single
        // minute of audio, which took the indexer from a couple of seconds a track to over two
        // minutes. Because a maximum over a rectangle is the maximum along one axis of the maxima
        // along the other, the same answer comes out of two linear passes.
        val neighbourhoodMax = maxFilter(spectrogram, frameCount)

        val found = mutableListOf<Peak>()
        for (frame in 0 until frameCount) {
            val row = spectrogram[frame]
            val limit = neighbourhoodMax[frame]
            for (bin in 1 until AudioFormat.BIN_COUNT) {
                // Equality rather than a strict comparison: this point *is* the maximum of its
                // neighbourhood. A plateau marks more than one point, which costs nothing — the
                // density cap below keeps the strongest and they are the same strength.
                if (row[bin] >= limit[bin]) found += Peak(frame, bin, row[bin])
            }
        }

        // A density target, not a level: the strongest survive, and how many that is follows the
        // length of the clip so a six-second query and a whole track are described alike.
        val keep = (PEAKS_PER_SECOND * frameCount / AudioFormat.FRAMES_PER_SECOND).toInt()
        if (found.size <= keep) return found.sortedBy { it.frame }
        return found.sortedByDescending { it.magnitude }
            .take(keep)
            .sortedBy { it.frame }
    }

    /**
     * The maximum of each point's neighbourhood, as two linear passes.
     *
     * Frequency first, then time. Each pass is a sliding-window maximum over a monotonic deque, so
     * every value enters and leaves once however wide the window is — the cost does not depend on
     * [NEIGHBOUR_BINS] or [NEIGHBOUR_FRAMES] at all.
     */
    private fun maxFilter(spectrogram: Array<FloatArray>, frameCount: Int): Array<FloatArray> {
        val bins = AudioFormat.BIN_COUNT
        val byFrequency = Array(frameCount) { FloatArray(bins) }
        for (frame in 0 until frameCount) {
            slidingMax(spectrogram[frame], byFrequency[frame], NEIGHBOUR_BINS)
        }

        val result = Array(frameCount) { FloatArray(bins) }
        val column = FloatArray(frameCount)
        val out = FloatArray(frameCount)
        for (bin in 0 until bins) {
            for (frame in 0 until frameCount) column[frame] = byFrequency[frame][bin]
            slidingMax(column, out, NEIGHBOUR_FRAMES)
            for (frame in 0 until frameCount) result[frame][bin] = out[frame]
        }
        return result
    }

    /** `destination[i]` is the largest of `source[i - radius .. i + radius]`. */
    private fun slidingMax(source: FloatArray, destination: FloatArray, radius: Int) {
        val n = source.size
        if (n == 0) return
        // Indices of candidates, values strictly decreasing: anything smaller than a later arrival
        // can never be the maximum of a window that contains both.
        val deque = IntArray(n)
        var head = 0
        var tail = 0
        for (i in 0 until n + radius) {
            if (i < n) {
                while (tail > head && source[deque[tail - 1]] <= source[i]) tail--
                deque[tail++] = i
            }
            val centre = i - radius
            if (centre >= 0) {
                while (deque[head] < centre - radius) head++
                destination[centre] = source[deque[head]]
            }
        }
    }


    /**
     * Pairs each peak with the few that follow it, inside a window ahead in time.
     *
     * The fan-out is small on purpose. Every extra target multiplies the size of the index and the
     * cost of a lookup, while adding less than the one before it — a handful of pairs per anchor is
     * already enough for the alignment step to find a clear winner.
     */
    fun pair(peaks: List<Peak>): List<Landmark> {
        val ordered = peaks.sortedBy { it.frame }
        val landmarks = mutableListOf<Landmark>()
        for (i in ordered.indices) {
            val anchor = ordered[i]
            var fanned = 0
            var j = i + 1
            while (j < ordered.size && fanned < FAN_OUT) {
                val target = ordered[j]
                val delta = target.frame - anchor.frame
                j++
                if (delta < HashPacking.MIN_DELTA_FRAMES) continue
                if (delta > HashPacking.MAX_DELTA_FRAMES) break
                landmarks += Landmark(
                    hash = HashPacking.pack(anchor.bin, target.bin, delta),
                    anchorFrame = anchor.frame
                )
                fanned++
            }
        }
        return landmarks
    }

    private companion object {
        /** Half-height of the neighbourhood a peak must dominate, in frames (~96 ms either side). */
        const val NEIGHBOUR_FRAMES = 3

        /** And in bins (~70 Hz either side). Wide enough that one note yields one peak, not five. */
        const val NEIGHBOUR_BINS = 9

        /**
         * How many peaks a second of audio keeps.
         *
         * Chosen by measurement rather than taste: swept against a real microphone capture of a
         * known track, 45 gave the best separation for the smallest index. Higher finds a few more
         * matches and costs proportionally more rows; lower starts losing quiet passages.
         */
        const val PEAKS_PER_SECOND = 45

        /**
         * Targets paired per anchor.
         *
         * Six rather than four, and rather than the ten a larger fan would allow: measured, ten
         * raised the true match's score but raised everything else's more — a clip of a *different*
         * track named this one third instead of seventeenth.
         */
        const val FAN_OUT = 6
    }
}
