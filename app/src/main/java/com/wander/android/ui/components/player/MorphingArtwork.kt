package com.wander.android.ui.components.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A subtle "settle," not a dramatic shrink — the docked cover barely moves, it just stops
 * breathing. Docked only: the full player's cover runs edge to edge, where any shrink would open a
 * gap at the screen's edges, so it breathes by [PLAYING_ZOOM] inside its frame instead.
 */
private const val PAUSED_SCALE = 0.96f

/**
 * How far the full player's cover zooms into its own frame while playing, easing back on pause.
 * The picture moves, never its edges. Shared with [PlayerCoverCarousel] so the hand-off matches.
 */
internal const val PLAYING_ZOOM = 1.04f

/**
 * Decode size for the travelling cover. **Constant on purpose.**
 *
 * Deriving it from the measured bounds meant the first frames of a drag — before the full player
 * had reported its bounds — asked for a 48 dp bitmap, and the next frame asked for a 360 dp one.
 * That is a different `ImageRequest`, so Coil decoded a second bitmap and swapped it in: the
 * "small blurry cover that grows, then gets replaced by a big one" bug. One size, one request,
 * one bitmap, for the whole gesture. Shared with [PeekArtwork], which decodes neighbour covers at
 * the same size.
 */
internal val MorphArtworkSize = 360.dp

/**
 * Corner as a percentage of the box, so the radius grows with the cover on its own. A fixed dp
 * radius would need a second animated value and would read as a small corner stretched across a
 * large image. Shared with [PeekArtwork], which is never docked and so never needs [MorphingCoverShape].
 */
internal val MorphShape = RoundedCornerShape(percent = 12)

/**
 * Space between the current cover and the neighbours waiting either side of it.
 *
 * Wide enough to park them off-screen at rest. The cover stops short of the full width now, which
 * left a margin either side — at the old 16dp the neighbours sat *in* that margin and were simply
 * visible, so the player showed three covers when it meant to show one. This is the gap that keeps
 * them past the screen edge until a swipe actually pulls one in.
 */
internal val PeekGap = 72.dp

/**
 * The single cover art shared by the docked strip and the full player, plus the previous and next
 * covers waiting either side of it.
 *
 * Composed once; only its geometry changes, inside deferred `layout` lambdas, so dragging the
 * sheet or swiping the cover neither recomposes it nor re-requests the image.
 *
 * The neighbours are composed only while a swipe is in flight ([TrackSwipeState.isSwiping], which
 * flips twice per gesture rather than every frame) and are faded out entirely while the player is
 * docked — the strip is far too small for a three-cover filmstrip to read as anything but noise.
 */
