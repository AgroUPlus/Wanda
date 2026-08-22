package com.wander.android.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * Material's shared-axis motion. Without these every destination fell back to NavHost's default
 * fade, which reads as a cut rather than a movement.
 *
 * Tabs move along X because they are peers; detail screens move along Z (a scale-and-fade) because
 * they sit on top of what opened them.
 */
private const val FADE_MS = 200
private const val Z_SCALE_IN = 0.92f
private const val Z_SCALE_OUT = 1.06f

/** Distance a shared-axis-X transition travels, as a fraction of the screen. */
private const val X_TRAVEL = 6

/**
 * Movement is a spring, so destinations arrive with a little overshoot instead of easing flatly
 * into place. The duration this replaced was 350 ms of `tween`, which is the same *length* of
 * motion but none of the character.
 */
private fun <T> spatial() = spring<T>(
    // Lighter than the player's: a screen transition is a smaller visual move, and it can carry a
    // little more spring without reading as slack.
    dampingRatio = 0.85f,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = null
)

/**
 * Fades stay a tween on purpose. An overshooting alpha would have to pass 1 and come back, which
 * reads as a flicker rather than as motion.
 */
private fun <T> effects() = tween<T>(durationMillis = FADE_MS)

/**
 * A tab arriving, from the side it sits on in the navigation bar: Library → Home comes in from the
 * left, Search → Settings from the right.
 *
 * When the other screen is not a tab there is no left or right to speak of — it is an artist page
 * being dismissed, or the queue closing — so the tab comes back along Z instead, matching the way
 * that screen left.
 */
fun tabEnter(from: String?, to: String?): EnterTransition {
    val direction = tabDirection(from, to) ?: return detailPopEnter()
    return slideInHorizontally(spatial()) { width -> direction * width / X_TRAVEL } +
        fadeIn(effects())
}

/** The counterpart: the outgoing tab leaves towards the side the incoming one came from. */
fun tabExit(from: String?, to: String?): ExitTransition {
    val direction = tabDirection(from, to) ?: return detailExit()
    return slideOutHorizontally(spatial()) { width -> -direction * width / X_TRAVEL } +
        fadeOut(effects())
}

/** Detail screens grow in from slightly behind the caller. */
fun detailEnter(): EnterTransition = scaleIn(spatial(), initialScale = Z_SCALE_IN) + fadeIn(effects())

fun detailExit(): ExitTransition = scaleOut(spatial(), targetScale = Z_SCALE_OUT) + fadeOut(effects())

fun detailPopEnter(): EnterTransition =
    scaleIn(spatial(), initialScale = Z_SCALE_OUT) + fadeIn(effects())

fun detailPopExit(): ExitTransition =
    scaleOut(spatial(), targetScale = Z_SCALE_IN) + fadeOut(effects())

/**
 * Which way the bar is being travelled: +1 rightwards, -1 leftwards, 0 for a tab to itself.
 * Null when either end is not a tab, and so has no place in that order.
 */
private fun tabDirection(from: String?, to: String?): Int? {
    val fromIndex = tabIndex(from) ?: return null
    val toIndex = tabIndex(to) ?: return null
    return toIndex.compareTo(fromIndex)
}

/**
 * Position in the bar. Arguments are stripped first: Search is registered as
 * `search?query={query}`, so matching the raw route against the tab's own `search` never hit and
 * every transition involving Search fell back to the same default direction.
 */
private fun tabIndex(route: String?): Int? {
    val base = route?.substringBefore("?") ?: return null
    return TopLevelDestination.entries.indexOfFirst { it.route == base }.takeIf { it >= 0 }
}
