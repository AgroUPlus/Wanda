package com.wander.android.ui.screens.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
    browse: LibraryBrowseViewModel,
    addToPlaylist: AddToPlaylistController,
    actions: LibraryPageActions
) {
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    when (tab) {
        LibraryTab.ALBUMS -> SortablePage(tab, browse) {
            val albums by browse.albums.collectAsStateWithLifecycle()
            val recentAlbums by browse.recentAlbums.collectAsStateWithLifecycle()
            val albumsError by viewModel.albumsError.collectAsStateWithLifecycle()
            val sorts by browse.sorts.collectAsStateWithLifecycle()
            AlbumGrid(
                albums = albums,
                recentAlbums = recentAlbums,
                contentPadding = contentPadding,
                onOpenAlbum = actions.onOpenAlbum,
                onAlbumLongPress = actions.onAlbumLongPress,
                loadError = albumsError,
                onRetry = viewModel::refresh,
                letterFor = albumLetter(sorts[tab])
            )
        }
        LibraryTab.PLAYLISTS -> SortablePage(tab, browse) {
            val all by viewModel.playlists.collectAsStateWithLifecycle()
            val chosen by browse.sourceFilter.collectAsStateWithLifecycle()
            val playlists = remember(all, chosen) {
                if (chosen.isEmpty()) all else all.filter { it.source in chosen }
            }
            PlaylistList(
                playlists, contentPadding, viewModel, addToPlaylist, actions.onOpenPlaylist, actions.onOpenImport
            )
        }
        LibraryTab.LIKED -> SortablePage(tab, browse) {
            val likedTracks by browse.likedTracks.collectAsStateWithLifecycle()
            TrackList(likedTracks, tab, isSyncing, contentPadding, viewModel, actions.onTrackLongPress)
        }
        LibraryTab.DOWNLOADS -> SortablePage(tab, browse) {
            val downloadedTracks by browse.downloadedTracks.collectAsStateWithLifecycle()
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
            val tracks = browse.tracks.collectAsLazyPagingItems()
            AllTracksPage(tracks, isSyncing, contentPadding, viewModel, browse, actions.onTrackLongPress)
        }
        LibraryTab.ARTISTS -> LibraryArtistsPage(contentPadding, browse, actions.onOpenArtist)
        LibraryTab.GENRES ->
            LibraryGenresPage(contentPadding, viewModel, browse, isSyncing, actions.onTrackLongPress)
        LibraryTab.FOLLOWED -> FollowedArtistsPage(contentPadding, actions.onOpenArtist)
    }
}

/** A list under its [LibraryListToolbar], which stays put even when the list below is empty. */
@Composable
private fun SortablePage(
    tab: LibraryTab,
    browse: LibraryBrowseViewModel,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LibraryListToolbar(tab, browse)
        content()
    }
}
