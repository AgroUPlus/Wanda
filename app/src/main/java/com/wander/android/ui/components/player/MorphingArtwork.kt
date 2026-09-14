package com.wander.android.ui.components.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.Artwork
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Decode size for the travelling cover. **Constant on purpose.**
 *
 * Deriving it from the measured bounds meant the first frames of a drag — before the full player
 * had reported its bounds — asked for a 48 dp bitmap, and the next frame asked for a 360 dp one.
 * That is a different `ImageRequest`, so Coil decoded a second bitmap and swapped it in: the
 * "small blurry cover that grows, then gets replaced by a big one" bug. One size, one request,
 * one bitmap, for the whole gesture.
 */
/** Clear of the rounded corner at full size, and off the artwork's busiest region. */
private val BadgeInset = 14.dp

private val MorphArtworkSize = 360.dp

/**
 * Corner as a percentage of the box, so the radius grows with the cover on its own. A fixed dp
 * radius would need a second animated value and would read as a small corner stretched across a
 * large image.
 */
private val MorphShape = RoundedCornerShape(percent = 12)

/** Space between the current cover and the neighbours peeking in either side of it. */
private val PeekGap = 16.dp

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
    visible: Boolean,
    swipe: TrackSwipeState,
    previousUrl: String?,
    nextUrl: String?,
    modifier: Modifier = Modifier,
    alpha: () -> Float = { 1f },
    fingerprintStatus: com.wander.android.data.repository.FingerprintStatus =
        com.wander.android.data.repository.FingerprintStatus.MISSING,
    carouselEnabled: Boolean = true
) {
    if (!visible) return

    // Checked here rather than inside the layout block below: the docked strip reports its bounds
    // one pass after the sheet first appears, and a `layout` lambda **must** measure its
    // measurable exactly once on every path. Bailing out inside it threw as soon as a track
    // started playing.
    val mini = anchors.miniBounds ?: return

    val showPeeks = swipe.isSwiping || (carouselEnabled && rawProgress() > 0.8f)
    if (showPeeks) {
        // Drawn before the current cover so it stays on top as the neighbours slide under it.
        PeekArtwork(previousUrl, anchors, mini, progress, rawProgress, swipe, side = -1, carouselEnabled = carouselEnabled)
        PeekArtwork(nextUrl, anchors, mini, progress, rawProgress, swipe, side = 1, carouselEnabled = carouselEnabled)
    }

    Box(
        // `graphicsLayer` goes *inside* `layout`, and the order is load-bearing. An alpha below 1
        // forces an offscreen layer, and that layer is clipped to the bounds of the node it sits
        // on. Outside the `layout` those bounds are this node's own box at the parent's origin,
        // while the cover is placed far down the screen at `rect.top` — so fading it showed only
        // the sliver where the two happened to overlap, which is the "top half of the cover"
        // effect. Inside, the layer is the placeable itself and travels with it.
        modifier = modifier
            .layout { measurable, _ ->
                val rect = anchors.currentRect(mini, rawProgress)
                val width = rect.width.roundToInt().coerceAtLeast(0)
                val height = rect.height.roundToInt().coerceAtLeast(0)
                val placeable = measurable.measure(Constraints.fixed(width, height))
                // The pitch the neighbours are spaced by, published so a committed swipe carries
                // this cover off by exactly one slot instead of an arbitrary distance.
                swipe.stepPx = rect.width + PeekGap.toPx()
                layout(width, height) {
                    placeable.place(
                        x = (rect.left + swipe.offsetX.value).roundToInt(),
                        y = rect.top.roundToInt()
                    )
                }
            }
            .graphicsLayer {
                this.alpha = alpha()
                if (carouselEnabled && progress() > 0.8f) {
                    val step = swipe.stepPx.takeIf { it > 0f } ?: FullExitDistance
                    val offset = swipe.offsetX.value
                    val progressAway = (abs(offset) / step).coerceIn(0f, 1f)
                    val scale = lerpFloat(1f, 0.85f, progressAway)
                    scaleX = scale
                    scaleY = scale
                    rotationY = (-offset / step * 12f).coerceIn(-14f, 14f)
                    cameraDistance = 12f * density
                }
            }
    ) {
        Artwork(
            // While a skip is settling this is the cover the gesture already put in the slot; see
            // [TrackSwipeState.pendingArtworkUrl].
            url = swipe.pendingArtworkUrl ?: url,
            contentDescription = contentDescription,
            sizeDp = MorphArtworkSize,
            shape = MorphShape,
            // Nothing to cross-fade: this is one continuous element, and animating it is what made
            // the hand-off visible.
            crossfade = false,
            modifier = Modifier.fillMaxSize()
        )

        // Bottom-left, and only once the sheet is open. On the docked strip the cover is a
        // thumbnail and a six-pixel dot on it would be lint rather than information, so it fades in
        // with the expansion rather than riding the cover all the way down.
        //
        // Drawn here rather than in `NowPlayingScreen` because this is the cover the user is
        // looking at: that screen's artwork slot is an empty box reporting bounds, so the badge it
        // contained was in a branch the real player never takes.
        com.wander.android.ui.components.FingerprintBadge(
            status = fingerprintStatus,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(BadgeInset)
                .graphicsLayer { this.alpha = smoothStep(progress(), 0.75f, 1f) }
        )
    }
}

