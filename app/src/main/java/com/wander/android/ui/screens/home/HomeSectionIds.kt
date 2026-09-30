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

/**
 * YouTube Music's own "Listen again" feed shelf (see `shelfId`). Dropped: Wanda's own Recently
 * Played covers every source's history, so this one was a second, YTM-only copy of it.
 */
internal const val FeedListenAgain = "ytm_listen_again"

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
