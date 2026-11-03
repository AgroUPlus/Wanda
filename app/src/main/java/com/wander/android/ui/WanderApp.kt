package com.wander.android.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.ui.agro.AgroSessionViewModel
import com.wander.android.ui.components.BackdropBlurController
import com.wander.android.ui.components.attachedBarOf
import com.wander.android.ui.components.height
import com.wander.android.ui.components.LocalBackBlurEnabled
import com.wander.android.ui.components.LocalBackdropBlur
import com.wander.android.ui.components.LocalDownloadMarks
import com.wander.android.ui.components.LocalOfflinePlayback
import com.wander.android.ui.components.SharedArtworkLayout
import com.wander.android.ui.components.TrackDownloadViewModel
import com.wander.android.ui.components.backdropBlur
import com.wander.android.ui.components.dockSpec
import com.wander.android.ui.components.player.MiniPlayerShadowInset
import com.wander.android.ui.components.player.PlayerSheet
import com.wander.android.ui.components.player.SheetDock
import com.wander.android.ui.components.player.PlayerSheetContent
import com.wander.android.ui.components.player.rememberPlayerSheetState
import com.wander.android.ui.navigation.LocalTabReselection
import com.wander.android.ui.navigation.Routes
import com.wander.android.ui.navigation.TabReselection
import com.wander.android.ui.navigation.TopLevelDestination
import com.wander.android.ui.navigation.wanderNavGraph
import com.wander.android.ui.screens.social.JamViewModel
import com.wander.android.ui.screens.social.ListenersViewModel
import com.wander.android.ui.screens.social.SocialViewModel
import com.wander.android.ui.theme.CoverTintedTheme
import com.wander.android.ui.theme.rememberCoverSeedColor
import kotlinx.coroutines.launch
import com.wander.android.ui.theme.LocalDarkTheme

/**
 * The app shell: a bottom bar, the nav graph, and a player sheet overlaying both.
 */
