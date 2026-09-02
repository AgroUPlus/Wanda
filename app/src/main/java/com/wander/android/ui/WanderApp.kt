package com.wander.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
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
import com.wander.android.ui.components.JamBar
import com.wander.android.ui.components.JamBarHeight
import com.wander.android.ui.components.ListenAlongBar
import com.wander.android.ui.components.ListenAlongBarHeight
import com.wander.android.ui.components.LocalOfflinePlayback
import com.wander.android.ui.components.UpdateAvailableDialog
import com.wander.android.ui.components.player.MiniPlayerGap
import com.wander.android.ui.components.player.MiniPlayerHeight
import com.wander.android.ui.components.player.MiniPlayerShadowInset
import com.wander.android.ui.components.player.MiniStripHeight
import com.wander.android.ui.components.player.PlayerSheet
import com.wander.android.ui.components.player.PlayerSheetContent
import com.wander.android.ui.components.player.PlayerSheetValue
import com.wander.android.ui.components.player.rememberPlayerSheetState
import com.wander.android.ui.navigation.Routes
import com.wander.android.ui.navigation.TopLevelDestination
import com.wander.android.ui.navigation.DockRowHeight
import com.wander.android.core.permissions.hasPermission
import com.wander.android.core.permissions.rememberLocalNetworkGate
import com.wander.android.ui.components.listen.ListenSheet
import com.wander.android.ui.components.SyncOfferSheet
import com.wander.android.ui.navigation.WanderDock
import com.wander.android.ui.navigation.WanderDockRow
import com.wander.android.ui.navigation.wanderNavGraph
import com.wander.android.ui.screens.home.InstantRadioFab
import com.wander.android.ui.screens.social.JamViewModel
import com.wander.android.ui.screens.social.SocialViewModel
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
    val showChrome = remember(currentRoute) { Routes.showsChrome(currentRoute) }
    // Two tiers, not one. An artist or album page keeps the player — the controls for what is
    // playing must stay reachable from the page you opened out of it — but not the dock row: the
    // search field belongs to the library you searched from, and a page reached from there is not
    // somewhere you search. So those screens get the strip alone, and it opens to the full player
    // the same way.
    val showDockRow = remember(currentRoute) { Routes.showsDock(currentRoute) }
    // Derived, not read straight off `playback`: PlaybackState carries isBuffering and durationMs,
    // which flip constantly while streaming, and every one of those recomposed this composable —
    // which reallocates contentPadding and so rebuilds the whole nav graph (see below).
    val hasTrack by remember { derivedStateOf { playbackState.value.currentTrack != null } }

    /** Whether sound is actually coming out here, which is what decides the resume offer. */
    val isPlayingHere by remember { derivedStateOf { playbackState.value.isPlaying } }

    val scope = rememberCoroutineScope()
    val sheetState = rememberPlayerSheetState()
    val playingFingerprintStatus by viewModel.playingFingerprintStatus.collectAsStateWithLifecycle()
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
    val syncCovers by viewModel.syncCovers.collectAsStateWithLifecycle()
    // Android 16 put local-network traffic behind its own grant, and peer-to-peer sync is the only
    // thing in the app that needs it. Asked on the tap that needs it, not at launch.
    val acceptSyncWithLocalNetwork = rememberLocalNetworkGate(viewModel::acceptSyncOffer)
    val syncDetailsOpen by viewModel.syncDetailsOpen.collectAsStateWithLifecycle()
    val fetchProgress by viewModel.fetchProgress.collectAsStateWithLifecycle()
    val offerRoute by viewModel.offerRoute.collectAsStateWithLifecycle()
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

    // The navigation destinations moved *into* the dock (see `WanderDockRow`), so there is no
    // `bottomBar` left to measure — what has to be cleared at the bottom of the screen is the
    // system's own gesture area, and the dock floats above it.
    // Read once, here, and handed to the nav graph: its transition lambdas are not composable and
    // cannot reach the theme themselves. See `NavTransitions`.
    val motionScheme = MaterialTheme.motionScheme

    val systemBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // What the dock itself has to clear, which is not the same thing. The dock's search field is
    // the reason the keyboard is up, so the dock rides above it — the window is edge-to-edge and
    // never resizes, and without this the keyboard covered the field being typed into.
    //
    // The larger of the two, not their sum: the IME draws over the gesture area, so adding both
    // left a nav-bar-sized gap between the keyboard and the dock. `WindowInsets.ime` animates with
    // the keyboard, so the dock rides up with it rather than jumping when it lands.
    val imeBottomInset = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    // Only where there *is* a field to type into. Without the dock row the keyboard belongs to
    // something else on the screen, and riding the strip up on it would move the transport out
    // from under the thumb for no reason.
    val dockInset = if (showDockRow) maxOf(systemBottomInset, imeBottomInset) else systemBottomInset

    // What every bottom-anchored thing has to clear: the system inset, then whichever dock is
    // actually on screen. Derived once here rather than re-summed at each call site, which is how
    // the old `navBarHeight + if (hasTrack) ...` arithmetic drifted between them.
    /** How tall the docked player is here — with a dock row under the strip, or without one. */
    val dockedPlayerHeight = if (showDockRow) MiniPlayerHeight else MiniStripHeight

    val dockBottom = systemBottomInset + MiniPlayerGap + when {
        hasTrack && showChrome -> dockedPlayerHeight
        showDockRow -> DockRowHeight
        else -> 0.dp
    }

    val offlinePlayback by viewModel.offlinePlayback.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalOfflinePlayback provides offlinePlayback) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
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
                // `dockBottom` already covers the system inset and whichever dock is showing;
                // the Scaffold no longer contributes a bottom bar of its own. Only the extra bars
                // that stack on top of the dock are added here.
                val extraBottom = when {
                    !showChrome -> 0.dp
                    activeJam != null -> dockBottom + JamBarHeight + MiniPlayerShadowInset
                    listenAlongSession != null ->
                        dockBottom + ListenAlongBarHeight + MiniPlayerShadowInset
                    else -> dockBottom + MiniPlayerShadowInset
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
                        motion = motionScheme,
                        playerConnection = playerConnection,
                        contentPadding = contentPadding,
                        onCollapsePlayer = { scope.launch { sheetState.collapse() } }
                    )
                }
            }

            // One dock row, two possible hosts: the player sheet when something is loaded, and a
            // standalone card when nothing is. Defined once so the two can never drift apart.
            val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
            val dockRoute = currentRoute?.substringBefore("?")
            // "What is this?" — the microphone, matched against the user's own library. The
            // permission is asked for here, on the tap, rather than at startup with the others:
            // it is used for the few seconds the sheet is open, and a microphone prompt on first
            // launch of a music player is the kind of thing that gets an app uninstalled.
            val context = LocalContext.current
            var showListen by remember { mutableStateOf(false) }
            val micLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted -> showListen = granted }
            val onListen: () -> Unit = {
                if (context.hasPermission(Manifest.permission.RECORD_AUDIO)) {
                    showListen = true
                } else {
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            if (showListen) {
                ListenSheet(
                    onDismiss = { showListen = false },
                    onOpenTrack = { showListen = false }
                )
            }

            // Focusing the field is what opens the library, and typing is what turns it into
            // results — so `onQueryChange` navigates too, for the case where text arrives without
            // the field ever taking focus (a hardware keyboard, an autofill).
            val openLibrary = { navController.switchTab(TopLevelDestination.LIBRARY) }
            val onQueryChange: (String) -> Unit = { value ->
                if (value.isNotBlank() && dockRoute != TopLevelDestination.LIBRARY.route) {
                    openLibrary()
                }
                viewModel.setSearchQuery(value)
            }
            val dockRow: @Composable () -> Unit = {
                WanderDockRow(
                    currentRoute = dockRoute,
                    query = searchQuery,
                    onOpenLibrary = openLibrary,
                    onOpenFriends = { navController.switchTab(TopLevelDestination.FRIENDS) },
                    onQueryChange = onQueryChange,
                    onSearch = openLibrary,
                    onListen = onListen
                )
            }

            // The same arrival the dock row gets inside the player, for the case where there is
            // no player to put it in. `AnimatedVisibility` rather than an `if`: the card is the
            // only thing at the bottom of these screens, and cutting it left the screen visibly
            // empty for a frame on the way to an artist page.
            AnimatedVisibility(
                visible = showDockRow && !hasTrack,
                enter = fadeIn(motionScheme.defaultEffectsSpec()) +
                    slideInVertically(motionScheme.slowSpatialSpec()) { it / 2 } +
                    scaleIn(motionScheme.slowSpatialSpec(), initialScale = 0.92f),
                exit = fadeOut(motionScheme.fastEffectsSpec()) +
                    slideOutVertically(motionScheme.slowSpatialSpec()) { it / 2 } +
                    scaleOut(motionScheme.slowSpatialSpec(), targetScale = 0.92f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = dockInset + MiniPlayerGap)
            ) {
                WanderDock(
                    currentRoute = dockRoute,
                    query = searchQuery,
                    onOpenLibrary = openLibrary,
                    onOpenFriends = { navController.switchTab(TopLevelDestination.FRIENDS) },
                    onQueryChange = onQueryChange,
                    onSearch = openLibrary,
                    onListen = onListen
                )
            }

            PlayerSheet(
                sheetState = sheetState,
                bottomInset = if (showChrome) dockInset else 0.dp,
                isVisible = hasTrack && showChrome,
                dockedHeight = dockedPlayerHeight
            ) { progress, rawProgress, expandedHeight ->
                PlayerSheetContent(
                    fingerprintStatus = playingFingerprintStatus,
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
                    onOpenArtist = { artist, artistId ->
                        scope.launch { sheetState.collapse() }
                        navController.navigate(Routes.artist(artist, artistId))
                    },
                    onOpenAlbum = { albumId ->
                        scope.launch { sheetState.collapse() }
                        navController.navigate(Routes.album(albumId))
                    },
                    onOpenJam = {
                        scope.launch { sheetState.collapse() }
                        navController.navigate(Routes.JAM)
                    },
                    dockRow = dockRow,
                    showDockRow = showDockRow
                )
            }

            // Both bottom-anchored cards belong to the browsing surface, not to the player: floating
            // them over a full-screen Now Playing reads as a stray dialog. `targetValue`, not
            // `progress`, so this costs one recomposition per gesture rather than one per frame.
            val sheetCollapsed = sheetState.targetValue == PlayerSheetValue.COLLAPSED

            // Drawn here rather than by `HomeScreen`, and this is the whole reason: the player
            // sheet above is painted after the entire nav host, so a button a screen puts near the
            // bottom edge is covered by the docked strip no matter what elevation it claims. Here
            // it is genuinely above it, whether or not anything is playing.
            val isStartingRadio by viewModel.isStartingRadio.collectAsStateWithLifecycle()
            // `progress`, not `targetValue` like the cards above: the target only flips when the
            // gesture is released and the sheet decides where it is going, so the button hung
            // around for the whole drag and only left once the player had already arrived. This
            // leaves at the first pixel of the slide, which is when it is in the way.
            //
            // `derivedStateOf` keeps that cheap — the float changes every frame, the boolean it is
            // read through changes twice per gesture.
            val playerDocked by remember(sheetState) {
                derivedStateOf { sheetState.progress <= DockedEpsilon }
            }
            // Nothing here gates composition. Both conditions are passed *in*, because an `if`
            // would tear the button out before its exit transition could run — which is what made
            // it pop rather than leave.
            InstantRadioFab(
                isStarting = isStartingRadio,
                visible = playerDocked && currentRoute == TopLevelDestination.HOME.route,
                onClick = viewModel::startInstantRadio,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    // Clears whatever the shell has parked at the bottom: the navigation bar
                    // always, and the docked strip when there is a track. Both are measured
                    // rather than assumed, so the button sits the same distance clear of the
                    // strip as it does of the bar on its own.
                    .padding(start = 20.dp, bottom = dockBottom + RadioFabClearance)
            )

            // Offered whenever this device is idle — not merely when it has never played anything.
            // The gate used to be "no track loaded", and a track stays loaded after it finishes, so
            // the card appeared exactly once per launch and never came back.
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
                agroDevices = agroDevices,
                isResuming = isResuming,
                sessionArtwork = sessionArtwork,
                onResume = agroViewModel::resume,
                onDismissHandoff = agroViewModel::dismiss,
                dockBottom = dockBottom
            )

            // The full list behind the offer card. A sheet rather than a screen: it is a decision
            // about something transient, and backing out of it should leave the user exactly where
            // they were.
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

            // Last in the Box, so errors sit above the player instead of behind it — previously the
            // Scaffold's own host was painted under the sheet and hidden entirely when expanded.
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = dockBottom + 8.dp
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
 * `restoreState` brings back the whole saved stack, detail pages included, so opening the library
 * after reaching an artist from it put you straight back on that artist page: the field looked
 * stuck.
 * The stack is still restored, for the screen state it carries — a typed query, a scroll position —
 * and then popped down to the tab's own destination, which is what the tap asked for.
 */
private fun androidx.navigation.NavHostController.switchTab(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    graph.findNode(destination.route)?.id?.let { popBackStack(it, inclusive = false) }
}

/** The gap between the radio button and whatever is parked at the bottom edge. */
private val RadioFabClearance = 16.dp

/**
 * How far the player sheet may travel before it counts as opening.
 *
 * Not zero: the docked sheet settles on an animated float, so an exact comparison would flicker on
 * the last fraction of a spring that has effectively already stopped.
 */
private const val DockedEpsilon = 0.01f
