package com.wander.android.ui.screens.search

import androidx.compose.runtime.Composable
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.artistIdFor
import com.wander.android.ui.components.AddToPlaylistController
import com.wander.android.ui.components.TrackActionsSheet

/**
 * The long-press sheet of a search result. Split out of [SearchScreen] for length. [onSelect] is
 * null for a lyric match, which is not a row of the song list a selection works on.
 */
@Composable
internal fun SearchTrackActions(
    track: UnifiedTrack,
    viewModel: SearchViewModel,
    addToPlaylist: AddToPlaylistController,
    onOpenArtist: (String, String?) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onSelect: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    TrackActionsSheet(
        track = track,
        isLiked = track.isLiked,
        onPlayNext = { viewModel.playNext(track) },
        onAddToQueue = { viewModel.addToQueue(track) },
        onStartRadio = { viewModel.startRadio(track) },
        onToggleLike = { viewModel.toggleLike(track) },
        onRemove = null,
        onOpenArtist = track.artist
            .takeIf { it.isNotBlank() }
            ?.let { { name -> onOpenArtist(name, track.artistIdFor(name)) } },
        onOpenAlbum = { albumId ->
            viewModel.rememberQuery()
            onOpenAlbum(albumId)
        },
        onDismiss = onDismiss,
        onShare = { viewModel.share(track) }.takeIf { viewModel.canShare(track) },
        onAddToPlaylist = { addToPlaylist.open(track) }.takeIf { addToPlaylist.canAdd(track) },
        onSelect = onSelect
    )
}
