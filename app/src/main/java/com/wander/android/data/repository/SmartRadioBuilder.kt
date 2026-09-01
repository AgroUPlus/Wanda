package com.wander.android.data.repository

import com.wander.android.core.audio.features.AcousticFeatures
import com.wander.android.data.model.UnifiedTrack

/**
 * Orders a pool of candidate tracks into a queue that flows.
 *
 * **The vectors rank; they never supply.** This is the whole shape of the feature and it is worth
 * stating plainly, because the obvious design is wrong. Only tracks stored on this device are ever
 * decoded, so only they have vectors — a radio built *from* the acoustic index could return
 * nothing but music already in the library, and would narrow every time it ran. The candidates
 * therefore keep coming from where they come from today, the source's own radio included, and all
 * this does is decide the order and refuse the jarring jumps.
 *
 * A candidate with no vector is unknown, not unsuitable. Treating missing data as a rejection is
 * the same mistake in a quieter form: it would silently delete every never-before-heard track from
 * the queue, which is precisely the music a radio exists to play. They get a reserved share
 * instead — see [EXPLORATION_SHARE].
 */
internal object SmartRadioBuilder {

    /**
     * One candidate: the track, and its vector if anything ever measured one.
     */
    internal data class Candidate(
        val track: UnifiedTrack,
        val features: AcousticFeatures?
    )

    /**
     * How much of the queue is held for tracks with no vector.
     *
     * Not a tuning knob so much as a floor on discovery. At zero, a library that has been fully
     * indexed would always out-rank anything new, and the radio would converge on the same well
     * measured songs; the listener would experience that as it "getting stale", with no way to
     * tell it was a ranking artefact.
     */
    const val EXPLORATION_SHARE = 0.35f

    /**
     * How far a neighbour may sit from the running position before it is passed over.
     *
     * Generous on purpose. This is here to stop a ballad landing between two club tracks, not to
     * enforce a mood — a radio that only plays one tempo is a worse failure than an occasional
     * awkward segue.
     */
    const val MAX_STEP = 0.55f

    /**
     * The queue.
     *
     * Ranked tracks are sequenced greedily from the seed: each next track is the closest remaining
     * one to *the track just chosen*, not to the seed. Ranking everything against the seed alone
     * produces a queue that is uniformly similar to one song and lurches between its own
     * neighbours; walking it makes the set drift, which is what a radio is supposed to do.
     */
    fun build(
        seed: AcousticFeatures?,
        candidates: List<Candidate>,
        count: Int
    ): List<UnifiedTrack> {
        val pool = candidates.distinctBy { it.track.id }
        if (pool.isEmpty() || count <= 0) return emptyList()

        val unranked = pool.filter { it.features == null }.map { it.track }
        val ranked = pool.filter { it.features != null }

        // With no seed vector — an unindexed seed, a stream — there is nothing to rank against and
        // the honest answer is the pool in the order it arrived, not a fabricated ordering.
        if (seed == null || ranked.isEmpty()) return pool.map { it.track }.take(count)

        val explore = (count * EXPLORATION_SHARE).toInt().coerceAtMost(unranked.size)
        val walk = walkFrom(seed, ranked, count - explore)
        return interleave(walk, unranked.take(explore)).take(count)
    }

    /** The greedy nearest-neighbour walk described on [build]. */
    private fun walkFrom(
        seed: AcousticFeatures,
        ranked: List<Candidate>,
        count: Int
    ): List<UnifiedTrack> {
        val remaining = ranked.toMutableList()
        val ordered = mutableListOf<UnifiedTrack>()
        var position = seed

        while (ordered.size < count && remaining.isNotEmpty()) {
            var bestIndex = -1
            var bestDistance = Float.MAX_VALUE
            remaining.forEachIndexed { index, candidate ->
                val distance = position.distanceTo(candidate.features!!)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestIndex = index
                }
            }
            // Everything left is a jump. Stopping is better than making it: the exploration share
            // below will fill the rest of the queue with music that was never claimed to fit.
            if (bestIndex < 0 || bestDistance > MAX_STEP) break
            val chosen = remaining.removeAt(bestIndex)
            ordered += chosen.track
            position = chosen.features!!
        }
        return ordered
    }

    /**
     * Spreads [extras] through [ordered] rather than appending them.
     *
     * Appended, the unranked share would be a block of unrelated music at the end of every queue,
     * which a listener reads as the radio breaking down. Spread out, an unknown track arrives as a
     * change of direction between two that fit.
     */
    private fun interleave(ordered: List<UnifiedTrack>, extras: List<UnifiedTrack>): List<UnifiedTrack> {
        if (extras.isEmpty()) return ordered
        if (ordered.isEmpty()) return extras

        val gap = (ordered.size / (extras.size + 1)).coerceAtLeast(1)
        val result = mutableListOf<UnifiedTrack>()
        var next = 0
        ordered.forEachIndexed { index, track ->
            result += track
            if (next < extras.size && (index + 1) % gap == 0) {
                result += extras[next]
                next++
            }
        }
        while (next < extras.size) {
            result += extras[next]
            next++
        }
        return result
    }
}
