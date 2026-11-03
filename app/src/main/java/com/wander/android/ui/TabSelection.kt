package com.wander.android.ui

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
 * "In it" means it is the tab the current screen was opened from — the nearest root under the top
 * of the back stack. On that tab's root the tap goes to [reselection] for the screen to scroll
 * itself back up; on a page opened from it (an album, an artist) it pops back to the root.
 */
internal fun NavHostController.selectTab(tab: TopLevelDestination, reselection: TabReselection) {
    val owner = currentBackStack.value
        .lastOrNull { it.destination.route in Routes.topLevel }
        ?.destination?.route
    when {
        currentDestination?.route == tab.route -> reselection.reselect(tab)
        owner == tab.route -> popBackStack(tab.route, inclusive = false)
        else -> switchTab(tab)
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
