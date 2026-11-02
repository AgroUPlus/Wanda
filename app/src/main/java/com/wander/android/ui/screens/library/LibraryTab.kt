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
 *
 * Liked comes first and is where the library opens: it is the collection you chose, small enough
 * to read at a glance, where Tracks is everything every source has ever handed over.
 *
 * Artists and Genres are the library regrouped, read from the tags its tracks already carry —
 * no source needs to support browsing by either.
 *
 * Followed is off until it is turned on (see [LibraryTab.Default]): it needs a paired Agro server,
 * and a tab that opens on "pair a server first" is not one to hand everyone by default.
 */
enum class LibraryTab(@StringRes val label: Int) {
    LIKED(R.string.library_tab_liked),
    TRACKS(R.string.library_tab_tracks),
    ALBUMS(R.string.library_tab_albums),
    ARTISTS(R.string.library_tab_artists),
    GENRES(R.string.library_tab_genres),
    PLAYLISTS(R.string.library_tab_playlists),
    PODCASTS(R.string.library_tab_podcasts),
    DOWNLOADS(R.string.library_tab_offline),
    FOLLOWED(R.string.library_tab_followed);

    companion object {
        /** What a Library nobody has customised shows. */
        val Default: List<LibraryTab> = entries - FOLLOWED
    }
}
