package com.wander.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The heart, with something to say when it fills.
 *
 * Liking a song is the one purely expressive thing the user does to a track — it changes nothing
 * about playback — so it is worth more than a tint swap. The heart overshoots and settles on the
 * theme's spatial spring, and a ring pushes out from under it and fades.
 *
 * The burst fires **only on the transition to liked**, never on unliking and never on arrival.
 * Celebrating an un-like would be reading the gesture wrong, and celebrating a row that merely
 * scrolled into view already liked would set the whole list off at once.
 */
@Composable
fun LikeButton(
    isLiked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val scale = remember { Animatable(1f) }
    val burst = remember { Animatable(0f) }
    val haptics = rememberHaptics()

    // The spec is read here, in composition, and held for the effect below — `Animatable.animateTo`
    // is not composable and cannot reach into the theme itself.
    val settle = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val currentSettle = rememberUpdatedState(settle)

    // Was it liked the last time this composable saw it?
    //
    // Load-bearing, and the reason this is not simply `LaunchedEffect(isLiked)`: that runs on the
    // first composition too, so every already-liked row would burst as it scrolled into view and a
    // list of favourites would go off like a firework. Seeded with the value it arrived holding,
    // so arrival is never a change.
    var wasLiked by remember { mutableStateOf(isLiked) }

    LaunchedEffect(isLiked) {
        val justLiked = isLiked && !wasLiked
        wasLiked = isLiked
        if (!justLiked) return@LaunchedEffect

        burst.snapTo(0f)
        scale.snapTo(PressedScale)
        launch { burst.animateTo(1f, tween(BurstMillis)) }
        scale.animateTo(1f, currentSettle.value)
    }

    val ringColor = MaterialTheme.colorScheme.primary

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        IconButton(
            onClick = {
                // The new state, not the old one: this fires before the caller has flipped it.
                haptics.toggled(!isLiked)
                onToggle()
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Drawn behind the heart and only while it is travelling, so a settled row costs
                // nothing to draw.
                if (burst.value > 0f && burst.value < 1f) {
                    Canvas(modifier = Modifier.size(size * BurstSizeFactor)) {
                        drawCircle(
                            color = ringColor,
                            radius = (this.size.minDimension / 2f) * burst.value,
                            alpha = 1f - burst.value,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
                Icon(
                    imageVector = if (isLiked) Icons.Rounded.Favorite
                    else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isLiked) "Remove from liked" else "Add to liked",
                    tint = if (isLiked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(size)
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                        }
                )
            }
        }
    }
}

/** Where the heart starts from when it fills. Below 1, so it springs *out* to its size. */
private const val PressedScale = 0.6f

/** The ring ends up half again the heart's size; big enough to read, small enough not to crowd. */
private const val BurstSizeFactor = 1.8f

private const val BurstMillis = 420
