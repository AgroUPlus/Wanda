package com.wander.android.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.wander.android.core.playback.PlaybackState
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.playback.indexInPlayOrder
import com.wander.android.data.model.UnifiedTrack

/**
 * The panel that holds everything you touch, overlapping the foot of the cover on its large rounded
 * top corners: title and like, the wavy seek bar, the transport row, and the next-up bar.
 *
 * [translucent] is the immersive layout's version, where the cover runs on behind the panel and
 * shows through it; otherwise the panel is the solid surface the cover ends on.
 */
@Composable
internal fun PlayerControlsPanel(
    translucent: Boolean,
    playerConnection: PlayerConnection,
    viewModel: NowPlayingViewModel,
    state: PlaybackState,
    track: UnifiedTrack,
    previewIndex: Int?,
    isLiked: Boolean,
    onOpenArtist: ((String, String?) -> Unit)?,
    onOpenAlbum: ((String) -> Unit)?,
    onOpenQueue: () -> Unit,
    contentAlpha: () -> Float,
    modifier: Modifier = Modifier
) {
    val surface = MaterialTheme.colorScheme.surfaceContainer
    Column(
        modifier = modifier
            .fillMaxWidth()
            .keepTouchesOffCover()
            // Before the background, so the panel's surface fades in with its contents while the
            // sheet opens rather than standing solid over the cover that is still travelling.
            .graphicsLayer { alpha = contentAlpha() }
            .background(if (translucent) surface.copy(alpha = TRANSLUCENT_ALPHA) else surface, PanelShape)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 16.dp)
    ) {
        val previewTrack = previewIndex?.let { state.queue.getOrNull(it) }
        PlayerTitleBlock(
            shown = previewTrack ?: track,
            shownIndex = if (previewTrack != null) previewIndex else state.currentIndex,
            current = track,
            isLiked = isLiked,
            onToggleLike = viewModel::toggleLike,
            onOpenArtist = onOpenArtist,
            onOpenAlbum = onOpenAlbum
        )

        PlayerSeekBar(
            playerConnection = playerConnection,
            durationMs = state.durationMs,
            onSeek = playerConnection::seekTo,
            modifier = Modifier.padding(top = 18.dp),
            isLive = track.isLive,
            isPlaying = state.isPlaying,
            isSeekable = state.isSeekable,
            scrubLyricAt = rememberScrubLyricLookup(viewModel, track.id)
        )

        PlayerControls(
            state = state,
            connection = playerConnection,
            modifier = Modifier.padding(top = 8.dp)
        )

        UpNextBar(
            // Play order, not index order: under shuffle this has to be the track a skip lands on.
            next = state.indexInPlayOrder(1)?.let(state.queue::getOrNull),
            isRadioMode = state.isRadioMode,
            onOpenQueue = onOpenQueue,
            modifier = Modifier.padding(top = 20.dp)
        )
    }
}

/**
 * Makes the panel the thing a touch on it lands on, without consuming anything.
 *
 * The cover is a sibling *under* the panel — it runs on behind it by [PanelOverlap], and all the
 * way down in the immersive layout — and Compose hands a touch to the topmost sibling that has
 * pointer input. Without one here, a tap on the panel's blank space fell through to the cover and
 * toggled the lyrics. Nothing is consumed, so the sheet's own drags above this still see it all.
 */
private fun Modifier.keepTouchesOffCover(): Modifier = pointerInput(Unit) {
    awaitEachGesture { awaitFirstDown(requireUnconsumed = false) }
}

/** How far the panel rides up over the foot of the cover — its own top corner radius. */
internal val PanelOverlap = 36.dp

private val PanelShape = RoundedCornerShape(topStart = PanelOverlap, topEnd = PanelOverlap)

/** Enough of the cover through it to read as the same screen, not so much the title gets lost. */
private const val TRANSLUCENT_ALPHA = 0.82f
