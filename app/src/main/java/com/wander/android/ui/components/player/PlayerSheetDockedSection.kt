package com.wander.android.ui.components.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import com.wander.android.core.playback.PlaybackState
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.ui.components.MiniArtworkSize
import com.wander.android.ui.components.MiniPlayer
import com.wander.android.ui.navigation.DockRowHeight

/**
 * Renders the mini player strip and the dock row beneath it when docked.
 */
@Composable
internal fun BoxScope.PlayerSheetDockedSection(
    playback: PlaybackState,
    playerConnection: PlayerConnection,
    progress: () -> Float,
    docked: Boolean,
    miniSwipe: Modifier,
    swipeOffsetX: () -> Float,
    anchors: PlayerArtworkAnchors,
    onExpand: () -> Unit,
    showDockRow: Boolean,
    dockRow: @Composable () -> Unit
) {
    MiniPlayer(
        track = playback.currentTrack,
        isPlaying = playback.isPlaying,
        durationMs = playback.durationMs,
        isBuffering = playback.isBuffering,
        playerConnection = playerConnection,
        contentAlpha = { 1f - smoothStep(progress(), 0f, 0.30f) },
        swipeOffset = swipeOffsetX,
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
            .then(if (docked) miniSwipe else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = docked,
                onClick = onExpand
            )
    )

    AnimatedVisibility(
        visible = showDockRow,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
            slideInVertically(MaterialTheme.motionScheme.slowSpatialSpec()) { it / 2 } +
            scaleIn(MaterialTheme.motionScheme.slowSpatialSpec(), initialScale = 0.92f),
        exit = fadeOut(MaterialTheme.motionScheme.slowSpatialSpec()) +
            slideOutVertically(MaterialTheme.motionScheme.slowSpatialSpec()) { it / 2 } +
            scaleOut(MaterialTheme.motionScheme.slowSpatialSpec(), targetScale = 0.92f),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(horizontal = DockedSideInset)
            .offset(y = MiniStripHeight)
            .height(DockRowHeight)
            .graphicsLayer { alpha = 1f - smoothStep(progress(), 0f, 0.30f) }
            .then(if (docked) Modifier else Modifier.swallowPointerInput())
    ) {
        dockRow()
    }
}
