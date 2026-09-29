package com.wander.android.ui.components.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
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
 *
 * A hand-drawn path morph between the triangle and the bars was tried here and reverted — it read
 * as broken rather than expressive at icon size, and Compose has no stable, low-risk equivalent of
 * the platform's own `AnimatedVectorDrawable` pathData morph (that needs a hand-authored XML pair
 * with matching path-command counts, which is exactly the kind of thing that is easy to get subtly
 * wrong and hard to verify without a device in hand). A cross-fade between two plain icons is the
 * safer choice here.
 *
 * [outlined] draws the empty-glyph pair instead of the solid one — only the docked mini player
 * asks for it, to read as the lighter, discreet touch that strip is meant to be. The full player's
 * transport keeps the solid glyphs: this is the one control on the whole page, and a hollow icon
 * that size reads as unavailable rather than as restrained.
 */
@Composable
internal fun PlayPauseIcon(
    isPlaying: Boolean,
    isBuffering: Boolean,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    outlined: Boolean = false
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
            val icon = when {
                isPlaying && outlined -> Icons.Outlined.Pause
                isPlaying -> Icons.Rounded.Pause
                outlined -> Icons.Outlined.PlayArrow
                else -> Icons.Rounded.PlayArrow
            }
            Icon(
                imageVector = icon,
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
