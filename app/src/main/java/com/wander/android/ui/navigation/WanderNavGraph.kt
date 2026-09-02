package com.wander.android.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.compose.material3.MotionScheme
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.ui.screens.album.AlbumScreen
import com.wander.android.ui.screens.artist.ArtistScreen
import com.wander.android.ui.screens.home.HomeScreen
import com.wander.android.ui.screens.library.LibrarySurface
import com.wander.android.ui.screens.login.NavidromeLoginScreen
import com.wander.android.ui.screens.login.YouTubeLoginScreen
import com.wander.android.ui.screens.queue.QueueScreen
import com.wander.android.ui.screens.settings.SettingsScreen
import com.wander.android.ui.screens.social.CircleScreen
import com.wander.android.ui.screens.social.InboxScreen
import com.wander.android.ui.screens.settings.FingerprintsScreen
import com.wander.android.ui.screens.social.OffGridScreen
import com.wander.android.ui.screens.social.JamScreen
import com.wander.android.ui.screens.social.MyProfileScreen
import com.wander.android.ui.screens.social.ProfileScreen
import com.wander.android.ui.screens.social.SocialScreen
import com.wander.android.ui.screens.stats.StatsScreen
import com.wander.android.ui.screens.welcome.WelcomeScreen

fun NavGraphBuilder.wanderNavGraph(
    navController: NavHostController,
    /**
     * The theme's motion scheme, read once by `WanderApp`.
     *
     * Passed rather than read here because the transition lambdas below are not `@Composable` —
     * see `NavTransitions`. Stable across recompositions, so it does not churn the graph.
     */
    motion: MotionScheme,
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
    tabDestination(motion, TopLevelDestination.HOME.route) {
        HomeScreen(
            contentPadding = contentPadding,
            onOpenArtist = { name, id -> navController.navigateSettled(Routes.artist(name, id)) },
            onOpenSettings = { navController.navigateSettled(Routes.SETTINGS) }
        )
    }

    // The library and the search results are one destination: the dock's field turns the first
    // into the second without moving on the back stack. See `LibrarySurface`.
    tabDestination(motion, TopLevelDestination.LIBRARY.route) {
        LibrarySurface(
            contentPadding = contentPadding,
            onOpenAlbum = { navController.navigateSettled(Routes.album(it)) },
            onOpenArtist = { name, id -> navController.navigateSettled(Routes.artist(name, id)) },
            onOpenHistory = { navController.navigateSettled(Routes.HISTORY) },
            onOpenPlaylist = { navController.navigateSettled(Routes.playlist(it)) },
            onOpenImport = { navController.navigateSettled(Routes.IMPORT_PLAYLIST) }
        )
    }

    tabDestination(motion, TopLevelDestination.FRIENDS.route) {
        SocialScreen(
            contentPadding = contentPadding,
            onOpenProfile = { navController.navigateSettled(Routes.profile(it)) },
            onOpenJam = { navController.navigateSettled(Routes.JAM) },
            onOpenInbox = { navController.navigateSettled(Routes.INBOX) },
            onOpenCircle = { navController.navigateSettled(Routes.CIRCLE) },
            onOpenOffGrid = { navController.navigateSettled(Routes.OFFGRID) },
            onOpenMyProfile = { navController.navigateSettled(Routes.MY_PROFILE) },
            onOpenSettings = { navController.navigateSettled(Routes.SETTINGS) }
        )
    }

    // Registered before `PROFILE`, whose `profile/{username}` pattern would otherwise swallow
    // `profile/me` and open a page about a friend called "me".
    tabDestination(motion, Routes.MY_PROFILE) {
        MyProfileScreen(
            contentPadding = contentPadding,
            onBack = navController::popBackStack,
            onOpenStats = { navController.navigateSettled(Routes.STATS) }
        )
    }

    tabDestination(motion, Routes.HISTORY) {
        com.wander.android.ui.screens.library.HistoryScreen(
            contentPadding = contentPadding,
            onBack = navController::popBackStack,
            onOpenArtist = { name, id -> navController.navigateSettled(Routes.artist(name, id)) }
        )
    }

    tabDestination(motion, Routes.MERGE_PREVIEW) {
        com.wander.android.ui.screens.settings.MergePreviewScreen(
            contentPadding = contentPadding,
            onBack = navController::popBackStack
        )
    }

    tabDestination(motion, Routes.STATS) {
        StatsScreen(contentPadding = contentPadding)
    }

    tabDestination(motion, Routes.SETTINGS) {
        SettingsScreen(
            contentPadding = contentPadding,
            onNavidromeLogin = { navController.navigateSettled(Routes.NAVIDROME_LOGIN) },
            onYouTubeLogin = { navController.navigateSettled(Routes.YTMUSIC_LOGIN) },
            onOpenImport = { navController.navigateSettled(Routes.IMPORT_PLAYLIST) },
            onOpenMergePreview = { navController.navigateSettled(Routes.MERGE_PREVIEW) },
            onOpenFingerprints = { navController.navigateSettled(Routes.FINGERPRINTS) }
        )
    }

    detailDestination(motion, Routes.IMPORT_PLAYLIST) {
        com.wander.android.ui.screens.importer.PlaylistImportScreen(
            contentPadding = contentPadding,
            onBack = navController::popBackStack,
            onOpenPlaylist = { navController.navigateSettled(Routes.playlist(it)) }
        )
    }

    detailDestination(motion, 
        route = Routes.ALBUM,
        arguments = listOf(navArgument("albumId") { type = NavType.StringType })
    ) {
        AlbumScreen(
            contentPadding = contentPadding,
            onBack = navController::popBackStack,
            onOpenArtist = { name, id -> navController.navigateSettled(Routes.artist(name, id)) }
        )
    }

    detailDestination(motion, 
        route = Routes.PLAYLIST,
        arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
    ) {
        com.wander.android.ui.screens.playlist.PlaylistScreen(
            contentPadding = contentPadding,
            onOpenArtist = { name, id -> navController.navigateSettled(Routes.artist(name, id)) },
            onBack = navController::popBackStack
        )
    }

    // A profile keeps the chrome — you arrive from a friend's now-playing card, and the player
    // that card is about must stay reachable.
    tabDestination(motion, 
        route = Routes.PROFILE,
        arguments = listOf(navArgument("username") { type = NavType.StringType })
    ) {
        ProfileScreen(
            contentPadding = contentPadding,
            onBack = navController::popBackStack
        )
    }

    detailDestination(motion, route = Routes.OFFGRID) {
        OffGridScreen(
            contentPadding = contentPadding,
            onBack = navController::popBackStack
        )
    }

    detailDestination(motion, route = Routes.FINGERPRINTS) {
        FingerprintsScreen(
            contentPadding = contentPadding,
            onBack = navController::popBackStack
        )
    }

    detailDestination(motion, route = Routes.INBOX) {
        InboxScreen(
            contentPadding = contentPadding,
            onBack = navController::popBackStack
        )
    }

    detailDestination(motion, route = Routes.CIRCLE) {
        CircleScreen(
            contentPadding = contentPadding,
            onBack = navController::popBackStack
        )
    }

    detailDestination(motion, 
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
            onOpenSettings = { navController.navigateSettled(Routes.SETTINGS) },
            onBack = navController::popBackStack,
            initialCode = initialCode
        )
    }

    detailDestination(motion, 
        route = Routes.ARTIST,
        arguments = listOf(
            navArgument("artist") { type = NavType.StringType },
            // Optional: callers that know who they mean pass it, and the page believes them over
            // anything it could infer from the name. See `Routes.artist`.
            navArgument("artistId") {
                type = NavType.StringType
                defaultValue = ""
                nullable = true
            }
        )
    ) {
        ArtistScreen(
            contentPadding = contentPadding,
            onBack = navController::popBackStack,
            onOpenAlbum = { navController.navigateSettled(Routes.album(it)) },
            onOpenArtist = { name, id -> navController.navigateSettled(Routes.artist(name, id)) }
        )
    }

    detailDestination(motion, Routes.QUEUE) {
        QueueScreen(
            playerConnection = playerConnection,
            onClose = navController::popBackStack,
            onOpenJam = {
                // The queue is opened from the maximized player, so the sheet is still expanded
                // behind it — without this the room opens correctly and is completely hidden.
                onCollapsePlayer()
                navController.popBackStack()
                // Not guarded: the pop above leaves the entry mid-transition by construction, so
                // a settled check here would swallow the navigation every time.
                navController.navigate(Routes.JAM)
            }
        )
    }

    detailDestination(motion, Routes.WELCOME) {
        WelcomeScreen(
            onNavidromeLogin = { navController.navigateSettled(Routes.NAVIDROME_LOGIN) },
            onYouTubeLogin = { navController.navigateSettled(Routes.YTMUSIC_LOGIN) },
            onDone = {
                // Not guarded: setup finishing is not a stray tap, and this must not be dropped.
                navController.navigate(TopLevelDestination.HOME.route) {
                    popUpTo(Routes.WELCOME) { inclusive = true }
                }
            }
        )
    }

    detailDestination(motion, Routes.NAVIDROME_LOGIN) {
        NavidromeLoginScreen(onDone = navController::popBackStack)
    }

    detailDestination(motion, Routes.YTMUSIC_LOGIN) {
        YouTubeLoginScreen(onDone = navController::popBackStack)
    }
}

/** A top-level tab: peers, so they slide along X in the direction of the bar. */
private fun NavGraphBuilder.tabDestination(
    motion: MotionScheme,
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) = composable(
    route = route,
    arguments = arguments,
    // Always the actual movement — where the transition comes from, where it goes — rather than
    // this destination's own route. The exits used to be handed the *leaving* screen as their
    // target, so a tab slid out as though every switch went the same way.
    enterTransition = { tabEnter(initialState.route(), targetState.route(), motion) },
    exitTransition = { tabExit(initialState.route(), targetState.route(), motion) },
    popEnterTransition = { tabEnter(initialState.route(), targetState.route(), motion) },
    popExitTransition = { tabExit(initialState.route(), targetState.route(), motion) },
    content = content
)

private fun NavBackStackEntry.route(): String? = destination.route

/** A screen opened on top of another: shared-axis Z. */
private fun NavGraphBuilder.detailDestination(
    motion: MotionScheme,
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) = composable(
    route = route,
    arguments = arguments,
    enterTransition = { detailEnter(motion) },
    exitTransition = { detailExit(motion) },
    popEnterTransition = { detailPopEnter(motion) },
    popExitTransition = { detailPopExit(motion) },
    content = content
)
