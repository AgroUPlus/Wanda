package com.wander.android.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MotionScheme
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.ui.screens.album.AlbumScreen
import com.wander.android.ui.screens.artist.ArtistScreen
import com.wander.android.ui.screens.home.HomeScreen
import com.wander.android.ui.screens.library.HistoryScreen
import com.wander.android.ui.screens.library.LibrarySurface
import com.wander.android.ui.screens.playlist.PlaylistScreen
import com.wander.android.ui.screens.queue.QueueScreen

/**
 * Top-level navigation graph for Wanda, wiring home, library, social, settings, and media destinations.
 */
fun NavGraphBuilder.wanderNavGraph(
    navController: NavHostController,
    motion: MotionScheme,
    playerConnection: PlayerConnection,
    contentPadding: PaddingValues,
    onCollapsePlayer: () -> Unit
) {
    tabDestination(motion, TopLevelDestination.HOME.route) {
        HomeScreen(
            contentPadding = contentPadding,
            onOpenArtist = { name, id -> navController.navigateSettled(Routes.artist(name, id)) },
            onOpenSettings = { navController.navigateSettled(Routes.SETTINGS) }
        )
    }

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

    tabDestination(motion, Routes.HISTORY) {
        HistoryScreen(
            contentPadding = contentPadding,
            onBack = navController::popBackStack,
            onOpenArtist = { name, id -> navController.navigateSettled(Routes.artist(name, id)) }
        )
    }

    detailDestination(
        motion,
        route = Routes.ALBUM,
        arguments = listOf(navArgument("albumId") { type = NavType.StringType })
    ) {
        AlbumScreen(
            contentPadding = contentPadding,
            onBack = navController::popBackStack,
            onOpenArtist = { name, id -> navController.navigateSettled(Routes.artist(name, id)) }
        )
    }

    detailDestination(
        motion,
        route = Routes.PLAYLIST,
        arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
    ) {
        PlaylistScreen(
            contentPadding = contentPadding,
            onOpenArtist = { name, id -> navController.navigateSettled(Routes.artist(name, id)) },
            onBack = navController::popBackStack
        )
    }

    detailDestination(
        motion,
        route = Routes.ARTIST,
        arguments = listOf(
            navArgument("artist") { type = NavType.StringType },
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
            onOpenArtist = { name, id -> navController.navigateSettled(Routes.artist(name, id)) },
            onOpenJam = {
                onCollapsePlayer()
                navController.popBackStack()
                navController.navigate(Routes.JAM)
            }
        )
    }

    socialNavGraph(navController, motion, contentPadding)
    settingsNavGraph(navController, motion, contentPadding)
}
