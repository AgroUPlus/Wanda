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

    /** Brings a removed shelf back, at the end where it is easy to find. */
    fun restore(configs: List<ShelfConfig>, id: String): List<ShelfConfig> {
        val config = configs.firstOrNull { it.id == id } ?: return configs
        return configs.filterNot { it.id == id } + config.copy(enabled = true)
    }

    fun setEnabled(configs: List<ShelfConfig>, id: String, enabled: Boolean) =
        update(configs, id) { it.copy(enabled = enabled) }

    /** A null [style] returns the shelf to its own default layout. */
    fun setStyle(configs: List<ShelfConfig>, id: String, style: HomeSectionStyle?) =
        update(configs, id) { it.copy(style = style) }

    /** A null [count] shows the shelf's full default length. */
    fun setCount(configs: List<ShelfConfig>, id: String, count: Int?) =
        update(configs, id) { it.copy(count = count) }

    /** Selects the category if it is not selected, and unselects it if it is. */
    fun toggleCategory(configs: List<ShelfConfig>, id: String, category: String) =
        update(configs, id) { it.copy(categories = it.categories.toggled(category)) }

    fun toggleLanguage(configs: List<ShelfConfig>, id: String, code: String) =
        update(configs, id) { it.copy(languages = it.languages.toggled(code)) }

    private fun List<String>.toggled(value: String) = if (value in this) this - value else this + value

    private fun update(configs: List<ShelfConfig>, id: String, change: (ShelfConfig) -> ShelfConfig) =
        configs.map { if (it.id == id) change(it) else it }
}
