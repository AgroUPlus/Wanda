package com.wander.android.data.model

/**
 * An artist page as Wanda lays it out: fixed buckets, in a fixed order.
 *
 * This replaces echoing each backend's shelves verbatim. Doing that meant YouTube Music's "Songs"
 * shelf was drawn, and then the screen's own library-derived song list was drawn immediately under
 * it — the same songs twice, under two headings, because the two lists come from different places
 * and neither knew about the other. Same for albums.
 *
 * The fixed layout only ever *merges and hides*. A bucket with nothing in it is not rendered, so
 * the page never claims an artist has no singles by showing an empty singles shelf — which was the
 * original argument against normalising, and is preserved here. Anything the merger cannot place
 * keeps the backend's own heading and its own position in [otherShelves] rather than being
 * dropped, so a page in a language this app cannot read degrades to exactly the old behaviour
 * instead of losing records.
 */
data class ArtistPage(
    val bio: String? = null,
    val imageUrl: String? = null,
    /** The songs shelf merged with everything the library knows by this artist, deduplicated. */
    val topSongs: List<UnifiedTrack> = emptyList(),
    val albums: ArtistAlbumSection? = null,
    val singles: ArtistAlbumSection? = null,
    val videos: List<UnifiedTrack> = emptyList(),
    val related: List<RelatedArtist> = emptyList(),
    /** Shelves whose heading could not be classified. Rendered last, under the backend's title. */
    val otherShelves: List<ArtistSection> = emptyList()
) {
    val isEmpty: Boolean
        get() = topSongs.isEmpty() && albums == null && singles == null &&
            videos.isEmpty() && related.isEmpty() && otherShelves.isEmpty()
}

/**
 * Another artist the backend suggests — the "Fans might also like" shelf.
 *
 * Carries no records of its own, only somewhere to go. Wanda's artist route is keyed by name (see
 * `CatalogRepository`), so [name] is what opens the page and [id] is kept only for the sources
 * that can share a link to one.
 */
data class RelatedArtist(
    val id: String,
    val name: String,
    val imageUrl: String? = null
)
