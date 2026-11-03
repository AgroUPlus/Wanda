package com.wander.android.ui.navigation

import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder

/**
 * Navigates for a tap on the screen that is on screen now, and drops one from a screen on its way out.
 *
 * Destination transitions are springs with a long tail (see `NavTransitions`), and for that tail
 * *both* screens are composed and hit-testable. A tap landing on the leaving one used to act for it
 * — coming out of Friends into Library, a friend row sat where the song row was about to be, and
 * the user tapped a song and a profile opened. Waiting for the arriving entry to reach RESUMED
 * fixed that, but at the cost of every tap on the new screen for the whole tail: a second or so of
 * an app that would not answer after leaving an album.
 *
 * So the question is asked of the tap instead. [TouchOrigin] knows which destination the finger
 * came down on; a tap from any entry but the current one is meant for a screen that is leaving and
 * is dropped, and one on the arriving screen goes through at once. That also covers two fast taps
 * on one row: after the first, the row's screen is no longer current. A tap that came down on no
 * destination (the dock, the player) has no origin and always goes through.
 */
internal fun NavHostController.navigateSettled(
    route: String,
    builder: NavOptionsBuilder.() -> Unit = {}
) {
    val origin = TouchOrigin.entry
    if (origin != null && origin != currentBackStackEntry) return
    navigate(route, builder)
}
