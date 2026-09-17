package com.wander.android.ui.screens.player

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.core.playback.PlaybackState
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.playback.RepeatMode
import com.wander.android.ui.components.rememberPressScale

/**
 * The secondary controls, as one connected group under the transport row.
 *
 * These four used to be spread across the screen: shuffle and repeat flanking the transport row,
 * the like button up beside the title, and the overflow menu in the very top corner — the one
 * place on a tall phone a thumb cannot reach without shifting grip. They are all the same *kind*
 * of control, so they sit together, low, where the hand already is.
 *
 * Pulling shuffle and repeat out of [PlayerControls] also gives the three transport buttons the
 * whole width to themselves, which is what lets the play button grow instead of the five of them
 * sharing 363dp and very nearly clipping.
 *
 * Drawn as segments in a shared container rather than four loose buttons: they read as a set that
 * way, and the container is what tells you the group is one thing while the transport row above it
 * is another.
 */
@Composable
internal fun PlayerActionBar(
    state: PlaybackState,
    connection: PlayerConnection,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = ContainerAlpha),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.height(BarHeight)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(SegmentGap),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(SegmentGap)
        ) {
            Segment(
                position = SegmentPosition.START,
                icon = Icons.Rounded.Shuffle,
                description = stringResource(R.string.action_shuffle),
                active = state.isShuffle,
                onClick = connection::toggleShuffle,
                // In a jam or a listen-along the running order is somebody else's. The segment
                // stays in place rather than disappearing — a control that vanishes reads as a
                // bug, one that dims reads as "not right now".
                enabled = !state.orderLocked,
                disabledDescription = "Shuffle, unavailable while the room chooses the order"
            )
            Segment(
                position = SegmentPosition.MIDDLE,
                icon = when (state.repeatMode) {
                    RepeatMode.ONE -> Icons.Rounded.RepeatOne
                    else -> Icons.Rounded.Repeat
                },
                description = stringResource(
                    if (state.repeatMode == RepeatMode.ONE) {
                        R.string.action_repeat_one
                    } else {
                        R.string.action_repeat
                    }
                ),
                active = state.repeatMode != RepeatMode.OFF,
                onClick = connection::toggleRepeat,
                enabled = !state.orderLocked,
                disabledDescription = "Repeat, unavailable while the room chooses the order"
            )
            Segment(
                position = SegmentPosition.MIDDLE,
                icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                description = stringResource(
                    if (isLiked) R.string.action_unlike else R.string.action_like
                ),
                active = isLiked,
                onClick = onToggleLike
            )
            Segment(
                position = SegmentPosition.END,
                icon = Icons.Rounded.MoreVert,
                description = stringResource(R.string.player_more_options),
                // Not a toggle — it opens a drawer and comes straight back, so it has no "on".
                // It takes a segment anyway because it belongs to this group by reach, which is
                // the whole reason it came down off the top bar.
                active = false,
                onClick = onOpenMenu
            )
        }
    }
}

/**
 * One cell of the group.
 *
 * A toggle button even for the menu, so all four are the same size and the same shape and the row
 * does not visibly re-space itself when one of them lights up. Equal weights, so the group's own
 * width is what decides the segment size rather than four fixed boxes leaving a ragged gap.
 */
/**
 * Where a segment sits in the group, which is what decides its corners.
 *
 * The outer two round off on their outer edge only and stay tight on the edge they share with a
 * neighbour, so the group reads as one shape that has been divided rather than four pills lined up.
 * This is the connected-group idiom from Material 3 Expressive, done by hand because these are
 * toggle buttons with their own checked colours rather than `ButtonGroup`'s members.
 */
private enum class SegmentPosition { START, MIDDLE, END }

@Composable
private fun RowScope.Segment(
    position: SegmentPosition,
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    disabledDescription: String? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interaction)

    FilledIconToggleButton(
        checked = active,
        onCheckedChange = { onClick() },
        enabled = enabled,
        shape = when (position) {
            SegmentPosition.START -> RoundedCornerShape(
                topStart = OuterCorner,
                bottomStart = OuterCorner,
                topEnd = InnerCorner,
                bottomEnd = InnerCorner
            )
            SegmentPosition.MIDDLE -> RoundedCornerShape(InnerCorner)
            SegmentPosition.END -> RoundedCornerShape(
                topStart = InnerCorner,
                bottomStart = InnerCorner,
                topEnd = OuterCorner,
                bottomEnd = OuterCorner
            )
        },
        interactionSource = interaction,
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            // A visible container at rest, not transparent. Unchecked segments used to disappear
            // into the group's own background, so three of the four looked like bare glyphs and
            // only the checked one looked like a button — the divisions the shapes were drawing
            // were invisible until something was switched on.
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = SegmentRestAlpha),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = SegmentRestAlpha / 2f),
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DisabledAlpha)
        ),
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = if (enabled) description else disabledDescription ?: description,
            modifier = Modifier.size(IconSize)
        )
    }
}

/** Enough to read as a surface against the group's container without competing with a checked one. */
private const val SegmentRestAlpha = 0.85f

/** Material's disabled-content opacity, for an icon that is present but not yours to press. */
private const val DisabledAlpha = 0.38f

/**
 * Translucent, not opaque: the group sits over the cover-tinted background the rest of the player
 * uses, and a solid container here would read as a separate surface pasted on top of it.
 */
private const val ContainerAlpha = 0.55f

/** The group's outer radius, and the tighter one on every edge shared with a neighbour. */
private val OuterCorner = 22.dp
private val InnerCorner = 8.dp

private val BarHeight = 60.dp
private val SegmentGap = 6.dp
private val IconSize = 24.dp
