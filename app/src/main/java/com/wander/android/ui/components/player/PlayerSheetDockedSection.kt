package com.wander.android.ui.components.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import com.wander.android.core.playback.PlaybackState
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.ui.components.MiniArtworkSize
import com.wander.android.ui.components.MiniPlayer

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
    MiniPlayer(
        track = playback.currentTrack,
        isPlaying = playback.isPlaying,
        durationMs = playback.durationMs,
        isBuffering = playback.isBuffering,
        playerConnection = playerConnection,
        contentAlpha = { 1f - smoothStep(progress(), 0f, 0.30f) },
        swipeOffset = swipe.offsetX,
        containerColor = Color.Transparent,
        artworkSlot = {
            Box(
                modifier = Modifier
                    .size(MiniArtworkSize)
                    .onGloballyPositioned(anchors::onMiniPositioned)
            )
        },
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(horizontal = DockedSideInset)
            .height(MiniStripHeight)
            .then(if (docked) swipe.modifier else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = docked,
                onClick = onExpand
            )
    )
}
