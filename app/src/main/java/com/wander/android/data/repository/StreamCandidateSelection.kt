package com.wander.android.data.repository

import com.wander.android.data.model.UnifiedTrack

/**
 * Which of several same-title rows, if any, may stand in for [wanted] at playback time.
 *
 * The tiered stream resolver used to take the first row sharing a title and play it. That is how
 * every track called "Memories" played one particular "Memories": the artist was never consulted,
 * so the query answered a question nobody had asked. Playback now uses the same identity test as
 * the library and the merge preview — splits and fingerprint links included — so a substitution
 * the user can see coming is the only substitution that happens.
 *
 * [candidates] arrive in the caller's order of preference and are not reordered here; the first
 * one that passes wins. [wanted] itself is skipped, since standing in for yourself is not a
 * fallback.
 */
internal fun selectSameRecording(
    wanted: UnifiedTrack,
    candidates: List<UnifiedTrack>,
    splits: SplitSet = SplitSet.EMPTY,
    links: RecordingLinkSet = RecordingLinkSet.EMPTY
): UnifiedTrack? = candidates.firstOrNull { candidate ->
    candidate.id != wanted.id &&
        TrackDeduplicator.isSameRecording(wanted, candidate, splits, links)
}
