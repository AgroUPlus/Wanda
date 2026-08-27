package com.wander.android.ui.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder

/**
 * Navigates only once the destination on screen has actually settled.
 *
 * Destination transitions are springs with a long tail (see `NavTransitions`), and for the whole
 * of that tail *both* screens are composed and both are hit-testable. A tap that lands in that
 * window is delivered to whichever composable happens to occupy that pixel — which, coming out of
 * Friends into Library, is a friend row sitting exactly where the song row is about to be. The
 * user taps a song and a profile opens.
 *
 * Navigation gives every back stack entry a lifecycle that only reaches RESUMED once its
 * transition has finished, so "is anything still moving?" is a question that can simply be asked.
 * A click arriving before then is a click meant for a screen that is on its way out, and is
 * dropped rather than acted on.
 *
 * This also settles the older double-navigation case: two fast taps on one row used to push the
 * same detail screen twice, because the second landed before the first had finished opening.
 */
internal fun NavHostController.navigateSettled(
    route: String,
    builder: NavOptionsBuilder.() -> Unit = {}
) {
    val settled = currentBackStackEntry?.lifecycle?.currentState
        ?.isAtLeast(Lifecycle.State.RESUMED) == true
    if (!settled) return
    navigate(route, builder)
}
