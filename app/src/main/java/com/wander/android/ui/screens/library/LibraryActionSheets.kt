package com.wander.android.ui.screens.library

import androidx.compose.runtime.Composable
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.artistIdFor
import com.wander.android.ui.components.AddToPlaylistController
import com.wander.android.ui.components.AlbumActionsSheet
import com.wander.android.ui.components.TrackActionsSheet

/** The long-press sheet for an album tile in the Library. */
@Composable
internal fun LibraryAlbumActions(
    album: UnifiedAlbum,
    viewModel: LibraryViewModel,
    addToPlaylist: AddToPlaylistController,
    onOpenArtist: (String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlbumActionsSheet(
        album = album,
        onPlay = { viewModel.playAlbum(album) },
        onPlayNext = { viewModel.playAlbumNext(album) },
        onAddToQueue = { viewModel.addAlbumToQueue(album) },
        onAddToPlaylist = { viewModel.addAlbumToPlaylist(album, addToPlaylist) },
        onGoToArtist = album.artist
            .takeIf { it.isNotBlank() }
            ?.let { artist -> { onOpenArtist(artist, album.artistId) } },
        // No `canShare` gate: an album link describes the record rather than naming a server,
        // so it works from every source including the local library.
        onShare = { viewModel.shareAlbum(album) },
        onDismiss = onDismiss
    )
}

/** The long-press sheet for a track row on any of the Library's track pages. */
@Composable
internal fun LibraryTrackActions(
    track: UnifiedTrack,
    tab: LibraryTab,
    viewModel: LibraryViewModel,
    podcastsViewModel: PodcastsViewModel,
    addToPlaylist: AddToPlaylistController,
    onOpenArtist: (String, String?) -> Unit,
    onOpenAlbum: (String) -> Unit,
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
        onOpenAlbum = onOpenAlbum,
        onDismiss = onDismiss,
        onShare = if (viewModel.canShare(track)) {
            { viewModel.share(track) }
        } else {
            null
        },
        onAddToPlaylist = if (addToPlaylist.canAdd(track)) {
            { addToPlaylist.open(track) }
        } else {
            null
        },
        onDeleteDownload = if (tab == LibraryTab.DOWNLOADS || track.isDownloaded) {
            { viewModel.deleteDownloadedTrack(track) }
        } else null,
        onResetProgress = if (tab == LibraryTab.PODCASTS && track.isEpisode) {
            { podcastsViewModel.resetProgress(track) }
        } else null
    )
}
