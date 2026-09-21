package com.wander.android.ui.screens.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The player, reduced to the parts a gesture lands on — same cover width fraction and shape as
 * [com.wander.android.ui.screens.player.NowPlayingScreen]'s real one, plus the title/artist/seek
 * bar/controls rows below it in the same order, so this reads as *that* screen, not a placeholder.
 *
 * Used only by [GesturePreview], which overlays the animated fingertip on top of this.
 */
@Composable
internal fun PhoneMock(highlight: GestureMotion, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    // The cover is the target of three of the five gestures, so it brightens for those — the
    // difference between "swipe the cover" and "swipe the player" is the whole distinction.
    val coverIsTarget = highlight != GestureMotion.SWIPE_UP && highlight != GestureMotion.SWIPE_DOWN

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(scheme.surfaceContainerHighest)
            .padding(horizontal = 28.dp, vertical = 18.dp)
    ) {
        // Real cover: fillMaxWidth(0.88f) at shapes.extraLarge, per NowPlayingScreen.
        Box(
            modifier = Modifier
                .fillMaxWidth(CoverWidthFraction)
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(
                    if (coverIsTarget) scheme.primaryContainer else scheme.surfaceContainerLow
                )
        )
        Bar(widthFraction = 0.58f, height = 9.dp, color = scheme.onSurface.copy(alpha = 0.55f)) // title
        Bar(widthFraction = 0.36f, height = 6.dp, color = scheme.onSurface.copy(alpha = 0.28f)) // artist
        SeekBarSkeleton(scheme)
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // prev / play / next: the centre one bigger and tinted, like the real control row.
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .size(if (i == 1) 22.dp else 14.dp)
                        .clip(CircleShape)
                        .background(
                            if (i == 1) scheme.primary else scheme.onSurface.copy(alpha = 0.35f)
                        )
                )
            }
        }
    }
}

/** A seek bar, not just a line: a track, a filled portion, and a thumb at the end of it. */
@Composable
private fun SeekBarSkeleton(scheme: ColorScheme) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Bar(widthFraction = 1f, height = SeekTrackHeight, color = scheme.onSurface.copy(alpha = 0.16f))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(SeekProgress)
                    .height(SeekTrackHeight)
                    .clip(CircleShape)
                    .background(scheme.primary.copy(alpha = 0.7f))
            )
            Box(
                modifier = Modifier
                    .size(SeekThumbSize)
                    .clip(CircleShape)
                    .background(scheme.primary)
            )
            Spacer(modifier = Modifier.weight(1f - SeekProgress))
        }
    }
}

@Composable
private fun Bar(
    widthFraction: Float,
    height: Dp,
    color: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(CircleShape)
            .background(color)
    )
}

private const val CoverWidthFraction = 0.88f
private const val SeekProgress = 0.35f
private val SeekTrackHeight = 4.dp
private val SeekThumbSize = 12.dp
