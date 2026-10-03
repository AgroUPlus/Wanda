package com.wander.android.ui.screens.artist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.ui.theme.heroTitle
import com.wander.android.R
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.AddToPlaylistController
import com.wander.android.ui.components.AddToPlaylistHost
import com.wander.android.ui.components.AlbumActionsSheet
import com.wander.android.ui.components.CompactHeroTopBar
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.TrackActionsSheet
import com.wander.android.ui.components.listInset
import com.wander.android.ui.components.collapsingTitleSource
import com.wander.android.ui.components.rememberCollapsingTitleState

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

    val listState = rememberLazyListState()
    val titleState = rememberCollapsingTitleState(listState)

    val addToPlaylist = AddToPlaylistHost()

    actionsFor?.let { track ->
        ArtistTrackActions(track, viewModel, addToPlaylist) { actionsFor = null }
    }

    albumActionsFor?.let { album ->
        ArtistAlbumActions(album, viewModel, addToPlaylist) { albumActionsFor = null }
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
                    title = stringResource(R.string.artist_nothing_by, state.artist),
                    message = stringResource(R.string.artist_none_connected_sources_has_anything)
                )
            }

            else -> LazyColumn(
                state = listState,
                contentPadding = contentPadding.listInset(),
                // No blanket item spacing: it also landed between the grouped song rows, which
                // are meant to sit 2 dp apart as one group. Section titles carry the room instead.
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
                        isFollowing = state.isFollowing,
                        onToggleFollow = viewModel::toggleFollow,
                        modifier = Modifier
                            .padding(bottom = 8.dp),
                        titleModifier = Modifier.collapsingTitleSource(titleState)
                    )
                }

                if (state.page.bio != null || state.page.genres.isNotEmpty()) {
                    item(key = "bio", contentType = "bio") {
                        Box(modifier = Modifier.padding(bottom = 8.dp)) {
                            ArtistBio(bio = state.page.bio, genres = state.page.genres)
                        }
                    }
                }

                // Only for a genuine fetch failure, never for "this artist has no backend page" —
                // see [ArtistUiState.pageFetchFailed]. The library-derived page above still renders
                // in full either way, so this is an offer to try again, not a blocking error.
                if (state.pageFetchFailed) {
                    item(key = "fetch_failed", contentType = "fetch_failed") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.artist_info_unavailable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.refresh() }, shapes = ButtonDefaults.shapes()) {
                                Text(stringResource(R.string.common_try_again))
                            }
                        }
                    }
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
                    onOpenAlbum = onOpenAlbum,
                    onLongPressAlbum = { albumActionsFor = it },
                    onOpenArtist = onOpenArtist
                )
            }
        }

        // The name slides and shrinks up into a compact bar as the portrait scrolls away, so the
        // page stays anchored without scrolling back up. No play button here — the hero below
        // already carries its own, bigger one; see `CompactHeroTopBar`'s own doc on `onPlay`.
        CompactHeroTopBar(
            titleState = titleState,
            onBack = onBack,
            title = state.artist,
            heroTitleStyle = MaterialTheme.typography.heroTitle,
            heroTitleMaxLines = ArtistNameMaxLines,
            topInset = contentPadding.calculateTopPadding(),
            modifier = Modifier.align(Alignment.TopStart)
        )
    }
}

private fun artistSubtitle(albumCount: Int, trackCount: Int): String = listOfNotNull(
    albumCount.takeIf { it > 0 }?.let { "$it album${if (it == 1) "" else "s"}" },
    trackCount.takeIf { it > 0 }?.let { "$it track${if (it == 1) "" else "s"}" }
).joinToString(" · ")

@Composable
private fun ArtistTrackActions(
    track: UnifiedTrack,
    viewModel: ArtistViewModel,
    addToPlaylist: AddToPlaylistController,
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
        }
    )
}

@Composable
private fun ArtistAlbumActions(
    album: UnifiedAlbum,
    viewModel: ArtistViewModel,
    addToPlaylist: AddToPlaylistController,
    onDismiss: () -> Unit
) {
    AlbumActionsSheet(
        album = album,
        onPlay = {
            viewModel.playAlbum(album)
            onDismiss()
        },
        onPlayNext = {
            viewModel.playAlbumNext(album)
            onDismiss()
        },
        onAddToQueue = {
            viewModel.addAlbumToQueue(album)
            onDismiss()
        },
        onDismiss = onDismiss,
        onShare = if (viewModel.canShareAlbum(album)) {
            { viewModel.shareAlbum(album) }
        } else null,
        onAddToPlaylist = {
            viewModel.getAlbumTracks(album) { tracks ->
                addToPlaylist.openForTracks(tracks, album.source)
            }
            onDismiss()
        }
    )
}
