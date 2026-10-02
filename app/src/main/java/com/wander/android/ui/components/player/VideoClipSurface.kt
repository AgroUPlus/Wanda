package com.wander.android.ui.components.player

import android.view.TextureView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.StreamInfo
import kotlin.math.roundToInt

/**
 * The music video over the cover, while there is one to show and someone to see it.
 *
 * [track] is the track playing as a video, or null in Song mode. [active] is the caller's say on
 * whether the cover is on screen at all — the player is out and the lyrics are not over it. The
 * app being in the foreground is checked here: leaving it disposes the clip, and with it the
 * decoder and the download, while the audio carries on.
 */
@Composable
internal fun VideoClipOverlay(track: UnifiedTrack?, fill: Boolean, active: Boolean) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Plain `collectAsState`: the lifecycle-aware collector stops listening below STARTED, which is
    // exactly the change this has to see.
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    if (track == null || !active || !lifecycleState.isAtLeast(Lifecycle.State.STARTED)) return
    key(track.id) { VideoClip(track, fill) }
}

/**
 * [track]'s clip, filling its box ([fill], cropped) or fitted inside it on black.
 *
 * Transparent until its first frame is drawn, so the cover underneath is what shows while the clip
 * loads — and what stays if it fails. A [TextureView] rather than a `SurfaceView`, since this sits
 * inside the travelling cover and has to clip to its shape and follow its fade and scale.
 */
@Composable
private fun VideoClip(track: UnifiedTrack, fill: Boolean, viewModel: VideoClipViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val audio by viewModel.audio.collectAsStateWithLifecycle()
    val stream by produceState<StreamInfo?>(null, track.id) { value = viewModel.stream(track) }
    val controller = audio ?: return
    val info = stream ?: return

    val clip = remember(info, controller) { VideoClipPlayer(context, viewModel.okHttpClient, info, controller) }
    var aspect by remember(clip) { mutableFloatStateOf(0f) }
    var shown by remember(clip) { mutableStateOf(false) }
    var failed by remember(clip) { mutableStateOf(false) }
    DisposableEffect(clip) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0) aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }

            override fun onRenderedFirstFrame() {
                shown = true
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = true
                viewModel.clipFailed(track)
            }
        }
        clip.player.addListener(listener)
        onDispose {
            clip.player.removeListener(listener)
            clip.release()
        }
    }
    LaunchedEffect(clip) { clip.followDrift() }

    val alpha by animateFloatAsState(
        targetValue = if (shown && !failed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "videoClipAlpha"
    )
    key(clip) {
        AndroidView(
            factory = { TextureView(it).also(clip.player::setVideoTextureView) },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha }
                .clipToBounds()
                .then(if (fill) Modifier else Modifier.background(Color.Black))
                // Sized in the layout pass, not in composition: the box changes every frame the
                // sheet is dragged, and this must follow it without recomposing.
                .layout { measurable, constraints ->
                    val (width, height) = clipSize(constraints.maxWidth, constraints.maxHeight, aspect, fill)
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place((constraints.maxWidth - width) / 2, (constraints.maxHeight - height) / 2)
                    }
                }
        )
    }
}

/**
 * The clip's size in a [boxWidth] by [boxHeight] box: covering it when [fill], inside it otherwise.
 * The whole box until the video's own shape is known.
 */
internal fun clipSize(boxWidth: Int, boxHeight: Int, aspect: Float, fill: Boolean): Pair<Int, Int> {
    if (aspect <= 0f || boxHeight <= 0) return boxWidth to boxHeight
    val wider = aspect > boxWidth.toFloat() / boxHeight
    return if (wider == fill) {
        (boxHeight * aspect).roundToInt() to boxHeight
    } else {
        boxWidth to (boxWidth / aspect).roundToInt()
    }
}
