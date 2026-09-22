package com.wander.android.ui.screens.replay

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The shared chrome every card sits in: a wash of colour, a kicker, a headline, and the card's own
 * content below.
 *
 * One frame rather than twelve near-identical layouts. It is also the only place the story's
 * padding is decided, so a card cannot accidentally run under the status bar — the insets are
 * applied once by the scaffold, and everything here is inside them.
 */
@Composable
internal fun ReplayCardFrame(
    accent: Color,
    kicker: String,
    headline: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Animated so moving between cards washes from one colour into the next rather than cutting.
    // Under reduced motion the theme's scheme is swapped for a snapping one, so this follows.
    val background by animateColorAsState(accent, label = "replayCardAccent")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(background, MaterialTheme.colorScheme.surface)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // The wash above is full-bleed on purpose; the *content* is not. Without this a
                // headline runs under the status bar and the outro's buttons under the gesture
                // handle. `RailClearance` is on top of the inset so a card never collides with the
                // progress rail the scaffold draws in the same space.
                .safeDrawingPadding()
                .padding(
                    start = FrameGutter,
                    end = FrameGutter,
                    top = FrameTopPadding + RailClearance,
                    bottom = FrameTopPadding
                ),
            verticalArrangement = Arrangement.spacedBy(HeadlineGap),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = kicker.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = headline,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
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
}

private val FrameGutter = 24.dp
private val FrameTopPadding = 32.dp

/** The rail the scaffold draws at the top of the same space. */
private val RailClearance = 16.dp
private val HeadlineGap = 8.dp
private val ContentGap = 16.dp
