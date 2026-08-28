package com.wander.android.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The search box's text, held outside the Search screen.
 *
 * The field moved into the dock at the bottom of the app, which outlives any one destination —
 * it is on screen while you are on Home, and it is the same field when you land on Search. A
 * `SearchViewModel` is scoped to the Search route and is destroyed when you leave it, so it cannot
 * be the owner of text that is still visible after it is gone.
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
     * Seeds the field from a deep link or a "search for this artist" tap, without clobbering text
     * the user is already typing — those arrive as a navigation argument, which is re-read every
     * time the Search screen is re-created, including on rotation.
     */
    fun seed(value: String) {
        if (value.isNotBlank() && _query.value.isBlank()) _query.value = value
    }
}
