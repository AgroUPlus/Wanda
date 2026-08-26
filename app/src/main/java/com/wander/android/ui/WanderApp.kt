package com.wander.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wander.android.core.permissions.rememberPermissionGate
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.ui.agro.AgroSessionViewModel
import com.wander.android.ui.components.LocalOfflinePlayback
import com.wander.android.ui.components.player.MiniPlayerGap
import com.wander.android.ui.components.player.MiniPlayerHeight
import com.wander.android.ui.components.player.MiniPlayerShadowInset
import com.wander.android.ui.components.JamBar
import com.wander.android.ui.components.JamBarHeight
import com.wander.android.ui.components.ListenAlongBar
import com.wander.android.ui.components.ListenAlongBarHeight
import com.wander.android.ui.components.UpdateAvailableDialog
import com.wander.android.ui.screens.social.JamViewModel
import com.wander.android.ui.screens.social.SocialViewModel
import com.wander.android.ui.components.player.PlayerSheet
import com.wander.android.ui.components.player.PlayerSheetContent
import com.wander.android.ui.components.player.PlayerSheetValue
import com.wander.android.ui.components.player.rememberPlayerSheetState
import com.wander.android.ui.navigation.Routes
import com.wander.android.ui.navigation.TopLevelDestination
import com.wander.android.ui.navigation.WanderNavigationBar
import com.wander.android.ui.navigation.wanderNavGraph
import kotlinx.coroutines.launch

/**
 * The app shell: a bottom bar, the nav graph, and a player sheet overlaying both.
 */
