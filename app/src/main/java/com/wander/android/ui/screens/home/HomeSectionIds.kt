package com.wander.android.ui.screens.home

// Shelf ids and sizes used by [HomeViewModel]. Ids are internal keys and never shown.

internal const val CarouselSize = 12
internal const val ListSize = 20

internal const val SectionOnRepeat = "on_repeat"
internal const val SectionContinueListening = "continue_listening"
internal const val SectionMixes = "mixes"
internal const val SectionRecentlyPlayed = "recently_played"
internal const val SectionLiked = "liked"
internal const val SectionDiscover = "discover"
internal const val SectionBecause = "because_you_listened"

/** A genre shelf the user added. Its categories live in its `ShelfConfig`; the rest of the id only tells shelves apart. */
internal const val GenreShelfPrefix = "genres:"

/** The shelves offered under "More shelves"; see `ExtraShelf`. */
internal const val ExtraShelfPrefix = "extra:"

/** The curated Christian music category of a genre shelf, as opposed to a genre tag from the library. */
internal const val ChristianCategory = "@christian"

/** Shelves that are not part of the default Home: added by the user, and gone when removed. */
internal fun isAddedShelf(id: String) = id.startsWith(GenreShelfPrefix) || id.startsWith(ExtraShelfPrefix)

/** Per-source shelves are unlisted, so they sort after these and before the closing list. */
internal val SectionOrder = listOf(
    SectionOnRepeat,
    SectionRecentlyPlayed,
    // A podcast shelf, once one exists, belongs here: right below Quick Picks, above everything
    // else. Recorded now so whoever adds it doesn't have to re-derive the placement.
    SectionContinueListening,
    SectionMixes,
    SectionLiked,
    SectionBecause,
    SectionDiscover
)
