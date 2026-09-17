package com.wander.android.ui.screens.player

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.core.playback.PlaybackState
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.ui.components.player.PlayPauseIcon
import com.wander.android.ui.components.rememberHaptics
import com.wander.android.ui.components.rememberPressScale

/**
 * The two controls the full-screen lyrics carry: which rendering to read, and the transport.
 *
 * Split out of `FullScreenLyrics` when that file passed the length where the screen's own
 * structure — the dialog, the header, the scroll and its fade — stopped being the only thing in it.
 */

/**
 * Synced or static, as the same connected group every other either-or in the app uses.
 *
 * `ButtonGroup` + `toggleableItem` rather than two hand-shaped surfaces: it is what
 * `SourceToggleChips` and the activity filters are built from, and it brings the expressive
 * squash — the checked member widens and its neighbour gives way — which is the animation that
 * was missing. Two Surfaces with a press-scale could imitate the look but never that.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SyncedStaticToggle(
    showStatic: Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // The group sits *in* a container rather than loose on the background. Without it the
    // unchecked half has nothing behind it — `ButtonGroup`'s members are transparent until
    // checked — so the control read as one button beside a floating word.
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = ContainerAlpha),
        shape = RoundedCornerShape(ContainerRadius),
        modifier = modifier
    ) {
        ButtonGroup(
            overflowIndicator = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(ContainerInset)
                // Clipped to the frame's *inner* curve, concentric with the container.
                //
                // `ButtonGroup` shapes its own members, and the two on the ends carry square outer
                // corners — so at rest they poked into the container's rounded corners, and the
                // expressive squash pushed the checked one past the edge entirely. Clipping here
                // makes the group physically unable to leave the frame, whatever width the
                // animation gives a member mid-squash.
                .clip(RoundedCornerShape(ContainerRadius - ContainerInset))
        ) {
            toggleableItem(
                checked = !showStatic,
                label = stringResource(R.string.player_synced),
                onCheckedChange = { onSelect(false) },
                weight = 1f
            )
            toggleableItem(
                checked = showStatic,
                label = stringResource(R.string.player_static),
                onCheckedChange = { onSelect(true) },
                weight = 1f
            )
        }
    }
}

/**
 * Play/pause sitting directly on top of the position bar, as one block over the lyrics.
 *
 * It used to float in the middle of the screen, over the words — which put the one control you
 * might reach for while reading on top of the thing you were reading. Down here it is still in
 * thumb reach and covers only the lines that are already fading out.
 */
@Composable
internal fun LyricsTransport(
    state: PlaybackState,
    playerConnection: PlayerConnection,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interaction)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        FilledIconButton(
            onClick = {
                haptics.toggled(!state.isPlaying)
                playerConnection.togglePlayPause()
            },
            // A plain rounded square, not the player's morph. Everything else on this screen is a
            // rounded rectangle — the header button, the toggle, the bar below — and a shape that
            // shifted its silhouette as it played was the one restless thing on a page meant for
            // reading. It squashes on press like every other button instead.
            shape = MaterialTheme.shapes.extraLarge,
            interactionSource = interaction,
            modifier = Modifier
                .size(PlayButtonSize)
                .graphicsLayer { scaleX = scale; scaleY = scale }
        ) {
            PlayPauseIcon(
                isPlaying = state.isPlaying,
                isBuffering = state.isBuffering,
                iconSize = PlayIconSize
            )
        }

        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            // Black, not a surface role. This pill is the one element that sits on top of moving
            // text on a background whose colour is whatever the cover made it, so it is the one
            // place a fixed, maximally-contrasting backdrop beats a tinted one — the times stay
            // legible over any sleeve.
            color = Color.Black,
            contentColor = Color.White,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            PlayerSeekBar(
                playerConnection = playerConnection,
                durationMs = state.durationMs,
                onSeek = playerConnection::seekTo,
                isLive = state.currentTrack?.isLive == true,
                isPlaying = state.isPlaying,
                isSeekable = state.isSeekable,
                // Times either side of the track rather than under it: one short row, so the pill
                // stays a pill instead of a two-line block over the lyrics.
                inlineLabels = true,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
            )
        }
    }
}

/** The biggest control on the screen, because it is the only one you press without looking. */
private val PlayButtonSize = 96.dp
private val PlayIconSize = 46.dp
/** How far the lyrics dissolve into the background at each end of the list. */
/** Matches `PlayerActionBar`'s container, so the two groups are visibly the same idiom. */
private const val ContainerAlpha = 0.55f
private val ContainerInset = 6.dp
/** Explicit, because the inner clip is derived from it — concentric corners need both radii. */
private val ContainerRadius = 26.dp
