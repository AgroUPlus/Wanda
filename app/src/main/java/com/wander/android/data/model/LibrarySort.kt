package com.wander.android.data.model

/**
 * How a Library list is ordered.
 *
 * The names are what the browse queries compare against (`:sort = 'ARTIST'`), so renaming an entry
 * is a query change too — see `LibraryBrowseDao`. Every order falls back to the title, so rows
 * that tie (two plays each, the same artist) still land in a stable, readable order.
 */
enum class LibrarySort {
    TITLE,
    ARTIST,
    ALBUM,
    DATE_ADDED,
    YEAR,
    MOST_PLAYED,
    RECENTLY_PLAYED;

    /** Whether the list reads alphabetically by a name, so a fast scroller can show its letter. */
    val isAlphabetical: Boolean
        get() = this == TITLE || this == ARTIST || this == ALBUM

    /** Album orders that need the album's tracks to decide, rather than the album row alone. */
    val needsTrackActivity: Boolean
        get() = this == DATE_ADDED || this == MOST_PLAYED || this == RECENTLY_PLAYED
}