@Composable
fun WanderApp(
    playerConnection: PlayerConnection,
    viewModel: WanderAppViewModel = hiltViewModel()
) {
    val setupDone by viewModel.hasCompletedSetup.collectAsStateWithLifecycle()
    WanderAppLaunchGate(viewModel = viewModel, setupDone = setupDone)

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    ShellRouteEffects(navController, viewModel, currentRoute)

    val playbackState = playerConnection.state.collectAsStateWithLifecycle()
    val playback = playbackState.value
    // Not `by`: this ticks up to twice a second while playing, and the sheet's own background
    // reads it inside a `drawBehind` lambda rather than in composition — see `PlayerSheet`'s own
    // discipline on `sheetState.progress` for the same reason. A `by` here would recompose the
    // whole shell every tick just to feed one drifting gradient.
    val playbackPosition = com.wander.android.core.playback.rememberPlaybackPosition(playerConnection)
    val showChrome = remember(currentRoute) { Routes.showsChrome(currentRoute) }
    val isImmersivePlayer by viewModel.isImmersivePlayer.collectAsStateWithLifecycle()
    val showDockRow = remember(currentRoute) { Routes.showsDock(currentRoute) }
    val hasTrack by remember { derivedStateOf { playbackState.value.currentTrack != null } }
    val isPlayingHere by remember { derivedStateOf { playbackState.value.isPlaying } }

    val scope = rememberCoroutineScope()
    val sheetState = rememberPlayerSheetState()
    val socialViewModel: SocialViewModel = hiltViewModel()
    val listenAlongSession = socialViewModel.state.collectAsStateWithLifecycle().value.session

    val snackbarHostState = remember { SnackbarHostState() }
    val dismissal = rememberMiniPlayerDismissal(playerConnection, playbackState, snackbarHostState)
    val showsMiniPlayer = hasTrack && !dismissal.isDismissed
    val jamViewModel: JamViewModel = hiltViewModel()
    val jamState by jamViewModel.state.collectAsStateWithLifecycle()
    val listenersViewModel: ListenersViewModel = hiltViewModel()
    val listeners by listenersViewModel.listeners.collectAsStateWithLifecycle()
    val attachedBar = attachedBarOf(jamState.jam, listenAlongSession, listeners)
    AppEvents(
        viewModel = viewModel,
        playerConnection = playerConnection,
        snackbarHostState = snackbarHostState
    )

    // Agro live session updates, held only while the app is on screen.
    val agroViewModel: AgroSessionViewModel = hiltViewModel()

    val motionScheme = MaterialTheme.motionScheme
    val dockMetrics = calculateDockMetrics(
        showDockRow = showDockRow,
        hasTrack = showsMiniPlayer,
        showChrome = showChrome
    )
    val dockInset = dockMetrics.dockInset
    val sheetBottomInset = dockMetrics.sheetBottomInset
    val dockedPlayerHeight = dockMetrics.dockedPlayerHeight
    // Animated, not read straight off `dockMetrics`: the dock row and mini player both fade/slide
    // in over a few hundred ms when they appear, but this figure fed straight into scrolling
    // content's own bottom padding, which Compose applies to layout in the same frame it changes.
    // The result was the content above snapping to its new inset instantly while the chrome below
    // it was still animating in underneath — the list visibly jumping (the "top part teleports"),
    // out of step with the thing its padding was supposedly making room for.
    val dockBottom by animateDpAsState(
        targetValue = dockMetrics.dockBottom,
        animationSpec = dockSpec(),
        label = "dockBottomInset"
    )
    // A wide window trades the dock's buttons for a rail at the start edge; see `rememberUseNavRail`.
    val useRail = rememberUseNavRail() && showChrome
    var railWidth by remember { mutableStateOf(0.dp) }
    val railInset = if (useRail) railWidth else 0.dp

    val offlinePlayback by viewModel.offlinePlayback.collectAsStateWithLifecycle()
    val downloadMarks by hiltViewModel<TrackDownloadViewModel>().marks.collectAsStateWithLifecycle()
    val isCoverArtThemeEnabled by viewModel.isCoverArtThemeEnabled.collectAsStateWithLifecycle()
    val isAmoledBlack by viewModel.isAmoledBlack.collectAsStateWithLifecycle()
    val isBackBlurEnabled by viewModel.isBackBlurEnabled.collectAsStateWithLifecycle()
    val backdropBlur = remember { BackdropBlurController() }
    val tabReselection = remember { TabReselection() }
    TrackTabOwner(navController, tabReselection)
    val shellCoverSeed = if (isCoverArtThemeEnabled) {
        rememberCoverSeedColor(playback.currentTrack?.artworkUrl)
    } else {
        null
    }

    CoverTintedTheme(
        seedColor = shellCoverSeed,
        base = MaterialTheme.colorScheme,
        dark = LocalDarkTheme.current,
        amoled = isAmoledBlack
    ) {
        CompositionLocalProvider(
            LocalOfflinePlayback provides offlinePlayback,
            LocalDownloadMarks provides downloadMarks,
            LocalBackBlurEnabled provides isBackBlurEnabled,
            LocalBackdropBlur provides backdropBlur,
            LocalTabReselection provides tabReselection
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .backdropBlur(isBackBlurEnabled) { backdropBlur.amount() }
            ) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = shellContentInsets(),
                    modifier = Modifier.backdropBlur(isBackBlurEnabled) {
                        val back = sheetState.predictiveBackProgress
                        val covered = sheetState.progress
                        when {
                            covered >= 1f && back == 0f -> 0f
                            else -> covered * (1f - back)
                        }
                    }
                ) { padding ->
                    // `!showChrome` used to snap this straight to `0.dp` instead of falling through
                    // to `dockBottom` — a hard cut the instant a route like History hid the chrome,
                    // while the dock row and mini player themselves kept sliding away underneath on
                    // their own spring for another few frames. The content above them jumped to fill
                    // the space immediately; the chrome leaving it was still visibly catching up.
                    // `dockBottom` already resolves to the right (small, system-inset-only) target
                    // for a chromeless route on its own — see `calculateDockMetrics`'s `else -> 0.dp`
                    // branch — and it is already animated, so routing through it here instead keeps
                    // this on the same spring rather than a second, un-animated one.
                    val extraBottom = when {
                        !showChrome || attachedBar == null -> dockBottom + MiniPlayerShadowInset
                        else -> dockBottom + attachedBar.height + MiniPlayerShadowInset
                    }
                    val direction = LocalLayoutDirection.current
                    val contentPadding = remember(padding, extraBottom, direction, railInset) {
                        padding.plusBottom(extraBottom, direction).withMinStart(railInset, direction)
                    }
                    SharedArtworkLayout(Modifier.fillMaxSize()) {
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
                }

                if (useRail) {
                    ShellNavRail(navController, currentRoute, bottomClearance = dockBottom) { railWidth = it }
                }

                val dockState = rememberWanderDockState(
                    navController = navController,
                    viewModel = viewModel,
                    motionScheme = motionScheme,
                    visibility = DockVisibility(currentRoute, showDockRow, showsMiniPlayer, railInset),
                    sheetState = sheetState,
                    dockInset = dockInset
                )

                dockState.standaloneDock(this)

                PlayerSheet(
                    sheetState = sheetState,
                    dock = SheetDock(
                        bottomInset = if (showChrome) sheetBottomInset else 0.dp,
                        dockedHeight = dockedPlayerHeight,
                        pairedWithDockRow = showDockRow
                    ),
                    isVisible = showsMiniPlayer && showChrome,
                    coverSeed = shellCoverSeed,
                    dockedDismiss = dismissal.gesture,
                    seekProgress = {
                        com.wander.android.core.playback.progressOf(
                            playbackPosition.value.positionMs,
                            playback.durationMs
                        )
                    }
                ) { progress, rawProgress, expandedHeight ->
                    PlayerSheetContent(
                        progress = progress,
                        rawProgress = rawProgress,
                        expandedHeight = expandedHeight,
                        playback = playback,
                        playerConnection = playerConnection,
                        onExpand = { scope.launch { sheetState.expand() } },
                        onMinimize = { scope.launch { sheetState.collapse() } },
                        onOpenQueue = {
                            scope.launch { sheetState.collapse() }
                            navController.navigate(Routes.QUEUE)
                        },
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
                        immersivePlayer = isImmersivePlayer,
                        coverCarousel = true
                    )
                }

                WanderAppOverlays(
                    viewModels = ShellViewModels(viewModel, agroViewModel, socialViewModel, jamViewModel, listenersViewModel),
                    navController = navController,
                    sheetState = sheetState,
                    snackbarHostState = snackbarHostState,
                    shell = ShellOverlayState(
                        currentRoute = currentRoute,
                        showChrome = showChrome,
                        isPlayingHere = isPlayingHere,
                        dockBottom = dockBottom,
                        attachedBar = attachedBar
                    )
                )
            }
        }
    }
}