@Composable
fun WanderApp(
    playerConnection: PlayerConnection,
    viewModel: WanderAppViewModel = hiltViewModel()
) {
    val setupDone by viewModel.hasCompletedSetup.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.checkForUpdateOnLaunch() }
    val launchUpdate by viewModel.launchUpdateAvailable.collectAsStateWithLifecycle()
    launchUpdate?.let { update ->
        UpdateAvailableDialog(update = update, onDismiss = viewModel::dismissLaunchUpdate)
    }

    rememberPermissionGate(
        onAudioGranted = viewModel::onAudioPermissionGranted,
        requestOnStart = setupDone
    )

    val navController = rememberNavController()

    // Routes asked for from outside the composition — a tapped notification. Consumed once
    // navigated, so bringing the app forward later does not send the user back to the inbox.
    LaunchedEffect(navController) {
        viewModel.deepLinkRoutes.collect { route ->
            navController.navigate(route)
            viewModel.consumeDeepLink()
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val playbackState = playerConnection.state.collectAsStateWithLifecycle()
    val playback = playbackState.value
    val showChrome = remember(currentRoute) {
        currentRoute?.substringBefore("?") in Routes.withChrome
    }
    // Derived, not read straight off `playback`: PlaybackState carries isBuffering and durationMs,
    // which flip constantly while streaming, and every one of those recomposed this composable —
    // which reallocates contentPadding and so rebuilds the whole nav graph (see below).
    val hasTrack by remember { derivedStateOf { playbackState.value.currentTrack != null } }

    /** Whether sound is actually coming out here, which is what decides the resume offer. */
    val isPlayingHere by remember { derivedStateOf { playbackState.value.isPlaying } }

    val scope = rememberCoroutineScope()
    val sheetState = rememberPlayerSheetState()
    val socialViewModel: SocialViewModel = hiltViewModel()
    val listenAlongSession = socialViewModel.state.collectAsStateWithLifecycle().value.session

    val snackbarHostState = remember { SnackbarHostState() }
    val jamViewModel: JamViewModel = hiltViewModel()
    val jamState by jamViewModel.state.collectAsStateWithLifecycle()
    val activeJam = jamState.jam
    AppEvents(
        viewModel = viewModel,
        playerConnection = playerConnection,
        snackbarHostState = snackbarHostState
    )

    // Agro live session updates, held only while the app is on screen — see AgroSessionRepository.
    val agroViewModel: AgroSessionViewModel = hiltViewModel()
    val incomingHandoff by agroViewModel.incomingHandoff.collectAsStateWithLifecycle()
    val isResuming by agroViewModel.isResuming.collectAsStateWithLifecycle()
    val agroDevices by agroViewModel.devices.collectAsStateWithLifecycle()
    val agroError by agroViewModel.error.collectAsStateWithLifecycle()
    val sessionArtwork by agroViewModel.sessionArtwork.collectAsStateWithLifecycle()
    val syncOffer by viewModel.syncOffer.collectAsStateWithLifecycle()
    val isFetchingSync by viewModel.isFetchingSync.collectAsStateWithLifecycle()

    LifecycleStartEffect(agroViewModel) {
        // Coming back to a silent Wanda is exactly when "that other device has something going —
        // pick it up?" is worth saying again, so a dismissal from an earlier visit is cleared.
        if (!playerConnection.state.value.isPlaying) agroViewModel.allowReoffer()
        // Cheap and metadata-only, so asking on every foreground costs nothing.
        viewModel.refreshSyncOffer()
        // Same trip: the share domain the fleet agreed on, if the server publishes one.
        viewModel.refreshShareSettings()
        val job = scope.launch {
            agroViewModel.observeLiveUpdates(onLibraryChanged = viewModel::refreshSyncOffer)
        }
        onStopOrDispose { job.cancel() }
    }

    LaunchedEffect(agroError) {
        val message = agroError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message, withDismissAction = true)
        agroViewModel.clearError()
    }

    // Measured, not assumed: ShortNavigationBar is shorter than the classic bar and its height
    // varies with the system gesture inset, so a constant left the sheet misaligned against it.
    var navBarHeight by remember { mutableStateOf(0.dp) }

    val offlinePlayback by viewModel.offlinePlayback.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalOfflinePlayback provides offlinePlayback) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (showChrome) {
                        WanderNavigationBar(
                            currentRoute = currentRoute?.substringBefore("?"),
                            onNavigate = { destination -> navController.switchTab(destination) },
                            onHeightChanged = { navBarHeight = it },
                            modifier = Modifier.graphicsLayer {
                                val p = sheetState.progress
                                translationY = p * size.height
                                // Eased on the same curve as the sheet, so the bar leaves with it
                                // rather than snapping out in the first fraction of the drag.
                                alpha = 1f - (p * p * (3f - 2f * p))
                            }
                        )
                    }
                }
            ) { padding ->
                // Remembered because `NavHost` keys its `remember(builder)` on the graph-building
                // lambda, which captures this. A fresh PaddingValues on every recomposition meant the
                // lambda was a new instance every time and all seven destinations were re-created.
                // The shadow inset is reserved on top of the strip itself: the elevation is drawn
                // outside the strip's clip box, so content that merely cleared the strip still sat
                // under it.
                // Whatever is actually anchored at the bottom, so content scrolls clear of it.
                // The listen-along bar replaces the mini player rather than stacking on it, so
                // exactly one of the two is reserved for — but it *is* reserved for: an overlay
                // with no matching inset silently sits on top of the last row of every list.
                // The bar sits above the mini player rather than replacing it: you should still be
                // able to open the player and see what you are hearing, so both are on screen and
                // both are reserved for.
                val extraBottom = when {
                    !showChrome -> 0.dp
                    activeJam != null ->
                        MiniPlayerHeight + JamBarHeight + MiniPlayerGap + MiniPlayerShadowInset
                    hasTrack && listenAlongSession != null ->
                        MiniPlayerHeight + ListenAlongBarHeight + MiniPlayerGap + MiniPlayerShadowInset
                    hasTrack -> MiniPlayerHeight + MiniPlayerGap + MiniPlayerShadowInset
                    listenAlongSession != null -> ListenAlongBarHeight + MiniPlayerGap
                    else -> 0.dp
                }
                val direction = LocalLayoutDirection.current
                val contentPadding = remember(padding, extraBottom, direction) {
                    padding.plusBottom(extraBottom, direction)
                }
                NavHost(
                    navController = navController,
                    startDestination = if (setupDone) TopLevelDestination.HOME.route
                    else Routes.WELCOME,
                    modifier = Modifier.fillMaxSize()
                ) {
                    wanderNavGraph(
                        navController = navController,
                        playerConnection = playerConnection,
                        contentPadding = contentPadding
                    )
                }
            }

            PlayerSheet(
                sheetState = sheetState,
                bottomInset = if (showChrome) navBarHeight else 0.dp,
                isVisible = hasTrack && showChrome
            ) { progress, rawProgress, expandedHeight ->
                PlayerSheetContent(
                    progress = progress,
                    rawProgress = rawProgress,
                    expandedHeight = expandedHeight,
                    playback = playback,
                    playerConnection = playerConnection,
                    onExpand = { scope.launch { sheetState.expand() } },
                    onCollapse = { scope.launch { sheetState.collapse() } },
                    onOpenQueue = { navController.navigate(Routes.QUEUE) },
                    // The sheet collapses first: the destination sits underneath it, and navigating
                    // while the player is still expanded left the user staring at the player.
                    onOpenArtist = { artist ->
                        scope.launch { sheetState.collapse() }
                        navController.navigate(Routes.artist(artist))
                    },
                    onOpenAlbum = { albumId ->
                        scope.launch { sheetState.collapse() }
                        navController.navigate(Routes.album(albumId))
                    },
                    onOpenJam = {
                        scope.launch { sheetState.collapse() }
                        navController.navigate(Routes.JAM)
                    }
                )
            }

            // Both bottom-anchored cards belong to the browsing surface, not to the player: floating
            // them over a full-screen Now Playing reads as a stray dialog. `targetValue`, not
            // `progress`, so this costs one recomposition per gesture rather than one per frame.
            val sheetCollapsed = sheetState.targetValue == PlayerSheetValue.COLLAPSED

            // Offered whenever this device is idle — not merely when it has never played anything.
            // The gate used to be "no track loaded", and a track stays loaded after it finishes, so
            // the card appeared exactly once per launch and never came back.
            BottomOffers(
                syncOffer = if (showChrome && sheetCollapsed) syncOffer else emptyList(),
                isFetchingSync = isFetchingSync,
                onAcceptSync = viewModel::acceptSyncOffer,
                onDismissSync = viewModel::dismissSyncOffer,
                handoff = incomingHandoff?.takeIf { !isPlayingHere && showChrome && sheetCollapsed },
                agroDevices = agroDevices,
                isResuming = isResuming,
                sessionArtwork = sessionArtwork,
                onResume = agroViewModel::resume,
                onDismissHandoff = agroViewModel::dismiss,
                navBarHeight = navBarHeight,
                hasTrack = hasTrack
            )

            // Follows the user across every screen, because the session does. It is the only place
            // the app says which source the track was matched from, or that it could not find one.
            // Takes the mini-player's place rather than sitting above it. While you are following
            // someone, the transport is not yours to touch — a pause here would fight the next
            // frame from the host — so the controls are removed instead of being left to lose.
            activeJam?.takeIf { showChrome && sheetCollapsed }?.let { jam ->
                JamBar(
                    jam = jam,
                    onOpenJam = { navController.navigate(Routes.JAM) },
                    onLeave = jamViewModel::leave,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = navBarHeight + (if (hasTrack) MiniPlayerHeight + MiniPlayerGap else 0.dp))
                )
            }

            listenAlongSession?.takeIf { showChrome && sheetCollapsed }?.let { session ->
                ListenAlongBar(
                    session = session,
                    onLeave = socialViewModel::stopListenAlong,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = navBarHeight + MiniPlayerHeight + MiniPlayerGap)
                )
            }

            // Last in the Box, so errors sit above the player instead of behind it — previously the
            // Scaffold's own host was painted under the sheet and hidden entirely when expanded.
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = if (hasTrack && showChrome) {
                            navBarHeight + MiniPlayerHeight + MiniPlayerGap + 8.dp
                        } else {
                            navBarHeight + 8.dp
                        }
                    )
            )
        }
    }
}

private fun PaddingValues.plusBottom(
    extra: androidx.compose.ui.unit.Dp,
    direction: LayoutDirection
): PaddingValues {
    return PaddingValues(
        start = calculateStartPadding(direction),
        top = calculateTopPadding(),
        end = calculateEndPadding(direction),
        bottom = calculateBottomPadding() + extra
    )
}

/**
 * Tab switching keeps each tab's own state — and lands on the tab itself.
 *
 * `restoreState` brings back the whole saved stack, detail pages included, so tapping "Search"
 * after opening an artist from it put you straight back on that artist page: the tab looked stuck.
 * The stack is still restored, for the screen state it carries — a typed query, a scroll position —
 * and then popped down to the tab's own destination, which is what the tap asked for.
 */
private fun androidx.navigation.NavHostController.switchTab(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    // By id, not route: Search's destination is registered as `search?query={query}`, which no
    // plain "search" string will match.
    graph.findNode(destination.route)?.id?.let { popBackStack(it, inclusive = false) }
}
