package com.wander.android.ui.screens.home.layout

import com.wander.android.ui.screens.home.GenreShelfPrefix
import com.wander.android.ui.screens.home.isAddedShelf
import com.wander.android.ui.screens.home.HomeSectionStyle
import com.wander.android.ui.screens.home.HomeUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * What the customizer does to Home. Every layout change is saved at once; the screen follows the
 * saved layout, so there is no separate draft to confirm or lose.
 */
internal class HomeLayoutActions(
    private val store: HomeLayoutStore,
    private val state: MutableStateFlow<HomeUiState>
) {
    /** Opens the customizer. Saves the shelves as they are now, so every one can be moved. */
    fun start() {
        val current = state.value
        store.save(HomeLayoutApplier.seed(current.allSections, current.layout))
        store.setEditing(true)
    }

    fun stop() = store.setEditing(false)

    /**
     * Back to the default order, hiding nothing and dropping added shelves. Still editable, so the
     * layout is re-seeded rather than cleared.
     */
    fun reset() {
        val builtIn = state.value.allSections.filterNot { isAddedShelf(it.id) }
        store.save(HomeLayoutApplier.seed(builtIn, emptyList()))
        state.update { it.copy(allSections = builtIn) }
    }

    fun move(id: String, targetId: String) = edit { HomeLayoutEditor.move(it, id, targetId) }

    fun setEnabled(id: String, enabled: Boolean) = edit { HomeLayoutEditor.setEnabled(it, id, enabled) }

    fun setStyle(id: String, style: HomeSectionStyle?) = edit { HomeLayoutEditor.setStyle(it, id, style) }

    fun setCount(id: String, count: Int?) = edit { HomeLayoutEditor.setCount(it, id, count) }

    /** Adds an empty genre shelf at the end and returns its id, for the caller to open its settings. */
    fun addGenreShelf(): String {
        val next = state.value.layout.mapNotNull { it.id.removePrefix(GenreShelfPrefix).toIntOrNull() }.maxOrNull() ?: 0
        val id = GenreShelfPrefix + (next + 1)
        edit { it + ShelfConfig(id) }
        return id
    }

    /** Adds one of the optional shelves at the end; the view model builds its tracks when the layout arrives. */
    fun addExtra(id: String) = edit { configs -> if (configs.any { it.id == id }) configs else configs + ShelfConfig(id) }

    fun toggleCategory(id: String, category: String) = edit { HomeLayoutEditor.toggleCategory(it, id, category) }

    fun toggleLanguage(id: String, code: String) = edit { HomeLayoutEditor.toggleLanguage(it, id, code) }

    /**
     * Takes a shelf off Home. A shelf the user added is deleted outright and can be added again; a
     * default one is kept in the layout as removed, so [restore] can bring it back.
     */
    fun remove(id: String) {
        if (!isAddedShelf(id)) return setEnabled(id, false)
        edit { configs -> configs.filterNot { it.id == id } }
        state.update { it.copy(allSections = it.allSections.filterNot { s -> s.id == id }) }
    }

    fun restore(id: String) = edit { HomeLayoutEditor.restore(it, id) }

    private fun edit(change: (List<ShelfConfig>) -> List<ShelfConfig>) = store.save(change(state.value.layout))
}
