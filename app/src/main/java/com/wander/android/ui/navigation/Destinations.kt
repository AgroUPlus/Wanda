package com.wander.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** The four top-level tabs. Now Playing and Queue are separate routes, not tabs. */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector
) {
    HOME("home", "Home", Icons.Rounded.Home, Icons.Outlined.Home),
    LIBRARY("library", "Library", Icons.Rounded.LibraryMusic, Icons.Outlined.LibraryMusic),
    SEARCH("search", "Search", Icons.Rounded.Search, Icons.Outlined.Search),
    SETTINGS("settings", "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings)
}

object Routes {
    const val WELCOME = "welcome"
    const val QUEUE = "queue"
    const val NAVIDROME_LOGIN = "login/navidrome"
    const val YTMUSIC_LOGIN = "login/ytmusic"

    val topLevel: Set<String> = TopLevelDestination.entries.map { it.route }.toSet()
}
