package com.wander.android.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale

/**
 * The shell's [SharedTransitionScope], wrapped around the nav host. Null outside it (previews, a
 * dialog's own window), where [sharedArtwork] does nothing.
 */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The destination's own enter/exit scope, provided by every nav destination. */
val LocalDestinationScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Wraps the nav host so covers can travel between destinations ([sharedArtwork]). What travels is
 * drawn in this layout's overlay: above the destinations, below the dock and the player.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedArtworkLayout(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    SharedTransitionLayout(modifier) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this, content = content)
    }
}

/** The key an album's cover travels under, from wherever it was tapped to the album's page. */
fun albumArtKey(albumId: String): String = "art:album:$albumId"

/** The same, for a playlist's cover. */
fun playlistArtKey(playlistId: String): String = "art:playlist:$playlistId"

/**
 * Carries a cover from the card that was tapped into the page it opens, and back again (a back
 * swipe seeks it with the finger, like the rest of the transition). Both ends draw their own
 * picture and cross-fade inside the moving bounds, so a hero whose bigger decode has not landed
 * yet still shows the card's picture on the way.
 *
 * [key] null, or no shell around it, leaves the artwork where it is. A key must be on screen once
 * per destination: a row that repeats a grid's albums passes null.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedArtwork(key: String?, shape: Shape): Modifier {
    val shared = LocalSharedTransitionScope.current
    val destination = LocalDestinationScope.current
    if (shared == null || destination == null || key == null) return this
    // The theme's spatial spring, like every other movement in the app.
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    return with(shared) {
        this@sharedArtwork.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = destination,
            boundsTransform = BoundsTransform { _, _ -> spatial },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Crop),
            clipInOverlayDuringTransition = OverlayClip(shape)
        )
    }
}

/**
 * Keeps this above a travelling cover. The cover flies in the shared layout's overlay, over the
 * whole page, so the hero's sheet and the top bar sat under it until it landed; while a cover is in
 * flight these are drawn in that overlay too, above it ([layer]: higher draws later), and fade
 * with the page they belong to.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.aboveSharedArtwork(layer: Float = 1f): Modifier {
    val shared = LocalSharedTransitionScope.current
    val destination = LocalDestinationScope.current
    if (shared == null || destination == null) return this
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    return with(shared) {
        with(destination) {
            this@aboveSharedArtwork
                .renderInSharedTransitionScopeOverlay(zIndexInOverlay = layer)
                .animateEnterExit(enter = fadeIn(effects), exit = fadeOut(effects))
        }
    }
}
