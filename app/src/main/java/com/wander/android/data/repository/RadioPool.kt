package com.wander.android.data.repository

import com.wander.android.data.model.UnifiedTrack

/**
 * The candidates a radio around [seed] may actually play.
 *
 * A station is "more like this", so three kinds of candidate are never it: the seed itself, the
 * seed on another backend (the same recording under a different id), and the same *song* in
 * another guise — a cover, a live take, a karaoke upload, a lyric video. The last is the one a
 * listener reads as the radio matching by name: a source's own radio routinely leads with every
 * other upload titled like the seed, and without this filter those sat at the top of the queue.
 *
 * One row per recording, keeping the first copy, so callers decide preference by order.
 */
internal object RadioPool {

    fun of(seed: UnifiedTrack, candidates: List<UnifiedTrack>, rules: RecordingRules): List<UnifiedTrack> {
        val seedTitle = TrackNormalizer.normalizeTitle(seed.title)
        return rules.distinct(candidates).filterNot { candidate ->
            candidate.id == seed.id ||
                rules.isSame(seed, candidate) ||
                (seedTitle.isNotEmpty() && TrackNormalizer.normalizeTitle(candidate.title) == seedTitle)
        }
    }
}
