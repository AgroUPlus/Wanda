package com.wander.android.data.repository

/**
 * When the shared catalogue's name for a track is worth showing over the source's own.
 *
 * The whole of the "complete, never overwrite" rule, in one place and with no database behind it.
 * The catalogue is a set of facts contributed by other devices, and those devices are running the
 * same importers with the same imperfect tags — so it is not automatically more right than the row
 * in front of the user. It is only allowed to fill a gap, or to say the same name with less
 * decoration around it.
 */
internal object CanonicalMetadataMerge {

    /**
     * Whether [candidate] is worth showing in place of the title [current].
     *
     * Two cases, and only two. A blank row takes anything. Otherwise both strings must normalise to
     * the same recording title *and* carry the same variant markers — so they are agreed on what
     * the song is and on which performance of it — and the candidate must be the shorter, which is
     * what "Song" against "Song (Official Video) [HQ]" means.
     *
     * The variant check is not redundant with the title one. `normalizeTitle` strips "(Live)" along
     * with the noise, so without it "All I Need (Live)" and "All I Need" would compare equal and
     * the live take would be silently renamed to the studio cut — the same mistake the deduplicator
     * refuses to make, arriving by way of the title instead of the grouping.
     *
     * Any other disagreement leaves the row alone. Two sources naming a song differently is not
     * evidence that the other device was right, and a user who has curated their tags should not
     * find them rewritten by whatever a stranger's importer happened to produce.
     */
    fun improvesOnTitle(current: String, candidate: String): Boolean {
        if (candidate.isBlank()) return false
        if (current.isBlank()) return true
        if (current == candidate) return false
        return TrackDeduplicator.normalizeTitle(current) == TrackDeduplicator.normalizeTitle(candidate) &&
            TrackDeduplicator.variantsOf(current) == TrackDeduplicator.variantsOf(candidate) &&
            candidate.length < current.length
    }

    /**
     * Whether [candidate] fills a gap in [current].
     *
     * Artist and album get the blank rule only. There is no equivalent of the noise regexes for
     * them — a differing artist string usually means a different credit ("A" against "A feat. B"),
     * which is information rather than decoration, and the app already normalises it for matching
     * without needing to rewrite what is displayed.
     */
    fun fills(current: String, candidate: String): Boolean =
        current.isBlank() && candidate.isNotBlank()
}
