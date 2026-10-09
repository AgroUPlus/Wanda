package com.wander.android.ui.screens.home.layout

import com.wander.android.ui.screens.home.HomeSectionStyle

/**
 * The edits the customizer can make to a layout. Shelves are addressed by id, never by list
 * position: the saved list can hold hidden shelves that are not on screen right now, so a screen
 * index would not point at the same entry.
 */
internal object HomeLayoutEditor {

    /** Puts [id] where [targetId] is, shifting the shelves in between. No-op if either is unknown. */
    fun move(configs: List<ShelfConfig>, id: String, targetId: String): List<ShelfConfig> {
        val from = configs.indexOfFirst { it.id == id }
        val to = configs.indexOfFirst { it.id == targetId }
        if (from < 0 || to < 0 || from == to) return configs
        return configs.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun setEnabled(configs: List<ShelfConfig>, id: String, enabled: Boolean) =
        update(configs, id) { it.copy(enabled = enabled) }

    /** A null [style] returns the shelf to its own default layout. */
    fun setStyle(configs: List<ShelfConfig>, id: String, style: HomeSectionStyle?) =
        update(configs, id) { it.copy(style = style) }

    /** A null [count] shows the shelf's full default length. */
    fun setCount(configs: List<ShelfConfig>, id: String, count: Int?) =
        update(configs, id) { it.copy(count = count) }

    private fun update(configs: List<ShelfConfig>, id: String, change: (ShelfConfig) -> ShelfConfig) =
        configs.map { if (it.id == id) change(it) else it }
}
