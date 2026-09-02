package com.wander.android.core.audio.fingerprint

/**
 * Picking the time offset a clip's landmarks agree on.
 *
 * A clip shares scattered hashes with every busy track, so the count of shared hashes identifies
 * nothing. What identifies a recording is that its matches line up at *one* distance: if the clip
 * really is forty seconds into a song, every matching landmark sits the same distance from where
 * it sits in the file. The fullest such bin is the answer.
 *
 * ## Why neighbouring bins count
 *
 * The offset is a whole number of frames, and a frame is 32 ms. Nothing makes the microphone start
 * on a frame boundary, so the clip's grid sits at an arbitrary phase against the grid the file was
 * indexed on. Each landmark's offset then rounds to whichever side of the boundary it happens to
 * fall — so a single true alignment arrives split across two adjacent bins, in whatever proportion
 * the phase dictates.
 *
 * Taking the fullest bin alone therefore reads a genuine match at roughly half its strength, and
 * near-worst phase halves it. That is enough to drop a real match under the score floor, and — the
 * worse failure — enough to let a coincidental alignment on another track come out ahead, which is
 * an answer that is confidently wrong rather than absent.
 *
 * Summing each bin with its two neighbours restores the split. The window is deliberately ±1 and
 * not wider: two frames is 64 ms, already past what phase alone can explain, and a wider window
 * would start gathering genuinely unrelated coincidences into one score.
 */
object OffsetAlignment {

    /** How many frames either side of a bin are counted as the same alignment. */
    const val TOLERANCE_FRAMES = 1

    /** The strongest alignment in [bins], as the offset and the votes standing behind it. */
    fun best(bins: Map<Int, Int>): Aligned? {
        var bestOffset = 0
        var bestScore = 0
        for ((offset, _) in bins) {
            var score = 0
            for (d in -TOLERANCE_FRAMES..TOLERANCE_FRAMES) {
                score += bins[offset + d] ?: 0
            }
            if (score > bestScore) {
                bestScore = score
                bestOffset = offset
            }
        }
        return if (bestScore == 0) null else Aligned(bestOffset, bestScore)
    }

    /** An offset, in frames, and how many landmarks agreed on it. */
    data class Aligned(val offsetFrames: Int, val votes: Int)
}
