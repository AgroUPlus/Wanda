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
        com.wander.android.data.repository.FingerprintStatus.MISSING
) {
    if (!visible) return

    // Checked here rather than inside the layout block below: the docked strip reports its bounds
    // one pass after the sheet first appears, and a `layout` lambda **must** measure its
    // measurable exactly once on every path. Bailing out inside it threw as soon as a track
    // started playing.
    val mini = anchors.miniBounds ?: return

    if (swipe.isSwiping) {
        // Drawn before the current cover so it stays on top as the neighbours slide under it.
        PeekArtwork(previousUrl, anchors, mini, progress, swipe, side = -1)
        PeekArtwork(nextUrl, anchors, mini, progress, swipe, side = 1)
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
            .graphicsLayer { this.alpha = alpha() }
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
    swipe: TrackSwipeState,
    side: Int
) {
    if (url == null) return

    Box(
        // Layer inside the layout, for the reason spelled out in [MorphingArtwork] — these are
        // placed at the same offsets and would be clipped the same way.
        modifier = Modifier
            .layout { measurable, _ ->
                val rect = anchors.currentRect(mini, progress)
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
                // Invisible while docked and through the first half of the drag open, so the
                // filmstrip only appears once there is room for it — and, within that, faded in
                // proportion to how far the finger has travelled.
                //
                // The second factor is what stops the neighbours *popping* out of existence when a
                // short drag is released: they used to be composed at full opacity for as long as
                // `isSwiping` was set and simply vanish when it cleared, which read as a glitch.
                // Tying the alpha to the live offset means they fade in with the drag and fade back
                // out with the spring, and it costs nothing — this lambda already runs per frame.
                val reach = abs(swipe.offsetX.value) / DistanceThreshold
                alpha = smoothStep(progress(), 0.5f, 0.9f) * reach.coerceIn(0f, 1f)
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
