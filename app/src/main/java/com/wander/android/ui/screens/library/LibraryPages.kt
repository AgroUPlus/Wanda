package com.wander.android.ui.screens.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.AddToPlaylistController

/** What the long-press callbacks and navigation of every Library page lead to. */
internal class LibraryPageActions(
    val onOpenAlbum: (String) -> Unit,
    val onOpenArtist: (String, String?) -> Unit,
    val onOpenPlaylist: (String) -> Unit,
    val onOpenImport: () -> Unit,
    val onOpenSubscriptions: () -> Unit,
    val onTrackLongPress: (UnifiedTrack) -> Unit,
    val onAlbumLongPress: (UnifiedAlbum) -> Unit
)

/** One page of the Library pager: the content of [tab]. */
@Composable
internal fun LibraryPage(
    tab: LibraryTab,
    contentPadding: PaddingValues,
    viewModel: LibraryViewModel,
    addToPlaylist: AddToPlaylistController,
    actions: LibraryPageActions
) {
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    when (tab) {
        LibraryTab.ALBUMS -> {
            val albums by viewModel.albums.collectAsStateWithLifecycle()
            val recentAlbums by viewModel.recentAlbums.collectAsStateWithLifecycle()
            val albumsError by viewModel.albumsError.collectAsStateWithLifecycle()
            AlbumGrid(
                albums = albums,
                recentAlbums = recentAlbums,
                contentPadding = contentPadding,
                onOpenAlbum = actions.onOpenAlbum,
                onAlbumLongPress = actions.onAlbumLongPress,
                loadError = albumsError,
                onRetry = viewModel::refresh
            )
        }
        LibraryTab.PLAYLISTS -> {
            val playlists by viewModel.playlists.collectAsStateWithLifecycle()
            PlaylistList(
                playlists, contentPadding, viewModel, addToPlaylist, actions.onOpenPlaylist, actions.onOpenImport
            )
        }
        LibraryTab.LIKED -> {
            val likedTracks by viewModel.likedTracks.collectAsStateWithLifecycle()
            TrackList(likedTracks, tab, isSyncing, contentPadding, viewModel, actions.onTrackLongPress)
        }
        LibraryTab.DOWNLOADS -> {
            val downloadedTracks by viewModel.downloadedTracks.collectAsStateWithLifecycle()
            TrackList(downloadedTracks, tab, isSyncing, contentPadding, viewModel, actions.onTrackLongPress)
        }
        LibraryTab.PODCASTS ->
            LibraryPodcastsPage(
                contentPadding,
                viewModel::toggleLike,
                onLongPress = actions.onTrackLongPress,
                onOpenSubscriptions = actions.onOpenSubscriptions
            )
        LibraryTab.TRACKS -> {
            val tracks = viewModel.tracks.collectAsLazyPagingItems()
            AllTracksPage(tracks, isSyncing, contentPadding, viewModel, actions.onTrackLongPress)
        }
        LibraryTab.FOLLOWED -> FollowedArtistsPage(contentPadding, actions.onOpenArtist)
    }
}
