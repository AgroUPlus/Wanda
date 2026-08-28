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
     * The peaks, band by band.
     *
     * Bands are logarithmic because pitch is: a fixed-width band would give the top octave as much
     * of the spectrum as the bottom four combined, and a bass line and a cymbal would compete for
     * the same slot. One peak per band per frame also spreads the fingerprint across the spectrum
     * rather than letting whatever is loudest — usually the kick drum — supply every landmark.
     *
     * Each band keeps a threshold that is set by the last peak it emitted and decays from there.
     * That is what makes a fingerprint survive a microphone. The obvious alternatives both fail:
     * a threshold taken from the whole recording's average moves when the recording gets quieter,
     * so a room-level excerpt keeps a *different* set of peaks than the file did; and requiring a
     * peak to beat its neighbours in time drops sustained notes entirely except where noise
     * happens to tip one frame over another, which is not repeatable between two recordings of the
     * same music. A decaying threshold is relative to what this band just did, so halving the
     * level of everything leaves the peaks where they were — which is exactly the property
     * matching needs.
     */
    fun peaks(samples: FloatArray): List<Peak> {
        val frameCount = (samples.size - AudioFormat.FRAME_SIZE) / AudioFormat.HOP_SIZE + 1
        if (frameCount <= 0) return emptyList()

        // Per call, not per instance. One `Fingerprinter` is shared by the indexing worker and the
        // listening sheet — `RecognitionRepository` is a singleton and holds exactly one — so
        // instance-level scratch buffers were a data race that silently corrupted both
        // fingerprints whenever an index run overlapped someone tapping the note. Three arrays per
        // call is nothing against the transform they feed.
        val real = FloatArray(AudioFormat.FRAME_SIZE)
        val imag = FloatArray(AudioFormat.FRAME_SIZE)

        val bandCount = BAND_EDGES.size - 1
        // Nothing has been heard yet, so the first frame of each band always sets its own level
        // rather than being judged against a number chosen in advance.
        val thresholds = FloatArray(bandCount) { Float.NEGATIVE_INFINITY }
        val found = mutableListOf<Peak>()
        val magnitudes = FloatArray(AudioFormat.BIN_COUNT)

        for (frame in 0 until frameCount) {
            val start = frame * AudioFormat.HOP_SIZE
            for (i in 0 until AudioFormat.FRAME_SIZE) {
                real[i] = samples[start + i] * window[i]
                imag[i] = 0f
            }
            Fft.transform(real, imag)
            for (bin in 0 until AudioFormat.BIN_COUNT) {
                // Log magnitude: loudness is perceived logarithmically, and on a linear scale a
                // quiet passage produces no peaks worth the name while a loud one produces only
                // the bass. The epsilon keeps silence finite rather than -inf.
                val power = real[bin] * real[bin] + imag[bin] * imag[bin]
                magnitudes[bin] = ln(sqrt(power) + 1e-9f)
            }

            for (band in 0 until bandCount) {
                var bestBin = -1
                var bestMagnitude = Float.NEGATIVE_INFINITY
                for (bin in BAND_EDGES[band] until BAND_EDGES[band + 1]) {
                    if (magnitudes[bin] > bestMagnitude) {
                        bestMagnitude = magnitudes[bin]
                        bestBin = bin
                    }
                }
                if (bestBin < 0) continue

                if (bestMagnitude >= thresholds[band]) {
                    found += Peak(frame, bestBin, bestMagnitude)
                    thresholds[band] = bestMagnitude
                }
                // Decays whether or not a peak fired, so a band that has gone quiet becomes
                // sensitive again instead of staying latched to a level that has passed.
                thresholds[band] -= THRESHOLD_DECAY
            }
        }
        return found
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
        /**
         * Band edges in FFT bins, roughly logarithmic over 0–4 kHz.
         *
         * Six bands: bass, low-mid, mid, high-mid, presence, air.
         */
        val BAND_EDGES = intArrayOf(1, 10, 20, 40, 80, 160, 512)

        /**
         * How fast a band's threshold falls, in log-magnitude units per frame.
         *
         * The one knob controlling how many landmarks a recording produces. Too slow and a loud
         * passage silences the band behind it for seconds; too fast and every frame fires, which
         * bloats the index without adding information.
         */
        const val THRESHOLD_DECAY = 0.08f

        /** Targets paired per anchor. */
        const val FAN_OUT = 4
    }
}
