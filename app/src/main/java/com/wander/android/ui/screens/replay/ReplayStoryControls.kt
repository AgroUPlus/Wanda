package com.wander.android.ui.screens.replay

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.components.rememberPlayPauseMorphShape

/**
 * Share, pause and close, top right of the story.
 *
 * Pause is a toggle, not a hold: holding a finger down still freezes the card, but somebody who
 * wants to read the list of their top artists properly should not have to keep their thumb on the
 * glass to do it. It takes the same shape as every play control in the app — a circle while
 * paused, a rounded square while the story runs — and fills solid while paused so the state reads
 * at a glance.
 */
@Composable
internal fun ReplayStoryControls(
    isPaused: Boolean,
    /** The card's background, which the paused button's icon is cut out in. */
    container: Color,
    canPause: Boolean,
    onTogglePause: () -> Unit,
    onClose: () -> Unit,
    canShare: Boolean,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val content = LocalContentColor.current
    val tonal = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = content.copy(alpha = ControlAlpha),
        contentColor = content
    )

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(ControlGap)) {
        if (canShare) {
            FilledTonalIconButton(
                onClick = onShare,
                shapes = IconButtonDefaults.shapes(),
                colors = tonal
            ) {
                Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.replay_share))
            }
        }
        if (canPause) {
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            FilledTonalIconButton(
                onClick = onTogglePause,
                shape = rememberPlayPauseMorphShape(isPlaying = !isPaused, isPressed = pressed),
                interactionSource = interaction,
                colors = if (isPaused) {
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = content,
                        contentColor = container
                    )
                } else {
                    tonal
                }
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = stringResource(
                        if (isPaused) R.string.replay_resume else R.string.replay_pause
                    )
                )
            }
        }
        IconButton(
            onClick = onClose,
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.iconButtonColors(contentColor = content)
        ) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.replay_close))
        }
    }
}

private val ControlGap = 4.dp
private const val ControlAlpha = 0.16f
