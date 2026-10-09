package com.wander.android.ui.screens.home
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import com.wander.android.R
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.agro.AgroSessionViewModel
import com.wander.android.ui.components.AddToPlaylistHost
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.ExpressiveRefreshIndicator
import com.wander.android.ui.components.SessionSheet
import com.wander.android.ui.components.SourceFilterChips
import com.wander.android.ui.components.TrackActionsSheet
import com.wander.android.ui.screens.home.customize.HomeEditBar
import com.wander.android.ui.screens.home.customize.HomeEditorAddSheet
import com.wander.android.ui.screens.home.customize.HomeEditorSettingsSheet
import com.wander.android.ui.screens.home.customize.homeEditorShelves
import com.wander.android.ui.screens.home.customize.rememberHomeEditor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The other devices' session, shown at the top of Home the way Spotify keeps "listening on…"
    // in reach. Independent of the one-shot resume card, so it is available whenever you look.
    val agroViewModel: AgroSessionViewModel = hiltViewModel()
    val session by agroViewModel.latestSession.collectAsStateWithLifecycle()
    val agroDevices by agroViewModel.devices.collectAsStateWithLifecycle()
    val sessionArtwork by agroViewModel.sessionArtwork.collectAsStateWithLifecycle()
    val isResuming by agroViewModel.isResuming.collectAsStateWithLifecycle()
    var showSessionSheet by rememberSaveable { mutableStateOf(false) }

    val sessionDevice = remember(session, agroDevices) {
        session?.let { handoff -> agroDevices.firstOrNull { it.deviceId == handoff.deviceId } }
    }
    // The device's own online state decides this, not the session's `isPlaying` flag: that flag is
    // whatever the sender last wrote, so a client that exited mid-track left it saying "playing"
    // forever. `isPlaying` only stands in when the sending device is not in the list at all.
    val sessionIsLive = sessionDevice?.isOnline
        ?: (session?.isPlaying == true)

    // The session can disappear while the sheet is open — it was resumed here, or the other device
    // stopped — and a sheet describing nothing should not stay up.
    if (showSessionSheet && session == null) showSessionSheet = false

    session?.let { handoff ->
        if (showSessionSheet) {
            SessionSheet(
                handoff = handoff,
                deviceName = sessionDevice?.petname ?: "another device",
                isLive = sessionIsLive,
                isResuming = isResuming,
                artworkUrl = sessionArtwork,
                onResume = {
                    agroViewModel.resume(handoff)
                    showSessionSheet = false
                },
                onDismiss = { showSessionSheet = false }
            )
        }
    }

    // Held above the LazyColumn so a shelf scrolling off screen does not forget where it was.
    // See [HomeShelfStates].
    val shelfStates = remember { HomeShelfStates() }
    val listState = rememberLazyListState()
    val editor = rememberHomeEditor(state, viewModel, listState)
    HomeEditorSettingsSheet(editor, state, viewModel)
    HomeEditorAddSheet(editor, state, viewModel)

    // The same long-press menu Library and Search use, so a track offers the same actions
    // wherever it is shown. Held here rather than per shelf: only one can be open at a time.
    var actionsFor by remember { mutableStateOf<UnifiedTrack?>(null) }

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
            state.isLoading -> LoadingIndicator(modifier = Modifier.align(Alignment.Center))

            // No music anywhere, under any filter — there is nothing for the header or the source
            // chips to do, so the whole screen is the "go connect something" message.
            state.isGloballyEmpty -> EmptyState(
                title = stringResource(R.string.home_nothing_play_yet),
                message = stringResource(R.string.home_connect_navidrome_youtube_music_settings),
                actionLabel = stringResource(R.string.social_open_settings),
                onAction = onOpenSettings,
                modifier = Modifier.align(Alignment.Center)
            )

            // The header and the source chips stay on screen even when the *current filter* comes
            // up empty — otherwise selecting a source with nothing in it strands the user on a
            // dead-end screen with no way back to "All" short of restarting the app.
            else -> {
                val refreshState = rememberPullToRefreshState()
                val fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                val fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::pullToRefresh,
                state = refreshState,
                indicator = {
                    ExpressiveRefreshIndicator(
                        isRefreshing = state.isRefreshing,
                        state = refreshState,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "header", contentType = "header") {
                        AnimatedContent(
                            targetState = state.editing,
                            transitionSpec = { fadeIn(fadeInSpec) togetherWith fadeOut(fadeOutSpec) },
                            label = "homeHeader"
                        ) { editing ->
                            if (editing) {
                                HomeEditBar(onReset = viewModel.layoutActions::reset, onDone = viewModel.layoutActions::stop)
                            } else {
                                HomeHeader(
                                    greeting = state.greeting,
                                    hasSession = session != null,
                                    onOpenSessions = { showSessionSheet = true },
                                    onOpenSettings = onOpenSettings
                                )
                            }
                        }
                    }

                    // Only earns its row when there is more than one backend to choose between.
                    if (state.sources.size > 1 && !state.editing) {
                        item(key = "sources", contentType = "source-chips") {
                            SourceFilterChips(
                                sources = state.sources,
                                selected = state.selectedSource,
                                onSelect = viewModel::selectSource,
                                label = SourceType::shortName,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }

                    if (state.editing) {
                        homeEditorShelves(editor, state, viewModel, shelfStates)
                    } else if (state.isEmpty) {
                        item(key = "filtered_empty", contentType = "empty") {
                            EmptyState(
                                title = stringResource(R.string.home_nothing_here_yet),
                                message = "${state.selectedSource?.displayName ?: "This source"} " +
                                    "has no tracks. Tap the filter again to see everything.",
                                modifier = Modifier.fillMaxWidth().padding(top = 48.dp)
                            )
                        }
                    } else {
                        state.sections.forEach { section ->
                            homeSection(section, viewModel, shelfStates) { actionsFor = it }
                        }
                    }
                }
            }
            }
        }

    }
}
