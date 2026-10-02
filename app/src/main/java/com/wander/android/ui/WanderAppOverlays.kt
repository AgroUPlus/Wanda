package com.wander.android.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.wander.android.core.permissions.rememberLocalNetworkGate
import com.wander.android.data.repository.ListenAlongSession
import com.wander.android.data.repository.MoodPresets
import com.wander.android.data.sources.agro.Jam
import com.wander.android.ui.agro.AgroSessionViewModel
import kotlinx.coroutines.launch
import com.wander.android.ui.components.JamBar
import com.wander.android.ui.components.ListenAlongBar
import com.wander.android.ui.components.SyncOfferSheet
import com.wander.android.ui.components.player.PlayerSheetState
import com.wander.android.ui.components.player.PlayerSheetValue
import com.wander.android.ui.navigation.Routes
import com.wander.android.ui.navigation.TopLevelDestination
import com.wander.android.ui.navigation.navigateSettled
import com.wander.android.ui.screens.home.MoodMatrixViewModel
import com.wander.android.ui.screens.home.RadioMoodFab
import com.wander.android.ui.screens.social.JamViewModel
import com.wander.android.ui.screens.social.SocialViewModel

/** The gap between the radio button and whatever is parked at the bottom edge. */
private val RadioFabClearance = 16.dp

/**
 * How far the player sheet may travel before it counts as opening.
 *
 * Not zero: the docked sheet settles on an animated float, so an exact comparison would flicker on
 * the last fraction of a spring that has effectively already stopped.
 */
private const val DockedEpsilon = 0.01f

/** The view models the shell overlays read from. */
internal class ShellViewModels(
    val app: WanderAppViewModel,
    val agro: AgroSessionViewModel,
    val social: SocialViewModel,
    val jam: JamViewModel
)

/** What the shell knows about the current screen and session that decides which overlays show. */
internal class ShellOverlayState(
    val currentRoute: String?,
    val showChrome: Boolean,
    val isPlayingHere: Boolean,
    val dockBottom: Dp,
    val activeJam: Jam?,
    val listenAlongSession: ListenAlongSession?
)

/**
 * Overlays and floating controls anchored to the app shell: the radio/mood FAB, bottom offers,
 * sync sheets, Jam bar, listen-along bar, and snackbar host.
 */
