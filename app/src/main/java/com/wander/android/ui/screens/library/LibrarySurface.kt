package com.wander.android.ui.screens.library

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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.ui.screens.search.SearchScreen
import com.wander.android.ui.screens.search.SearchViewModel

/**
 * The library, and what it becomes while there is something in the search field.
 *
 * Search is not a destination. The dock's field is always on screen, so a separate Search route
 * meant a navigation transition on the first keystroke and another on the last backspace — two
 * screens fighting over one question. Here the query is a *state* of the library: your own music
 * until you type, results from every connected source while you are typing, back to your own music
 * when you clear it. Nothing moves on the back stack, so the back gesture still means "leave the
 * library", not "undo a letter".
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
    searchViewModel: SearchViewModel = hiltViewModel()
) {
    val query by searchViewModel.query.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = query.isNotBlank(),
        transitionSpec = {
            // Results rise into place and the shelves settle back down: the direction of travel
            // says which of the two you asked for. `SizeTransform(clip = false)` because both
            // sides fill the surface — there is no box to grow, only content to swap.
            (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith
                (fadeOut() + scaleOut(targetScale = 0.96f)) using SizeTransform(clip = false)
        },
        label = "librarySurface",
        modifier = Modifier.fillMaxSize()
    ) { searching ->
        if (searching) {
            SearchScreen(
                contentPadding = contentPadding,
                onOpenArtist = onOpenArtist,
                viewModel = searchViewModel
            )
        } else {
            LibraryScreen(
                contentPadding = contentPadding,
                onOpenAlbum = onOpenAlbum,
                onOpenArtist = onOpenArtist,
                onOpenHistory = onOpenHistory,
                onOpenPlaylist = onOpenPlaylist,
                onOpenImport = onOpenImport
            )
        }
    }
}
