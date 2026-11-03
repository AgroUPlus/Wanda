package com.wander.android.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

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

/** A card's corner, as a share of its shorter side: close to `MaterialShapes.Square` at rest. */
const val CARD_ARTWORK_CORNER = 0.15f

/** A hero runs edge to edge: square corners. */
const val HERO_ARTWORK_CORNER = 0f

/**
 * Carries a cover from the card that was tapped into the page it opens, and back again (a back
 * swipe seeks it with the finger, like the rest of the transition).
 *
 * - The flight is clipped to a plain rounded rectangle whose corner goes from [restingCorner] (this
 *   end's) to [travelCorner] (the other end's) with the page's own transition, so both ends draw
 *   the same corner at every frame. Clipping to the card's own shape instead flew its press morph,
 *   half way to a circle at the moment of the tap, and grew it to the hero's size.
 * - Both ends are laid out at the moving size, not scaled, so a card's corners are not blown up
 *   with it; each picture fades quickly to the other.
 * - The bounds move on a spring that does not overshoot: one that did grew the cover past the hero
 *   before settling, and held the page's transition (and its taps) open for its long tail.
 *
 * [key] null, no shell around it, or a key already flying from elsewhere on this screen (an album
 * listed twice) leaves the artwork where it is: two covers answering to one key flicker.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedArtwork(key: String?, restingCorner: Float, travelCorner: Float): Modifier {
    val shared = LocalSharedTransitionScope.current
    val destination = LocalDestinationScope.current
    if (shared == null || destination == null || key == null) return this
    val claimed = rememberSharedKeyClaim(key)
    val corner by destination.transition.animateFloat(label = "artworkCorner") { state ->
        if (state == EnterExitState.Visible) restingCorner else travelCorner
    }
    val clip = remember { CornerClip() }
    clip.fraction = { corner }
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    // Not the expressive scheme's: its spatial springs overshoot, which reads as the cover
    // swelling past the hero; the standard one settles without passing its target.
    val bounds = remember { MotionScheme.standard().defaultSpatialSpec<Rect>() }
    return if (!claimed) {
        this
    } else {
        with(shared) {
            this@sharedArtwork.sharedBounds(
                sharedContentState = rememberSharedContentState(key),
                animatedVisibilityScope = destination,
                enter = fadeIn(effects),
                exit = fadeOut(effects),
                boundsTransform = BoundsTransform { _, _ -> bounds },
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                clipInOverlayDuringTransition = clip
            )
        }
    }
}

/** A rounded rectangle over the moving bounds, its corner a share of their shorter side. */
@OptIn(ExperimentalSharedTransitionApi::class)
private class CornerClip : SharedTransitionScope.OverlayClip {
    var fraction: () -> Float = { 0f }
    private val path = Path()

    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density
    ): Path {
        val radius = bounds.minDimension * fraction().coerceIn(0f, 0.5f)
        path.reset()
        path.addRoundRect(RoundRect(bounds, CornerRadius(radius)))
        return path
    }
}

/**
 * Keeps this above a travelling cover. The cover flies in the shared layout's overlay, over the
 * whole page, so the hero's sheet and the top bar sat under it until it landed; while a cover is in
 * flight these are drawn in that overlay too, above it ([layer]: higher draws later). Whole from
 * the first frame of an opening, so the cover never shows through them, and gone quickly on the
 * way out, so they do not linger over the screen coming back.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.aboveSharedArtwork(layer: Float = 1f): Modifier {
    val shared = LocalSharedTransitionScope.current
    val destination = LocalDestinationScope.current
    if (shared == null || destination == null) return this
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    return with(shared) {
        with(destination) {
            this@aboveSharedArtwork
                .renderInSharedTransitionScopeOverlay(zIndexInOverlay = layer)
                .animateEnterExit(enter = EnterTransition.None, exit = fadeOut(effects))
        }
    }
}
