package com.wander.android.ui.screens.replay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The shared chrome every card sits in: a kicker pill, a big headline, and the card's own content
 * below.
 *
 * Transparent on purpose. The flat colour behind it is the scaffold's, so it crossfades between
 * cards instead of sliding with them, and every colour here is [LocalContentColor] — whatever the
 * scaffold says reads on that background.
 *
 * It is also the only place the story's padding is decided, so a card cannot accidentally run under
 * the status bar.
 */
@Composable
internal fun ReplayCardFrame(
    kicker: String,
    headline: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // Without this a headline runs under the status bar and the outro's buttons under the
            // gesture handle. `RailClearance` is on top of the inset so a card never collides with
            // the rail and controls the story draws in the same space.
            .safeDrawingPadding()
            .padding(
                start = FrameGutter,
                end = FrameGutter,
                top = FramePadding + RailClearance,
                bottom = FramePadding
            ),
        verticalArrangement = Arrangement.spacedBy(HeadlineGap),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = kicker.uppercase(),
            style = MaterialTheme.typography.labelLargeEmphasized,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(CircleShape)
                .background(LocalContentColor.current.copy(alpha = KickerAlpha))
                .padding(horizontal = KickerPaddingH, vertical = KickerPaddingV)
        )
        Text(
            text = headline,
            style = MaterialTheme.typography.displayMediumEmphasized.copy(fontWeight = FontWeight.Black),
            textAlign = TextAlign.Center
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = ContentGap),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

private val FrameGutter = 24.dp
private val FramePadding = 24.dp

/** The rail and the year picker the story screen draws at the top of the same space. */
private val RailClearance = 56.dp
private val HeadlineGap = 12.dp
private val ContentGap = 16.dp
private val KickerPaddingH = 14.dp
private val KickerPaddingV = 6.dp
private const val KickerAlpha = 0.14f
