package com.wander.android.data.repository

import com.wander.android.data.model.UnifiedTrack

/**
 * Picking which of the tracks a search returned is the one a [UniversalTrackLink] meant.
 *
 * Mirrors [AlbumResolution]: deliberately strict, and pure so it stays separate from the
 * searching. A track that does not clearly match is no match — the honest outcome is telling the
 * recipient the recording was not found in their sources.
 */
internal object TrackResolution {

    /** Same tolerance as [TrackDeduplicator]'s own duration match, kept private to each. */
    private const val DURATION_TOLERANCE_MS = 3_000L

    /**
     * The best candidate, or null.
     *
     * Candidates are expected in source-preference order — the caller searches its configured
     * sources in the order it prefers them — and that order breaks ties.
     */
    fun bestMatch(link: UniversalTrackLink, candidates: List<UnifiedTrack>): UnifiedTrack? {
        val wantedTitle = normalise(link.title)
        val wantedArtist = normalise(link.artist)
        if (wantedTitle.isEmpty() || wantedArtist.isEmpty()) return null

        val artistMatches = candidates.filter { normalise(it.artist) == wantedArtist }
        if (artistMatches.isEmpty()) return null

        val exact = artistMatches.filter { normalise(it.title) == wantedTitle }
        if (exact.isEmpty()) return null
        if (exact.size == 1) return exact.first()

        // Several recordings of one song — a live version, a cover, a re-record — so the hints
        // decide, and where they cannot, the caller's preference order already has.
        return exact.firstOrNull { candidate ->
            link.album != null && normalise(candidate.album.orEmpty()) == normalise(link.album)
        } ?: exact.firstOrNull { candidate ->
            link.durationMs != null && durationsMatch(candidate.durationMs, link.durationMs)
        } ?: exact.first()
    }

    private fun durationsMatch(a: Long, b: Long): Boolean =
        kotlin.math.abs(a - b) <= DURATION_TOLERANCE_MS

    private fun normalise(value: String): String =
        TrackDeduplicator.normalizeTitle(value)
}
