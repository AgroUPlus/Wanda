package com.wander.android.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.ui.screens.home.HomeScreen
import com.wander.android.ui.screens.library.LibraryScreen
import com.wander.android.ui.screens.login.NavidromeLoginScreen
import com.wander.android.ui.screens.login.YouTubeLoginScreen
import com.wander.android.ui.screens.queue.QueueScreen
import com.wander.android.ui.screens.search.SearchScreen
import com.wander.android.ui.screens.settings.SettingsScreen
import com.wander.android.ui.screens.welcome.WelcomeScreen

fun NavGraphBuilder.wanderNavGraph(
    navController: NavHostController,
    playerConnection: PlayerConnection,
    contentPadding: PaddingValues
) {
    tabDestination(TopLevelDestination.HOME.route) {
        HomeScreen(
            contentPadding = contentPadding,
            onOpenSettings = { navController.navigate(TopLevelDestination.SETTINGS.route) }
        )
    }

    tabDestination(TopLevelDestination.LIBRARY.route) {
        LibraryScreen(contentPadding = contentPadding)
    }

    tabDestination(
        route = "${TopLevelDestination.SEARCH.route}?query={query}",
        arguments = listOf(
            navArgument("query") {
                type = NavType.StringType
                defaultValue = ""
                nullable = true
            }
        )
    ) {
        SearchScreen(contentPadding = contentPadding)
    }

    tabDestination(TopLevelDestination.SETTINGS.route) {
        SettingsScreen(
            contentPadding = contentPadding,
            onNavidromeLogin = { navController.navigate(Routes.NAVIDROME_LOGIN) },
            onYouTubeLogin = { navController.navigate(Routes.YTMUSIC_LOGIN) }
        )
    }

    detailDestination(Routes.QUEUE) {
        QueueScreen(
            playerConnection = playerConnection,
            onClose = navController::popBackStack
        )
    }

    detailDestination(Routes.WELCOME) {
        WelcomeScreen(
            onNavidromeLogin = { navController.navigate(Routes.NAVIDROME_LOGIN) },
            onYouTubeLogin = { navController.navigate(Routes.YTMUSIC_LOGIN) },
            onDone = {
                navController.navigate(TopLevelDestination.HOME.route) {
                    popUpTo(Routes.WELCOME) { inclusive = true }
                }
            }
        )
    }

    detailDestination(Routes.NAVIDROME_LOGIN) {
        NavidromeLoginScreen(onDone = navController::popBackStack)
    }

    detailDestination(Routes.YTMUSIC_LOGIN) {
        YouTubeLoginScreen(onDone = navController::popBackStack)
    }
}

/** A top-level tab: peers, so they slide along X in the direction of the bar. */
private fun NavGraphBuilder.tabDestination(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) = composable(
    route = route,
    arguments = arguments,
    enterTransition = { tabEnter(isForward(initialState.destination.route, route)) },
    exitTransition = { tabExit(isForward(initialState.destination.route, route)) },
    popEnterTransition = { tabEnter(isForward(initialState.destination.route, route)) },
    popExitTransition = { tabExit(isForward(initialState.destination.route, route)) },
    content = content
)

/** A screen opened on top of another: shared-axis Z. */
private fun NavGraphBuilder.detailDestination(
    route: String,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) = composable(
    route = route,
    enterTransition = { detailEnter() },
    exitTransition = { detailExit() },
    popEnterTransition = { detailPopEnter() },
    popExitTransition = { detailPopExit() },
    content = content
)
