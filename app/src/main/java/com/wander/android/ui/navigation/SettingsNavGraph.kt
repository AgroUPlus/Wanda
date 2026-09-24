package com.wander.android.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MotionScheme
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.wander.android.data.replay.ReplayAvailability
import com.wander.android.ui.screens.importer.PlaylistImportScreen
import com.wander.android.ui.screens.login.NavidromeLoginScreen
import com.wander.android.ui.screens.login.YouTubeLoginScreen
import com.wander.android.ui.screens.replay.ReplayStoryScreen
import com.wander.android.ui.screens.settings.FingerprintsScreen
import com.wander.android.ui.screens.settings.MergePreviewScreen
import com.wander.android.ui.screens.settings.SettingsCategory
import com.wander.android.ui.screens.settings.SettingsCategoryScreen
import com.wander.android.ui.screens.settings.SettingsScreen
import com.wander.android.ui.screens.stats.StatsScreen
import com.wander.android.ui.screens.welcome.WelcomeScreen
import java.time.LocalDate

internal fun NavGraphBuilder.settingsNavGraph(
    navController: NavHostController,
    motion: MotionScheme,
    contentPadding: PaddingValues
) {
    tabDestination(motion, Routes.MERGE_PREVIEW) {
        MergePreviewScreen(
            contentPadding = contentPadding,
            onBack = navController::popBackStack
        )
    }

    detailDestination(
        motion,
        Routes.REPLAY,
        arguments = listOf(navArgument("year") { type = NavType.IntType })
    ) { entry ->
        ReplayStoryScreen(
            year = entry.arguments?.getInt("year")?.takeIf { it > 0 }
                ?: ReplayAvailability.yearOnDemand(LocalDate.now()),
            onDismiss = navController::popBackStack
        )
    }

    tabDestination(motion, Routes.STATS) {
        StatsScreen(contentPadding = contentPadding)
    }

    tabDestination(motion, Routes.SETTINGS) {
        SettingsScreen(
            contentPadding = contentPadding,
            onOpenCategory = { navController.navigateSettled(Routes.settingsCategory(it.name)) }
        )
    }

    detailDestination(
        motion,
        route = Routes.SETTINGS_CATEGORY,
        arguments = listOf(navArgument("category") { type = NavType.StringType })
    ) { entry ->
        val category = SettingsCategory.fromRoute(entry.arguments?.getString("category"))
        if (category == null) {
            navController.popBackStack()
        } else {
            SettingsCategoryScreen(
                category = category,
                contentPadding = contentPadding,
                onBack = navController::popBackStack,
                onNavidromeLogin = { navController.navigateSettled(Routes.NAVIDROME_LOGIN) },
                onYouTubeLogin = { navController.navigateSettled(Routes.YTMUSIC_LOGIN) },
                onOpenImport = { navController.navigateSettled(Routes.IMPORT_PLAYLIST) },
                onOpenMergePreview = { navController.navigateSettled(Routes.MERGE_PREVIEW) },
                onOpenReplay = {
                    navController.navigateSettled(
                        Routes.replay(ReplayAvailability.yearOnDemand(LocalDate.now()))
                    )
                },
                onOpenFingerprints = { navController.navigateSettled(Routes.FINGERPRINTS) }
            )
        }
    }

    detailDestination(motion, Routes.IMPORT_PLAYLIST) {
        PlaylistImportScreen(
            onBack = navController::popBackStack,
            onOpenPlaylist = { navController.navigateSettled(Routes.playlist(it)) },
            onOpenYouTubeLogin = { navController.navigateSettled(Routes.YTMUSIC_LOGIN) }
        )
    }

    detailDestination(motion, route = Routes.FINGERPRINTS) {
        FingerprintsScreen(
            contentPadding = contentPadding,
            onBack = navController::popBackStack
        )
    }

    detailDestination(motion, Routes.WELCOME) {
        WelcomeScreen(
            onNavidromeLogin = { navController.navigateSettled(Routes.NAVIDROME_LOGIN) },
            onYouTubeLogin = { navController.navigateSettled(Routes.YTMUSIC_LOGIN) },
            onDone = {
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
