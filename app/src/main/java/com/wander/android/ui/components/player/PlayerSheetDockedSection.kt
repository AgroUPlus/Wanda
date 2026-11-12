package com.wander.android.ui.components.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.wander.android.R
import com.wander.android.core.playback.PlaybackState
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.ui.components.MiniPlayer

/**
 * Progress by which the travelling cover (`MorphingArtwork`) has faded in over the strip's own
 * bleeding cover. While docked the strip draws the cover itself, faded into the card, so the
 * travelling one has to stay invisible at rest: `PlayerSheetContent` must scale its alpha by
 * `smoothStep(progress(), 0f, DOCKED_COVER_HANDOFF)`.
 */
internal const val DOCKED_COVER_HANDOFF = 0.08f

/** Progress by which the strip's own cover has gone, a little ahead of its text at 0.30. */
private const val STRIP_COVER_GONE = 0.25f

/** The horizontal dismiss swipe on the docked strip: the gesture [modifier] and the live drag offset. */
internal class DockedSwipe(val modifier: Modifier, val offsetX: () -> Float)

/**
 * Renders the docked mini player strip.
 *
 * Used to also lay out the dock row beneath it, inside this same box — see `WanderDock`'s doc for
 * why that moved out. This is now purely "the mini player strip and nothing else."
 */
@Composable
internal fun BoxScope.PlayerSheetDockedSection(
    playback: PlaybackState,
    playerConnection: PlayerConnection,
    progress: () -> Float,
    docked: Boolean,
    swipe: DockedSwipe,
    anchors: PlayerArtworkAnchors,
    onExpand: () -> Unit
) {
    // Skipping back is otherwise only a swipe, which TalkBack and Switch Access cannot make; both
    // directions are offered as actions on the strip itself.
    val previousLabel = stringResource(R.string.action_previous)
    val nextLabel = stringResource(R.string.action_next)
    val skipActions = remember(playerConnection, previousLabel, nextLabel) {
        listOf(
            CustomAccessibilityAction(previousLabel) { playerConnection.previous(); true },
            CustomAccessibilityAction(nextLabel) { playerConnection.next(); true }
        )
    }
    MiniPlayer(
        track = playback.currentTrack,
        isPlaying = playback.isPlaying,
        durationMs = playback.durationMs,
        isBuffering = playback.isBuffering,
        playerConnection = playerConnection,
        contentAlpha = { 1f - smoothStep(progress(), 0f, 0.30f) },
        swipeOffset = swipe.offsetX,
        // Holds on until the travelling cover is opaque over it, then goes: the two are the same
        // bitmap in the same place, so the hand-over reads as the fade filling in, not a swap.
        coverAlpha = { 1f - smoothStep(progress(), DOCKED_COVER_HANDOFF, STRIP_COVER_GONE) },
        containerColor = Color.Transparent,
        // The bleeding cover region is the travelling cover's starting frame.
        coverModifier = Modifier.onGloballyPositioned(anchors::onMiniPositioned),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            // Side insets are the strip's own business — see `MiniPlayer` — so its cover can still
            // reach the card's edge.
            .padding(horizontal = DockedSideInset)
            .height(MiniStripHeight)
            .then(if (docked) swipe.modifier else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = docked,
                onClickLabel = stringResource(R.string.player_open),
                onClick = onExpand
            )
            .then(if (docked) Modifier.semantics { customActions = skipActions } else Modifier)
    )
}
