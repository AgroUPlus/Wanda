package com.wander.android.ui.navigation

import java.net.URLEncoder

/**
 * The three places the dock can put you.
 *
 * There is no navigation bar any more. The dock is a search field with a Friends button beside it,
 * so a destination no longer needs a label or a pair of icons — nothing draws a tab for it. What is
 * left is the route, and the fact that these three are the roots of the back stack rather than
 * pages within it.
 *
 * [HOME] is where the app opens and where back lands: it is the default view rather than a button.
 * [LIBRARY] is what the search field opens, and what it searches from. Search is not a destination
 * of its own — see `LibrarySurface`.
 */
enum class TopLevelDestination(val route: String) {
    HOME("home"),
    LIBRARY("library"),
    FRIENDS("friends")
}

object Routes {
    const val WELCOME = "welcome"
    const val SETTINGS = "settings"
    const val STATS = "stats"

    /** Everything you have played. A log reached from the Library header, not a tab. */
    const val HISTORY = "history"

    /**
     * A dry run of the recording migration — see `MergePreviewScreen`.
     *
     * Reachable from Settings rather than shown automatically: it is a decision aid for a change
     * that has not happened yet, not something the app needs the user to look at.
     */
    const val MERGE_PREVIEW = "merge-preview"
    const val QUEUE = "queue"
    const val NAVIDROME_LOGIN = "login/navidrome"
    const val YTMUSIC_LOGIN = "login/ytmusic"
    const val IMPORT_PLAYLIST = "import/playlist"

    const val ALBUM = "album/{albumId}"
    const val PLAYLIST = "playlist/{playlistId}"
    const val ARTIST = "artist/{artist}?artistId={artistId}"
    const val PROFILE = "profile/{username}"

    /**
     * Your own page, and the way into your listening statistics.
     *
     * Its own route rather than `profile/{me}`: it is editable and the other is not, and reaching
     * it must not depend on knowing your own username — which, with no server paired, you do not
     * have.
     */
    const val MY_PROFILE = "profile/me"

    /** The shared queue. A detail screen off Friends, not a root: you are only in one sometimes. */
    const val JAM = "jam"
    const val JAM_ROUTE = "jam?code={code}"
    fun jam(code: String? = null): String = if (code.isNullOrBlank()) "jam" else "jam?code=$code"

    /** Songs friends have handed you. A detail screen off Friends, for the same reason [JAM] is. */
    const val INBOX = "inbox"

    /** The activity feed and the circle's shared recap. Reached from Friends, beside the inbox. */
    const val CIRCLE = "circle"

    /**
     * Ids and names both contain `/` and `:` — a `navidrome:al-42` id, an artist called
     * "AC/DC" — so they are encoded into the path rather than interpolated raw, which produced a
     * route with extra segments that matched nothing.
     */
    fun album(albumId: String): String = "album/${albumId.encodeForRoute()}"

    fun playlist(playlistId: String): String = "playlist/${playlistId.encodeForRoute()}"

    /**
     * An artist page, carrying the backend's id for them when the caller knows it.
     *
     * The name alone is not an identity — two artists can share one, differing only in case, and
     * Room's lookups fold case deliberately so that one artist spelled differently by two backends
     * stays together. Deriving the id from whatever Room returned for the name therefore picked
     * *an* artist rather than *the* artist, and a page opened from a yuri track could go and fetch
     * Yuri's discography instead.
     *
     * Whoever tapped almost always knows: a track carries `artistId`, a related-artist tile is one.
     * Passing it is what makes the page about the person you pointed at.
     */
    fun artist(name: String, artistId: String? = null): String {
        val base = "artist/${name.encodeForRoute()}"
        return if (artistId.isNullOrBlank()) base else "$base?artistId=${artistId.encodeForRoute()}"
    }

    fun profile(username: String): String = "profile/${username.encodeForRoute()}"

    private fun String.encodeForRoute(): String = URLEncoder.encode(this, "UTF-8")

    val topLevel: Set<String> = TopLevelDestination.entries.map { it.route }.toSet()

    /**
     * Routes that keep the dock and the docked player.
     *
     * Album and artist pages are browsing, not a modal task: hiding the player to show a
     * tracklist would stop the music's controls being reachable from the very screen you opened
     * *from* the player. The queue and the login flows still take the screen over.
     */
    val withChrome: Set<String> =
        topLevel + ALBUM + PLAYLIST + ARTIST + PROFILE + MY_PROFILE + SETTINGS + STATS + HISTORY + MERGE_PREVIEW + JAM + "jam" +
            INBOX + CIRCLE
}
