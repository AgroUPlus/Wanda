package com.wander.android.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MotionScheme
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.wander.android.ui.screens.social.ActivityScreen
import com.wander.android.ui.screens.social.CircleScreen
import com.wander.android.ui.screens.social.InboxScreen
import com.wander.android.ui.screens.social.JamScreen
import com.wander.android.ui.screens.social.MyProfileScreen
import com.wander.android.ui.screens.social.OffGridScreen
import com.wander.android.ui.screens.social.ProfileScreen
import com.wander.android.ui.screens.social.SocialScreen

internal fun NavGraphBuilder.socialNavGraph(
    navController: NavHostController,
    motion: MotionScheme,
    contentPadding: PaddingValues
) {
    tabDestination(motion, TopLevelDestination.FRIENDS.route) {
        SocialScreen(
            contentPadding = contentPadding,
            onOpenProfile = { navController.navigateSettled(Routes.profile(it)) },
            onOpenJam = { navController.navigateSettled(Routes.JAM) },
            onOpenActivity = { navController.navigateSettled(Routes.ACTIVITY) },
            onOpenOffGrid = { navController.navigateSettled(Routes.OFFGRID) },
            onOpenMyProfile = { navController.navigateSettled(Routes.MY_PROFILE) },
            onOpenSettings = { navController.navigateSettled(SYNC_SETTINGS) }
        )
    }

    tabDestination(motion, Routes.MY_PROFILE) {
        MyProfileScreen(
            contentPadding = contentPadding,
            onBack = navController::popBackStack,
            onOpenStats = { navController.navigateSettled(Routes.STATS) }
        )
    }

    tabDestination(
        motion,
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

    detailDestination(motion, route = Routes.ACTIVITY) {
        ActivityScreen(
            contentPadding = contentPadding,
            onBack = navController::popBackStack,
            onOpenThread = { navController.navigateSettled(Routes.inbox(it)) },
            onOpenCircleRecap = { navController.navigateSettled(Routes.CIRCLE) },
            onOpenProfile = { navController.navigateSettled(Routes.profile(it)) },
            onOpenArtist = { name, id -> navController.navigateSettled(Routes.artist(name, id)) }
        )
    }

    detailDestination(motion, route = Routes.INBOX) { entry ->
        InboxScreen(
            contentPadding = contentPadding,
            onBack = navController::popBackStack,
            openWith = entry.arguments?.getString("username")
        )
    }

    detailDestination(motion, route = Routes.CIRCLE) {
        CircleScreen(
            contentPadding = contentPadding,
            onBack = navController::popBackStack
        )
    }

    detailDestination(
        motion,
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
            onOpenSettings = { navController.navigateSettled(SYNC_SETTINGS) },
            onBack = navController::popBackStack,
            initialCode = initialCode
        )
    }
}
