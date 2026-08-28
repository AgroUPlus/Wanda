package com.wander.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.material3.toPath
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
private val PlayResting = MaterialShapes.Cookie12Sided
private val PlayPressed = MaterialShapes.Circle

/** Resting and pressed shapes of the satellites beside it. */
private val ActionResting = MaterialShapes.Square
private val ActionPressed = MaterialShapes.Circle

val ShapedPlaySize: Dp = 64.dp
val ShapedActionSize: Dp = 44.dp

/**
 * The one control a detail page exists for.
 *
 * A twelve-lobed cookie at rest that settles into a circle while held. It is deliberately the only
 * shape of its kind on the screen — the satellites beside it are quiet squircles — so the page has
 * exactly one thing that draws the eye.
 */
@Composable
fun ShapedPlayButton(
    onClick: () -> Unit,
    contentDescription: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = ShapedPlaySize
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // The fast spatial spec, not the default: this sits directly under a finger, and anything
    // leisurely reads as the tap not having registered.
    val shape = rememberPressMorphShape(PlayResting, PlayPressed, pressed)

    FilledIconButton(
        onClick = onClick,
        shape = shape,
        interactionSource = interaction,
        modifier = modifier.size(size)
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(28.dp))
    }
}

/**
 * Shuffle, radio, share — the actions that sit beside [ShapedPlayButton].
 *
 * Tonal rather than filled, and a squircle rather than a cookie, so the hierarchy on the page is
 * legible without reading a single label.
 */
@Composable
fun ShapedActionButton(
    onClick: () -> Unit,
    contentDescription: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = ShapedActionSize
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = rememberPressMorphShape(ActionResting, ActionPressed, pressed)

    FilledTonalIconButton(
        onClick = onClick,
        shape = shape,
        interactionSource = interaction,
        colors = IconButtonDefaults.filledTonalIconButtonColors(),
        modifier = modifier.size(size)
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
}
