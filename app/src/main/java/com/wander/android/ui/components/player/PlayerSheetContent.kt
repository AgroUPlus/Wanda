package com.wander.android.ui.components.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import com.wander.android.core.playback.PlaybackState
import com.wander.android.core.playback.PlayerConnection
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import com.wander.android.ui.components.MiniArtworkSize
import com.wander.android.ui.navigation.DockRowHeight
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
    rawProgress: () -> Float,
    expandedHeight: Dp,
    playback: PlaybackState,
    playerConnection: PlayerConnection,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenArtist: (String, String?) -> Unit = { _, _ -> },
    onOpenAlbum: (String) -> Unit = {},
    onOpenJam: () -> Unit = {},
    /**
     * The dock row drawn under the player strip while docked — the app's destinations, or the
     * search field. Passed in rather than built here so the sheet keeps knowing nothing about
     * navigation; it only knows how tall the row is and when to fade it.
     */
    dockRow: @Composable () -> Unit = {},
    /**
     * Whether this screen has a dock row at all.
     *
     * A boolean rather than an empty [dockRow] on the screens without one: swapping the content
     * out gives the row nothing to leave *with*, and it disappeared in a frame while the sheet
     * spent the next half-second shrinking over the hole it left. Passed in, it gets an exit.
     */
    showDockRow: Boolean = true
) {
    val anchors = remember { PlayerArtworkAnchors() }
    // Owned here, not in `NowPlayingScreen`. The sheet is what draws the cover the lyrics replace,
    // and it outlives the screen: collapsing disposes `NowPlayingScreen` while this composable
    // stays, so state kept down there left the strip's cover hidden with nothing on screen able to
    // bring it back.
    var lyricsVisible by rememberSaveable { mutableStateOf(false) }

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

    // Collapsing puts the cover back. The docked strip is a cover and two lines of text — there is
    // nowhere for lyrics to be, so carrying the toggle down into it only ever means a missing
    // cover.
    LaunchedEffect(docked) { if (docked) lyricsVisible = false }

    // One drag state for both layouts, so the cover — which is drawn once, above both — can follow
    // the finger either way round, and so the neighbouring covers know how far to slide in.
    val swipe = rememberTrackSwipeState()

    // Horizontal only, so the sheet's own vertical drag still opens the player. The strip's own
    // surface stays put — sliding the docked bar sideways over the navigation bar looks broken —
    // but its cover and text now move with the gesture, which is what was missing.
    // The covers either side of this one in the queue, for the swipe to peek at. Coarse enough
    // not to change during a gesture, so reading them here costs nothing per frame.
    //
    // Past the intro, "previous" restarts the track it is already on rather than stepping back
    // (see [PlayerConnection.previous]) — so the cover it lands on is the *current* one. Taking
    // the neighbour's cover unconditionally is what left a restarted track wearing the previous
    // track's artwork, and left it there: the hand-off below waits for the artwork to change,
    // and on a restart it never does.
    //
    // Evaluated once per gesture rather than per frame: `isSwiping` flips twice, and the answer
    // only has to be right for as long as the finger is down.
    val previousRestartsCurrent = remember(swipe.isSwiping) {
        swipe.isSwiping && playerConnection.restartsOnPrevious
    }
    val previousArtwork = if (previousRestartsCurrent) {
        playback.currentTrack?.artworkUrl
    } else {
        playback.queue.getOrNull(playback.currentIndex - 1)?.artworkUrl
    }
    val nextArtwork = playback.queue.getOrNull(playback.currentIndex + 1)?.artworkUrl

    // The two surfaces disagree about what "previous" means, and they are both right.
    //
    // The docked strip shows no filmstrip, so it keeps the ordinary convention: far enough into a
    // track, swiping back restarts it. The full player has just slid the *previous cover* into the
    // slot, so restarting would contradict the thing the user watched happen — there it always
    // steps back.
    val miniSwipe = Modifier.swipeToChangeTrack(
        state = swipe,
        onNext = playerConnection::next,
        onPrevious = {
            // Read at gesture time, never in composition: it changes with playback position.
            val restarts = playerConnection.restartsOnPrevious
            playerConnection.previous()
            // A restart leaves the track — and so its cover — unchanged, so the peek cover the
            // swipe adopted would otherwise sit there until the next real track change.
            if (restarts) swipe.clearPending()
        },
        nextArtworkUrl = nextArtwork,
        previousArtworkUrl = previousArtwork,
        exitDistance = DockedExitDistance
    )
    val fullSwipe = Modifier.swipeToChangeTrack(
        state = swipe,
        onNext = playerConnection::next,
        onPrevious = playerConnection::previousTrack,
        nextArtworkUrl = nextArtwork,
        previousArtworkUrl = previousArtwork
    )

    // The skip has landed: hand the cover back to playback state. Keyed on the track's identity
    // rather than the index so a queue edit cannot strand the override — and rather than the
    // artwork URL, which two tracks off the same album share, leaving the override set.
    val currentArtwork = playback.currentTrack?.artworkUrl
    LaunchedEffect(playback.currentTrack?.id) {
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
            durationMs = playback.durationMs,
            isBuffering = playback.isBuffering,
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
                .height(MiniStripHeight)
                .then(if (docked) miniSwipe else Modifier)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = docked,
                    onClick = onExpand
                )
        )

        // Directly under the strip, inside the same surface, so the two read as one block. It
        // fades on the same curve the strip's own contents do.
        //
        // Always composed, and that is the fix: this used to be `if (docked) dockRow()`, and
        // `docked` flips at the *first pixel* of the drag — so the search field and the Friends
        // button were cut out of the tree instantly while the alpha they were supposed to fade on
        // never got to run. They vanished rather than left. Now only input is withdrawn on that
        // first pixel, which is the part that has to be immediate: a search field that still
        // worked while sliding out from under the full player would take focus and raise the
        // keyboard mid-gesture. The pixels fade out over the first third of the drag.
        //
        // The visibility above it is the *other* axis: dragging the player open fades this row on
        // `progress`, while navigating to a screen that has no dock row takes it away entirely.
        // The two compose — a row can be halfway faded by a drag and on its way out at once —
        // which is why one is an alpha and the other a transition rather than both being either.
        AnimatedVisibility(
            visible = showDockRow,
            // Drops away under the strip and shrinks slightly as it goes, so the row reads as
            // being tucked back into the player rather than blinking out. Coming back it springs
            // up into place; the sheet is growing to meet it on the same spatial spec.
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                slideInVertically(MaterialTheme.motionScheme.slowSpatialSpec()) { it / 2 } +
                scaleIn(MaterialTheme.motionScheme.slowSpatialSpec(), initialScale = 0.92f),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                slideOutVertically(MaterialTheme.motionScheme.slowSpatialSpec()) { it / 2 } +
                scaleOut(MaterialTheme.motionScheme.slowSpatialSpec(), targetScale = 0.92f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .offset(y = MiniStripHeight)
                .height(DockRowHeight)
                .graphicsLayer { alpha = 1f - smoothStep(progress(), 0f, 0.30f) }
                .then(if (docked) Modifier else Modifier.swallowPointerInput())
        ) {
            dockRow()
        }

        MorphingArtwork(
            url = currentArtwork,
            contentDescription = playback.currentTrack?.title,
            anchors = anchors,
            progress = progress,
            rawProgress = rawProgress,
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
                onOpenJam = onOpenJam,
                contentAlpha = { smoothStep(progress(), 0.20f, 0.55f) },
                // Gone almost as soon as the sheet leaves the top. The cover starts travelling at
                // the first pixel of the drag, and these have to leave with it rather than
                // linger over the space it used to fill.
                //
                // The same is true sideways, and for the same reason: swiping to the next track
                // slides the cover out from under two buttons that are not drawn with it, so
                // share and lyrics sat over the incoming song's artwork still labelled for the
                // outgoing one. They fade with the drag and come back as it settles.
                //
                // Both read at draw time — `offsetX` changes every frame, and `overlayAlpha` is
                // only ever called inside a `graphicsLayer`.
                overlayAlpha = {
                    smoothStep(progress(), 0.85f, 1f) * swipeFade(swipe.offsetX.value)
                },
                showLyrics = lyricsVisible,
                onToggleLyrics = { lyricsVisible = !lyricsVisible },
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

/**
 * How much of the overlay survives a horizontal drag.
 *
 * Full strength until the drag is clearly a drag rather than a stray touch, then out by the point
 * the gesture would count as a skip — so the buttons are gone before the cover behind them has
 * changed, not after.
 */
private fun swipeFade(offsetX: Float): Float =
    1f - smoothStep(kotlin.math.abs(offsetX), SwipeFadeStart, DistanceThreshold)

private const val SwipeFadeStart = 12f


/**
 * Takes every pointer event and gives nothing to the content beneath.
 *
 * Consumed on the initial pass, so children never see the gesture at all rather than seeing it and
 * being asked to behave. Used for content that is still on screen — mid-fade — but no longer
 * belongs to the user.
 */
private fun Modifier.swallowPointerInput(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}
