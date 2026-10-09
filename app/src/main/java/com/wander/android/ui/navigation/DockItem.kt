package com.wander.android.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.wander.android.R

/**
 * A shortcut the bottom bar can carry beside the search field. [route] is where it goes, and what
 * marks it selected while you are there.
 */
enum class DockItem(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    LIBRARY(TopLevelDestination.LIBRARY.route, R.string.nav_library, Icons.Outlined.LibraryMusic),
    FRIENDS(TopLevelDestination.FRIENDS.route, R.string.nav_friends, Icons.Outlined.People),
    HISTORY(Routes.HISTORY, R.string.nav_history, Icons.Outlined.History),
    STATS(Routes.STATS, R.string.nav_stats, Icons.Outlined.BarChart),
    SETTINGS(Routes.SETTINGS, R.string.nav_settings, Icons.Outlined.Settings);

    companion object {
        /** What the bar shows until the user chooses. */
        val Default: List<DockItem> = listOf(LIBRARY, FRIENDS)

        /** More than this and the search field is squeezed too narrow to use. */
        const val MaxShown = 3
    }
}
