package com.wander.android.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.wander.android.core.permissions.hasPermission
import com.wander.android.ui.components.listen.ListenSheet
import com.wander.android.ui.components.player.MiniPlayerGap
import com.wander.android.ui.navigation.TopLevelDestination
import com.wander.android.ui.navigation.WanderDock
import com.wander.android.ui.navigation.WanderDockRow

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.player.MiniPlayerHeight
import com.wander.android.ui.components.player.MiniStripHeight
import com.wander.android.ui.navigation.DockRowHeight

internal class WanderDockState(
    val dockRow: @Composable () -> Unit,
    val standaloneDock: @Composable BoxScope.() -> Unit
)

internal data class WanderDockMetrics(
    val dockInset: Dp,
    val dockedPlayerHeight: Dp,
    val dockBottom: Dp
)

@Composable
internal fun calculateDockMetrics(
    showDockRow: Boolean,
    hasTrack: Boolean,
    showChrome: Boolean
): WanderDockMetrics {
    val systemBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val imeBottomInset = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val dockInset = if (showDockRow) maxOf(systemBottomInset, imeBottomInset) else systemBottomInset
    val dockedPlayerHeight = if (showDockRow) MiniPlayerHeight else MiniStripHeight
    val dockBottom = systemBottomInset + MiniPlayerGap + when {
        hasTrack && showChrome -> dockedPlayerHeight
        showDockRow -> DockRowHeight
        else -> 0.dp
    }
    return WanderDockMetrics(dockInset, dockedPlayerHeight, dockBottom)
}

/**
 * Creates and remembers the dock state, handling search query updates, microphone recognition
 * launches, and rendering both the embedded dock row and standalone dock card.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberWanderDockState(
    navController: NavHostController,
    viewModel: WanderAppViewModel,
    motionScheme: MotionScheme,
    currentRoute: String?,
    showDockRow: Boolean,
    hasTrack: Boolean,
    dockInset: Dp
): WanderDockState {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val dockRoute = currentRoute?.substringBefore("?")
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

    val standaloneDock: @Composable BoxScope.() -> Unit = {
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
    }

    return remember(dockRow, standaloneDock) {
        WanderDockState(dockRow, standaloneDock)
    }
}

internal fun PaddingValues.plusBottom(
    extra: Dp,
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
 * Checks for updates on launch and manages initial audio permission gates.
 */
@Composable
internal fun WanderAppLaunchGate(
    viewModel: WanderAppViewModel,
    setupDone: Boolean
) {
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.checkForUpdateOnLaunch() }
    val launchUpdate by viewModel.launchUpdateAvailable.collectAsStateWithLifecycle()
    launchUpdate?.let { update ->
        com.wander.android.ui.components.UpdateAvailableDialog(
            update = update,
            onDismiss = viewModel::dismissLaunchUpdate
        )
    }

    com.wander.android.core.permissions.rememberPermissionGate(
        onAudioGranted = viewModel::onAudioPermissionGranted,
        requestOnStart = setupDone
    )
}

/**
 * Tab switching keeps each tab's own state — and lands on the tab itself.
 */
internal fun NavHostController.switchTab(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    graph.findNode(destination.route)?.id?.let { popBackStack(it, inclusive = false) }
}
