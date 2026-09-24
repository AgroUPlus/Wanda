package com.wander.android.ui.components.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.wander.android.core.playback.PlaybackState
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.repository.FingerprintStatus
import com.wander.android.ui.components.LocalBackBlurEnabled
import com.wander.android.ui.components.backdropBlur
import com.wander.android.ui.screens.player.NowPlayingScreen
import com.wander.android.ui.screens.queue.QueueDrawer
import com.wander.android.ui.screens.queue.QueueDrawerHeightFraction
import com.wander.android.ui.screens.queue.rememberQueueDrawerState
import com.wander.android.ui.theme.rememberCoverSeedColor
import kotlinx.coroutines.launch

/**
 * Grows the docked strip into the full player as the sheet is dragged.
 */
@Composable
fun PlayerSheetContent(
    progress: () -> Float,
    rawProgress: () -> Float,
    expandedHeight: Dp,
    playback: PlaybackState,
    playerConnection: PlayerConnection,
    onExpand: () -> Unit,
    onMinimize: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenArtist: (String, String?) -> Unit = { _, _ -> },
    onOpenAlbum: (String) -> Unit = {},
    onOpenJam: () -> Unit = {},
    dockRow: @Composable () -> Unit = {},
    showDockRow: Boolean = true,
    fingerprintStatus: FingerprintStatus = FingerprintStatus.MISSING,
    immersivePlayer: Boolean = false,
    coverCarousel: Boolean = true,
) {
    val anchors = remember { PlayerArtworkAnchors() }
    var lyricsVisible by rememberSaveable { mutableStateOf(false) }

    val queueDrawer = rememberQueueDrawerState()
    val drawerSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val drawerScope = rememberCoroutineScope()
    val density = LocalDensity.current
    SideEffect { queueDrawer.heightPx = with(density) { (expandedHeight * QueueDrawerHeightFraction).toPx() } }

    var lockedDrag by remember { mutableFloatStateOf(0f) }
    val jamOpenDistancePx = with(density) { JamQueueOpenDistance.toPx() }

    val artworkAlphaState = animateFloatAsState(
        targetValue = if (lyricsVisible && !immersivePlayer) 0f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "artwork-lyrics-fade"
    )

    val artworkPresent by remember { derivedStateOf { artworkAlphaState.value > 0f } }
    val fullPlayerPresent by remember { derivedStateOf { progress() > 0f } }
    val docked by remember { derivedStateOf { progress() == 0f } }
    val playerFullyOpen by remember { derivedStateOf { progress() >= QueueGestureArmed } }

    LaunchedEffect(playerFullyOpen) { if (!playerFullyOpen) queueDrawer.snapTo(0f) }
    LaunchedEffect(docked) { if (docked) lyricsVisible = false }

    val swipe = rememberTrackSwipeState()
    val previousRestartsCurrent = remember(swipe.isSwiping) {
        swipe.isSwiping && playerConnection.restartsOnPrevious
    }
    val previousArtwork = if (previousRestartsCurrent) {
        playback.currentTrack?.artworkUrl
    } else {
        playback.queue.getOrNull(playback.currentIndex - 1)?.artworkUrl
    }
    val nextArtwork = playback.queue.getOrNull(playback.currentIndex + 1)?.artworkUrl

    val hasPreviousSong = playback.currentIndex > 0
    val hasNextSong = playback.currentIndex in 0 until (playback.queue.size - 1)
    val canPreviousMini = if (previousRestartsCurrent) true else hasPreviousSong

    val miniSwipe = Modifier.swipeToChangeTrack(
        state = swipe,
        onNext = playerConnection::next,
        onPrevious = {
            val restarts = playerConnection.restartsOnPrevious
            playerConnection.previous()
            if (restarts) swipe.clearPending()
        },
        nextArtworkUrl = nextArtwork,
        previousArtworkUrl = previousArtwork,
        canNext = hasNextSong,
        canPrevious = canPreviousMini,
        exitDistance = DockedExitDistance
    )
    val fullSwipe = Modifier.swipeToChangeTrack(
        state = swipe,
        onNext = playerConnection::next,
        onPrevious = playerConnection::previousTrack,
        nextArtworkUrl = nextArtwork,
        previousArtworkUrl = previousArtwork,
        canNext = hasNextSong,
        canPrevious = hasPreviousSong
    )

    val currentArtwork = playback.currentTrack?.artworkUrl
    rememberCoverSeedColor(currentArtwork)

    swipe.rememberTrackArrival(
        trackId = playback.currentTrack?.id,
        index = playback.currentIndex,
        enabled = !docked,
        spec = MaterialTheme.motionScheme.defaultSpatialSpec()
    )
    LaunchedEffect(playback.currentTrack?.id) {
        swipe.clearPending()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(expandedHeight)
            .onGloballyPositioned(anchors::onRootPositioned)
            .swipeUpToOpenQueue(
                enabled = playerFullyOpen,
                canStart = { !queueDrawer.isVisible },
                onDrag = { dy ->
                    if (playback.orderLocked) {
                        val before = lockedDrag
                        lockedDrag += dy
                        if (before > -jamOpenDistancePx && lockedDrag <= -jamOpenDistancePx) onOpenQueue()
                    } else {
                        queueDrawer.dragBy(dy)
                    }
                },
                onRelease = { velocity ->
                    lockedDrag = 0f
                    if (!playback.orderLocked) drawerScope.launch { queueDrawer.settle(velocity, drawerSpec) }
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .backdropBlur(LocalBackBlurEnabled.current) { queueDrawer.progress }
        ) {
            PlayerSheetDockedSection(
                playback = playback,
                playerConnection = playerConnection,
                progress = progress,
                docked = docked,
                miniSwipe = miniSwipe,
                swipeOffsetX = { swipe.offsetX.value },
                anchors = anchors,
                onExpand = onExpand,
                showDockRow = showDockRow,
                dockRow = dockRow
            )

            MorphingArtwork(
                url = currentArtwork,
                contentDescription = playback.currentTrack?.title,
                anchors = anchors,
                progress = progress,
                rawProgress = rawProgress,
                visible = artworkPresent,
                alpha = { artworkAlphaState.value },
                swipe = swipe,
                previousUrl = previousArtwork,
                nextUrl = nextArtwork,
                canPrevious = hasPreviousSong,
                canNext = hasNextSong,
                fingerprintStatus = fingerprintStatus,
                carouselEnabled = coverCarousel,
                isPlaying = playback.isPlaying
            )

            if (fullPlayerPresent) {
                NowPlayingScreen(
                    playerConnection = playerConnection,
                    onOpenQueue = {
                        if (playback.orderLocked) {
                            onOpenQueue()
                        } else {
                            drawerScope.launch { queueDrawer.open(drawerSpec) }
                        }
                    },
                    onMinimize = onMinimize,
                    onOpenArtist = onOpenArtist,
                    onOpenAlbum = onOpenAlbum,
                    onOpenJam = onOpenJam,
                    contentAlpha = { smoothStep(progress(), 0.20f, 0.55f) },
                    overlayAlpha = {
                        smoothStep(progress(), 0.85f, 1f) * swipeFade(swipe.shift)
                    },
                    showLyrics = lyricsVisible,
                    onToggleLyrics = { lyricsVisible = !lyricsVisible },
                    artworkModifier = fullSwipe,
                    artworkSlot = { _, _ ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .onGloballyPositioned(anchors::onFullPositioned)
                        )
                    },
                    immersivePlayer = immersivePlayer,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (queueDrawer.isVisible) {
            QueueDrawer(
                drawer = queueDrawer,
                playerConnection = playerConnection,
                onOpenArtist = { artist, artistId ->
                    queueDrawer.snapTo(0f)
                    onOpenArtist(artist, artistId)
                }
            )
        }
    }
}
