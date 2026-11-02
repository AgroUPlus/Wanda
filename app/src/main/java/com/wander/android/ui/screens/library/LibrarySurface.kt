package com.wander.android.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.ui.screens.search.SearchHistoryViewModel
import com.wander.android.ui.screens.search.SearchScreen
import com.wander.android.ui.screens.search.SearchViewModel

/**
 * The library, and what it becomes while there is something in the search field.
 *
 * Search is not a destination. The dock's field is always on screen, so a separate Search route
 * meant a navigation transition on the first keystroke and another on the last backspace — two
 * screens fighting over one question. Here the query is a *state* of the library: your own music
 * until you type, results from every connected source while you are typing, back to your own music
 * when you clear it. Nothing moves on the back stack, so back is not "undo a letter" — but while a
 * search is showing, back ends the search first and only then leaves the library. Without that,
 * backing out of a page of results dropped you on Home with the query still in the field.
 *
 * [SearchViewModel] is hoisted to this level rather than left to [SearchScreen], so the query that
 * decides which side is showing and the query that runs the search are read from one instance —
 * the same one, scoped to this back stack entry, that [SearchScreen] would have resolved anyway.
 */
@Composable
fun LibrarySurface(
    contentPadding: PaddingValues,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPlaylist: (String) -> Unit = {},
    onOpenImport: () -> Unit = {},
    onOpenSubscriptions: () -> Unit = {},
    searchViewModel: SearchViewModel = hiltViewModel(),
    historyViewModel: SearchHistoryViewModel = hiltViewModel()
) {
    val query by searchViewModel.query.collectAsStateWithLifecycle()
    val fieldFocused by historyViewModel.isFieldFocused.collectAsStateWithLifecycle()
    val recentSearches by historyViewModel.recent.collectAsStateWithLifecycle()
    // An empty field with focus is someone about to search: their recent searches are more use
    // to them than the shelves, as long as there are any to offer.
    val searching = query.isNotBlank() || (fieldFocused && recentSearches.isNotEmpty())

    val focusManager = LocalFocusManager.current
    BackHandler(enabled = searching) {
        searchViewModel.clearQuery()
        // Focus too, or an empty focused field would put the recent searches straight back.
        focusManager.clearFocus()
    }

    AnimatedContent(
        targetState = searching,
        transitionSpec = {
            // Results rise into place and the shelves settle back down: the direction of travel
            // says which of the two you asked for. `SizeTransform(clip = false)` because both
            // sides fill the surface — there is no box to grow, only content to swap.
            (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith
                (fadeOut() + scaleOut(targetScale = 0.96f)) using SizeTransform(clip = false)
        },
        label = "librarySurface",
        modifier = Modifier.fillMaxSize()
    ) { showingSearch ->
        if (showingSearch) {
            SearchScreen(
                contentPadding = contentPadding,
                onOpenAlbum = onOpenAlbum,
                onOpenArtist = onOpenArtist,
                viewModel = searchViewModel,
                historyViewModel = historyViewModel
            )
        } else {
            LibraryScreen(
                contentPadding = contentPadding,
                onOpenAlbum = onOpenAlbum,
                onOpenArtist = onOpenArtist,
                onOpenHistory = onOpenHistory,
                onOpenPlaylist = onOpenPlaylist,
                onOpenImport = onOpenImport,
                onOpenSubscriptions = onOpenSubscriptions
            )
        }
    }
}
