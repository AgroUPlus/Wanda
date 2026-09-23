package com.wander.android.ui.screens.replay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

/**
 * One segment per card: the ones already seen full, the upcoming ones empty, and the current one
 * filling with the auto-advance clock — so it stops under a held finger exactly when the story
 * does.
 *
 * Built from the deck that will actually be shown, so somebody with a short year sees three
 * segments rather than eleven with eight that never fill — the bar is a promise about how long
 * this takes, and it should not lie.
 *
 * Drawn rather than laid out: the fill is a width, and a layout-truncated child still paints at
 * full width, which is how every segment used to look complete from the first card.
 */
@Composable
internal fun ReplayProgressRail(
    cardCount: Int,
    currentIndex: Int,
    /** How far through the current card the clock is, 0..1. */
    currentProgress: () -> Float,
    modifier: Modifier = Modifier
) {
    // The card's own content colour, so the rail reads on every flat background the story paints.
    val fill = LocalContentColor.current
    val track = fill.copy(alpha = TrackAlpha)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SegmentGap)
    ) {
        repeat(cardCount) { index ->
            Spacer(
                modifier = Modifier
                    .weight(1f)
                    .height(SegmentHeight)
                    .drawBehind {
                        val radius = CornerRadius(size.height / 2f)
                        drawRoundRect(track, cornerRadius = radius)
                        // Read inside the draw phase, so the clock ticking redraws the rail
                        // without recomposing it.
                        val progress = when {
                            index < currentIndex -> 1f
                            index > currentIndex -> 0f
                            else -> currentProgress().coerceIn(0f, 1f)
                        }
                        if (progress > 0f) {
                            drawRoundRect(
                                fill,
                                size = Size(size.width * progress, size.height),
                                cornerRadius = radius
                            )
                        }
                    }
            )
        }
    }
}

private val SegmentHeight = 4.dp
private val SegmentGap = 4.dp
private const val TrackAlpha = 0.28f
