package com.wander.android.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Shape
import com.wander.android.ui.theme.LocalReducedMotion
import kotlinx.coroutines.delay

/**
 * A card's artwork rests as a gently rounded square — [MaterialShapes.Square] — rather than a
 * scalloped shape: the deeper cuts (`Cookie4Sided`, `Clover4Leaf`, `Burst`, ...) read as a fun
 * motion in isolation but crop enough of a square cover that the art underneath got hard to make
 * out across a whole shelf of them. Still morphs to a circle while held.
 */
@Composable
internal fun rememberShelfArtworkShape(isPressed: Boolean): Shape {
    return rememberPressMorphShape(
        resting = MaterialShapes.Square,
        pressed = MaterialShapes.Circle,
        isPressed = isPressed
    )
}

/**
 * A short, staggered pop as a shelf card first enters composition — a fresh card scrolling into
 * view, or the shelf appearing for the first time — instead of every card simply *being there*.
 *
 * Plays once per composable lifetime, so it replays each time a `LazyRow`/`LazyGrid` item leaves
 * and re-enters the composition window, the same rule those lists already apply to everything
 * else about a recycled item.
 *
 * The stagger and the spring both got cut down once already, and too far the other way: this used
 * to cascade up to 320ms (40ms/index) before an item even started, then settle on
 * `slowSpatialSpec`'s slower, bouncier spring — long enough that a fast fling moved an item
 * off-screen mid-pop, frozen at whatever scale it had reached rather than the finished card.
 * `Spring.StiffnessHigh` fixed that by finishing in a handful of milliseconds, but that also made
 * the pop too quick to actually see at any scroll speed. `StiffnessMedium` here settles in around
 * a quarter of a second — long enough to read as a pop, short enough that only a genuinely extreme
 * fling would still catch an item mid-way, which no fixed-duration animation can rule out entirely
 * without reading the list's actual scroll velocity. Critically damped either way: an overshoot
 * has to un-overshoot before it's done, which is exactly the kind of lag this is trying to avoid.
 *
 * This is one of the few animations in the app that doesn't read `MaterialTheme.motionScheme` —
 * the stagger `delay()` in particular has nothing to do with a spec — so it checks
 * [LocalReducedMotion] directly rather than being covered for free by the theme's scheme swap.
 */
@Composable
internal fun rememberShelfEntranceScale(index: Int): Float {
    if (LocalReducedMotion.current) return 1f

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(MaxStaggeredIndex) * StaggerStepMs)
        appeared = true
    }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else EntranceStartScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "shelfCardEntrance"
    )
    return scale
}

private const val StaggerStepMs = 12L
private const val MaxStaggeredIndex = 5
private const val EntranceStartScale = 0.75f
