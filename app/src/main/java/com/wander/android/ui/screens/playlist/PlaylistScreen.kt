package com.wander.android.ui.screens.playlist

import com.wander.android.ui.theme.heroTitle
import com.wander.android.ui.screens.album.HeroTitleMaxLines
import com.wander.android.ui.components.rememberShelfEntranceScale
import androidx.compose.ui.draw.scale
import com.wander.android.ui.components.groupedListItem
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.AddToPlaylistHost
import com.wander.android.ui.components.CompactHeroTopBar
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.MatchStatus
import com.wander.android.ui.components.TrackActionsSheet
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.components.listInset
import com.wander.android.ui.components.collapsingTitleSource
import com.wander.android.ui.components.rememberCollapsingTitleState
import com.wander.android.ui.screens.album.AlbumHero
import com.wander.android.ui.screens.album.AlbumSkeleton

@Composable
fun PlaylistScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel()
) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val importWork by viewModel.importWork.collectAsStateWithLifecycle()
    val sharedViewModel: SharedPlaylistViewModel = hiltViewModel()
    val shared by sharedViewModel.state.collectAsStateWithLifecycle()
    var actionsFor by remember { mutableStateOf<IndexedValue<UnifiedTrack>?>(null) }

    LaunchedEffect(sharedViewModel) { sharedViewModel.left.collect { onBack() } }

    val listState = rememberLazyListState()
    val titleState = rememberCollapsingTitleState(listState)

    val addToPlaylist = AddToPlaylistHost()

    val shareActions = PlaylistShareHost(playlist, tracks, onOpenPlaylist)

    actionsFor?.let { (index, track) ->
        val current = playlist
        val movable = current != null && sharedViewModel.canMove(current) && !sharedViewModel.isPending(index)
        TrackActionsSheet(
            track = track,
            isLiked = track.isLiked,
            onPlayNext = { viewModel.playNext(track) },
            onAddToQueue = { viewModel.addToQueue(track) },
            onStartRadio = { viewModel.startRadio(track) },
            onToggleLike = { viewModel.toggleLike(track) },
            onRemove = current?.takeIf { sharedViewModel.canRemove(it, index) }
                ?.let { { sharedViewModel.remove(it, index, viewModel::refresh) } },
            removeLabel = stringResource(R.string.action_remove_from_playlist),
            onMoveUp = current?.takeIf { movable && index > 0 }?.let { { sharedViewModel.move(it, index, index - 1, viewModel::refresh) } },
            onMoveDown = current?.takeIf { movable && index < tracks.lastIndex }?.let { { sharedViewModel.move(it, index, index + 1, viewModel::refresh) } },
            onOpenArtist = track.artist
                .takeIf { it.isNotBlank() }
                ?.let { artist -> { onOpenArtist(artist, track.artistId) } },
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

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading && playlist == null && tracks.isEmpty() -> {
                AlbumSkeleton(contentPadding.listInset())
            }

            playlist == null && tracks.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        title = stringResource(R.string.playlist_playlist_unavailable),
                        message = stringResource(R.string.playlist_playlist_couldn_t_loaded_from)
                    )
                }
            }

            else -> {
                LazyColumn(
                    state = listState,
                    contentPadding = contentPadding.listInset(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "header", contentType = "header") {
                        val current = playlist
                        val trackCount = if (current?.songCount != null && current.songCount > 0) current.songCount else tracks.size
                        val subtitle = listOfNotNull(
                            current?.source?.displayName,
                            "$trackCount track${if (trackCount == 1) "" else "s"}",
                            shareActions.sharedWith?.let { stringResource(R.string.playlist_shared_badge, stringResource(visibilityTitle(it))) },
                            current?.comment?.takeIf { it.isNotBlank() }
                        ).joinToString(" · ")

                        AlbumHero(
                            title = current?.name ?: "Playlist",
                            subtitle = subtitle,
                            artworkUrl = current?.coverArtUrl ?: tracks.firstNotNullOfOrNull { it.artworkUrl },
                            onPlay = viewModel::playAll,
                            onShuffle = viewModel::shuffle,
                            onShare = shareActions.onShare,
                            isShared = shareActions.sharedWith != null,
                            onConvert = shareActions.onConvert,
                            onDownload = shareActions.onDownload,
                            modifier = Modifier
                                .padding(bottom = 16.dp),
                            titleModifier = Modifier.collapsingTitleSource(titleState),
                            overline = R.string.hero_overline_playlist
                        )
                    }

                    shared?.let { state ->
                        item(key = "shared-status", contentType = "shared-status") {
                            SharedPlaylistBanner(
                                state = state,
                                onRetry = sharedViewModel::retry,
                                onUnfollow = sharedViewModel::unfollow.takeIf { state.localPlaylistId == null }
                            )
                        }
                    }

                    item(key = "import-status", contentType = "import-status") {
                        PlaylistImportBanner(
                            work = importWork,
                            notFoundCount = tracks.count { it.source == SourceType.UNRESOLVED },
                            onRetry = viewModel::retryImport
                        )
                    }

                    itemsIndexed(
                        items = tracks,
                        key = { index, track -> "${track.id}_$index" },
                        contentType = { _, _ -> "track" }
                    ) { index, track ->
                        val unmatched = track.source == SourceType.UNRESOLVED
                        // An unmatched track has nothing to play, but can still be taken out of a
                        // playlist this account may edit.
                        val removable = playlist?.let { sharedViewModel.canRemove(it, index) } == true
                        TrackRow(
                            track = track,
                            onPlay = { viewModel.play(index) },
                            onLongPress = { actionsFor = IndexedValue(index, track) }.takeUnless { unmatched && !removable },
                            trailingLabel = sharedViewModel.addedBy(index)
                                ?.let { stringResource(R.string.shared_playlist_added_by, it) },
                            matchStatus = when {
                                !unmatched -> null
                                importWork.isRunning -> MatchStatus.MATCHING
                                else -> MatchStatus.NOT_FOUND
                            },
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
            title = playlist?.name ?: "Playlist",
            onPlay = viewModel::playAll,
            heroTitleStyle = MaterialTheme.typography.heroTitle,
            heroTitleMaxLines = HeroTitleMaxLines,
            topInset = contentPadding.calculateTopPadding(),
            modifier = Modifier.align(Alignment.TopStart)
        )
    }
}
