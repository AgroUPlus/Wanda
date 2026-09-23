package com.wander.android.ui.screens.library

import androidx.annotation.StringRes
import com.wander.android.R

/**
 * The library's tabs.
 *
 * History is deliberately not among them. It is not a *collection* — it is a log, it is never
 * curated, and it was costing a slot in a tab row that was already clipping its labels. It lives
 * behind an icon in the header instead, the way Settings does on Home.
 *
 * Podcasts is: spoken audio is resumed and finished rather than replayed, so mixed in with songs
 * it was only ever in the way of both.
 */
enum class LibraryTab(@StringRes val label: Int) {
    TRACKS(R.string.library_tab_tracks),
    LIKED(R.string.library_tab_liked),
    ALBUMS(R.string.library_tab_albums),
    PLAYLISTS(R.string.library_tab_playlists),
    PODCASTS(R.string.library_tab_podcasts),
    DOWNLOADS(R.string.library_tab_offline)
}
