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
import com.wander.android.ui.screens.social.CircleScreen
import com.wander.android.ui.screens.social.InboxScreen
import com.wander.android.ui.screens.social.JamScreen
import com.wander.android.ui.screens.social.MyProfileScreen
import com.wander.android.ui.screens.social.ProfileScreen
import com.wander.android.ui.screens.social.SocialScreen
import com.wander.android.ui.screens.stats.StatsScreen
import com.wander.android.ui.screens.welcome.WelcomeScreen

fun NavGraphBuilder.wanderNavGraph(
    navController: NavHostController,
    playerConnection: PlayerConnection,
    contentPadding: PaddingValues,
    /**
     * Docks the player sheet.
     *
     * The sheet lives in the shell, above the whole nav host, and keeps its expanded state across
     * navigation — so a destination reached *from* the maximized player opens underneath it and
     * cannot be seen. Screens that navigate somewhere worth looking at have to say so.
     */
    onCollapsePlayer: () -> Unit
) {
    tabDestination(TopLevelDestination.HOME.route) {
        HomeScreen(
            contentPadding = contentPadding,
            onOpenSettings = { navController.navigate(Routes.SETTINGS) }
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

    tabDestination(TopLevelDestination.FRIENDS.route) {
        SocialScreen(
            contentPadding = contentPadding,
            onOpenProfile = { navController.navigate(Routes.profile(it)) },
            onOpenJam = { navController.navigate(Routes.JAM) },
            onOpenInbox = { navController.navigate(Routes.INBOX) },
            onOpenCircle = { navController.navigate(Routes.CIRCLE) },
            onOpenMyProfile = { navController.navigate(Routes.MY_PROFILE) },
            onOpenSettings = { navController.navigate(Routes.SETTINGS) }
        )
    }

    // Registered before `PROFILE`, whose `profile/{username}` pattern would otherwise swallow
    // `profile/me` and open a page about a friend called "me".
    tabDestination(Routes.MY_PROFILE) {
        MyProfileScreen(
            contentPadding = contentPadding,
            onBack = navController::popBackStack,
            onOpenStats = { navController.navigate(Routes.STATS) }
        )
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

    // A profile keeps the chrome — you arrive from a friend's now-playing card, and the player
    // that card is about must stay reachable.
    tabDestination(
        route = Routes.PROFILE,
        arguments = listOf(navArgument("username") { type = NavType.StringType })
    ) {
        ProfileScreen(
            contentPadding = contentPadding,
            onBack = navController::popBackStack
        )
    }

    detailDestination(route = Routes.INBOX) {
        InboxScreen(
            contentPadding = contentPadding,
            onBack = navController::popBackStack
        )
    }

    detailDestination(route = Routes.CIRCLE) {
        CircleScreen(
            contentPadding = contentPadding,
            onBack = navController::popBackStack
        )
    }

    detailDestination(
        route = Routes.JAM_ROUTE,
        arguments = listOf(
            navArgument("code") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) { backStackEntry ->
        val rawCode = backStackEntry.arguments?.getString("code")
        val initialCode = if (rawCode.isNullOrBlank() || rawCode == "{code}" || rawCode.equals("CODE", ignoreCase = true)) {
            null
        } else {
            rawCode.trim().uppercase().filter { it.isLetterOrDigit() }.take(10)
        }
        JamScreen(
            contentPadding = contentPadding,
            onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            onBack = navController::popBackStack,
            initialCode = initialCode
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
            onClose = navController::popBackStack,
            onOpenJam = {
                // The queue is opened from the maximized player, so the sheet is still expanded
                // behind it — without this the room opens correctly and is completely hidden.
                onCollapsePlayer()
                navController.popBackStack()
                navController.navigate(Routes.JAM)
            }
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
