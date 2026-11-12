package com.wander.android.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
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
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.sources.agro.Jam
import com.wander.android.ui.components.AvatarGroup
import com.wander.android.ui.theme.LiveIndicator

/**
 * The two quiet buttons over the cover — minimize on the left, the overflow menu on the right —
 * with the [PlayerStatus] chips between them when any apply.
 *
 * The source name and the queue button used to sit here too. Both are a step away now: the source
 * is on the menu's info page and behind its "Switch source" entry, and the queue opens from the
 * next-up bar at the foot of the player, which is where the hand already is.
 */
@Composable
internal fun PlayerTopBar(
    status: PlayerStatus,
    onMinimize: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(verticalAlignment = Alignment.Top, modifier = modifier.fillMaxWidth()) {
        FilledTonalIconButton(
            onClick = onMinimize,
            colors = playerOverlayButtonColors(),
            modifier = Modifier.size(OverlayButtonSize)
        ) {
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = stringResource(R.string.player_minimize)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        ) {
            val jam = status.jam
            if (jam != null) {
                JamBadge(jam, status.onOpenJam)
            } else {
                AnimatedMediaToggle(state = status.mediaToggle, onSwap = status.onSwapMediaType)
            }
            SleepTimerChip(status.sleepTimer)
        }

        FilledTonalIconButton(
            onClick = onOpenMenu,
            colors = playerOverlayButtonColors(),
            modifier = Modifier.size(OverlayButtonSize)
        ) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.player_more_options))
        }
    }
}

/** The live jam's badge: who is in the room, and the way to it. */
@Composable
private fun JamBadge(jam: Jam, onOpenJam: () -> Unit) {
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
}

/**
 * Colors for a control that floats over the cover — minimize and the overflow here, the lyrics
 * toggle in [PlayerCoverOverlay], the sleep chip — chosen fixed rather than read off
 * `MaterialTheme.colorScheme`.
 *
 * The whole app is wrapped in a cover-tinted theme while a track with cover-art theming is playing
 * (see `CoverTintedTheme`), and a tonal container is exactly one of the roles it repaints — so a
 * button here used to pick up the very cover's own extracted colour. A control floating over that
 * same cover and dressed in its colour reads as barely there instead of discreet; a fixed neutral
 * scrim reads as a control regardless of what is behind it.
 */
@Composable
internal fun playerOverlayButtonColors() = IconButtonDefaults.filledTonalIconButtonColors(
    containerColor = Color.Black.copy(alpha = OVERLAY_SCRIM_ALPHA),
    contentColor = Color.White
)

/** The same scrim as `MediaTypeToggle`'s, so every chip over the cover is one family. */
private const val OVERLAY_SCRIM_ALPHA = 0.28f

/** Roomier than the default 40dp: these are reached for over a busy cover, at the top of the screen. */
private val OverlayButtonSize = 44.dp
