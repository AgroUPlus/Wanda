package com.wander.android.core.audio.melody

import kotlin.math.abs
import kotlin.math.min

/**
 * Compares a hummed melody against a stored one, allowing for the ways people hum.
 *
 * Dynamic time warping, because nobody hums in time. A listener who takes the second bar slowly
 * and rushes the third has produced the right tune, and any comparison that lines the two up note
 * for note calls it a different one. DTW finds the best alignment between the sequences instead of
 * assuming they are already aligned — the cost is a grid of one cell per pair of notes, which for
 * melodies of a few dozen notes is nothing.
 *
 * Key invariance comes for free from [MelodyContour] storing intervals rather than pitches: hum in
 * D what the record plays in F and every delta is identical. Tempo invariance is what the warping
 * itself buys.
 *
 * **Subsequence matching, not whole-sequence.** People hum the chorus, and the stored contour is
 * the whole track. So the alignment may start anywhere in the stored melody and end anywhere after
 * it — anchoring either end would mean only a hum that begins at the first note of the file could
 * ever match, which is nobody.
 */
internal object ContourMatcher {

    /**
     * How badly [query] fits the best-matching stretch of [candidate]. Lower is better.
     *
     * [Float.MAX_VALUE] when there is not enough of either to compare.
     */
    fun distance(query: MelodyContour, candidate: MelodyContour): Float {
        val q = query.notes
        val c = candidate.notes
        if (q.size < MelodyContour.MIN_NOTES || c.isEmpty()) return Float.MAX_VALUE

        // One row per query note; the previous row is all the recurrence needs.
        var previous = FloatArray(c.size + 1)
        var current = FloatArray(c.size + 1)

        // Row zero is zero across the whole candidate: starting the alignment anywhere in the
        // stored melody is free. This is the "starts anywhere" half of subsequence matching.
        for (index in q.indices) {
            current[0] = Float.MAX_VALUE / 4f
            for (j in 1..c.size) {
                val cost = costOf(q[index], c[j - 1])
                val best = min(
                    min(previous[j], current[j - 1]),
                    previous[j - 1]
                )
                current[j] = cost + best
            }
            val swap = previous
            previous = current
            current = swap
        }

        // The cheapest ending anywhere along the candidate: the "ends anywhere" half.
        var best = Float.MAX_VALUE
        for (j in 1..c.size) best = min(best, previous[j])
        // Normalised by the hum's length, so a long hum is not automatically a worse match than a
        // short one. Without this the ranking would prefer whoever gave up soonest.
        return if (best >= Float.MAX_VALUE / 4f) Float.MAX_VALUE else best / q.size
    }

    /**
     * What one note pairing costs.
     *
     * The interval dominates and the duration is a light tiebreak. A wrong interval is a wrong
     * tune; a note held too long is a person humming. Weighting them equally makes rhythm as
     * important as melody, which is precisely backwards for the thing being searched.
     */
    private fun costOf(query: MelodyContour.Note, candidate: MelodyContour.Note): Float {
        val interval = abs(query.delta - candidate.delta).toFloat()
        val duration = abs(query.ticks - candidate.ticks).toFloat() / DURATION_SCALE
        return interval + DURATION_WEIGHT * min(duration, MAX_DURATION_COST)
    }

    /**
     * The distance under which a match is worth showing at all.
     *
     * An average of two semitones of error per note. Above that the alignment is finding
     * coincidences: any two melodies can be warped onto each other if the bar is low enough, and a
     * wrong answer offered confidently is worse than no answer.
     */
    const val MAX_DISTANCE = 2.0f

    /**
     * How much better the winner must be than the runner-up.
     *
     * The same reasoning as the landmark engine's margin: when two tracks fit equally well, naming
     * one is a coin toss presented as a result.
     */
    const val MIN_MARGIN = 1.25f

    private const val DURATION_WEIGHT = 0.35f

    /** A tick is 100 ms; this puts a full second of difference at a cost of one semitone. */
    private const val DURATION_SCALE = 10f

    /** However long a note is held, it cannot outweigh a wrong interval. */
    private const val MAX_DURATION_COST = 3f
}
