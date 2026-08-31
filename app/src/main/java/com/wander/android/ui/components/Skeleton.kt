package com.wander.android.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The shape of something that has not arrived yet.
 *
 * Several screens showed nothing at all while they loaded — a blank list is indistinguishable from
 * an empty one, so "you have no messages" and "your messages are on their way" looked identical.
 * A placeholder in the shape of the real row says which of the two it is without a spinner, and
 * without the layout jumping when the content lands on top of it.
 *
 * A breath rather than a sweeping shimmer: one alpha animation shared by every placeholder on the
 * screen costs a single animation frame source, where a gradient sweep per element is a new brush
 * every frame. This is a loading state on a battery-first app, and it is on screen for a second.
 */
@Composable
private fun skeletonAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeleton-breath"
    )
    return alpha
}

/** A single placeholder block. Size it like the thing it stands in for. */
@Composable
internal fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraSmall
) {
    Box(
        modifier = modifier
            .alpha(skeletonAlpha())
            .background(
                androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                shape
            )
    )
}

/** A placeholder line of text. [widthFraction] stands in for how long the real line runs. */
@Composable
internal fun SkeletonLine(
    widthFraction: Float,
    height: Dp = 14.dp,
    modifier: Modifier = Modifier
) {
    SkeletonBox(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height),
        shape = RoundedCornerShape(height / 2)
    )
}

/**
 * Avatar, title, subtitle — the shape almost every list in this app is made of.
 *
 * [leadingSize] and the paddings match `ThreadRow` and `TrackRow`, so the real row lands exactly
 * where its placeholder was.
 */
@Composable
internal fun SkeletonRow(
    leadingSize: Dp = 44.dp,
    leadingShape: Shape = CircleShape,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        SkeletonBox(
            modifier = Modifier.size(leadingSize),
            shape = leadingShape
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            SkeletonLine(widthFraction = 0.45f, height = 16.dp)
            Spacer(Modifier.height(8.dp))
            SkeletonLine(widthFraction = 0.7f, height = 12.dp)
        }
    }
}

/**
 * Album/card shaped placeholder for grid pagination and initial loads.
 */
@Composable
internal fun SkeletonCard(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(8.dp)
            .fillMaxWidth()
    ) {
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .androidx.compose.foundation.layout.aspectRatio(1f),
            shape = MaterialTheme.shapes.small
        )
        Spacer(Modifier.height(8.dp))
        SkeletonLine(widthFraction = 0.7f, height = 14.dp)
        Spacer(Modifier.height(4.dp))
        SkeletonLine(widthFraction = 0.45f, height = 12.dp)
    }
}
