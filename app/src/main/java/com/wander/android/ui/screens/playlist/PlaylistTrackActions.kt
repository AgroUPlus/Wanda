package com.wander.android.ui.screens.playlist

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.artistIdFor
import com.wander.android.ui.components.AddToPlaylistController
import com.wander.android.ui.components.TrackActionsSheet

/**
 * The actions sheet of the playlist row at [index]: the usual track actions, and the edits this
 * account may make to the playlist — remove, and Move up/down, which stay here beside the drag
 * grip for anyone who cannot drag.
 */
@Composable
internal fun PlaylistTrackActions(
    index: Int,
    track: UnifiedTrack,
    playlist: UnifiedPlaylist?,
    lastIndex: Int,
    viewModel: PlaylistViewModel,
    sharedViewModel: SharedPlaylistViewModel,
    addToPlaylist: AddToPlaylistController,
    onOpenArtist: (String, String?) -> Unit,
    onDismiss: () -> Unit,
    onOpenAlbum: ((String) -> Unit)? = null,
    onSelect: (() -> Unit)? = null
) {
    val movable = playlist != null && sharedViewModel.canMove(playlist) && !sharedViewModel.isPending(index)
    TrackActionsSheet(
        track = track,
        isLiked = track.isLiked,
        onPlayNext = { viewModel.playNext(track) },
        onAddToQueue = { viewModel.addToQueue(track) },
        onStartRadio = { viewModel.startRadio(track) },
        onToggleLike = { viewModel.toggleLike(track) },
        onRemove = playlist?.takeIf { sharedViewModel.canRemove(it, index) }
            ?.let { { sharedViewModel.remove(it, index, viewModel::refresh) } },
        removeLabel = stringResource(R.string.action_remove_from_playlist),
        onMoveUp = playlist?.takeIf { movable && index > 0 }
            ?.let { { sharedViewModel.move(it, index, index - 1, viewModel::refresh) } },
        onMoveDown = playlist?.takeIf { movable && index < lastIndex }
            ?.let { { sharedViewModel.move(it, index, index + 1, viewModel::refresh) } },
        onOpenArtist = track.artist
            .takeIf { it.isNotBlank() }
            ?.let { { name -> onOpenArtist(name, track.artistIdFor(name)) } },
        onOpenAlbum = onOpenAlbum,
        onDismiss = onDismiss,
        onShare = { viewModel.share(track) }.takeIf { viewModel.canShare(track) },
        onAddToPlaylist = { addToPlaylist.open(track) }.takeIf { addToPlaylist.canAdd(track) },
        onSelect = onSelect
    )
}
