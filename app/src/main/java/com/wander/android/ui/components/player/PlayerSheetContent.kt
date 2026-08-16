package com.wander.android.ui.components.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import com.wander.android.core.playback.PlaybackState
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.ui.components.MiniArtworkSize
import com.wander.android.ui.components.MiniPlayer
import com.wander.android.ui.screens.player.NowPlayingScreen

/**
 * Grows the docked strip into the full player as the sheet is dragged.
 *
 * The cover art is drawn once, here, and travels between the two layouts ([MorphingArtwork]);
 * only the surrounding text and controls fade.
 *
 * [progress] is a lambda, not a value, and is never read in this composable's own scope — reading
 * it here would recompose the whole player on every drag frame. Alphas are passed down as lambdas
 * to be read inside `graphicsLayer`; the only things derived eagerly are two coarse booleans that
 * decide what is *composed*, and those flip at most twice per gesture.
 */
@Composable
fun PlayerSheetContent(
    progress: () -> Float,
    expandedHeight: Dp,
    playback: PlaybackState,
    playerConnection: PlayerConnection,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenArtist: (String) -> Unit = {},
    onOpenAlbum: (String) -> Unit = {}
) {
    val anchors = remember { PlayerArtworkAnchors() }
    var lyricsVisible by remember { mutableStateOf(false) }
    // Opacity only, and read in a `graphicsLayer` rather than here — see the note on this
    // composable. The cover is drawn outside `NowPlayingScreen`'s `AnimatedContent`, so it has no
    // transition of its own; this is what cross-fades it with the lyrics instead of cutting.
    val artworkAlphaState = animateFloatAsState(
        targetValue = if (lyricsVisible) 0f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "artwork-lyrics-fade"
    )

    // Derived, so this flips twice per toggle instead of once per animation frame. Reading the
    // raw alpha in composition scope would recompose the whole player on every frame of the
    // fade — the one thing this file is built to avoid.
    val artworkPresent by remember {
        derivedStateOf { artworkAlphaState.value > 0f }
    }

    val fullPlayerPresent by remember { derivedStateOf { progress() > 0f } }
    val docked by remember { derivedStateOf { progress() == 0f } }

    // One drag state for both layouts, so the cover — which is drawn once, above both — can follow
    // the finger either way round, and so the neighbouring covers know how far to slide in.
    val swipe = rememberTrackSwipeState()

    // Horizontal only, so the sheet's own vertical drag still opens the player. The strip's own
    // surface stays put — sliding the docked bar sideways over the navigation bar looks broken —
    // but its cover and text now move with the gesture, which is what was missing.
    // The covers either side of this one in the queue, for the swipe to peek at. Coarse enough
    // not to change during a gesture, so reading them here costs nothing per frame.
    val previousArtwork = playback.queue.getOrNull(playback.currentIndex - 1)?.artworkUrl
    val nextArtwork = playback.queue.getOrNull(playback.currentIndex + 1)?.artworkUrl

    val miniSwipe = Modifier.swipeToChangeTrack(
        state = swipe,
        onNext = playerConnection::next,
        onPrevious = playerConnection::previous,
        nextArtworkUrl = nextArtwork,
        previousArtworkUrl = previousArtwork,
        exitDistance = DockedExitDistance
    )
    val fullSwipe = Modifier.swipeToChangeTrack(
        state = swipe,
        onNext = playerConnection::next,
        onPrevious = playerConnection::previous,
        nextArtworkUrl = nextArtwork,
        previousArtworkUrl = previousArtwork
    )

    // The skip has landed: hand the cover back to playback state. Keyed on the track rather than
    // the index so a queue edit cannot strand the override.
    val currentArtwork = playback.currentTrack?.artworkUrl
    LaunchedEffect(currentArtwork) {
        swipe.clearPending()
    }

    // Full height regardless of how far the sheet is open — the sheet clips it. This keeps the
    // full player's layout, and so its artwork bounds, stable for the whole drag.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(expandedHeight)
            .onGloballyPositioned(anchors::onRootPositioned)
    ) {
        // Draw order matters and is the whole point of this Box:
        //   mini strip → travelling artwork → full player.
        // The artwork sits above the strip (which reserves a hole for it) and below the full
        // player, or it paints over the full player's own controls — that is what put the lyrics
        // button behind the cover.
        MiniPlayer(
            track = playback.currentTrack,
            isPlaying = playback.isPlaying,
            playerConnection = playerConnection,
            contentAlpha = { 1f - smoothStep(progress(), 0f, 0.30f) },
            // Only the title and artist slide; see MiniPlayer.
            swipeOffset = { swipe.offsetX.value },
            // The sheet already paints this colour; an opaque strip here would hide the artwork
            // drawn after it.
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
                .height(MiniPlayerHeight)
                .then(if (docked) miniSwipe else Modifier)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = docked,
                    onClick = onExpand
                )
        )

        MorphingArtwork(
            url = currentArtwork,
            contentDescription = playback.currentTrack?.title,
            anchors = anchors,
            progress = progress,
            // Cross-fades with the lyrics rather than cutting. The cover is drawn here, outside
            // `NowPlayingScreen`'s `AnimatedContent`, so it had no transition of its own — the
            // lyrics faded in over a cover that had already vanished.
            visible = artworkPresent,
            alpha = { artworkAlphaState.value },
            swipe = swipe,
            previousUrl = previousArtwork,
            nextUrl = nextArtwork
        )

        // Composed as soon as the drag starts, so its artwork bounds are known and nothing
        // pops in partway through the gesture.
        if (fullPlayerPresent) {
            NowPlayingScreen(
                playerConnection = playerConnection,
                onCollapse = onCollapse,
                onOpenQueue = onOpenQueue,
                onOpenArtist = onOpenArtist,
                onOpenAlbum = onOpenAlbum,
                contentAlpha = { smoothStep(progress(), 0.20f, 0.55f) },
                onLyricsVisibleChange = { lyricsVisible = it },
                // The gesture only; the cover that visibly follows it is drawn above, so the
                // reported artwork bounds stay still and the peek covers have a fixed frame.
                artworkModifier = fullSwipe,
                artworkSlot = { _, _ ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned(anchors::onFullPositioned)
                    )
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Eased 0→1 ramp between [from] and [to], flat outside them. */
internal fun smoothStep(value: Float, from: Float, to: Float): Float {
    val t = ((value - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
