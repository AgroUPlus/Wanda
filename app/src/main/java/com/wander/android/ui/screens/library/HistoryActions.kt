package com.wander.android.ui.screens.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.data.model.HistoryTrack
import com.wander.android.data.repository.artistIdFor
import com.wander.android.ui.components.AddToPlaylistController
import com.wander.android.ui.components.ConfirmRequest
import com.wander.android.ui.components.TrackActionsSheet
import com.wander.android.ui.components.rememberConfirmState

/** The long-press sheet for one play in History, with Remove from history. */
@Composable
internal fun HistoryTrackActions(
    entry: HistoryTrack,
    viewModel: HistoryViewModel,
    addToPlaylist: AddToPlaylistController,
    onOpenArtist: (String, String?) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val track = entry.track
    TrackActionsSheet(
        track = track,
        isLiked = track.isLiked,
        onPlayNext = { viewModel.playNext(track) },
        onAddToQueue = { viewModel.addToQueue(track) },
        onStartRadio = { viewModel.startRadio(track) },
        onToggleLike = { viewModel.toggleLike(track) },
        // This one play, not every play of the track: the log is one row per listen.
        onRemove = { viewModel.removeEntry(entry) },
        removeLabel = stringResource(R.string.history_remove_entry),
        onOpenArtist = track.artist
            .takeIf { it.isNotBlank() }
            ?.let { { name -> onOpenArtist(name, track.artistIdFor(name)) } },
        onOpenAlbum = onOpenAlbum,
        onDismiss = onDismiss,
        onShare = null,
        onAddToPlaylist = if (addToPlaylist.canAdd(track)) {
            { addToPlaylist.open(track) }
        } else {
            null
        }
    )
}

/** Clear history, behind a confirmation: one tap from the header must not empty the log. */
@Composable
internal fun HistoryClearButton(onClear: () -> Unit) {
    val confirm = rememberConfirmState()
    val title = stringResource(R.string.history_clear_confirm_title)
    val message = stringResource(R.string.history_clear_confirm_message)
    val action = stringResource(R.string.history_clear_confirm_action)
    IconButton(onClick = { confirm.ask(ConfirmRequest(title, message, action, onClear)) }) {
        Icon(Icons.Rounded.DeleteSweep, contentDescription = stringResource(R.string.history_clear_all))
    }
}