@Composable
internal fun MorphingArtwork(
    url: String?,
    contentDescription: String?,
    anchors: PlayerArtworkAnchors,
    progress: () -> Float,
    rawProgress: () -> Float,
    swipe: TrackSwipeState,
    previousUrl: String?,
    nextUrl: String?,
    canPrevious: Boolean = true,
    canNext: Boolean = true,
    modifier: Modifier = Modifier,
    alpha: () -> Float = { 1f },
    carouselEnabled: Boolean = true,
    isPlaying: Boolean = true,
    /** Drawn over the cover inside its shape, so it travels and shrinks with it — the video clip. */
    overlay: @Composable () -> Unit = {}
) {
    // A small settle on pause, springing back on resume — the same "breathing" idiom as the
    // player's own transport buttons, just on the thing those buttons control. The frame settles
    // while docked; open, the picture zooms inside a frame that stays put (see [PLAYING_ZOOM]).
    val pauseScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else PAUSED_SCALE,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "coverPauseScale"
    )
    val playingZoom by animateFloatAsState(
        targetValue = if (isPlaying) PLAYING_ZOOM else 1f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "coverPlayingZoom"
    )

    // Checked here rather than inside the layout block below: the docked strip reports its bounds
    // one pass after the sheet first appears, and a `layout` lambda **must** measure its
    // measurable exactly once on every path. Bailing out inside it threw as soon as a track
    // started playing.
    val mini = anchors.miniBounds ?: return

    val showPeeks = swipe.isSwiping || (carouselEnabled && rawProgress() > 0.8f)
    if (showPeeks) {
        // Drawn before the current cover so it stays on top as the neighbours slide under it.
        if (canPrevious) {
            PeekArtwork(
                previousUrl, anchors, mini, progress, rawProgress, swipe,
                side = -1, carouselEnabled = carouselEnabled
            )
        }
        if (canNext) {
            PeekArtwork(
                nextUrl, anchors, mini, progress, rawProgress, swipe,
                side = 1, carouselEnabled = carouselEnabled
            )
        }
    }

    Box(
        // `graphicsLayer` goes *inside* `layout`, and the order is load-bearing. An alpha below 1
        // forces an offscreen layer, and that layer is clipped to the bounds of the node it sits
        // on. Outside the `layout` those bounds are this node's own box at the parent's origin,
        // while the cover is placed far down the screen at `rect.top` — so fading it showed only
        // the sliver where the two happened to overlap, which is the "top half of the cover"
        // effect. Inside, the layer is the placeable itself and travels with it.
        modifier = modifier
            .placeCover(anchors, mini, rawProgress, swipe)
            .graphicsLayer {
                this.alpha = alpha()
                val settle = lerpFloat(pauseScale, 1f, progress().coerceIn(0f, 1f))
                scaleX = settle
                scaleY = settle
                if (carouselEnabled && progress() > 0.8f) tiltWithSwipe(swipe)
            }
    ) {
        MorphingCoverContent(
            // While a skip is settling this is the cover the gesture already put in the slot;
            // see [TrackSwipeState.pendingArtworkUrl].
            url = swipe.pendingArtworkUrl ?: url,
            contentDescription = contentDescription,
            progress = progress,
            playingZoom = { playingZoom },
            overlay = overlay
        )
    }
}

/**
 * Sizes and places the cover at [currentRect], shifted by the live swipe. Every path measures
 * exactly once, as a `layout` block must.
 */
private fun Modifier.placeCover(
    anchors: PlayerArtworkAnchors,
    mini: Rect,
    rawProgress: () -> Float,
    swipe: TrackSwipeState
): Modifier = layout { measurable, _ ->
    val rect = anchors.currentRect(mini, rawProgress)
    val width = rect.width.roundToInt().coerceAtLeast(0)
    val height = rect.height.roundToInt().coerceAtLeast(0)
    val placeable = measurable.measure(Constraints.fixed(width, height))
    // The pitch the neighbours are spaced by, published so a committed swipe carries this cover
    // off by exactly one slot instead of an arbitrary distance.
    swipe.stepPx = rect.width + PeekGap.toPx()
    layout(width, height) {
        placeable.place(
            x = (rect.left + swipe.shift).roundToInt(),
            y = rect.top.roundToInt()
        )
    }
}

/** Shrinks the open cover and turns it on its vertical axis as a swipe carries it away. */
private fun GraphicsLayerScope.tiltWithSwipe(swipe: TrackSwipeState) {
    val step = swipe.stepPx.takeIf { it > 0f } ?: FullExitDistance
    val offset = swipe.shift
    val progressAway = (abs(offset) / step).coerceIn(0f, 1f)
    val swipeScale = lerpFloat(1f, 0.85f, progressAway)
    scaleX *= swipeScale
    scaleY *= swipeScale
    rotationY = (-offset / step * 12f).coerceIn(-14f, 14f)
    cameraDistance = 12f * density
}

/**
 * Where the cover sits right now, between the docked strip and the full player.
 *
 * Until the full player has been measured there is nowhere to travel to, so this stays on the
 * mini rect rather than interpolating towards a placeholder.
 */
internal fun PlayerArtworkAnchors.currentRect(mini: Rect, progress: () -> Float): Rect {
    val full = fullBounds ?: return mini
    val p = progress()
    // Past 1 the cover is overshooting its resting frame, and the easing curve cannot help: a
    // cubic bezier easing *throws* outside 0..1. Extrapolate linearly instead, which meets the
    // eased curve exactly at 1 (easing(1) == 1) and so stays continuous through the handover.
    val t = if (p > 1f) p else FastOutSlowInEasing.transform(p.coerceIn(0f, 1f))
    return lerp(mini, full, t)
}
