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
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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

/** The track's source chip: its [label], the [mutedColor] it is drawn in, and the picker it opens (if any). */
internal class PlayerSourceChip(val label: String, val mutedColor: Color, val onOpenPicker: (() -> Unit)?)

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
    source: PlayerSourceChip,
    onOpenJam: () -> Unit,
    onMinimize: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        FilledTonalIconButton(onClick = onMinimize, colors = playerOverlayButtonColors()) {
            Icon(
                Icons.Outlined.KeyboardArrowDown,
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
                    text = source.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = source.mutedColor,
                    textAlign = TextAlign.Center,
                    modifier = source.onOpenPicker?.let { Modifier.clickable(onClick = it) } ?: Modifier
                )
            }
        }

        FilledTonalIconButton(onClick = onOpenQueue, colors = playerOverlayButtonColors()) {
            Icon(Icons.Outlined.QueueMusic, contentDescription = stringResource(R.string.player_queue))
        }
    }
}

/**
 * Colors for a control that floats over the full player — minimize, queue here, the share button
 * in [PlayerOverlayButtons] — chosen fixed rather than read off `MaterialTheme.colorScheme`.
 *
 * The whole app is wrapped in a cover-tinted theme while a track with cover-art theming is playing
 * (see `CoverTintedTheme`), and a tonal container is exactly one of the roles it repaints — so a
 * button here used to pick up the very cover's own extracted colour. A transport control floating
 * over that same cover and dressed in its colour reads as barely there instead of discreet; a fixed
 * neutral scrim reads as a control regardless of what is behind it.
 */
@Composable
internal fun playerOverlayButtonColors() = IconButtonDefaults.filledTonalIconButtonColors(
    containerColor = Color.Black.copy(alpha = OverlayScrimAlpha),
    contentColor = Color.White
)

private const val OverlayScrimAlpha = 0.28f
