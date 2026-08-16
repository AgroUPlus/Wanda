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
import com.wander.android.ui.screens.album.AlbumScreen
import com.wander.android.ui.screens.artist.ArtistScreen
import com.wander.android.ui.screens.home.HomeScreen
import com.wander.android.ui.screens.library.LibraryScreen
import com.wander.android.ui.screens.login.NavidromeLoginScreen
import com.wander.android.ui.screens.login.YouTubeLoginScreen
import com.wander.android.ui.screens.queue.QueueScreen
import com.wander.android.ui.screens.search.SearchScreen
import com.wander.android.ui.screens.settings.SettingsScreen
import com.wander.android.ui.screens.stats.StatsScreen
import com.wander.android.ui.screens.welcome.WelcomeScreen

fun NavGraphBuilder.wanderNavGraph(
    navController: NavHostController,
    playerConnection: PlayerConnection,
    contentPadding: PaddingValues
) {
    tabDestination(TopLevelDestination.HOME.route) {
        HomeScreen(
            contentPadding = contentPadding,
            onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            onOpenStats = { navController.navigate(Routes.STATS) }
        )
    }

    tabDestination(TopLevelDestination.LIBRARY.route) {
        LibraryScreen(
            contentPadding = contentPadding,
            onOpenAlbum = { navController.navigate(Routes.album(it)) }
        )
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

    tabDestination(Routes.STATS) {
        StatsScreen(contentPadding = contentPadding)
    }

    tabDestination(Routes.SETTINGS) {
        SettingsScreen(
            contentPadding = contentPadding,
            onNavidromeLogin = { navController.navigate(Routes.NAVIDROME_LOGIN) },
            onYouTubeLogin = { navController.navigate(Routes.YTMUSIC_LOGIN) }
        )
    }

    detailDestination(
        route = Routes.ALBUM,
        arguments = listOf(navArgument("albumId") { type = NavType.StringType })
    ) {
        AlbumScreen(
            contentPadding = contentPadding,
            onBack = navController::popBackStack,
            onOpenArtist = { navController.navigate(Routes.artist(it)) }
        )
    }

    detailDestination(
        route = Routes.ARTIST,
        arguments = listOf(navArgument("artist") { type = NavType.StringType })
    ) {
        ArtistScreen(
            contentPadding = contentPadding,
            onBack = navController::popBackStack,
            onOpenAlbum = { navController.navigate(Routes.album(it)) }
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
    // Always the actual movement — where the transition comes from, where it goes — rather than
    // this destination's own route. The exits used to be handed the *leaving* screen as their
    // target, so a tab slid out as though every switch went the same way.
    enterTransition = { tabEnter(initialState.route(), targetState.route()) },
    exitTransition = { tabExit(initialState.route(), targetState.route()) },
    popEnterTransition = { tabEnter(initialState.route(), targetState.route()) },
    popExitTransition = { tabExit(initialState.route(), targetState.route()) },
    content = content
)

private fun NavBackStackEntry.route(): String? = destination.route

/** A screen opened on top of another: shared-axis Z. */
private fun NavGraphBuilder.detailDestination(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) = composable(
    route = route,
    arguments = arguments,
    enterTransition = { detailEnter() },
    exitTransition = { detailExit() },
    popEnterTransition = { detailPopEnter() },
    popExitTransition = { detailPopExit() },
    content = content
)
