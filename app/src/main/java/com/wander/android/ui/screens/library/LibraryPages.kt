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
import com.wander.android.ui.components.selection.TrackSelectionState

/** What the long-press callbacks and navigation of every Library page lead to. */
internal class LibraryPageActions(
    val onOpenAlbum: (String) -> Unit,
    val onOpenArtist: (String, String?) -> Unit,
    val onOpenPlaylist: (String) -> Unit,
    val onOpenImport: () -> Unit,
    val onOpenSubscriptions: () -> Unit,
    val onTrackLongPress: (UnifiedTrack) -> Unit,
    val onAlbumLongPress: (UnifiedAlbum) -> Unit,
    /** Shared by the track pages; see [selectsTracks]. */
    val selection: TrackSelectionState
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
            AlbumsContent(tab, contentPadding, viewModel, browse, actions)
        }
        LibraryTab.PLAYLISTS -> SortablePage(tab, browse) {
            PlaylistsContent(contentPadding, viewModel, browse, addToPlaylist, actions)
        }
        LibraryTab.LIKED -> SortablePage(tab, browse) {
            LikedContent(tab, isSyncing, contentPadding, viewModel, browse, actions)
        }
        LibraryTab.DOWNLOADS -> SortablePage(tab, browse) {
            DownloadsContent(tab, isSyncing, contentPadding, viewModel, browse, actions)
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
            AllTracksPage(
                tracks, isSyncing, contentPadding, viewModel, browse, actions.onTrackLongPress, actions.selection
            )
        }
        LibraryTab.ARTISTS -> LibraryArtistsPage(contentPadding, browse, actions.onOpenArtist)
        LibraryTab.GENRES ->
            LibraryGenresPage(contentPadding, viewModel, browse, isSyncing, actions.onTrackLongPress, actions.selection)
        LibraryTab.FOLLOWED -> FollowedArtistsPage(contentPadding, actions.onOpenArtist)
    }
}

/** The Albums tab, as a list or a grid per the tab's chosen view. */
@Composable
private fun AlbumsContent(
    tab: LibraryTab,
    contentPadding: PaddingValues,
    viewModel: LibraryViewModel,
    browse: LibraryBrowseViewModel,
    actions: LibraryPageActions
) {
    val albums by browse.albums.collectAsStateWithLifecycle()
    val recentAlbums by browse.recentAlbums.collectAsStateWithLifecycle()
    val albumsError by viewModel.albumsError.collectAsStateWithLifecycle()
    val sorts by browse.sorts.collectAsStateWithLifecycle()
    val views by browse.views.collectAsStateWithLifecycle()
    val sort = sorts[tab]
    // The grid owns the empty state, list or not: "no albums yet" reads the same either way.
    if (albums.isNotEmpty() && (views[tab] ?: tab.defaultView) == LibraryView.LIST) {
        AlbumList(
            albums = albums,
            contentPadding = contentPadding,
            onOpenAlbum = actions.onOpenAlbum,
            onAlbumLongPress = actions.onAlbumLongPress,
            letterFor = albumLetter(sort),
            order = sort
        )
    } else {
        AlbumGrid(
            albums = albums,
            recentAlbums = recentAlbums,
            contentPadding = contentPadding,
            onOpenAlbum = actions.onOpenAlbum,
            onAlbumLongPress = actions.onAlbumLongPress,
            loadError = albumsError,
            onRetry = viewModel::refresh,
            letterFor = albumLetter(sort),
            order = sort
        )
    }
}

/** The Playlists tab, narrowed to the sources picked in its filter. */
@Composable
private fun PlaylistsContent(
    contentPadding: PaddingValues,
    viewModel: LibraryViewModel,
    browse: LibraryBrowseViewModel,
    addToPlaylist: AddToPlaylistController,
    actions: LibraryPageActions
) {
    val all by viewModel.playlists.collectAsStateWithLifecycle()
    val chosen by browse.sourceFilter.collectAsStateWithLifecycle()
    val playlists = remember(all, chosen) {
        if (chosen.isEmpty()) all else all.filter { it.source in chosen }
    }
    PlaylistList(
        playlists, contentPadding, viewModel, addToPlaylist, actions.onOpenPlaylist, actions.onOpenImport
    )
}

/** The Liked tab, optionally narrowed to what is on the device. */
@Composable
private fun LikedContent(
    tab: LibraryTab,
    isSyncing: Boolean,
    contentPadding: PaddingValues,
    viewModel: LibraryViewModel,
    browse: LibraryBrowseViewModel,
    actions: LibraryPageActions
) {
    val likedTracks by browse.shownLikedTracks.collectAsStateWithLifecycle()
    val downloadedOnly by browse.downloadedOnly.collectAsStateWithLifecycle()
    val sorts by browse.sorts.collectAsStateWithLifecycle()
    // Narrowed to the device and empty, it is the Offline list that is empty, and says so.
    TrackList(
        likedTracks, if (downloadedOnly) LibraryTab.DOWNLOADS else tab, isSyncing, contentPadding,
        viewModel, actions.onTrackLongPress, actions.selection,
        letterFor = trackLetter(sorts[tab]),
        order = sorts[tab]
    )
}

/** The Downloads tab: every track kept on the device. */
@Composable
private fun DownloadsContent(
    tab: LibraryTab,
    isSyncing: Boolean,
    contentPadding: PaddingValues,
    viewModel: LibraryViewModel,
    browse: LibraryBrowseViewModel,
    actions: LibraryPageActions
) {
    val downloadedTracks by browse.downloadedTracks.collectAsStateWithLifecycle()
    val sorts by browse.sorts.collectAsStateWithLifecycle()
    TrackList(
        downloadedTracks, tab, isSyncing, contentPadding, viewModel,
        actions.onTrackLongPress, actions.selection,
        letterFor = trackLetter(sorts[tab]),
        order = sorts[tab]
    )
}

/** The tabs whose rows can be selected; the sheet offers Select only there. */
internal val LibraryTab.selectsTracks: Boolean
    get() = this == LibraryTab.TRACKS || this == LibraryTab.LIKED ||
        this == LibraryTab.DOWNLOADS || this == LibraryTab.GENRES

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
