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
     * Sharing music phone to phone with no network at all.
     *
     * Reached from the Friends header rather than from the tab's body, because the body is behind
     * `isPaired` and this is the one social feature that deliberately needs no server.
     */
    const val OFFGRID = "offgrid"

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
     * A route pattern without its optional arguments.
     *
     * `artist/{artist}?artistId={artistId}` and `artist/{artist}` are the same destination — the
     * query half is what the caller *may* pass, not part of the address. Both sides of every
     * lookup below go through this, which is the bug it exists for: the caller stripped the tail
     * off the current route and then looked it up in a set that still had it, so the artist page
     * matched nothing and lost its player and its dock.
     */
    private fun String.withoutArgs(): String = substringBefore("?")

    /**
     * Routes that keep the dock row — the search field and the Friends button — under the player.
     *
     * Only the roots. The dock row is how you get *between* the three of them and how you search
     * from the library; on a page reached from one of them it is neither, and it pushed the thing
     * the page was opened to show a dock row further up the screen for nothing.
     */
    private val withDock: Set<String> = topLevel.map { it.withoutArgs() }.toSet()

    /**
     * Routes that keep the docked player.
     *
     * Album and artist pages are browsing, not a modal task: hiding the player to show a
     * tracklist would stop the music's controls being reachable from the very screen you opened
     * *from* the player. On those the strip stands alone — see [showsDock]. The queue and the
     * login flows still take the screen over.
     */
    private val withChrome: Set<String> =
        (topLevel + ALBUM + PLAYLIST + ARTIST + PROFILE + MY_PROFILE + SETTINGS + STATS +
            HISTORY + MERGE_PREVIEW + JAM + JAM_ROUTE + INBOX + CIRCLE + OFFGRID)
            .map { it.withoutArgs() }
            .toSet()

    /** Whether [route] keeps the docked player. */
    fun showsChrome(route: String?): Boolean = route?.withoutArgs() in withChrome

    /** Whether [route] keeps the dock row under the player. */
    fun showsDock(route: String?): Boolean = route?.withoutArgs() in withDock
}
