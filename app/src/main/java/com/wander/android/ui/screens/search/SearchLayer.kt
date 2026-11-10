package com.wander.android.ui.screens.search

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.ui.components.BackLayers
import com.wander.android.ui.navigation.InterceptReselect

/**
 * A top-level screen, and what it becomes while the dock's search field is in use.
 *
 * Search is not a destination. The field is in the dock under every root, so searching opens the
 * results over the screen you were on — Home, Library or Friends — rather than taking you to one
 * of them first. Nothing moves on the back stack: clearing the field, back, or tapping the tab
 * again ends the search and leaves you exactly where you were, with [base] keeping its scroll
 * underneath the whole time.
 *
 * [SearchViewModel] is resolved here rather than in [SearchScreen], so the query that decides which
 * side is showing and the query that runs the search are read from one instance.
 */
@Composable
fun SearchLayer(
    contentPadding: PaddingValues,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    searchViewModel: SearchViewModel = hiltViewModel(),
    historyViewModel: SearchHistoryViewModel = hiltViewModel(),
    base: @Composable () -> Unit
) {
    val query by searchViewModel.query.collectAsStateWithLifecycle()
    val fieldFocused by historyViewModel.isFieldFocused.collectAsStateWithLifecycle()
    // An empty field with focus is someone about to search: the start page — recent searches,
    // shortcuts, moods — is more use to them than the screen underneath.
    val searching = query.isNotBlank() || fieldFocused

    val focusManager = LocalFocusManager.current
    val endSearch = {
        searchViewModel.clearQuery()
        // Focus too, or an empty focused field would put the start page straight back.
        focusManager.clearFocus()
    }

    // Reselecting the tab while results are showing ends the search, as back does; with no search
    // the tap reaches [base], which scrolls back up.
    InterceptReselect(consume = { searching.also { if (it) endSearch() } }) {
        BackLayers(
            page = if (searching) Unit else null,
            onBack = endSearch,
            base = base
        ) {
            SearchScreen(
                contentPadding = contentPadding,
                onOpenAlbum = onOpenAlbum,
                onOpenArtist = onOpenArtist,
                viewModel = searchViewModel,
                historyViewModel = historyViewModel
            )
        }
    }
}
