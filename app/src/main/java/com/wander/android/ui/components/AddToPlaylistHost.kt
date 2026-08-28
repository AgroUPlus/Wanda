package com.wander.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.data.model.UnifiedTrack

/**
 * Opens the add-to-playlist picker for a track.
 *
 * Handed back by [AddToPlaylistHost] so a screen can wire the action without owning any of the
 * state behind it — five screens offer this action, and none of them should have to hold a list of
 * playlists to do so.
 */
@Stable
class AddToPlaylistController internal constructor(
    private val viewModel: AddToPlaylistViewModel
) {
    /** Whether this track's source can be written to at all. Null the action when it cannot. */
    fun canAdd(track: UnifiedTrack): Boolean = viewModel.canAdd(track)

    fun open(track: UnifiedTrack) = viewModel.open(track)

    fun openForTracks(tracks: List<UnifiedTrack>, source: com.wander.android.data.model.SourceType) =
        viewModel.openForTracks(tracks, source)
}

/**
 * Hosts the add-to-playlist sheet and returns the handle that opens it.
 *
 * Place once per screen, near the other sheets. Outcomes are not reported here: they go to the
 * app-wide snackbar via `PlaylistWriteRepository.messages`, because the sheet has already closed
 * by the time the server answers.
 */
@Composable
fun AddToPlaylistHost(): AddToPlaylistController {
    val viewModel: AddToPlaylistViewModel = hiltViewModel()
    val target by viewModel.target.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    if (target != null) {
        AddToPlaylistSheet(
            playlists = playlists,
            isLoading = isLoading,
            onSelect = viewModel::addToExisting,
            onCreate = viewModel::createWith,
            onDismiss = viewModel::dismiss
        )
    }

    return remember(viewModel) { AddToPlaylistController(viewModel) }
}
