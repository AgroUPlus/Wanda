package com.wander.android.ui.components.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.wander.android.ui.components.Artwork
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Renders the peek preview of neighbour track artwork during a horizontal swipe gesture.
 *
 * [side] is -1 for the previous track (left) and +1 for the next track (right).
 */
@Composable
internal fun PeekArtwork(
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
        modifier = Modifier
            .layout { measurable, _ ->
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

internal fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction
