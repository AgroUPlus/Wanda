package com.wander.android.ui.screens.artist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.AddToPlaylistHost
import com.wander.android.ui.components.AlbumActionsSheet
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.TrackActionsSheet
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.listInset

/**
 * An artist: what they are known for, what they released, and who they sound like — gathered
 * across every connected backend and folded into one fixed set of sections.
 *
 * Keyed by name rather than by id — see [com.wander.android.data.repository.CatalogRepository].
 */
@Composable
internal fun ArtistScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    viewModel: ArtistViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var actionsFor by remember { mutableStateOf<UnifiedTrack?>(null) }
    var albumActionsFor by remember { mutableStateOf<UnifiedAlbum?>(null) }
    // Survives rotation but not the back stack: "show all" is a decision about this visit to this
    // page, not a preference.
    var showAllSongs by rememberSaveable { mutableStateOf(false) }

    val addToPlaylist = AddToPlaylistHost()

    actionsFor?.let { track ->
        TrackActionsSheet(
            track = track,
            isLiked = track.isLiked,
            onPlayNext = { viewModel.playNext(track) },
            onAddToQueue = { viewModel.addToQueue(track) },
            onStartRadio = { viewModel.startRadio(track) },
            onToggleLike = { viewModel.toggleLike(track) },
            onRemove = null,
            onDismiss = { actionsFor = null },
            onShare = if (viewModel.canShare(track)) {
                { viewModel.share(track) }
            } else {
                null
            },
            onAddToPlaylist = if (addToPlaylist.canAdd(track)) {
                { addToPlaylist.open(track) }
            } else {
                null
            }
        )
    }

    albumActionsFor?.let { album ->
        AlbumActionsSheet(
            album = album,
            onPlay = {
                viewModel.playAlbum(album)
                albumActionsFor = null
            },
            onPlayNext = {
                viewModel.playAlbumNext(album)
                albumActionsFor = null
            },
            onAddToQueue = {
                viewModel.addAlbumToQueue(album)
                albumActionsFor = null
            },
            onDismiss = { albumActionsFor = null },
            onShare = if (viewModel.canShareAlbum(album)) {
                { viewModel.shareAlbum(album) }
            } else null,
            onAddToPlaylist = {
                viewModel.getAlbumTracks(album) { tracks ->
                    addToPlaylist.openForTracks(tracks, album.source)
                }
                albumActionsFor = null
            }
        )
    }

    // A single Box rather than a header above a list: the portrait runs to the top of the window,
    // under the status bar, so there is no bar to put a back control in. It floats over the image
    // instead, inset by the status bar itself.
    Box(modifier = Modifier.fillMaxSize()) {
        // Three states, and only three. A skeleton while nothing definite is known, the empty
        // state once every source has answered with nothing, and the page itself otherwise —
        // including while a refresh is still running underneath it.
        when {
            state.isLoading -> ArtistSkeleton(contentPadding.listInset())

            state.isEmpty -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    title = "Nothing by ${state.artist}",
                    message = "None of your connected sources has anything by this artist."
                )
            }

            else -> LazyColumn(
                contentPadding = contentPadding.listInset(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "header", contentType = "header") {
                    ArtistHero(
                        name = state.artist,
                        subtitle = artistSubtitle(state.albumCount, state.trackCount),
                        imageUrl = state.heroImage,
                        onPlay = viewModel::playTop,
                        onRadio = viewModel::startArtistRadio,
                        onShuffle = viewModel::shuffle,
                        onShare = viewModel::shareArtist.takeIf { state.canShare },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                state.page.bio?.let { bio ->
                    item(key = "bio", contentType = "bio") { ArtistBio(bio) }
                }

                artistPageSections(
                    page = state.page,
                    showAllSongs = showAllSongs,
                    expandedShelves = state.expandedShelves,
                    loadingShelf = state.loadingShelf,
                    onToggleShowAllSongs = { showAllSongs = !showAllSongs },
                    onExpandShelf = viewModel::expandShelf,
                    onPlaySong = viewModel::play,
                    onPlayTrack = viewModel::playOne,
                    onLongPressTrack = { actionsFor = it },
                    onToggleLike = viewModel::toggleLike,
                    onOpenAlbum = onOpenAlbum,
                    onLongPressAlbum = { albumActionsFor = it },
                    onOpenArtist = onOpenArtist
                )
            }
        }

        // Tinted rather than plain: a bare icon lands on whatever the portrait happens to be, and
        // a back arrow that disappears into a light photo is a page with no way out of it.
        FilledTonalIconButton(
            onClick = onBack,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            ),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(contentPadding.headerInset())
                .padding(start = 12.dp, top = 8.dp)
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
    }
}

private fun artistSubtitle(albumCount: Int, trackCount: Int): String = listOfNotNull(
    albumCount.takeIf { it > 0 }?.let { "$it album${if (it == 1) "" else "s"}" },
    trackCount.takeIf { it > 0 }?.let { "$it track${if (it == 1) "" else "s"}" }
).joinToString(" · ")
