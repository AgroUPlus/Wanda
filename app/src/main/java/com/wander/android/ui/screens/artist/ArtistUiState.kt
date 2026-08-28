package com.wander.android.ui.screens.artist

import androidx.compose.runtime.Immutable
import com.wander.android.data.model.ArtistPage
import com.wander.android.data.model.UnifiedAlbum

/**
 * Everything the artist screen draws, in one value.
 *
 * The screen used to read four independent flows and one `isLoading` boolean, and the boolean was
 * cleared *before* the backend's artist page was fetched — so the page deliberately rendered its
 * library-only self and then reflowed once the real shelves arrived. One state means there is only
 * ever one answer to "what is true right now".
 *
 * [isLoading] is narrower than it sounds: it means *nothing definite is known yet*, and it is the
 * only thing that puts a skeleton on screen. Once a page has content, a later refresh sets
 * [isRefreshing] instead, so re-entering the screen never replaces what is already there with
 * placeholders.
 */
@Immutable
internal data class ArtistUiState(
    val artist: String = "",
    val page: ArtistPage = ArtistPage(),
    val heroImage: String? = null,
    val albumCount: Int = 0,
    val trackCount: Int = 0,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val canShare: Boolean = false,
    /**
     * Shelves the user asked to see in full, keyed by the shelf's heading.
     *
     * Absent means the shelf is showing the tiles the artist's page came with, and its "See all"
     * is still offered. Present means the whole shelf has been fetched and is rendered as a grid.
     */
    val expandedShelves: Map<String, List<UnifiedAlbum>> = emptyMap(),
    val loadingShelf: String? = null
) {
    /** Nothing anywhere has anything by this artist, and we have finished looking. */
    val isEmpty: Boolean get() = !isLoading && page.isEmpty
}
