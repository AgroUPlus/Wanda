package com.wander.android.ui.screens.search

import androidx.lifecycle.ViewModel
import com.wander.android.data.repository.SearchHistoryRepository
import com.wander.android.data.repository.SearchQueryHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * Recent searches, for the moment between focusing the search field and typing into it.
 *
 * Its own view model rather than more of [SearchViewModel]: it is read by `LibrarySurface` to
 * decide whether the search side is showing at all, before any query exists to search for.
 */
@HiltViewModel
class SearchHistoryViewModel @Inject constructor(
    private val history: SearchHistoryRepository,
    private val queryHolder: SearchQueryHolder
) : ViewModel() {

    val recent: StateFlow<List<String>> = history.recent

    /** Whether the dock's field has focus — see [SearchQueryHolder.isFieldFocused]. */
    val isFieldFocused: StateFlow<Boolean> = queryHolder.isFieldFocused

    /** Runs a recent search again by putting it back in the field. */
    fun search(query: String) = queryHolder.set(query)

    fun remove(query: String) = history.remove(query)

    fun clear() = history.clear()
}
