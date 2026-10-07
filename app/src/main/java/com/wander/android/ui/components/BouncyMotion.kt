package com.wander.android.ui.components

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * This app's one bouncy spring, pulled out of [ElasticSwipeBox] so a second place wanting the same
 * "a little overshoot past rest on release" feel doesn't have to retune it from scratch.
 *
 * Everywhere else in the app deliberately reaches for [Spring.DampingRatioNoBouncy] instead — see
 * `CardMotion.kt` — because a bounce on four values animating together
 * (an offset's x/y, a width, a height) reads as jitter, not liveliness. This is for the opposite
 * case: one value, reacting to something the person just did, where the overshoot *is* the point.
 */
fun <T> bouncySpec(): FiniteAnimationSpec<T> = spring(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow
)

/**
 * The spring the dock row, the mini player's resting inset and the content padding all share while
 * navigation shows or hides the dock. Deliberately much tamer than [bouncySpec] — that one's ~16%
 * overshoot reads as lively on a lone card, but on a card returning to its rest position, with the
 * player and the list moving in lockstep above it, it overshoots visibly and settles late. One
 * spec for all three is what keeps them from drifting apart.
 */
fun <T> dockSpec(): FiniteAnimationSpec<T> = spring(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow
)