@Composable
internal fun BoxScope.WanderAppOverlays(
    viewModels: ShellViewModels,
    navController: NavHostController,
    sheetState: PlayerSheetState,
    snackbarHostState: SnackbarHostState,
    shell: ShellOverlayState
) {
    val viewModel = viewModels.app
    val agroViewModel = viewModels.agro
    val socialViewModel = viewModels.social
    val jamViewModel = viewModels.jam
    val currentRoute = shell.currentRoute
    val showChrome = shell.showChrome
    val isPlayingHere = shell.isPlayingHere
    val dockBottom = shell.dockBottom
    val activeJam = shell.activeJam
    val listenAlongSession = shell.listenAlongSession
    // Both bottom-anchored cards belong to the browsing surface, not to the player: floating
    // them over a full-screen Now Playing reads as a stray dialog.
    val sheetCollapsed = sheetState.targetValue == PlayerSheetValue.COLLAPSED

    val isStartingRadio by viewModel.isStartingRadio.collectAsStateWithLifecycle()
    val moodViewModel: MoodMatrixViewModel = hiltViewModel()
    val moodState by moodViewModel.state.collectAsStateWithLifecycle()
    val playerDocked by remember(sheetState) {
        derivedStateOf { sheetState.progress <= DockedEpsilon }
    }

    val syncOffer by viewModel.syncOffer.collectAsStateWithLifecycle()
    val syncCovers by viewModel.syncCovers.collectAsStateWithLifecycle()
    val acceptSyncWithLocalNetwork = rememberLocalNetworkGate(viewModel::acceptSyncOffer)
    val syncDetailsOpen by viewModel.syncDetailsOpen.collectAsStateWithLifecycle()
    val fetchProgress by viewModel.fetchProgress.collectAsStateWithLifecycle()
    val offerRoute by viewModel.offerRoute.collectAsStateWithLifecycle()
    val isFetchingSync by viewModel.isFetchingSync.collectAsStateWithLifecycle()

    val replayOffer by viewModel.replayOffer.collectAsStateWithLifecycle()
    val incomingHandoff by agroViewModel.incomingHandoff.collectAsStateWithLifecycle()
    val isResuming by agroViewModel.isResuming.collectAsStateWithLifecycle()
    val agroDevices by agroViewModel.devices.collectAsStateWithLifecycle()
    val sessionArtwork by agroViewModel.sessionArtwork.collectAsStateWithLifecycle()
    val agroError by agroViewModel.error.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    androidx.lifecycle.compose.LifecycleStartEffect(agroViewModel) {
        if (!isPlayingHere) agroViewModel.allowReoffer()
        viewModel.refreshSyncOffer()
        viewModel.refreshShareSettings()
        val job = scope.launch {
            agroViewModel.observeLiveUpdates(onLibraryChanged = viewModel::refreshSyncOffer)
        }
        onStopOrDispose { job.cancel() }
    }

    androidx.compose.runtime.LaunchedEffect(agroError) {
        val message = agroError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message, withDismissAction = true)
        agroViewModel.clearError()
    }

    RadioMoodFab(
        isStarting = isStartingRadio,
        visible = playerDocked && currentRoute == TopLevelDestination.HOME.route,
        moods = MoodPresets,
        playingMoodKey = moodState.playingKey,
        onInstantRadio = viewModel::startInstantRadio,
        onSelectMood = moodViewModel::playMood,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = dockBottom + RadioFabClearance)
    )

    BottomOffers(
        syncOffer = if (showChrome && sheetCollapsed) syncOffer else emptyList(),
        isFetchingSync = isFetchingSync,
        onAcceptSync = acceptSyncWithLocalNetwork,
        onOpenSyncDetails = viewModel::openSyncDetails,
        syncCovers = syncCovers,
        fetchProgress = fetchProgress,
        offerRoute = offerRoute,
        onDismissSync = viewModel::dismissSyncOffer,
        handoff = incomingHandoff?.takeIf { !isPlayingHere && showChrome && sheetCollapsed },
        replayYear = replayOffer.takeIf { showChrome && sheetCollapsed },
        onOpenReplay = { year ->
            viewModel.retireReplayOffer()
            navController.navigateSettled(Routes.replay(year))
        },
        onDismissReplay = viewModel::dismissReplayOffer,
        onNeverShowReplay = viewModel::retireReplayOffer,
        agroDevices = agroDevices,
        isResuming = isResuming,
        sessionArtwork = sessionArtwork,
        onResume = agroViewModel::resume,
        onDismissHandoff = agroViewModel::dismiss,
        dockBottom = dockBottom
    )

    if (syncDetailsOpen) {
        SyncOfferSheet(
            tracks = syncOffer,
            isFetching = isFetchingSync,
            progress = fetchProgress,
            route = fetchProgress.route ?: offerRoute,
            onAccept = acceptSyncWithLocalNetwork,
            onDismiss = viewModel::closeSyncDetails
        )
    }

    activeJam?.takeIf { showChrome && sheetCollapsed }?.let { jam ->
        JamBar(
            jam = jam,
            onOpenJam = { navController.navigate(Routes.JAM) },
            onLeave = jamViewModel::leave,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = dockBottom)
        )
    }

    listenAlongSession?.takeIf { showChrome && sheetCollapsed }?.let { session ->
        ListenAlongBar(
            session = session,
            onLeave = socialViewModel::stopListenAlong,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = dockBottom)
        )
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = dockBottom + 8.dp)
    )
}
