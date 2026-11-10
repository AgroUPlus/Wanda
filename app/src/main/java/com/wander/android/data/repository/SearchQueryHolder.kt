package com.wander.android.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The search box's text, held outside every screen that reacts to it.
 *
 * The field is in the dock at the bottom of the app, which outlives any one destination — it is the
 * same field on Home, Library and Friends. It is also the switch that turns whichever of them is
 * showing into search results, which must be readable before a `SearchViewModel` exists. No one
 * screen can own it, so nothing does but this.
 *
 * Deliberately not persisted. A query is about what you are doing right now; restoring last week's
 * search into the field on a cold start would be answering a question nobody asked.
 */
@Singleton
class SearchQueryHolder @Inject constructor() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    fun set(value: String) {
        _query.value = value
    }

    /**
     * Whether the field has focus. An empty, focused field is someone about to search, which is
     * when the tab underneath steps aside for the search start page — see `SearchLayer`.
     */
    private val _isFieldFocused = MutableStateFlow(false)
    val isFieldFocused: StateFlow<Boolean> = _isFieldFocused.asStateFlow()

    fun setFieldFocused(focused: Boolean) {
        _isFieldFocused.value = focused
    }
}
