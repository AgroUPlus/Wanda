package com.wander.android.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.playback.progressOf
import com.wander.android.core.playback.rememberPlaybackPosition
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.player.MiniBleedingCover
import com.wander.android.ui.components.player.MiniCoverBleedWidth
import com.wander.android.ui.components.player.MiniProgressHairline
import com.wander.android.ui.components.player.MiniProgressHeight
import com.wander.android.ui.components.player.TurningCookiePlayButton
import kotlin.math.abs

/**
 * The strip's 48dp content row — the play cookie's edge. Named for the round cover it once
 * measured; with [MiniRowVerticalPadding] it still sets `MiniStripHeight`.
 */
val MiniArtworkSize = 48.dp

/**
 * Drag distance at which the strip's text has faded out completely. Matches the swipe's own
 * commit threshold (see `TrackSwipe.kt`), so the text is gone exactly when releasing would skip.
 */
private const val SWIPE_FADE_DISTANCE_PX = 120f

/** Padding above and below the content row. Summed into `MiniStripHeight`. */
val MiniRowVerticalPadding = 8.dp

/** Where the title starts: halfway across the cover's fade, so it reads on the art. */
private val MiniTextInset = 64.dp

private val MiniPlayIconSize = 24.dp

/**
 * The docked strip at the top of the player sheet.
 *
 * It no longer navigates anywhere: the sheet it sits on is dragged open, and this fades out as
 * that happens (see `PlayerSheetContent`).
 *
 * The cover bleeds in from the leading edge and fades into the strip, with the title starting on
 * top of the fade. [coverModifier] goes on that cover region, which is how the sheet learns where
 * the travelling cover has to start from; [coverAlpha] hands it over to that cover as the sheet
 * opens. [contentAlpha] fades everything else. Both are lambdas so the fades are read during draw
 * rather than composition — the sheet's progress changes every frame.
 *
 * [swipeOffset] is the live drag-to-skip distance, and only the title and artist follow it: the
 * strip's own surface must not slide around over the navigation bar, but a gesture where nothing
 * at all moved read as if the swipe had not registered.
 */
@Composable
fun MiniPlayer(
    track: UnifiedTrack?,
    isPlaying: Boolean,
    playerConnection: PlayerConnection,
    /**
     * The length the *player* reports, not the one the metadata claimed.
     *
     * These disagree, and only one of them is reliable. A YouTube Music row whose subtitle carried
     * no `3:45` reaches Room with `durationMs = 0`, so progress driven from the track ran at zero
     * for the whole song. The full player never had the bug because it was already reading the
     * player's own duration; this is that same number.
     */
    durationMs: Long,
    /** Whether the engine is still fetching audio — see `PlayPauseIcon`. */
    isBuffering: Boolean = false,
    modifier: Modifier = Modifier,
    contentAlpha: () -> Float = { 1f },
    coverAlpha: () -> Float = contentAlpha,
    swipeOffset: () -> Float = { 0f },
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    coverModifier: Modifier = Modifier
) {
    if (track == null) return
    // The State itself, read only inside the hairline's draw: a position tick must not recompose
    // the strip.
    val position = rememberPlaybackPosition(playerConnection)

    Surface(
        color = containerColor,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(Modifier.fillMaxSize()) {
            // Drawn edge to edge, under any side inset: it is decoration, and stopping it short of
            // the card's own edge would leave a strip of background between the two.
            MiniBleedingCover(
                url = track.artworkUrl,
                alpha = coverAlpha,
                modifier = coverModifier
                    .width(MiniCoverBleedWidth)
                    .fillMaxHeight()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    // The sheet spans the whole window: in landscape a three-button navigation bar
                    // or a cutout sits on a side edge, and the controls must not land under it.
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                    )
                    .padding(start = MiniTextInset, end = 8.dp)
                    .padding(vertical = MiniRowVerticalPadding)
            ) {
                MiniStripText(
                    track = track,
                    contentAlpha = contentAlpha,
                    swipeOffset = swipeOffset,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                MiniStripControls(
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    playerConnection = playerConnection,
                    contentAlpha = contentAlpha
                )
            }

            MiniProgressHairline(
                progress = { progressOf(position.value.positionMs, durationMs) },
                alpha = contentAlpha,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(MiniProgressHeight)
            )
        }
    }
}

/**
 * The title and artist, the only part of the strip that follows [swipeOffset]: the surface under
 * them stays put over the navigation bar.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MiniStripText(
    track: UnifiedTrack,
    contentAlpha: () -> Float,
    swipeOffset: () -> Float,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = modifier.graphicsLayer {
            translationX = swipeOffset()
            // Fades as it travels, so the outgoing title does not simply run into the play
            // button. Reaching zero at the skip threshold means the gesture's commit point is
            // something you can see.
            val travel = (abs(swipeOffset()) / SWIPE_FADE_DISTANCE_PX).coerceIn(0f, 1f)
            alpha = contentAlpha() * (1f - travel)
        }
    ) {
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.scrollingTitle()
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodyMediumEmphasized,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.scrollingTitle()
        )
    }
}

/** The play cookie and the skip button at the strip's trailing end. */
@Composable
private fun MiniStripControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    playerConnection: PlayerConnection,
    contentAlpha: () -> Float
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.graphicsLayer { alpha = contentAlpha() }
    ) {
        // Still, not spinning: this is on screen for the whole session, and a turn would redraw
        // it every frame of it. It gets a quarter turn on play and pause.
        TurningCookiePlayButton(
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            onToggle = playerConnection::togglePlayPause,
            size = MiniArtworkSize,
            iconSize = MiniPlayIconSize,
            spins = false,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
        val nextInteraction = remember { MutableInteractionSource() }
        val nextScale by rememberPressScale(nextInteraction)
        IconButton(
            onClick = playerConnection::next,
            interactionSource = nextInteraction,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.graphicsLayer { scaleX = nextScale; scaleY = nextScale }
        ) {
            Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.action_next))
        }
    }
}
