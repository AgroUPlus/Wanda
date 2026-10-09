package com.wander.android.ui.screens.home.layout

import com.wander.android.ui.screens.home.HomeSection
import com.wander.android.ui.screens.home.HomeSectionStyle

/**
 * Turns the shelves Home built into the shelves the user asked for.
 *
 * Pure on purpose: ordering, hiding and restyling are the whole behaviour of the customizer, and
 * this is the one place that decides them.
 */
internal object HomeLayoutApplier {

    /**
     * With no [configs] the shelves come back untouched, so Home is unchanged until customised.
     * A shelf with no config (a feed shelf that appeared since the last edit) sorts after the
     * configured ones, in the order it arrived.
     */
    fun apply(
        sections: List<HomeSection>,
        configs: List<ShelfConfig>
    ): List<HomeSection> {
        if (configs.isEmpty()) return sections
        val byId = configs.associateBy { it.id }
        val rank = configs.withIndex().associate { (index, config) -> config.id to index }
        return sections
            .sortedBy { rank[it.id] ?: Int.MAX_VALUE }
            .filter { byId[it.id]?.enabled != false }
            .map { section -> byId[section.id]?.let { section.styledBy(it) } ?: section }
    }

    private fun HomeSection.styledBy(config: ShelfConfig): HomeSection {
        // Mix shelves only know how to draw mix cards, so a track layout cannot be forced on them.
        val style = config.style?.takeIf { mixes.isEmpty() } ?: style
        val shown = config.count?.let { tracks.take(it) } ?: tracks
        return copy(style = style, tracks = shown)
    }

    /**
     * The configs for the shelves on screen right now, for the editor to start from: the saved
     * order first, then any shelf that has not been configured yet, each enabled by default.
     */
    fun seed(sections: List<HomeSection>, configs: List<ShelfConfig>): List<ShelfConfig> {
        val known = configs.map { it.id }.toSet()
        val present = sections.map { it.id }.toSet()
        val kept = configs.filter { it.id in present || !it.enabled }
        return kept + sections.filter { it.id !in known }.map { ShelfConfig(it.id) }
    }
}
