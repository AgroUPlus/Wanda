package com.wander.android.core.audio.melody

import kotlin.math.abs

/**
 * Folds octave slips out of a pitch track.
 *
 * ## Why this is needed at all
 *
 * YIN answers "how long until the signal looks like itself again", and a signal that repeats every
 * 200 samples also repeats every 400 — so the wrong answer is almost always the right one doubled
 * or halved. The cumulative-mean normalisation makes that rare on a clean monophonic signal and
 * common on anything else, because a mix gives it several periodicities to choose between and it
 * can pick a different one from frame to frame.
 *
 * Measured across a real library's stored contours: **a quarter of all intervals were jumps of
 * eleven semitones or more**, and many were pinned at the ±18 clamp — a melody that leaps an
 * octave and a half and immediately comes back is not a melody, it is the detector changing its
 * mind. Those intervals are not merely wrong, they are actively misleading: two of them wreck the
 * shape of a phrase that was otherwise correct.
 *
 * ## What it does
 *
 * Each voiced frame is compared against a running reference — the median of the recent voiced
 * frames, which is robust to exactly the outliers being removed — and shifted by whole octaves to
 * sit as close to it as possible. A frame that is already closest wins unchanged, so a track with
 * no slips passes through untouched.
 *
 * ## What it costs
 *
 * A melody that genuinely leaps an octave is folded flat, and that is a real loss. It is the right
 * trade: a genuine octave leap is rare and costs one interval, while a slip is common and costs
 * two — one out and one back — and takes the surrounding phrase with it.
 */
internal object OctaveCorrection {

    /** How many recent voiced frames the reference is taken from. */
    private const val WINDOW = 12

    /** Semitones per octave, and the most this will shift anything. */
    private const val OCTAVE = 12f
    private const val MAX_SHIFTS = 2

    /**
     * Answers a copy of [pitches] with octave slips folded in. Unvoiced frames (zero) pass through
     * untouched, because *when* a note ended is information the contour needs.
     */
    fun apply(pitches: FloatArray): FloatArray {
        if (pitches.isEmpty()) return pitches
        val corrected = pitches.copyOf()
        val recent = ArrayDeque<Float>()

        for (index in corrected.indices) {
            val hz = corrected[index]
            if (hz <= 0f) continue
            val midi = PitchDetector.midiOf(hz)

            val reference = median(recent)
            val chosen = if (reference == null) midi else nearestOctave(midi, reference)
            if (chosen != midi) {
                // Back to hertz, so the rest of the pipeline sees an ordinary pitch track.
                corrected[index] = 440f * Math.pow(2.0, ((chosen - 69f) / 12f).toDouble()).toFloat()
            }

            recent.addLast(chosen)
            if (recent.size > WINDOW) recent.removeFirst()
        }
        return corrected
    }

    /** [midi] shifted by whole octaves to sit as near [reference] as it can. */
    private fun nearestOctave(midi: Float, reference: Float): Float {
        var best = midi
        var bestDistance = abs(midi - reference)
        for (shift in -MAX_SHIFTS..MAX_SHIFTS) {
            if (shift == 0) continue
            val candidate = midi + shift * OCTAVE
            val distance = abs(candidate - reference)
            if (distance < bestDistance) {
                bestDistance = distance
                best = candidate
            }
        }
        return best
    }

    /**
     * The middle of the recent frames, not their mean.
     *
     * A mean sits between the true pitch and whatever the detector slipped to, which is a reference
     * that belongs to neither and pulls the next frame towards the error.
     */
    private fun median(values: ArrayDeque<Float>): Float? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }
}
