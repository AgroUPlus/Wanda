package com.wander.android.ui.components.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.wander.android.ui.components.Artwork
import kotlin.math.roundToInt

/**
 * The picture and its [overlay] under one clip, so the playing zoom stays inside the frame.
 * [playingZoom] is a lambda so the zoom is read during draw, not composition.
 */
@Composable
internal fun MorphingCoverContent(
    url: String?,
    contentDescription: String?,
    progress: () -> Float,
    playingZoom: () -> Float,
    overlay: @Composable () -> Unit
) {
    val coverShape = remember(progress) { MorphingCoverShape(progress) }
    Box(Modifier.fillMaxSize().clip(coverShape)) {
        Artwork(
            url = url,
            contentDescription = contentDescription,
            sizeDp = MorphArtworkSize,
            shape = RectangleShape,
            // Nothing to cross-fade: this is one continuous element, and animating it is what
            // made the hand-off visible.
            crossfade = false,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val zoom = lerpFloat(1f, playingZoom(), progress().coerceIn(0f, 1f))
                    scaleX = zoom
                    scaleY = zoom
                }
        )
        overlay()
    }
}

/** Square once expanded: the full player's cover runs edge to edge, under the panel's corners. */
private const val FULL_CORNER_PERCENT = 0

/** A full circle — `RoundedCornerShape(percent = 50)`'s own figure — while fully docked. */
private const val CIRCLE_CORNER_PERCENT = 50

/**
 * The cover's own shape, circular while docked and easing to a square frame as the player opens —
 * the one thing about the strip that reads as a "mini player" rather than a smaller copy of the
 * full one, the same way a circular avatar reads differently from a square thumbnail.
 *
 * A [Shape], not a plain `by` computed at the call site: [progress] changes every frame of a drag,
 * and the whole morph's discipline is never recomposing over that — see [MorphingArtwork]. Corner
 * *percent*, not two competing `RoundedCornerShape`s cross-faded, so the two endpoints are one
 * continuous animation of the same underlying value rather than two shapes drawn on top of each
 * other with one fading out.
 */
private class MorphingCoverShape(private val progress: () -> Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val percent = lerpFloat(
            CIRCLE_CORNER_PERCENT.toFloat(),
            FULL_CORNER_PERCENT.toFloat(),
            progress().coerceIn(0f, 1f)
        ).roundToInt()
        return RoundedCornerShape(percent).createOutline(size, layoutDirection, density)
    }
}
