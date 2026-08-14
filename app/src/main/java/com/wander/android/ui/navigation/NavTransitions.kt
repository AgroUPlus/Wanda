package com.wander.android.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

/**
 * Material's shared-axis motion. Without these every destination fell back to NavHost's default
 * fade, which reads as a cut rather than a movement.
 *
 * Tabs move along X because they are peers; detail screens move along Z (a scale-and-fade) because
 * they sit on top of what opened them.
 */
private const val DURATION_MS = 350
private const val FADE_MS = 200
private const val Z_SCALE_IN = 0.92f
private const val Z_SCALE_OUT = 1.06f

/** Distance a shared-axis-X transition travels, as a fraction of the screen. */
private const val X_TRAVEL = 6

private fun <T> spatial() = tween<T>(durationMillis = DURATION_MS)

private fun <T> effects() = tween<T>(durationMillis = FADE_MS)

fun AnimatedContentTransitionScope<NavBackStackEntry>.tabEnter(forward: Boolean): EnterTransition =
    slideInHorizontally(spatial()) { width -> if (forward) width / X_TRAVEL else -width / X_TRAVEL } +
        fadeIn(effects())

fun AnimatedContentTransitionScope<NavBackStackEntry>.tabExit(forward: Boolean): ExitTransition =
    slideOutHorizontally(spatial()) { width -> if (forward) -width / X_TRAVEL else width / X_TRAVEL } +
        fadeOut(effects())

/** Detail screens grow in from slightly behind the caller. */
fun detailEnter(): EnterTransition = scaleIn(spatial(), initialScale = Z_SCALE_IN) + fadeIn(effects())

fun detailExit(): ExitTransition = scaleOut(spatial(), targetScale = Z_SCALE_OUT) + fadeOut(effects())

fun detailPopEnter(): EnterTransition =
    scaleIn(spatial(), initialScale = Z_SCALE_OUT) + fadeIn(effects())

fun detailPopExit(): ExitTransition =
    scaleOut(spatial(), targetScale = Z_SCALE_IN) + fadeOut(effects())

/** Tab order decides which way an X transition travels, so the motion matches the bar layout. */
fun isForward(from: String?, to: String?): Boolean {
    val fromIndex = TopLevelDestination.entries.indexOfFirst { it.route == from }
    val toIndex = TopLevelDestination.entries.indexOfFirst { it.route == to }
    return fromIndex < 0 || toIndex < 0 || toIndex > fromIndex
}
