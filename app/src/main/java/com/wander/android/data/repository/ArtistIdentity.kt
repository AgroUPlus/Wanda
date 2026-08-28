package com.wander.android.data.repository

/**
 * Tells two artists who share a name apart.
 *
 * Room finds an artist's work with `WHERE artist = :name COLLATE NOCASE`, and that is not a
 * mistake — one artist genuinely reaches Room capitalised differently by different backends, so an
 * exact match would split their discography in half. The cost is that two *different* artists whose
 * names differ only in case become one: a Japanese singer called "misa" and an unrelated "MISA"
 * arrive as the same page, with each other's songs on it.
 *
 * The name cannot settle it, so identity does. Where a backend published an artist id — YouTube
 * Music always does — an item carrying a *different* id is definitely somebody else and is dropped.
 *
 * What is deliberately **not** done is preferring an exact-case name match when no ids are
 * available. It would fix the same-name case for sources that publish no ids, and it would break
 * the case this query was written for, silently hiding the half of a discography that a second
 * backend spelled differently. An item we cannot disprove is kept; a page with a stranger's song on
 * it is a smaller failure than a page missing the user's own music.
 */
internal object ArtistIdentity {

    /**
     * Keeps items that could belong to this artist.
     *
     * [pageArtistId] null means nothing is known about identity and everything is kept — the
     * behaviour before this existed. [idOf] returning null likewise means "cannot tell", never
     * "different".
     */
    fun <T> sameArtist(items: List<T>, pageArtistId: String?, idOf: (T) -> String?): List<T> {
        if (pageArtistId.isNullOrBlank()) return items
        return items.filter { item ->
            val id = idOf(item)
            id.isNullOrBlank() || id == pageArtistId
        }
    }
}
