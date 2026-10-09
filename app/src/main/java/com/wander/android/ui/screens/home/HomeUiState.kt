package com.wander.android.ui.screens.home

import androidx.compose.runtime.Immutable
import com.wander.android.data.model.SourceType
import com.wander.android.ui.screens.home.layout.HomeLayoutApplier
import com.wander.android.ui.screens.home.layout.ShelfConfig

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
    val selectedSource: SourceType? = null,
    /** The user's order, visibility and styles; empty until they customise Home. */
    val layout: List<ShelfConfig> = emptyList(),
    /** The customizer is open: Home shows every shelf, hidden ones included, with edit controls. */
    val editing: Boolean = false
) {
    /**
     * What Home draws. Filtering happens here rather than in the load path so clearing the filter
     * costs nothing and hands back the very same list instance, leaving the shelves where they
     * were instead of rebuilding them.
     */
    val sections: List<HomeSection> by lazy {
        val filtered = when (selectedSource) {
            null -> allSections
            else -> allSections
                .map { section -> section.copy(tracks = section.tracks.filter { it.source == selectedSource }) }
                .filterNot(HomeSection::isEmpty)
        }
        HomeLayoutApplier.apply(filtered, layout)
    }

    /**
     * What the customizer draws: the shelves on Home in the user's order, never narrowed by the
     * source filter — removing a shelf must not depend on which chip is selected.
     */
    val editorSections: List<HomeSection> by lazy {
        // A shelf the user added that has nothing to show still needs a card, or it could not be
        // set up or removed. It is empty, and the card says so.
        val empty = layout
            .filter { it.enabled && isAddedShelf(it.id) && allSections.none { s -> s.id == it.id } }
            .map { HomeSection(it.id, "", HomeSectionStyle.TRACK_CAROUSEL) }
        HomeLayoutApplier.apply(allSections + empty, layout)
    }

    /** Optional shelves not on Home, for the Add shelf sheet to offer. */
    internal val availableExtras: List<ExtraShelf> by lazy {
        val onHome = layout.filter { it.enabled }.mapTo(HashSet()) { it.id }
        ExtraShelf.entries.filter { it.id !in onHome }
    }

    /** Default shelves the user removed, for the Add shelf sheet to offer back. */
    val removedSections: List<HomeSection> by lazy {
        val removed = layout.filterNot { it.enabled }.mapTo(HashSet()) { it.id }
        allSections.filter { it.id in removed }
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
