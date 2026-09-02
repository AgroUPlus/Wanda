package com.wander.android.data.repository

import com.wander.android.data.model.UnifiedAlbum

/**
 * Picking which of the albums a search returned is the one the link meant.
 *
 * Pure, and separate from the searching, because this is the part that can be wrong in a way the
 * user notices: a link to *Kid A* that opens *Kid A Mnesia* has technically resolved. Search
 * ranking across three backends is not something to leave to whichever one answered first.
 *
 * Deliberately strict. An album that does not clearly match is no match, and the honest outcome is
 * telling the recipient the record was not found in their sources — they can then go and look for
 * it themselves, which is a far better position than a wrong record playing.
 */
internal object AlbumResolution {

    /**
     * The best candidate, or null.
     *
     * Candidates are expected in source-preference order — the caller searches its configured
     * sources in the order it prefers them — and that order breaks ties, so someone who owns the
     * record gets their own copy rather than a stream of it.
     */
    fun bestMatch(link: UniversalAlbumLink, candidates: List<UnifiedAlbum>): UnifiedAlbum? {
        val wantedTitle = normalise(link.title)
        val wantedArtist = normalise(link.artist)
        if (wantedTitle.isEmpty() || wantedArtist.isEmpty()) return null

        val artistMatches = candidates.filter { normalise(it.artist) == wantedArtist }
        if (artistMatches.isEmpty()) return null

        // An exact title, once the punctuation is set aside. Anything less is not offered: a
        // "contains" test matches a deluxe edition, a live record and a compilation with the same
        // confidence as the album itself.
        val exact = artistMatches.filter { normalise(it.title) == wantedTitle }
        if (exact.isEmpty()) return null
        if (exact.size == 1) return exact.first()

        // Several copies of one record — the usual case for someone with a server *and* a
        // subscription. The hints decide, and where they cannot, the caller's preference order
        // already has.
        return exact.firstOrNull { candidate -> link.year != null && candidate.year == link.year }
            ?: exact.firstOrNull { candidate ->
                link.trackCount != null && candidate.songCount == link.trackCount
            }
            ?: exact.first()
    }

    /**
     * Case, accents and punctuation set aside — the same treatment
     * [TrackDeduplicator.normalizeTitle] gives a track, for the same reason: two backends spell one
     * record three ways and none of them is wrong.
     */
    private fun normalise(value: String): String =
        TrackDeduplicator.normalizeTitle(value)
}
