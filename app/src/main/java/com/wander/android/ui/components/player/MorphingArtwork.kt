package com.wander.android.ui.components.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.Artwork
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
    visible: Boolean,
    swipe: TrackSwipeState,
    previousUrl: String?,
    nextUrl: String?,
    modifier: Modifier = Modifier
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
        modifier = modifier.layout { measurable, _ ->
            val rect = anchors.currentRect(mini, progress)
            val width = rect.width.roundToInt().coerceAtLeast(0)
            val height = rect.height.roundToInt().coerceAtLeast(0)
            val placeable = measurable.measure(Constraints.fixed(width, height))
            layout(width, height) {
                placeable.place(
                    x = (rect.left + swipe.offsetX.value).roundToInt(),
                    y = rect.top.roundToInt()
                )
            }
        }
    ) {
        Artwork(
            url = url,
            contentDescription = contentDescription,
            sizeDp = MorphArtworkSize,
            shape = MorphShape,
            // Nothing to cross-fade: this is one continuous element, and animating it is what made
            // the hand-off visible.
            crossfade = false,
            modifier = Modifier.fillMaxSize()
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
        modifier = Modifier
            .graphicsLayer {
                // Invisible while docked and through the first half of the drag open, so the
                // filmstrip only appears once there is room for it.
                alpha = smoothStep(progress(), 0.5f, 0.9f)
            }
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
    return lerp(mini, full, FastOutSlowInEasing.transform(progress().coerceIn(0f, 1f)))
}
