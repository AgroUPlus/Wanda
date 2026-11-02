package com.wander.android.ui.screens.search

import com.wander.android.data.model.LyricMatch
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack

data class SearchUiState(
    val isSearching: Boolean = false,
    val results: List<UnifiedTrack> = emptyList(),
    val lyricMatches: List<LyricMatch> = emptyList(),
    val hasQuery: Boolean = false,
    /** Backends that were asked and did not answer; see `SearchOutcome`. */
    val unansweredSources: List<SourceType> = emptyList(),
    /** None of the backends asked answered at all. */
    val allSourcesFailed: Boolean = false
) {
    val isEmpty: Boolean get() = results.isEmpty() && lyricMatches.isEmpty()

    /**
     * The search failed rather than came back empty: nothing to show and nobody answered, so
     * "No matches" would be a claim about the music that is really one about the network.
     */
    val isFailure: Boolean get() = allSourcesFailed && isEmpty
}
