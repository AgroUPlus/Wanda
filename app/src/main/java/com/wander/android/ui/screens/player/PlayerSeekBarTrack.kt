package com.wander.android.ui.screens.player

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The dot's width while a finger holds it — narrower than at rest, since it's now a bar. */
internal val ScrubbingThumbWidth = 6.dp

/** The dot's height while a finger holds it — taller than at rest, for the same reason. */
internal val ScrubbingThumbHeight = 24.dp

/** The empty space between the played segment and the unplayed one, either side of the thumb. */
private val ThumbGap = 6.dp

/** The unplayed segment's own thickness — the wavy/linear indicator sets the played side's. */
private val UnplayedTrackHeight = 4.dp

/** `Modifier.weight` rejects exactly 0 — the floor keeps a segment from vanishing at either end. */
private const val MinTrackWeight = 0.001f

/**
 * The seek bar's thumb: a dot at rest, a short vertical bar while [width]/[height] are animated
 * toward a scrubbing shape by the caller — the same `RoundedCornerShape(50)` in both states, since
 * a square with fully-rounded corners already reads as a circle.
 */
@Composable
internal fun SeekBarThumb(width: Dp, height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
    )
}

/**
 * The seek bar's track: a visible break at the thumb rather than one continuous bar — the played
 * side touches the dot, the unplayed side starts clear of it. The wave (the playing state, not
 * decoration) runs only on the played segment, which is fully "there" for the width it's given, so
 * it's drawn at a fixed `progress = 1f` rather than re-deriving the fraction the row's weights
 * already spent.
 */
@Composable
internal fun SeekBarTrack(fraction: Float, showWavy: Boolean, amplitude: Animatable<Float, *>) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.weight(fraction.coerceAtLeast(MinTrackWeight))) {
            if (showWavy) {
                LinearWavyProgressIndicator(
                    progress = { 1f },
                    amplitude = { amplitude.value },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(progress = { 1f }, modifier = Modifier.fillMaxWidth())
            }
        }
        Box(modifier = Modifier.width(ThumbGap).fillMaxHeight())
        Box(
            modifier = Modifier
                .weight((1f - fraction).coerceAtLeast(MinTrackWeight))
                .height(UnplayedTrackHeight)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
        )
    }
}
