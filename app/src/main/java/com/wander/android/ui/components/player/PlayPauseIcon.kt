package com.wander.android.ui.components.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay

/**
 * The transport's play/pause face, which becomes the loading shape while the engine fetches audio.
 *
 * Pressing play on a streamed track does nothing visible for as long as it takes to open the
 * connection and fill the buffer — the icon stays a triangle, so the only reading available is
 * "the tap missed". This is the same morphing `LoadingIndicator` the pull-to-refresh uses, in the
 * place you are already looking: the button you just pressed.
 */
@Composable
internal fun PlayPauseIcon(
    isPlaying: Boolean,
    isBuffering: Boolean,
    iconSize: Dp,
    modifier: Modifier = Modifier
) {
    val loading = rememberSettledBuffering(isBuffering)

    // Read here, not inside `transitionSpec` — that lambda is not composable and cannot reach the
    // theme itself, the same constraint the nav graph's transitions run into.
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    AnimatedContent(
        targetState = loading,
        transitionSpec = {
            (fadeIn(effects) + scaleIn(spatial, initialScale = 0.7f))
                .togetherWith(fadeOut(effects) + scaleOut(spatial, targetScale = 0.7f))
        },
        modifier = modifier,
        label = "play-button-face"
    ) { isLoading ->
        if (isLoading) {
            LoadingIndicator(
                color = LocalContentColor.current,
                modifier = Modifier.size(iconSize)
            )
        } else {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/**
 * [buffering], with the flicker taken out of it.
 *
 * A streaming player dips in and out of `BUFFERING` constantly — every seek, every chunk boundary,
 * every stall short enough that nobody hears it. Rendering that raw gives a button that strobes
 * between two shapes while the music plays perfectly well, which is worse than no indicator at
 * all.
 *
 * So a stall has to last before it is worth saying anything about, and once it is being shown it
 * stays up briefly rather than blinking off on the first chunk to arrive. The delays are
 * deliberately lopsided: appearing late costs nothing, and leaving late reads as the shape
 * settling.
 */
@Composable
private fun rememberSettledBuffering(buffering: Boolean): Boolean {
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(buffering) {
        delay(if (buffering) BufferingShowDelayMs else BufferingHideDelayMs)
        settled = buffering
    }
    return settled
}

/** How long the engine may stall before the button says so. */
private const val BufferingShowDelayMs = 250L

/** How long the shape stays after the audio comes back, so a run of short stalls is one indicator. */
private const val BufferingHideDelayMs = 400L
