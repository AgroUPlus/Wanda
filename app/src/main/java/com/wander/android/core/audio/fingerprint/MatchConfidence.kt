package com.wander.android.core.audio.fingerprint

/**
 * Deciding whether the best-aligned track is actually the answer.
 *
 * ## Why the runner-up is the wrong thing to compare against
 *
 * The obvious rule — the winner must clear the second place by some margin — assumes the runner-up
 * is a rival. With a hundred indexed tracks it is not: it is simply the luckiest coincidence, and
 * how lucky the luckiest of a hundred gets grows with the size of the library and the length of the
 * clip, not with anything about the music.
 *
 * Measured on a real library, every attempt — hits and misses alike — put the runner-up in the same
 * narrow band, because that band *is* the noise. A correct identification scoring 214 against a
 * runner-up of 144 was thrown away for failing to clear it by 1.6×, while the clip that matched
 * nothing scored 142 against 140. Those two are not close calls of the same kind, and a ratio
 * against the runner-up cannot tell them apart.
 *
 * ## What is compared instead
 *
 * The median of every candidate's score estimates that noise floor directly, and what identifies a
 * recording is how far it stands *above* it. Subtracting the floor first turns the two cases above
 * into a lead of about a hundred against a lead of about two — which is the distinction the ratio
 * was always trying and failing to express.
 *
 * The median rather than the mean, because the winner and a handful of near-misses are exactly the
 * outliers that would drag a mean upward and hide the thing being measured.
 */
object MatchConfidence {

    /** How far above the noise the winner must stand before it is an answer at all. */
    const val MIN_EXCESS = 20

    /** And how far it must clear the best coincidence, once both are measured from that floor. */
    const val MIN_MARGIN = 1.6

    /**
     * Judges the candidates.
     *
     * [scores] is every track's best aligned vote count, in any order. Fewer than three candidates
     * leaves no distribution to estimate a floor from, so the floor is taken as zero and the rule
     * degrades to the plain comparison it replaces — which is the right behaviour on a small index,
     * where there is no crowd for a coincidence to be the luckiest of.
     */
    fun assess(scores: List<Int>): Assessment {
        if (scores.isEmpty()) return Assessment(0, 0, 0, false)

        val sorted = scores.sortedDescending()
        val best = sorted[0]
        val runnerUp = sorted.getOrNull(1) ?: 0
        val noiseFloor = if (sorted.size >= 3) sorted[sorted.size / 2] else 0

        val bestExcess = (best - noiseFloor).coerceAtLeast(0)
        val runnerUpExcess = (runnerUp - noiseFloor).coerceAtLeast(0)

        val accepted = bestExcess >= MIN_EXCESS &&
            (runnerUpExcess == 0 || bestExcess >= runnerUpExcess * MIN_MARGIN)

        return Assessment(noiseFloor, bestExcess, runnerUpExcess, accepted)
    }

    data class Assessment(
        val noiseFloor: Int,
        val bestExcess: Int,
        val runnerUpExcess: Int,
        val accepted: Boolean
    )
}