/**
 * The cover one step either side of the current one, parked just off the edge of the current
 * cover's box and dragged in with the finger.
 *
 * [side] is -1 for the previous track (sitting to the left) and +1 for the next.
 */
@Composable
private fun PeekArtwork(
    url: String?,
    anchors: PlayerArtworkAnchors,
    mini: Rect,
    progress: () -> Float,
    rawProgress: () -> Float,
    swipe: TrackSwipeState,
    side: Int,
    carouselEnabled: Boolean = true
) {
    if (url == null) return

    Box(
        // Layer inside the layout, for the reason spelled out in [MorphingArtwork] — these are
        // placed at the same offsets and would be clipped the same way.
        modifier = Modifier
            .layout { measurable, _ ->
                // The same rect the current cover is using, overshoot included. Ranking these off
                // the clamped `progress` while the cover rode `rawProgress` meant the filmstrip
                // was pitched from a *different* box than `swipe.stepPx` for the whole settle:
                // the neighbours held still at their resting spacing while the cover between them
                // grew past its frame, so the gaps visibly closed up and sprang back open.
                val rect = anchors.currentRect(mini, rawProgress)
                val width = rect.width.roundToInt().coerceAtLeast(0)
                val height = rect.height.roundToInt().coerceAtLeast(0)
                val placeable = measurable.measure(Constraints.fixed(width, height))
                val step = rect.width + PeekGap.toPx()
                layout(width, height) {
                    placeable.place(
                        x = (rect.left + swipe.offsetX.value + side * step).roundToInt(),
                        y = rect.top.roundToInt()
                    )
                }
            }
            .graphicsLayer {
                val step = swipe.stepPx.takeIf { it > 0f } ?: FullExitDistance
                val offset = swipe.offsetX.value
                val reach = abs(offset) / DistanceThreshold

                if (carouselEnabled) {
                    val currentX = offset + side * step
                    val distRatio = (abs(currentX) / step).coerceIn(0f, 1f)
                    val centerProximity = (1f - distRatio).coerceIn(0f, 1f)

                    val scale = lerpFloat(0.85f, 1f, centerProximity)
                    scaleX = scale
                    scaleY = scale
                    rotationY = (side * (1f - centerProximity) * 12f).coerceIn(-14f, 14f)
                    cameraDistance = 12f * density

                    val baseAlpha = lerpFloat(0.5f, 1f, centerProximity)
                    alpha = smoothStep(progress(), 0.82f, 0.98f) * baseAlpha
                } else {
                    alpha = smoothStep(progress(), 0.5f, 0.9f) * reach.coerceIn(0f, 1f)
                }
            }
    ) {
        Artwork(
            url = url,
            contentDescription = null,
            sizeDp = MorphArtworkSize,
            shape = MorphShape,
            crossfade = false,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

/**
 * Where the cover sits right now, between the docked strip and the full player.
 *
 * Until the full player has been measured there is nowhere to travel to, so this stays on the
 * mini rect rather than interpolating towards a placeholder.
 */
private fun PlayerArtworkAnchors.currentRect(mini: Rect, progress: () -> Float): Rect {
    val full = fullBounds ?: return mini
    val p = progress()
    // Past 1 the cover is overshooting its resting frame, and the easing curve cannot help: a
    // cubic bezier easing *throws* outside 0..1. Extrapolate linearly instead, which meets the
    // eased curve exactly at 1 (easing(1) == 1) and so stays continuous through the handover.
    val t = if (p > 1f) p else FastOutSlowInEasing.transform(p.coerceIn(0f, 1f))
    return lerp(mini, full, t)
}
