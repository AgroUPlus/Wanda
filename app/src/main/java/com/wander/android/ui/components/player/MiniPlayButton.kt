package com.wander.android.ui.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.playback.progressOf
import com.wander.android.core.playback.rememberPlaybackPosition
import com.wander.android.ui.components.rememberPressScale

/** The strip's transport icons. `IconButton`'s own default, stated so the loading shape matches. */
private val MiniPlayIconSize = 24.dp

/** The container behind the play/pause button. */
internal val MiniButtonSize = 40.dp

/** Clearance between the play button and the progress ring drawn around it. */
private val MiniRingInset = 6.dp
private val MiniRingSize = MiniButtonSize + MiniRingInset * 2

/**
 * The strip's play/pause control: a circle at rest that scallops into a soft "cookie" while
 * playing — both the button's own outline and the progress ring drawn around it, from the one
 * [amplitude] animation, so they always agree on how "cookie" they currently are and stay
 * concentric regardless of it. Settles back to a plain circle on pause.
 */
@Composable
internal fun MiniPlayButton(
    isPlaying: Boolean,
    isBuffering: Boolean,
    playerConnection: PlayerConnection,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    val position by rememberPlaybackPosition(playerConnection)
    val progress = { progressOf(position.positionMs, durationMs) }

    val amplitudeState = remember { Animatable(if (isPlaying) 1f else 0f) }
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(isPlaying) { amplitudeState.animateTo(if (isPlaying) 1f else 0f, spec) }
    val amplitude = { amplitudeState.value }
    val playShape = rememberCookieMorphShape(amplitude)
    val playInteraction = remember { MutableInteractionSource() }
    val playLabel = stringResource(if (isPlaying) R.string.action_pause else R.string.action_play)

    // The ring is the touch target: at 52dp it clears the 48dp minimum the 40dp button does not.
    CircularProgressRing(
        progress = progress,
        ringSize = MiniRingSize,
        amplitude = amplitude,
        modifier = modifier.miniTouchTarget(playLabel, playInteraction, playerConnection::togglePlayPause)
    ) {
        val playScale by rememberPressScale(playInteraction)
        FilledIconButton(
            onClick = playerConnection::togglePlayPause,
            interactionSource = playInteraction,
            shape = playShape,
            modifier = Modifier
                .size(MiniButtonSize)
                .hiddenFromAccessibility()
                .graphicsLayer { scaleX = playScale; scaleY = playScale }
        ) {
            PlayPauseIcon(
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                iconSize = MiniPlayIconSize
            )
        }
    }
}
