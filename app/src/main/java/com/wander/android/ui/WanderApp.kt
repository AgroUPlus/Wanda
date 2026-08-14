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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wander.android.core.permissions.rememberPermissionGate
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.ui.components.player.MiniPlayerGap
import com.wander.android.ui.components.player.MiniPlayerHeight
import com.wander.android.ui.components.player.PlayerSheet
import com.wander.android.ui.components.player.PlayerSheetContent
import com.wander.android.ui.components.player.rememberPlayerSheetState
import com.wander.android.ui.navigation.Routes
import com.wander.android.ui.navigation.TopLevelDestination
import com.wander.android.ui.navigation.WanderNavigationBar
import com.wander.android.ui.navigation.wanderNavGraph
import kotlinx.coroutines.launch
import java.net.URLEncoder

/**
 * The app shell: a bottom bar, the nav graph, and a player sheet overlaying both.
 */
@Composable
fun WanderApp(
    playerConnection: PlayerConnection,
    viewModel: WanderAppViewModel = hiltViewModel()
) {
    val setupDone by viewModel.hasCompletedSetup.collectAsStateWithLifecycle()

    rememberPermissionGate(
        onAudioGranted = viewModel::onAudioPermissionGranted,
        requestOnStart = setupDone
    )

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val playback by playerConnection.state.collectAsStateWithLifecycle()
    val showChrome = remember(currentRoute) { currentRoute?.substringBefore("?") in Routes.topLevel }
    // Derived, not read straight off `playback`: PlaybackState carries isBuffering and durationMs,
    // which flip constantly while streaming, and every one of those recomposed this composable —
    // which reallocates contentPadding and so rebuilds the whole nav graph (see below).
    val hasTrack by remember { derivedStateOf { playback.currentTrack != null } }

    val scope = rememberCoroutineScope()
    val sheetState = rememberPlayerSheetState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(playerConnection) {
        playerConnection.errors.collect { message ->
            snackbarHostState.showSnackbar(message, withDismissAction = true)
        }
    }

    // Measured, not assumed: ShortNavigationBar is shorter than the classic bar and its height
    // varies with the system gesture inset, so a constant left the sheet misaligned against it.
    var navBarHeight by remember { mutableStateOf(0.dp) }

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
            val extraBottom = if (hasTrack && showChrome) MiniPlayerHeight + MiniPlayerGap else 0.dp
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
        ) { progress, expandedHeight ->
            PlayerSheetContent(
                progress = progress,
                expandedHeight = expandedHeight,
                playback = playback,
                playerConnection = playerConnection,
                onExpand = { scope.launch { sheetState.expand() } },
                onCollapse = { scope.launch { sheetState.collapse() } },
                onOpenQueue = { navController.navigate(Routes.QUEUE) },
                onNavigateToSearch = { query ->
                    scope.launch { sheetState.collapse() }
                    val encoded = URLEncoder.encode(query, "UTF-8")
                    navController.navigate("${TopLevelDestination.SEARCH.route}?query=$encoded") {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
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

/** Tab switching keeps each tab's own back stack and scroll position. */
private fun androidx.navigation.NavHostController.switchTab(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
