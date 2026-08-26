package com.wander.android.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.SmartMix
import com.wander.android.ui.components.scrollingTitle

/**
 * A one-press mix. Each mix carries its own gradient so the row reads as a set of distinct
 * destinations rather than a wall of identical cards.
 *
 * Pressing it squashes the card on the theme's spatial spring — the expressive cue that this is a
 * physical surface being pushed, not a flat rectangle being clicked.
 */
@Composable
fun SmartMixCard(
    mix: SmartMix,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fallback = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
    // These cards live in a LazyRow; building the colour list and the brush on every recomposition
    // was allocating twice per visible card per frame.
    val brush = remember(mix.gradientColors, fallback) {
        Brush.linearGradient(mix.gradientColors.map { Color(it) }.ifEmpty { fallback })
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) PRESSED_SCALE else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "mixPress"
    )

    Box(
        modifier = modifier
            .width(210.dp)
            .height(128.dp)
            .scale(scale)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(brush)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onPlay
            )
    ) {
        Column(
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = mix.title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${mix.tracks.size} tracks · ${mix.subtitle}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.scrollingTitle()
            )
        }
    }
}

private const val PRESSED_SCALE = 0.95f
