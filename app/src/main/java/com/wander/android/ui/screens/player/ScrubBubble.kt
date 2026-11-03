package com.wander.android.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The bubble that rides above the thumb while a finger is on it: the time a release would seek to
 * and, when the track has time-synced lyrics, the line sung there — so a scrub can aim for the
 * chorus by its words rather than by guessing a timestamp.
 *
 * A function of its own rather than written inline, and not for tidiness: inline, it sat lexically
 * inside the seek bar's `Column`, so `ColumnScope.AnimatedVisibility` won overload resolution — and
 * was then rejected, because the slider's `thumb` lambda is not a `ColumnScope`. Out here there is
 * no such receiver in scope and the ordinary overload is chosen.
 *
 * Centred on the thumb, but never past either end of the bar: near 0% or 100% a bubble wide enough
 * for a lyric would otherwise hang off the screen edge. [barWidthPx] is read at layout time, so the
 * bar's size reaching here never recomposes anything.
 *
 * @param fraction where the thumb sits along the bar, 0..1, measured from the start edge.
 * @param thumbInset half the thumb's slot: how far the thumb's centre is kept from each end.
 */
@Composable
internal fun ScrubTooltip(
    visible: Boolean,
    time: String,
    line: String?,
    fraction: Float,
    barWidthPx: () -> Int,
    thumbInset: Dp
) {
    // The line is only looked up while a finger is down, so on release it goes null the same frame
    // the bubble starts to leave. Hold the last one through the exit, or the bubble would snap to
    // time-only mid-fade. A plain holder, not snapshot state: it is written during composition and
    // nothing should recompose because of it.
    val held = remember { HeldLine() }
    if (visible) held.text = line
    val shownLine = held.text

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
            scaleIn(MaterialTheme.motionScheme.fastSpatialSpec(), initialScale = 0.8f),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
            scaleOut(MaterialTheme.motionScheme.fastEffectsSpec(), targetScale = 0.8f),
        modifier = Modifier.layout { measurable, _ ->
            val barWidth = barWidthPx()
            val maxWidth = MaxBubbleWidth.roundToPx().let { if (barWidth > 0) minOf(it, barWidth) else it }
            val placeable = measurable.measure(Constraints(maxWidth = maxWidth))
            val x = if (barWidth > 0) {
                val inset = thumbInset.toPx()
                val thumbCentre = inset + fraction.coerceIn(0f, 1f) * (barWidth - 2 * inset)
                val start = (thumbCentre - placeable.width / 2f)
                    .coerceIn(0f, (barWidth - placeable.width).coerceAtLeast(0).toFloat())
                (start - thumbCentre).roundToInt()
            } else {
                -placeable.width / 2
            }
            // Zero-sized, so the thumb slot keeps its own size; `placeRelative` mirrors the offset
            // in RTL, where the slider measures `fraction` from the right.
            layout(0, 0) {
                placeable.placeRelative(x = x, y = -placeable.height - BubbleGap.roundToPx())
            }
        }
    ) {
        Surface(
            // A pill for the time alone; a rounded card once a line of words may wrap under it.
            shape = if (shownLine == null) CircleShape else MaterialTheme.shapes.large,
            // The sleeve's own container pair. The player sits inside `CoverTintedTheme`, so this
            // is the cover's colour rather than a neutral grey slab that belonged to no screen in
            // particular. The pair is contrast-matched by the scheme itself, whatever the cover,
            // and it is opaque with a shadow, so it still reads over artwork in the immersive
            // layout — where a translucent or surface role would take on the image behind it.
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shadowElevation = 6.dp
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(
                    horizontal = if (shownLine == null) 8.dp else 12.dp,
                    vertical = if (shownLine == null) 4.dp else 6.dp
                )
            ) {
                Text(text = time, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                if (shownLine != null) {
                    Text(
                        text = shownLine,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private class HeldLine {
    var text: String? = null
}

/** Wide enough for most lyric lines on two rows; the bar's own width caps it on narrow screens. */
private val MaxBubbleWidth = 280.dp

/** Space between the bubble and the thumb's slot, clearing the finger that is on it. */
private val BubbleGap = 24.dp
