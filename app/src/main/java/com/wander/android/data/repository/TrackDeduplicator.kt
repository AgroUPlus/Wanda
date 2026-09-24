package com.wander.android.data.repository

import com.wander.android.data.model.UnifiedTrack
import kotlin.math.abs

/**
 * Collapses the same recording appearing from several backends down to the best-ranked one, so a
 * song held locally does not also show up as a YouTube Music row.
 *
 * Matching is deliberately conservative. Hiding a track the user wanted is far worse than showing
 * a duplicate, so anything short of strong evidence leaves both rows in place.
 */
object TrackDeduplicator {

    /**
     * Two transfers of one recording run to nearly the same length; different arrangements do not.
     * Three seconds absorbs encoder and tagging drift without merging distinct takes.
     */
    private const val DURATION_TOLERANCE_MS = 3_000L

    internal typealias RecordingKey = TrackNormalizer.RecordingKey

    /**
     * Keeps one track per distinct recording, preferring the source with the lowest
     * [com.wander.android.data.model.SourceType.priority]. Input order is otherwise preserved.
     */
    fun deduplicate(tracks: List<UnifiedTrack>): List<UnifiedTrack> {
        if (tracks.size < 2) return tracks

        val distinct = tracks.distinctBy { it.id }
        if (distinct.size < 2) return distinct

        val buckets = LinkedHashMap<RecordingKey, MutableList<UnifiedTrack>>()
        val unmergeable = mutableListOf<UnifiedTrack>()

        for (track in distinct) {
            if (track.durationMs <= 0L) {
                unmergeable += track
                continue
            }
            buckets.getOrPut(keyOf(track)) { mutableListOf() } += track
        }

        val originalOrder = distinct.withIndex().associate { (index, track) -> track.id to index }
        return (buckets.values.flatMap(::collapseByDuration) + unmergeable)
            .sortedBy { originalOrder[it.id] }
    }

    /**
     * The same tracks, gathered into one list per recording.
     */
    fun groupRecordings(
        tracks: List<UnifiedTrack>,
        splits: SplitSet = SplitSet.EMPTY,
        links: RecordingLinkSet = RecordingLinkSet.EMPTY
    ): List<List<UnifiedTrack>> {
        val buckets = LinkedHashMap<RecordingKey, MutableList<UnifiedTrack>>()
        val alone = mutableListOf<List<UnifiedTrack>>()

        for (track in tracks.distinctBy { it.id }) {
            if (track.durationMs <= 0L) {
                alone += listOf(track)
                continue
            }
            buckets.getOrPut(keyOf(track)) { mutableListOf() } += track
        }

        val grouped = buckets.values.flatMap { bucket ->
            val groups = mutableListOf<MutableList<UnifiedTrack>>()
            for (track in bucket) {
                val match = groups.firstOrNull { group ->
                    abs(group.first().durationMs - track.durationMs) <= DURATION_TOLERANCE_MS &&
                        group.none { splits.isApart(it.id, track.id) }
                }
                if (match == null) groups += mutableListOf(track) else match += track
            }
            groups.map { group -> group.sortedBy { it.source.priority } }
        }
        return foldLinkedGroups(grouped + alone, splits, links)
            .map { group -> group.sortedBy { it.source.priority } }
    }

    /**
     * The same list with later copies of a recording already in it removed.
     */
    fun distinctRecordings(
        tracks: List<UnifiedTrack>,
        splits: SplitSet = SplitSet.EMPTY,
        links: RecordingLinkSet = RecordingLinkSet.EMPTY
    ): List<UnifiedTrack> {
        val kept = mutableListOf<UnifiedTrack>()
        for (track in tracks) {
            if (kept.none { isSameRecording(it, track, splits, links) }) kept += track
        }
        return kept
    }

    /** Within one bucket, merge tracks whose lengths agree and keep the best-ranked of each. */
    private fun collapseByDuration(bucket: List<UnifiedTrack>): List<UnifiedTrack> {
        if (bucket.size == 1) return bucket
        val winners = mutableListOf<UnifiedTrack>()
        for (track in bucket) {
            val match = winners.indexOfFirst {
                abs(it.durationMs - track.durationMs) <= DURATION_TOLERANCE_MS
            }
            when {
                match < 0 -> winners += track
                track.source.priority < winners[match].source.priority -> winners[match] = track
            }
        }
        return winners
    }

    /**
     * A stable identity for the *recording* rather than the row.
     */
    fun recordingKey(track: UnifiedTrack): String = with(keyOf(track)) {
        "$artist|$title|${variants.sorted().joinToString(",")}"
    }

    /**
     * Whether two tracks are the same performance.
     */
    fun isSameRecording(
        a: UnifiedTrack,
        b: UnifiedTrack,
        splits: SplitSet = SplitSet.EMPTY,
        links: RecordingLinkSet = RecordingLinkSet.EMPTY
    ): Boolean {
        if (splits.isApart(a.id, b.id)) return false
        if (links.isLinked(a.id, b.id)) return true
        if (a.durationMs <= 0L || b.durationMs <= 0L) return false
        if (keyOf(a) != keyOf(b)) return false
        return abs(a.durationMs - b.durationMs) <= DURATION_TOLERANCE_MS
    }

    internal fun keyOf(track: UnifiedTrack) = TrackNormalizer.keyOf(track)

    internal fun normalizeArtist(artist: String): String =
        TrackNormalizer.normalizeArtist(artist)

    internal fun normalizeTitle(title: String): String =
        TrackNormalizer.normalizeTitle(title)

    internal fun variantsOf(title: String): Set<String> =
        TrackNormalizer.variantsOf(title)
}
