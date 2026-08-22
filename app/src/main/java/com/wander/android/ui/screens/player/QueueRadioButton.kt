package com.wander.android.ui.screens.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Matches the touch target an `IconButton` would have given this slot. */
private val ButtonSize = 48.dp

/**
 * Queue button that doubles as the endless-radio toggle on long press.
 *
 * Radio used to be a labelled chip sitting next to this button. Folding it into a long press buys
 * back the space, but a long press is invisible and [isRadioMode] is *persisted* — so without a
 * visible state the user can be left in radio mode with nothing on screen admitting it. That is
 * what the tint is for, and why the state is spoken in the content description rather than left to
 * the label.
 */
@Composable
internal fun QueueRadioButton(
    isRadioMode: Boolean,
    onOpenQueue: () -> Unit,
    onToggleRadio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val tint by animateColorAsState(
        targetValue = if (isRadioMode) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "queueRadioTint"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(ButtonSize)
            .clip(CircleShape)
            .combinedClickable(
                onClick = onOpenQueue,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleRadio()
                }
            )
            .semantics {
                contentDescription = if (isRadioMode) {
                    "Open queue. Radio on — long press to turn off"
                } else {
                    "Open queue. Long press to turn on radio"
                }
            }
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
            contentDescription = null,
            tint = tint
        )
    }
}
