package com.wander.android.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.sources.agro.Jam
import com.wander.android.ui.components.AvatarGroup
import com.wander.android.ui.theme.LiveIndicator

/**
 * The player's top bar: minimize on the left, a centred chip (the source name, or a live jam
 * badge when one is active) in the middle, the queue button on the right.
 *
 * Shared between [ImmersivePlayerLayout] and [StandardPlayerLayout], which differ only in the
 * chip's text colour (drawn over artwork vs. over a plain surface) and in what wraps this — the
 * immersive layout adds window-inset padding and measures its own height, the standard one doesn't
 * need to.
 */
@Composable
internal fun PlayerTopBar(
    jam: Jam?,
    sourceLabel: String,
    onOpenJam: () -> Unit,
    onMinimize: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSourcePicker: (() -> Unit)?,
    sourceLabelColorMuted: Color,
    modifier: Modifier = Modifier
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        FilledTonalIconButton(onClick = onMinimize) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.player_minimize)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f)
        ) {
            if (jam != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.clickable(onClick = onOpenJam)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).background(LiveIndicator, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_jam),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(6.dp))
                        AvatarGroup(usernames = jam.members, size = 18.dp, overlap = 5.dp, maxDisplay = 3)
                    }
                }
            } else {
                Text(
                    text = sourceLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = sourceLabelColorMuted,
                    textAlign = TextAlign.Center,
                    modifier = onOpenSourcePicker?.let { Modifier.clickable(onClick = it) } ?: Modifier
                )
            }
        }

        FilledTonalIconButton(onClick = onOpenQueue) {
            Icon(Icons.Rounded.QueueMusic, contentDescription = stringResource(R.string.player_queue))
        }
    }
}
