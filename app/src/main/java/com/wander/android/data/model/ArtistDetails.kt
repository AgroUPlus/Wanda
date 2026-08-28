package com.wander.android.data.model

/**
 * An artist's own page, as the backend publishes it.
 *
 * Wanda used to build an artist screen by *searching* for their name and grouping whatever came
 * back — which is why every artist looked the same everywhere: one row of records and one list of
 * songs, in an order nobody chose, with no bio, because a search result set has none of that.
 *
 * This is the page itself instead. The [sections] arrive in the order the source put them in and
 * keep the titles the source gave them — "Albums", "Singles", "Videos", "Fans might also like" —
 * because that ordering is editorial and rewriting it into a fixed layout would throw away the one
 * thing an artist page has that a search does not.
 */
data class ArtistDetails(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
    /** What the source says about them, in their own page's words. Null when it says nothing. */
    val bio: String? = null,
    val sections: List<ArtistSection> = emptyList(),
    /** Other artists the backend suggests. Empty when it suggests none, which is common. */
    val related: List<RelatedArtist> = emptyList()
)

/** One titled block on an artist's page. */
sealed interface ArtistSection {
    val title: String
}

/** Songs — the "Songs", "Top songs" or "Videos" shelves. */
data class ArtistTrackSection(
    override val title: String,
    val tracks: List<UnifiedTrack>
) : ArtistSection

/** Records — "Albums", "Singles", "EPs". */
data class ArtistAlbumSection(
    override val title: String,
    val albums: List<UnifiedAlbum>,
    /**
     * Where the rest of this shelf lives, when the page only showed the first handful.
     *
     * An artist carousel carries about ten tiles; the full discography sits behind the shelf's
     * "more" button, which is a browse id plus an opaque `params` blob. Null means the shelf is
     * already complete — there is nothing further to fetch, and the UI offers no "See all".
     */
    val moreBrowseId: String? = null,
    val moreParams: String? = null
) : ArtistSection
