package com.wander.android.ui.screens.playlist

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.sharedplaylist.SharedSyncState
import com.wander.android.ui.components.AddToPlaylistHost
import com.wander.android.ui.components.CompactHeroTopBar
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.MatchStatus
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.components.listInset
import com.wander.android.ui.components.rememberCollapsingTitleState
import com.wander.android.ui.screens.album.AlbumSkeleton
import com.wander.android.ui.screens.album.DetailRefreshBox
import com.wander.android.ui.screens.album.HeroTitleMaxLines
import com.wander.android.ui.theme.heroTitle

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
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val importWork by viewModel.importWork.collectAsStateWithLifecycle()
    val sharedViewModel: SharedPlaylistViewModel = hiltViewModel()
    val shared by sharedViewModel.state.collectAsStateWithLifecycle()
    var actionsFor by remember { mutableStateOf<IndexedValue<UnifiedTrack>?>(null) }
    var editingDetails by remember { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(sharedViewModel) { sharedViewModel.left.collect { onBack() } }

    val listState = rememberLazyListState()
    val titleState = rememberCollapsingTitleState(listState)

    val addToPlaylist = AddToPlaylistHost()

    val shareActions = PlaylistShareHost(playlist, tracks, onOpenPlaylist)

    val reorder = rememberPlaylistReorder(listState, tracks) { from, to ->
        val current = playlist ?: return@rememberPlaylistReorder false
        viewModel.showMove(from, to).also { shown ->
            if (shown) sharedViewModel.move(current, from, to, viewModel::refresh)
        }
    }
    val canMove = playlist?.let(sharedViewModel::canMove) == true && filter.isBlank()

    playlist?.takeIf { editingDetails }?.let { current ->
        PlaylistDetailsDialog(
            name = current.name,
            description = current.comment,
            isShared = shareActions.sharedWith != null,
            onSave = viewModel::editDetails,
            onDismiss = { editingDetails = false }
        )
    }

    actionsFor?.let { (index, track) ->
        PlaylistTrackActions(
            index = index,
            track = track,
            playlist = playlist,
            lastIndex = tracks.lastIndex,
            viewModel = viewModel,
            sharedViewModel = sharedViewModel,
            addToPlaylist = addToPlaylist,
            onOpenArtist = onOpenArtist,
            onDismiss = { actionsFor = null }
        )
    }

    DetailRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::pullToRefresh,
        topInset = contentPadding.calculateTopPadding()
    ) {
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
                        message = stringResource(R.string.playlist_playlist_couldn_t_loaded_from),
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
                ) {
                    item(key = "header", contentType = "header") {
                        PlaylistHeader(
                            playlist = playlist,
                            tracks = tracks,
                            shareActions = shareActions,
                            titleState = titleState,
                            onPlay = viewModel::playAll,
                            onShuffle = viewModel::shuffle
                        )
                    }

                    shared?.takeIf { it.isBlend }?.let { state ->
                        item(key = "blend", contentType = "blend") {
                            com.wander.android.ui.screens.playlist.blend.BlendBanner(agroId = state.agroId, onLeft = onBack)
                        }
                    }

                    // A blend's own banner says who is in it; this one is still needed when its copy
                    // is behind, so an old version is never mistaken for the current one.
                    shared?.takeIf { !it.isBlend || it.syncState != SharedSyncState.SYNCED }?.let { state ->
                        item(key = "shared-status", contentType = "shared-status") {
                            SharedPlaylistBanner(
                                state = state,
                                onRetry = sharedViewModel::retry,
                                onUnfollow = sharedViewModel::unfollow.takeIf { state.localPlaylistId == null && !state.isBlend }
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

                    val rows = reorder.shown.filteredBy(filter)
                    if (tracks.size >= PlaylistFilterMinTracks || filter.isNotEmpty()) {
                        item(key = "filter", contentType = "filter") {
                            PlaylistFilterField(
                                query = filter,
                                onQueryChange = { filter = it },
                                noMatches = rows.isEmpty() && filter.isNotBlank()
                            )
                        }
                    }

                    itemsIndexed(
                        items = rows,
                        key = { _, entry -> entry.key },
                        contentType = { _, _ -> "track" }
                    ) { position, entry ->
                        val index = entry.index
                        val track = entry.track
                        val unmatched = track.source == SourceType.UNRESOLVED
                        // An unmatched track has nothing to play, but can still be taken out of a
                        // playlist this account may edit.
                        val removable = playlist?.let { sharedViewModel.canRemove(it, index) } == true
                        PlaylistTrackItem(
                            reorder = reorder,
                            entry = entry,
                            position = position,
                            count = rows.size,
                            draggable = canMove && !sharedViewModel.isPending(index)
                        ) { rowModifier ->
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
                                modifier = rowModifier
                            )
                        }
                    }
                }
            }
        }

        CompactHeroTopBar(
            titleState = titleState,
            onBack = onBack,
            title = playlist?.name ?: stringResource(R.string.playlist_fallback_title),
            onPlay = viewModel::playAll,
            heroTitleStyle = MaterialTheme.typography.heroTitle,
            heroTitleMaxLines = HeroTitleMaxLines,
            topInset = contentPadding.calculateTopPadding(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            PlaylistOverflow(onEditDetails = { editingDetails = true }.takeIf { viewModel.canEditDetails() })
        }
    }
}
