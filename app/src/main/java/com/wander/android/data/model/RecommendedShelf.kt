package com.wander.android.data.model

import androidx.compose.runtime.Immutable

/**
 * A shelf of recommendations as a backend itself groups them — its own title, its own picks.
 *
 * Deliberately not flattened into one list of tracks. A recommendation feed's *shape* is part of
 * what it recommends: "Listen again" and "Mixed for you" are different suggestions about the same
 * library, and merging them throws away the only explanation the user gets for why a track is
 * being offered.
 */
@Immutable
data class RecommendedShelf(
    val id: String,
    val title: String,
    val tracks: List<UnifiedTrack> = emptyList()
) {
    /**
     * Whether this shelf belongs on Home as a kind of music. A feed also carries shelves that are
     * about one thing — "Troye Sivan - She's the Best", "Because you listened to X" — which pile up
     * (one per recent listen), crowd out the rest, and in the second case duplicate the single
     * "Because you listened to" shelf Home makes itself. Also left out: "Listen again", which
     * Recently Played already covers, and music videos, which this player has no surface for.
     */
    val isGeneric: Boolean
        get() = id != ListenAgainId &&
            !title.contains("video", ignoreCase = true) &&
            !SpecificTitle.containsMatchIn(title) &&
            PersonalPrefixes.none { title.startsWith(it, ignoreCase = true) }

    private companion object {
        const val ListenAgainId = "ytm_listen_again"

        /** "Artist - Song": a dash set off by spaces. */
        val SpecificTitle = Regex("""\s[-\u2013\u2014]\s""")

        val PersonalPrefixes = listOf("Because you", "Similar to", "More like")
    }
}
