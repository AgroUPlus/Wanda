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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.wander.android.core.permissions.hasPermission
import com.wander.android.ui.components.dockSpec
import com.wander.android.ui.components.listen.ListenSheet
import com.wander.android.ui.components.player.MiniPlayerGap
import com.wander.android.ui.components.player.PlayerSheetState
import com.wander.android.ui.navigation.DockItem
import com.wander.android.ui.navigation.NavLayoutViewModel
import com.wander.android.ui.navigation.TopLevelDestination
import com.wander.android.ui.navigation.navigateSettled
import com.wander.android.ui.navigation.WanderDock

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.player.MiniStripHeight
import com.wander.android.ui.navigation.DockRowHeight

internal class WanderDockState(
    val standaloneDock: @Composable BoxScope.() -> Unit
)

internal data class WanderDockMetrics(
    /** Where the dock row itself rests — unchanged by whether the mini player is showing. */
    val dockInset: Dp,
    /**
     * Where the *sheet* rests while docked. Equal to [dockInset] when there is no dock row to
     * clear; otherwise [dockInset] plus the dock row's own height and the gap above it, since the
     * dock row is its own independent card now and the sheet has to float above it rather than
     * (as before, when the two were one fused block) simply resting at the same inset.
     */
    val sheetBottomInset: Dp,
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

    // The dock row is its own card now, always resting at [dockInset], so the sheet — floating
    // above it rather than fused to it — has to clear the dock row's own height plus the gap
    // between the two cards on top of that, or it would rest exactly where the dock row does.
    val dockRowClearance = if (showDockRow) DockRowHeight + MiniPlayerGap else 0.dp
    val sheetBottomInset = dockInset + dockRowClearance

    // Always just the mini strip: the sheet no longer reserves room for the dock row inside its
    // own docked height, because the dock row is no longer inside it — see `PlayerSheet.kt`.
    val dockedPlayerHeight = MiniStripHeight

    val dockBottom = systemBottomInset + MiniPlayerGap + when {
        hasTrack && showChrome -> dockRowClearance + dockedPlayerHeight
        showDockRow -> DockRowHeight
        else -> 0.dp
    }
    return WanderDockMetrics(dockInset, sheetBottomInset, dockedPlayerHeight, dockBottom)
}

/** What decides whether and how the dock shows: the current route, whether it has a dock row, and whether a track is loaded. */
internal class DockVisibility(val currentRoute: String?, val showDockRow: Boolean, val hasTrack: Boolean)

/**
 * Creates and remembers the dock state, handling search query updates, microphone recognition
 * launches, and rendering the dock card — always its own independent element now, whether or not
 * a track is playing; see [WanderDock].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberWanderDockState(
    navController: NavHostController,
    viewModel: WanderAppViewModel,
    motionScheme: MotionScheme,
    visibility: DockVisibility,
    sheetState: PlayerSheetState,
    dockInset: Dp
): WanderDockState {
    val currentRoute = visibility.currentRoute
    val showDockRow = visibility.showDockRow
    val hasTrack = visibility.hasTrack
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

    val dockItems by hiltViewModel<NavLayoutViewModel>().dockItems.collectAsStateWithLifecycle()
    val openLibrary = { navController.switchTab(TopLevelDestination.LIBRARY) }
    val onQueryChange: (String) -> Unit = { value ->
        if (value.isNotBlank() && dockRoute != TopLevelDestination.LIBRARY.route) {
            openLibrary()
        }
        viewModel.setSearchQuery(value)
    }

    val standaloneDock: @Composable BoxScope.() -> Unit = {
        AnimatedVisibility(
            visible = showDockRow,
            // One spring for enter and exit, and the same one the player's resting inset and the
            // content padding animate on, so the dock leaving and returning reads as one motion
            // the player above it follows rather than three unrelated ones.
            enter = fadeIn(motionScheme.defaultEffectsSpec()) +
                slideInVertically(dockSpec()) { it } +
                scaleIn(dockSpec(), initialScale = 0.85f),
            exit = fadeOut(motionScheme.fastEffectsSpec()) +
                slideOutVertically(dockSpec()) { it } +
                scaleOut(dockSpec(), targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = dockInset + MiniPlayerGap)
        ) {
            WanderDock(
                currentRoute = dockRoute,
                query = searchQuery,
                items = dockItems,
                onOpenItem = { item ->
                    when (item) {
                        DockItem.LIBRARY -> navController.switchTab(TopLevelDestination.LIBRARY)
                        DockItem.FRIENDS -> navController.switchTab(TopLevelDestination.FRIENDS)
                        else -> navController.navigateSettled(item.route)
                    }
                },
                onOpenLibrary = openLibrary,
                onQueryChange = onQueryChange,
                onSearch = openLibrary,
                onListen = onListen,
                // The raw signal, overshoot and all — see `WanderDock`'s own doc for why this is
                // read straight off the sheet rather than animated again.
                progress = { sheetState.rawProgress },
                // Whether the mini player is (or is about to be) floating above this — see
                // `WanderDock`'s own doc on why that changes its corners, not just its position.
                pairedWithMiniPlayer = hasTrack
            )
        }
    }

    return remember(standaloneDock) {
        WanderDockState(standaloneDock)
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
