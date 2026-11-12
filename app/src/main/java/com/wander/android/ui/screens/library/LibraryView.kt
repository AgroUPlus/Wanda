package com.wander.android.ui.screens.library

/** How a Library list lays its items out: rows, or a grid of covers. */
enum class LibraryView { LIST, GRID }

/**
 * Whether [this] tab can be shown as a grid as well as a list. Only the tabs that have both
 * renderers offer the switch; a track list has no grid worth drawing.
 */
internal val LibraryTab.offersGrid: Boolean
    get() = this == LibraryTab.ALBUMS || this == LibraryTab.ARTISTS

/** Where a tab starts before anyone switches it: covers for albums and artists, rows otherwise. */
internal val LibraryTab.defaultView: LibraryView
    get() = if (offersGrid) LibraryView.GRID else LibraryView.LIST

/**
 * Whether [this] tab's list can be narrowed to what is downloaded. Only track lists know: an
 * album or a playlist has no downloaded state of its own. Offline already is that list.
 */
internal val LibraryTab.offersDownloadedFilter: Boolean
    get() = this == LibraryTab.TRACKS || this == LibraryTab.LIKED
