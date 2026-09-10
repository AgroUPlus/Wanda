package com.wander.android.data.repository

import com.wander.android.data.model.UnifiedTrack

/**
 * Everything that decides whether two rows are one recording, as one snapshot.
 *
 * The user's pins ([SplitSet]) and the fingerprinter's links ([RecordingLinkSet]) are never useful
 * apart: every caller that has one needs the other, because [TrackDeduplicator] takes both and they
 * overrule each other in a fixed order — a pin makes two rows different whatever the audio says,
 * and a link makes them the same whatever the tags say. Read separately they were fetched as a pair
 * in fourteen places across eight files, and each site had to remember to thread both through to
 * every helper it called.
 *
 * Holding them together also closes a narrow window: the two arrive from two `suspend` calls, so a
 * write landing between them produced a judgement made half before it and half after. One snapshot
 * cannot straddle a write.
 */
class RecordingRules(
    val splits: SplitSet,
    val links: RecordingLinkSet
) {

    /** Whether [a] and [b] are the same performance. */
    fun isSame(a: UnifiedTrack, b: UnifiedTrack): Boolean =
        TrackDeduplicator.isSameRecording(a, b, splits, links)

    /** One row per recording, keeping the first copy of each. */
    fun distinct(tracks: List<UnifiedTrack>): List<UnifiedTrack> =
        TrackDeduplicator.distinctRecordings(tracks, splits, links)

    /** The same rows, bucketed by recording. */
    fun group(tracks: List<UnifiedTrack>): List<List<UnifiedTrack>> =
        TrackDeduplicator.groupRecordings(tracks, splits, links)

    /**
     * The first of [candidates] that may stand in for [wanted], in the caller's own order of
     * preference. [wanted] is never a substitute for itself.
     */
    fun substituteFor(wanted: UnifiedTrack, candidates: List<UnifiedTrack>): UnifiedTrack? =
        selectSameRecording(wanted, candidates, splits, links)

    /** The rows in [candidates] that are the same recording as [track], excluding [track] itself. */
    fun renditionsAmong(track: UnifiedTrack, candidates: List<UnifiedTrack>): List<UnifiedTrack> =
        candidates.filter { it.id != track.id && isSame(track, it) }

    companion object {
        /**
         * No pins and no links — the matcher on tags and duration alone.
         *
         * For pure helpers that are handed their rules rather than loading them, and for tests
         * that are exercising the tag comparison itself.
         */
        val NONE = RecordingRules(SplitSet.EMPTY, RecordingLinkSet.EMPTY)
    }
}
