package com.wander.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material3.toPath
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/**
 * A shape part-way between two [RoundedPolygon]s.
 *
 * The morph itself is the animation — not a scale, not a crossfade — which is what makes a press
 * on these buttons read as the control *relaxing* rather than as a highlight drawn over it.
 *
 * [progress] is a lambda so the outline is rebuilt when the shape is asked to draw itself, rather
 * than by recomposing the button on every frame of the spring.
 *
 * The transform mirrors what `MaterialShapes.toShape()` does for a static polygon, and it is
 * derived from the morph's own bounds rather than assumed. Hard-coding either convention is a
 * guess about how `normalized()` lays these shapes out, and guessing wrong does not throw: it
 * silently moves the outline off the button, which renders as a fragment of a shape in a corner.
 * Measuring instead is correct under either convention and cannot drift if one of them changes.
 */
private class MorphShape(
    private val morph: Morph,
    private val progress: () -> Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = morph.toPath(progress().coerceIn(0f, 1f), Path())
        // Measured from the path itself: scale its bounds onto the button, then move its origin
        // onto the button's. A zero-width or zero-height bound would divide by zero and put NaNs
        // into the outline, which does not throw — it wedges the render thread.
        val bounds = path.getBounds()
        if (bounds.width <= 0f || bounds.height <= 0f) return Outline.Rectangle(size.toRect())
        val matrix = Matrix()
        matrix.scale(size.width / bounds.width, size.height / bounds.height)
        path.transform(matrix)
        path.translate(Offset(-bounds.left * size.width / bounds.width, -bounds.top * size.height / bounds.height))
        return Outline.Generic(path)
    }
}

/**
 * A shape that relaxes into [pressed] while the control is held.
 *
 * Shared rather than re-derived per button: the morph *is* the expressive idiom here, and a
 * control that opts out of it — a stock rounded-corner FAB, say — reads as belonging to a
 * different app than everything around it.
 */
@Composable
fun rememberPressMorphShape(
    resting: RoundedPolygon,
    pressed: RoundedPolygon,
    isPressed: Boolean
): Shape {
    val progress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "pressMorph"
    )
    val morph = remember(resting, pressed) { Morph(resting, pressed) }
    return remember(morph) { MorphShape(morph) { progress } }
}

/** Resting and pressed shapes of the big play control. */
internal val PlayResting = MaterialShapes.Cookie12Sided
internal val PlayPressed = MaterialShapes.Circle

/**
 * Shape for every play/pause toggle: a circle while paused, a rounded rectangle while playing —
 * the M3 Expressive "selected" shape — and a little squarer under the finger either way.
 *
 * Corners, not a polygon morph: the player's toggle is wider than it is tall, and a morph is
 * stretched to fit its bounds, which turns round corners into ellipses. The corner here is a share
 * of the button's short side, so it reads the same on a 40dp icon button and a full-width pill.
 */
@Composable
fun rememberPlayPauseMorphShape(
    isPlaying: Boolean,
    isPressed: Boolean
): Shape {
    val targetCorner = when {
        isPlaying && isPressed -> PlayingPressedCorner
        isPlaying -> PlayingCorner
        isPressed -> PausedPressedCorner
        else -> PausedCorner
    }
    val corner by animateFloatAsState(
        targetValue = targetCorner,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "playPauseCorner"
    )
    return remember { ShortSideCornerShape { corner } }
}

/** Rounded corners at [fraction] of the short side, read at draw time so a spring never recomposes. */
private class ShortSideCornerShape(private val fraction: () -> Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radius = size.minDimension * fraction().coerceIn(0f, 0.5f)
        return Outline.Rounded(RoundRect(size.toRect(), CornerRadius(radius)))
    }
}

/** Half the short side: a circle, or a pill on a wide button. */
private const val PausedCorner = 0.5f
private const val PausedPressedCorner = 0.4f

/** The reference's rounded rectangle: roughly 28dp on the player's 92dp toggle. */
private const val PlayingCorner = 0.3f
private const val PlayingPressedCorner = 0.22f
