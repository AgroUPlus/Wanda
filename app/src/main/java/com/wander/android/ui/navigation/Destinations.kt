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
import java.net.URLEncoder

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

    const val ALBUM = "album/{albumId}"
    const val ARTIST = "artist/{artist}"

    /**
     * Ids and names both contain `/` and `:` — a `navidrome:al-42` id, an artist called
     * "AC/DC" — so they are encoded into the path rather than interpolated raw, which produced a
     * route with extra segments that matched nothing.
     */
    fun album(albumId: String): String = "album/${albumId.encodeForRoute()}"

    fun artist(name: String): String = "artist/${name.encodeForRoute()}"

    private fun String.encodeForRoute(): String = URLEncoder.encode(this, "UTF-8")

    val topLevel: Set<String> = TopLevelDestination.entries.map { it.route }.toSet()

    /**
     * Routes that keep the navigation bar and the docked player.
     *
     * Album and artist pages are browsing, not a modal task: hiding the player to show a
     * tracklist would stop the music's controls being reachable from the very screen you opened
     * *from* the player. The queue and the login flows still take the screen over.
     */
    val withChrome: Set<String> = topLevel + ALBUM + ARTIST
}
