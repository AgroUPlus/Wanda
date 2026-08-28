package com.wander.android.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The search box's text, held outside every screen that reacts to it.
 *
 * The field is in the dock at the bottom of the app, which outlives any one destination — it is on
 * screen while you are on Home, and it is the same field once you are in the library. It is also
 * the switch that turns the library into search results, which the library must be able to read
 * before a `SearchViewModel` exists. Neither screen can own it, so nothing does but this.
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
}
