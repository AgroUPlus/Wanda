package com.wander.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.wander.android.ui.navigation.DockItem
import com.wander.android.ui.navigation.Routes
import com.wander.android.ui.navigation.TabReselection
import com.wander.android.ui.navigation.TopLevelDestination
import com.wander.android.ui.navigation.navigateSettled

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

/**
 * A tap on a tab's button: home within it when you are already in it, otherwise a switch.
 *
 * "In it" means it is the tab the current screen was opened from ([TrackTabOwner]). On that tab's root the tap goes to [reselection] for the screen to scroll
 * itself back up; on a page opened from it (an album, an artist) it pops back to the root.
 */
internal fun NavHostController.selectTab(tab: TopLevelDestination, reselection: TabReselection) {
    when {
        currentDestination?.route == tab.route -> reselection.reselect(tab)
        reselection.owner == tab.route -> popBackStack(tab.route, inclusive = false)
        else -> switchTab(tab)
    }
}

/**
 * Keeps [TabReselection.owner] on the tab the current screen was opened from: the last tab root
 * arrived at, forward or back. Every route to a page passes through its tab's root first (or pops
 * back onto one), so the last root seen is the nearest one under the top of the stack — which the
 * back stack itself would say, were reading it not restricted to Navigation's own library.
 */
@Composable
internal fun TrackTabOwner(navController: NavHostController, reselection: TabReselection) {
    DisposableEffect(navController, reselection) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            if (destination.route in Routes.topLevel) reselection.owner = destination.route
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
}

/** A tap on one of the dock's or rail's [DockItem]s. */
internal fun NavHostController.openDockItem(item: DockItem, reselection: TabReselection) {
    when (item) {
        DockItem.LIBRARY -> selectTab(TopLevelDestination.LIBRARY, reselection)
        DockItem.FRIENDS -> selectTab(TopLevelDestination.FRIENDS, reselection)
        else -> navigateSettled(item.route)
    }
}
