package com.wander.android.ui.screens.album

import com.wander.android.ui.theme.heroTitle
import com.wander.android.ui.components.rememberShelfEntranceScale
import androidx.compose.ui.draw.scale
import com.wander.android.ui.components.groupedListItem
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.artistIdFor
import com.wander.android.ui.components.AddToPlaylistHost
import com.wander.android.ui.components.CompactHeroTopBar
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.TrackActionsSheet
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.components.listInset
import com.wander.android.ui.components.collapsingTitleSource
import com.wander.android.ui.components.rememberCollapsingTitleState
import com.wander.android.ui.components.rememberDownloadAll
import com.wander.android.ui.components.selection.SelectedTrack
import com.wander.android.ui.components.selection.TrackSelectionBar
import com.wander.android.ui.components.selection.rememberTrackSelectionState

/**
 * One record, with its actual tracklist — reached by tapping the album name in the player, which
 * previously just ran a search for it.
 */
@Composable
fun AlbumScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    viewModel: AlbumViewModel = hiltViewModel()
) {
    val album by viewModel.album.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val loadError by viewModel.loadError.collectAsStateWithLifecycle()
    var actionsFor by remember { mutableStateOf<UnifiedTrack?>(null) }

    val listState = rememberLazyListState()
    val titleState = rememberCollapsingTitleState(listState)

    val addToPlaylist = AddToPlaylistHost()
    val downloadAlbum = rememberDownloadAll(tracks)
    val selection = rememberTrackSelectionState()

    actionsFor?.let { track ->
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
            },
            onSelect = { selection.toggle(SelectedTrack(track.id, track)) }
        )
    }

    DetailRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::pullToRefresh,
        topInset = contentPadding.calculateTopPadding()
    ) {
        when {
            isLoading && album == null && tracks.isEmpty() -> {
                AlbumSkeleton(contentPadding.listInset())
            }

            album == null && tracks.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        title = stringResource(R.string.album_album_unavailable),
                        message = stringResource(loadError ?: R.string.album_album_isn_t_any_connected),
                        actionLabel = stringResource(R.string.common_try_again),
                        onAction = viewModel::refresh
                    )
                }
            }

            else -> {
                LazyColumn(
                    state = listState,
                    contentPadding = contentPadding.listInset(),
                    modifier = Modifier.fillMaxSize()
                        // The list carries the side insets; the hero inside it must not add them again.
                        .consumeWindowInsets(contentPadding.listInset())
                ) {
                    item(key = "header", contentType = "header") {
                        val current = album
                        AlbumHero(
                            title = current?.title ?: tracks.firstOrNull()?.album.orEmpty(),
                            subtitle = albumSubtitle(
                                artist = current?.artist ?: tracks.firstOrNull()?.artist.orEmpty(),
                                year = current?.year,
                                trackCount = tracks.size
                            ),
                            artworkUrl = current?.coverArtUrl
                                ?: tracks.firstNotNullOfOrNull { it.artworkUrl },
                            onPlay = viewModel::playAll,
                            onShuffle = viewModel::shuffle,
                            // No `canShare` gate: an album link describes the record rather than
                            // naming a server, so it works from every source.
                            onShare = viewModel::shareAlbum.takeIf { current != null },
                            onDownload = downloadAlbum,
                            modifier = Modifier
                                .padding(bottom = 16.dp),
                            titleModifier = Modifier.collapsingTitleSource(titleState)
                        )
                    }

                    loadError?.let { error ->
                        item(key = "load-error", contentType = "load-error") {
                            EmptyState(
                                title = stringResource(R.string.collection_tracks_load_failed),
                                message = stringResource(error),
                                actionLabel = stringResource(R.string.error_retry),
                                onAction = viewModel::refresh,
                                modifier = Modifier.padding(vertical = 24.dp)
                            )
                        }
                    }

                    // The artist name is a way *out* of this page, into the rest of their work.
                    item(key = "artist-link", contentType = "artist-link") {
                        val artist = album?.artist ?: tracks.firstOrNull()?.artist
                        if (!artist.isNullOrBlank()) {
                            ArtistLinkRow(
                                artist = artist,
                                onClick = { onOpenArtist(artist, album?.artistId) }
                            )
                        }
                    }

                    itemsIndexed(
                        items = tracks,
                        key = { _, track -> track.id },
                        contentType = { _, _ -> "track" }
                    ) { index, track ->
                        val item = SelectedTrack(track.id, track)
                        TrackRow(
                            track = track,
                            onPlay = selection.onTap(item) { viewModel.play(index) },
                            onLongPress = selection.onLongPress(item) { actionsFor = track },
                            selected = selection.stateOf(item.key),
                            modifier = Modifier
                                .animateItem()
                                .scale(rememberShelfEntranceScale(index))
                                .groupedListItem(index, tracks.size)
                        )
                    }
                }
            }
        }

        CompactHeroTopBar(
            titleState = titleState,
            onBack = onBack,
            title = album?.title ?: tracks.firstOrNull()?.album.orEmpty(),
            onPlay = viewModel::playAll,
            heroTitleStyle = MaterialTheme.typography.heroTitle,
            heroTitleMaxLines = HeroTitleMaxLines,
            topInset = contentPadding.calculateTopPadding(),
            modifier = Modifier.align(Alignment.TopStart)
        )
        TrackSelectionBar(
            selection = selection,
            addToPlaylist = addToPlaylist,
            topInset = contentPadding.calculateTopPadding(),
            modifier = Modifier.align(Alignment.TopStart)
        )
    }
}

/** "Nirvana · 1991 · 13 tracks", skipping whatever is unknown. */
@Composable
private fun albumSubtitle(artist: String, year: Int?, trackCount: Int): String = listOfNotNull(
    artist.takeIf { it.isNotBlank() },
    year?.toString(),
    trackCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.collection_track_count, it, it) }
).joinToString(" · ")
