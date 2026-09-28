package com.wander.android.ui.components

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * This app's one bouncy spring, pulled out of [ElasticSwipeBox] so a second place wanting the same
 * "a little overshoot past rest on release" feel doesn't have to retune it from scratch.
 *
 * Everywhere else in the app deliberately reaches for [Spring.DampingRatioNoBouncy] instead — see
 * `CardMotion.kt`, `TravelingHighlight.kt` — because a bounce on four values animating together
 * (an offset's x/y, a width, a height) reads as jitter, not liveliness. This is for the opposite
 * case: one value, reacting to something the person just did, where the overshoot *is* the point.
 */
fun <T> bouncySpec(): FiniteAnimationSpec<T> = spring(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow
)
