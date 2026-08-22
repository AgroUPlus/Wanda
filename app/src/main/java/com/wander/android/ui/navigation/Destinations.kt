package com.wander.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.graphics.vector.ImageVector
import java.net.URLEncoder

/**
 * The four top-level tabs. Now Playing, Queue and Settings are separate routes, not tabs.
 *
 * Settings used to be a tab. It is reached from the icon in the Home header instead — two permanent
 * entry points to the same screen, one of them occupying a quarter of the navigation bar, was one
 * too many. Friends earns the slot Settings gave up: unlike Settings it is somewhere you go to see
 * what changed, which is the thing a navigation bar is for.
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector
) {
    HOME("home", "Home", Icons.Rounded.Home, Icons.Outlined.Home),
    LIBRARY("library", "Library", Icons.Rounded.LibraryMusic, Icons.Outlined.LibraryMusic),
    SEARCH("search", "Search", Icons.Rounded.Search, Icons.Outlined.Search),
    FRIENDS("friends", "Friends", Icons.Rounded.People, Icons.Outlined.People)
}

object Routes {
    const val WELCOME = "welcome"
    const val SETTINGS = "settings"
    const val STATS = "stats"
    const val QUEUE = "queue"
    const val NAVIDROME_LOGIN = "login/navidrome"
    const val YTMUSIC_LOGIN = "login/ytmusic"

    const val ALBUM = "album/{albumId}"
    const val ARTIST = "artist/{artist}"
    const val PROFILE = "profile/{username}"

    /** The shared queue. A detail screen off Friends, not a tab: you are only in one sometimes. */
    const val JAM = "jam"
    const val JAM_ROUTE = "jam?code={code}"
    fun jam(code: String? = null): String = if (code.isNullOrBlank()) "jam" else "jam?code=$code"

    /**
     * Songs friends have handed you.
     *
     * A detail screen off Friends for the same reason [JAM] is one, and because four tabs is the
     * ceiling this navigation bar was designed around — a fifth would make every one of them
     * narrower to serve something you visit when a notification says to.
     */
    const val INBOX = "inbox"

    /** The activity feed and the circle's shared recap. Reached from Friends, beside the inbox. */
    const val CIRCLE = "circle"

    /**
     * Ids and names both contain `/` and `:` — a `navidrome:al-42` id, an artist called
     * "AC/DC" — so they are encoded into the path rather than interpolated raw, which produced a
     * route with extra segments that matched nothing.
     */
    fun album(albumId: String): String = "album/${albumId.encodeForRoute()}"

    fun artist(name: String): String = "artist/${name.encodeForRoute()}"

    fun profile(username: String): String = "profile/${username.encodeForRoute()}"

    private fun String.encodeForRoute(): String = URLEncoder.encode(this, "UTF-8")

    val topLevel: Set<String> = TopLevelDestination.entries.map { it.route }.toSet()

    /**
     * Routes that keep the navigation bar and the docked player.
     *
     * Album and artist pages are browsing, not a modal task: hiding the player to show a
     * tracklist would stop the music's controls being reachable from the very screen you opened
     * *from* the player. The queue and the login flows still take the screen over.
     */
    val withChrome: Set<String> =
        topLevel + ALBUM + ARTIST + PROFILE + SETTINGS + STATS + JAM + "jam" + INBOX + CIRCLE
}
