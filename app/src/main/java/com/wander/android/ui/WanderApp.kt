package com.wander.android.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.wander.android.ui.components.JamBarHeight
import com.wander.android.ui.components.ListenAlongBarHeight
import com.wander.android.ui.components.LocalBackBlurEnabled
import com.wander.android.ui.components.LocalBackdropBlur
import com.wander.android.ui.components.LocalOfflinePlayback
import com.wander.android.ui.components.backdropBlur
import com.wander.android.ui.components.dockSpec
import com.wander.android.ui.components.player.MiniPlayerShadowInset
import com.wander.android.ui.components.player.PlayerSheet
import com.wander.android.ui.components.player.SheetDock
import com.wander.android.ui.components.player.PlayerSheetContent
import com.wander.android.ui.components.player.rememberPlayerSheetState
import com.wander.android.ui.navigation.Routes
import com.wander.android.ui.navigation.TopLevelDestination
import com.wander.android.ui.navigation.wanderNavGraph
import com.wander.android.ui.screens.social.JamViewModel
import com.wander.android.ui.screens.social.SocialViewModel
import com.wander.android.ui.theme.CoverTintedTheme
import com.wander.android.ui.theme.rememberCoverSeedColor
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
    WanderAppLaunchGate(viewModel = viewModel, setupDone = setupDone)

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

    // The query is held outside any one screen so it survives the trip into an artist or album and
    // back. Reaching another tab is the end of that search, and without this the field was still
    // full, and Library still showing results, the next time the tab opened.
    LaunchedEffect(currentRoute) {
        if (currentRoute in Routes.topLevel && currentRoute != TopLevelDestination.LIBRARY.route) {
            viewModel.setSearchQuery("")
        }
    }

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

    // Agro live session updates, held only while the app is on screen.
    val agroViewModel: AgroSessionViewModel = hiltViewModel()

    val motionScheme = MaterialTheme.motionScheme
    val dockMetrics = calculateDockMetrics(
        showDockRow = showDockRow,
        hasTrack = hasTrack,
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

    val offlinePlayback by viewModel.offlinePlayback.collectAsStateWithLifecycle()
    val isCoverArtThemeEnabled by viewModel.isCoverArtThemeEnabled.collectAsStateWithLifecycle()
    val isAmoledBlack by viewModel.isAmoledBlack.collectAsStateWithLifecycle()
    val isBackBlurEnabled by viewModel.isBackBlurEnabled.collectAsStateWithLifecycle()
    val backdropBlur = remember { BackdropBlurController() }
    val shellCoverSeed = if (isCoverArtThemeEnabled) {
        rememberCoverSeedColor(playback.currentTrack?.artworkUrl)
    } else {
        null
    }

    CoverTintedTheme(
        seedColor = shellCoverSeed,
        base = MaterialTheme.colorScheme,
        dark = isSystemInDarkTheme(),
        amoled = isAmoledBlack
    ) {
        CompositionLocalProvider(
            LocalOfflinePlayback provides offlinePlayback,
            LocalBackBlurEnabled provides isBackBlurEnabled,
            LocalBackdropBlur provides backdropBlur
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .backdropBlur(isBackBlurEnabled) { backdropBlur.amount() }
            ) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
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
                        !showChrome -> dockBottom + MiniPlayerShadowInset
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

                val dockState = rememberWanderDockState(
                    navController = navController,
                    viewModel = viewModel,
                    motionScheme = motionScheme,
                    visibility = DockVisibility(currentRoute, showDockRow, hasTrack),
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
                    isVisible = hasTrack && showChrome,
                    coverSeed = shellCoverSeed,
                    seekProgress = {
                        com.wander.android.core.playback.progressOf(
                            playbackPosition.value.positionMs,
                            playback.durationMs
                        )
                    }
                ) { progress, rawProgress, expandedHeight ->
                    PlayerSheetContent(
                        fingerprintStatus = playingFingerprintStatus,
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
                    viewModels = ShellViewModels(viewModel, agroViewModel, socialViewModel, jamViewModel),
                    navController = navController,
                    sheetState = sheetState,
                    snackbarHostState = snackbarHostState,
                    shell = ShellOverlayState(
                        currentRoute = currentRoute,
                        showChrome = showChrome,
                        isPlayingHere = isPlayingHere,
                        dockBottom = dockBottom,
                        activeJam = activeJam,
                        listenAlongSession = listenAlongSession
                    )
                )
            }
        }
    }
}
