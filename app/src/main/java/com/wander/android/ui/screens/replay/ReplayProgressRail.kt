package com.wander.android.ui.screens.replay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp

/**
 * One segment per card, filling as the story moves.
 *
 * Built from the deck that will actually be shown, so somebody with a short year sees three full
 * segments rather than eleven with eight that never fill — the bar is a promise about how long
 * this takes, and it should not lie.
 */
@Composable
internal fun ReplayProgressRail(
    cardCount: Int,
    currentIndex: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SegmentGap)
    ) {
        repeat(cardCount) { index ->
            // Whole segments only. A partially-filled segment would be tracking the auto-advance
            // clock, and the clock pauses under a finger and stops entirely under reduced motion —
            // so it would sometimes be a progress bar that does not progress.
            val target = if (index <= currentIndex) 1f else 0f
            val fill by animateFloatAsState(target, label = "replaySegment$index")

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(SegmentHeight)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = TrackAlpha))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            // Measured at full width and then truncated, so the fill grows from the
                            // leading edge instead of the segment itself changing size.
                            val placeable = measurable.measure(constraints)
                            val width = (placeable.width * fill).toInt()
                            layout(width, placeable.height) { placeable.place(0, 0) }
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface)
                )
            }
        }
    }
}

private val SegmentHeight = 3.dp
private val SegmentGap = 4.dp
private const val TrackAlpha = 0.24f
