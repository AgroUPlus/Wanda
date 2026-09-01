package com.wander.android.core.audio.melody

import com.wander.android.core.audio.fingerprint.AudioFormat
import javax.inject.Inject
import kotlin.math.log2

/**
 * Tracks the fundamental frequency of a monophonic signal, frame by frame, with YIN.
 *
 * Autocorrelation asks "how long until the signal looks like itself again", and answers an octave
 * too low about as often as it is right, because a signal repeating every 200 samples also repeats
 * every 400. YIN is that idea with the two fixes that matter: a *difference* function rather than a
 * product, so loud frames do not automatically score better, and a cumulative-mean normalisation
 * that makes the first period stand out from its own multiples. Both are cheap, and octave errors
 * are the failure a hummed melody cannot survive — a contour half of which is an octave out matches
 * nothing.
 *
 * Monophonic is a real precondition, not a detail. Humming, whistling and singing satisfy it; a
 * finished record does not, and this returns whatever periodicity dominates there — often the bass
 * rather than the tune. See [MelodyContour] for what that costs and why it is still worth storing.
 */
class PitchDetector @Inject constructor() {

    /**
     * One frequency estimate per hop, in Hz. Zero where no pitch was found.
     *
     * Zero rather than an omission, because *when* a frame was unvoiced is information: it is where
     * a note ends, and dropping it would glue a phrase into one long note.
     */
    fun track(samples: FloatArray): FloatArray {
        val frame = AudioFormat.FRAME_SIZE
        val hop = AudioFormat.HOP_SIZE
        if (samples.size < frame) return FloatArray(0)

        val count = (samples.size - frame) / hop + 1
        val pitches = FloatArray(count)
        val difference = FloatArray(MAX_TAU + 1)
        val normalised = FloatArray(MAX_TAU + 1)

        for (index in 0 until count) {
            pitches[index] = pitchOf(samples, index * hop, frame, difference, normalised)
        }
        return pitches
    }

    /** The buffers are passed in and reused: this runs a few hundred times per clip. */
    private fun pitchOf(
        samples: FloatArray,
        offset: Int,
        frame: Int,
        difference: FloatArray,
        normalised: FloatArray
    ): Float {
        // Step 1: the squared difference between the frame and itself delayed by tau.
        for (tau in MIN_TAU..MAX_TAU) {
            var sum = 0f
            for (i in 0 until frame - tau) {
                val delta = samples[offset + i] - samples[offset + i + tau]
                sum += delta * delta
            }
            difference[tau] = sum
        }

        // Step 2: divide each by the running mean of everything before it. This is the whole trick.
        // A true period and its double both dip; only the true one dips below the running average
        // of the dips before it, because there is nothing before it to average.
        var running = 0f
        normalised[MIN_TAU] = 1f
        for (tau in MIN_TAU..MAX_TAU) {
            running += difference[tau]
            normalised[tau] = if (running == 0f) 1f else difference[tau] * (tau - MIN_TAU + 1) / running
        }

        // Step 3: the first tau that clears the threshold, not the smallest value overall. Taking
        // the global minimum is what puts the answer an octave down.
        var chosen = -1
        var tau = MIN_TAU
        while (tau <= MAX_TAU) {
            if (normalised[tau] < THRESHOLD) {
                while (tau + 1 <= MAX_TAU && normalised[tau + 1] < normalised[tau]) tau++
                chosen = tau
                break
            }
            tau++
        }
        if (chosen < 0) return 0f

        // Step 4: the dip rarely sits exactly on a sample. A parabola through its neighbours puts
        // it between them, which is the difference between a clean semitone and a smeared one.
        val period = refine(normalised, chosen)
        if (period <= 0f) return 0f
        return AudioFormat.SAMPLE_RATE / period
    }

    private fun refine(values: FloatArray, tau: Int): Float {
        if (tau <= MIN_TAU || tau >= MAX_TAU) return tau.toFloat()
        val previous = values[tau - 1]
        val current = values[tau]
        val next = values[tau + 1]
        val denominator = 2f * (2f * current - next - previous)
        if (denominator == 0f) return tau.toFloat()
        return tau + (next - previous) / denominator
    }

    companion object {
        /**
         * Absolute threshold. Below this a dip is called a period.
         *
         * The value from the YIN paper. Lower finds fewer notes but trusts them; higher calls
         * room noise a pitch, and a contour built from noise is worse than a short one.
         */
        private const val THRESHOLD = 0.15f

        /**
         * The pitch range searched, as lag in samples at [AudioFormat.SAMPLE_RATE].
         *
         * Roughly 78 Hz to 1000 Hz: below a low male voice, above a whistle. Widening it costs
         * time on every frame and buys octave errors at both ends.
         */
        private const val MIN_TAU = 8
        private const val MAX_TAU = 102

        /** Hz to MIDI, where 69 is A440. The scale a melody is actually compared in. */
        fun midiOf(hz: Float): Float = 69f + 12f * log2(hz / 440f)
    }
}
