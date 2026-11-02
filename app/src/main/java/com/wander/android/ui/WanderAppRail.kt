package com.wander.android.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavHostController
import androidx.window.core.layout.WindowSizeClass
import com.wander.android.ui.navigation.DockItem
import com.wander.android.ui.navigation.TopLevelDestination
import com.wander.android.ui.navigation.WanderNavRail
import com.wander.android.ui.navigation.navigateSettled

/**
 * Whether the window is wide enough for a navigation rail instead of the dock's buttons.
 *
 * Medium width *and* medium height: a phone held sideways is wide but short, and the rail's items
 * would end up under the docked mini player and the dock — that case keeps the phone layout.
 */
@Composable
internal fun rememberUseNavRail(): Boolean {
    val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
    return sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) &&
        sizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
}

/**
 * The rail at the window's start edge, reporting its measured width — which already includes the
 * start inset it pads itself by — so the content and the dock can start past it rather than
 * guessing its width from a constant.
 */
@Composable
internal fun BoxScope.ShellNavRail(
    navController: NavHostController,
    currentRoute: String?,
    bottomClearance: Dp,
    onWidthChanged: (Dp) -> Unit
) {
    val density = LocalDensity.current
    WanderNavRail(
        currentRoute = currentRoute?.substringBefore("?"),
        bottomClearance = bottomClearance,
        onOpenHome = { navController.switchTab(TopLevelDestination.HOME) },
        onOpenItem = { item ->
            when (item) {
                DockItem.LIBRARY -> navController.switchTab(TopLevelDestination.LIBRARY)
                DockItem.FRIENDS -> navController.switchTab(TopLevelDestination.FRIENDS)
                else -> navController.navigateSettled(item.route)
            }
        },
        modifier = Modifier
            .align(Alignment.TopStart)
            .onSizeChanged { size -> onWidthChanged(with(density) { size.width.toDp() }) }
    )
}

/** This padding with its start edge pushed out to at least [start] — past the rail, when shown. */
internal fun PaddingValues.withMinStart(start: Dp, direction: LayoutDirection): PaddingValues =
    PaddingValues(
        start = maxOf(calculateStartPadding(direction), start),
        top = calculateTopPadding(),
        end = calculateEndPadding(direction),
        bottom = calculateBottomPadding()
    )
