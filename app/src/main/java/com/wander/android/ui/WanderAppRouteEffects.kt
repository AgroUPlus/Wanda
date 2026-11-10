package com.wander.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import com.wander.android.ui.navigation.Routes

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
    // back to the tab it was typed on. Reaching a *different* tab is the end of that search; without
    // this the field was still full, and the old tab still showing results, the next time it opened.
    var lastTab by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(currentRoute) {
        if (currentRoute in Routes.topLevel && currentRoute != lastTab) {
            if (lastTab != null) viewModel.setSearchQuery("")
            lastTab = currentRoute
        }
    }
}
