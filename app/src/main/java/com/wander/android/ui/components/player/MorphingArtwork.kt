package com.wander.android.ui.components.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.lerp
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

/**
 * The single cover art shared by the docked strip and the full player.
 *
 * Composed once; only its geometry changes, inside a deferred `layout` lambda, so dragging the
 * sheet neither recomposes it nor re-requests the image.
 */
@Composable
internal fun MorphingArtwork(
    url: String?,
    contentDescription: String?,
    anchors: PlayerArtworkAnchors,
    progress: () -> Float,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    // Checked here rather than inside the layout block below: the docked strip reports its bounds
    // one pass after the sheet first appears, and a `layout` lambda **must** measure its
    // measurable exactly once on every path. Bailing out inside it threw as soon as a track
    // started playing.
    val mini = anchors.miniBounds ?: return

    Box(
        modifier = modifier.layout { measurable, _ ->
            val full = anchors.fullBounds
            // Until the full player has been measured there is nowhere to travel to, so sit on
            // the mini rect instead of interpolating towards a placeholder.
            val eased =
                if (full == null) 0f else FastOutSlowInEasing.transform(progress().coerceIn(0f, 1f))
            val rect = lerp(mini, full ?: mini, eased)

            val width = rect.width.roundToInt().coerceAtLeast(0)
            val height = rect.height.roundToInt().coerceAtLeast(0)
            val placeable = measurable.measure(Constraints.fixed(width, height))
            layout(width, height) {
                placeable.place(rect.left.roundToInt(), rect.top.roundToInt())
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
