package com.wander.android.ui.components.player

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.carousel.CarouselItemDrawInfo
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * A cover's rounded mask, pulled in on the side that faces its neighbour so two covers sliding past
 * each other keep a thin gap between them.
 *
 * The carousel itself has none to give: its spacing also pins the first and last cover off-centre
 * (see [PlayerCoverCarousel]). So the gap is cut out of the covers instead, and only while they
 * are mid-swipe: at rest the mask is the full cover, so the hero still fills its slot exactly.
 *
 * @param itemWidth the width of a cover at rest, which is what a mask narrower than it is measured
 *   against.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal class SwipeGapShape(
    private val drawInfo: CarouselItemDrawInfo,
    private val cornerRadius: Dp,
    private val itemWidth: Dp
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val restWidth = with(density) { itemWidth.toPx() }
        val radius = with(density) { cornerRadius.toPx() }
        val visible = drawInfo.maskRect

        // Fully open once a cover has shrunk by a quarter, so the gap is there for nearly the whole swipe.
        val openness = ((1f - visible.width / restWidth) / GapRampFraction).coerceIn(0f, 1f)
        val inset = with(density) { (Gap / 2).toPx() } * openness

        val left = if (visible.left > EdgeTolerancePx) inset else 0f
        val right = if (visible.right < restWidth - EdgeTolerancePx) inset else 0f
        return Outline.Rounded(
            RoundRect(
                left = left,
                top = 0f,
                right = (size.width - right).coerceAtLeast(left),
                bottom = size.height,
                cornerRadius = CornerRadius(radius)
            )
        )
    }

    private companion object {
        /** Between two covers, at its widest. */
        val Gap = 6.dp
        const val GapRampFraction = 0.25f
        const val EdgeTolerancePx = 0.5f
    }
}
