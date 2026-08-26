package com.wander.android.ui.screens.home

import androidx.compose.runtime.Immutable
import com.wander.android.data.model.SourceType

@Immutable
data class HomeUiState(
    val isLoading: Boolean = true,
    /**
     * A pull-to-refresh in progress. Deliberately separate from [isLoading]: that one replaces the
     * whole screen with a spinner, which is right on a cold start and wrong when the user is
     * looking at shelves and pulled them down.
     */
    val isRefreshing: Boolean = false,
    val greeting: String = "",
    /** Every shelf that was built, before [selectedSource] narrows them. */
    val allSections: List<HomeSection> = emptyList(),
    /** The backends actually configured, for the filter row. */
    val sources: List<SourceType> = emptyList(),
    val selectedSource: SourceType? = null
) {
    /**
     * What Home draws. Filtering happens here rather than in the load path so clearing the filter
     * costs nothing and hands back the very same list instance, leaving the shelves where they
     * were instead of rebuilding them.
     */
    val sections: List<HomeSection> by lazy {
        when (selectedSource) {
            null -> allSections
            else -> allSections
                // A per-source shelf says nothing once you have filtered to that source.
                .filterNot { it.id.startsWith(SourceSectionPrefix) }
                .map { section ->
                    section.copy(tracks = section.tracks.filter { it.source == selectedSource })
                }
                .filterNot(HomeSection::isEmpty)
        }
    }

    /** Nothing to show for the current filter — may still have music under a different source. */
    val isEmpty: Boolean get() = !isLoading && sections.isEmpty()

    /**
     * Nothing to show no matter the filter. Distinct from [isEmpty]: filtering to a source with no
     * tracks is not the same situation as having no music at all, and only this one should take
     * over the whole screen — the other should leave the header and source chips reachable so the
     * user can pick a different filter instead of being stuck looking at "open Settings."
     */
    val isGloballyEmpty: Boolean get() = !isLoading && allSections.isEmpty()
}

/** Per-source shelves are id'd with this, so the filter can recognise and drop them. */
internal const val SourceSectionPrefix = "source_"
