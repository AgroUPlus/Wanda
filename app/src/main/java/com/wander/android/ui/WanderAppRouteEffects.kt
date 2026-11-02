package com.wander.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import com.wander.android.ui.navigation.Routes
import com.wander.android.ui.navigation.TopLevelDestination

/** The shell's reactions to where the user is, and to routes asked for from outside it. */
@Composable
internal fun ShellRouteEffects(
    navController: NavHostController,
    viewModel: WanderAppViewModel,
    currentRoute: String?
) {
    // Routes asked for from outside the composition — a tapped notification. Consumed once
    // navigated, so bringing the app forward later does not send the user back to the inbox.
    LaunchedEffect(navController) {
        viewModel.deepLinkRoutes.collect { route ->
            navController.navigate(route)
            viewModel.consumeDeepLink()
        }
    }

    // The query is held outside any one screen so it survives the trip into an artist or album and
    // back. Reaching another tab is the end of that search, and without this the field was still
    // full, and Library still showing results, the next time the tab opened.
    LaunchedEffect(currentRoute) {
        if (currentRoute in Routes.topLevel && currentRoute != TopLevelDestination.LIBRARY.route) {
            viewModel.setSearchQuery("")
        }
    }
}
