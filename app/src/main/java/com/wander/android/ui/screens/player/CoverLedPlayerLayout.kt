package com.wander.android.ui.screens.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.wander.android.core.playback.PlaybackState
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.Artwork

/**
 * The Now Playing layout: the cover is the screen's top, edge to edge and up behind the status bar,
 * with the two buttons and the lyrics pill floating on it; a rounded panel overlaps its foot and
 * holds every control.
 *
 * [immersive] is the "cover fills the screen" setting: the cover then runs all the way down behind
 * a translucent panel instead of ending on a solid one. Everything else is the same layout — one
 * set of controls, one set of gestures — so the setting changes how much of the picture shows, not
 * where anything is.
 *
 * Measured rather than proportioned: the panel takes the height its contents (and the bottom
 * inset) need, the cover takes the rest plus [PanelOverlap] to run on under the panel's corners,
 * and the overlay is fitted to exactly the visible part. A fixed fraction would leave a gap above
 * the panel on a tall phone or bury the transport on a short one.
 *
 * @param artworkSlot fills the cover. The player sheet passes one that only reserves and reports
 *   the space, since it draws the single cover that travels from the docked strip; without one this
 *   draws its own.
 * @param artworkModifier the sheet's drag-to-skip, applied to the cover so the gesture lives on the
 *   artwork it moves.
 */
@Composable
internal fun CoverLedPlayerLayout(
    immersive: Boolean,
    playerConnection: PlayerConnection,
    viewModel: NowPlayingViewModel,
    state: PlaybackState,
    track: UnifiedTrack,
    previewIndex: Int?,
    isLiked: Boolean,
    status: PlayerStatus,
    onOpenArtist: ((String, String?) -> Unit)?,
    onOpenAlbum: ((String) -> Unit)?,
    onOpenQueue: () -> Unit,
    onMinimize: () -> Unit,
    onOpenMenu: () -> Unit,
    showLyrics: Boolean,
    lyrics: @Composable () -> Unit,
    onToggleLyrics: () -> Unit,
    onOpenFullScreenLyrics: () -> Unit,
    onOpenSpeedPitch: () -> Unit,
    contentAlpha: () -> Float,
    overlayAlpha: () -> Float,
    artworkModifier: Modifier,
    artworkSlot: (@Composable (url: String?, contentDescription: String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val cover: @Composable () -> Unit = {
        PlayerCover(
            track = track,
            isLiked = isLiked,
            showLyrics = showLyrics,
            onLike = viewModel::like,
            onToggleLyrics = onToggleLyrics,
            onOpenSpeedPitch = onOpenSpeedPitch,
            artworkModifier = artworkModifier,
            artworkSlot = artworkSlot
        )
    }
    val overlay: @Composable () -> Unit = {
        PlayerCoverOverlay(
            status = status,
            showLyrics = showLyrics,
            lyrics = lyrics,
            onToggleLyrics = onToggleLyrics,
            onOpenFullScreenLyrics = onOpenFullScreenLyrics,
            onMinimize = onMinimize,
            onOpenMenu = onOpenMenu,
            contentAlpha = contentAlpha,
            overlayAlpha = overlayAlpha
        )
    }
    val panel: @Composable () -> Unit = {
        PlayerControlsPanel(
            translucent = immersive,
            playerConnection = playerConnection,
            viewModel = viewModel,
            state = state,
            track = track,
            previewIndex = previewIndex,
            isLiked = isLiked,
            onOpenArtist = onOpenArtist,
            onOpenAlbum = onOpenAlbum,
            onOpenQueue = onOpenQueue,
            contentAlpha = contentAlpha
        )
    }

    Layout(
        contents = listOf(cover, overlay, panel),
        modifier = modifier.fillMaxSize(),
        measurePolicy = remember(immersive) { coverLedMeasurePolicy(immersive) }
    )
}

/**
 * Panel first, at the height it needs; the overlay gets exactly what is left above it, and the
 * cover that plus [PanelOverlap] — or the whole height when [immersive].
 */
private fun coverLedMeasurePolicy(immersive: Boolean) = MultiContentMeasurePolicy { measurables, constraints ->
    val (coverMeasurables, overlayMeasurables, panelMeasurables) = measurables
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val panelPlaceable = panelMeasurables.single()
        .measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
    val visibleHeight = (height - panelPlaceable.height).coerceAtLeast(0)
    val coverHeight = if (immersive) height else (visibleHeight + PanelOverlap.roundToPx()).coerceAtMost(height)
    val coverPlaceable = coverMeasurables.single().measure(Constraints.fixed(width, coverHeight))
    val overlayPlaceable = overlayMeasurables.single().measure(Constraints.fixed(width, visibleHeight))
    layout(width, height) {
        // In this order on purpose: the panel is drawn over the foot of the cover it overlaps.
        coverPlaceable.place(0, 0)
        overlayPlaceable.place(0, 0)
        panelPlaceable.place(0, height - panelPlaceable.height)
    }
}

/**
 * The cover itself: the artwork (or the sheet's slot for it), dimmed under the lyrics, with the
 * double-tap heart over it and the cover's own gestures on it.
 */
@Composable
private fun PlayerCover(
    track: UnifiedTrack,
    isLiked: Boolean,
    showLyrics: Boolean,
    onLike: (UnifiedTrack) -> Unit,
    onToggleLyrics: () -> Unit,
    onOpenSpeedPitch: () -> Unit,
    artworkModifier: Modifier,
    artworkSlot: (@Composable (url: String?, contentDescription: String) -> Unit)?
) {
    // `pointerInput` is keyed on the like state, which outlives every recomposition, and the
    // lambdas the screen passes are fresh each time — so the gestures read the current callbacks
    // through these rather than capturing the ones that existed when the detector started.
    val toggleLyrics by rememberUpdatedState(onToggleLyrics)
    val openSpeedPitch by rememberUpdatedState(onOpenSpeedPitch)
    val doubleTapLike = rememberCoverDoubleTapLike(track, isLiked, onLike)
    val dim = animateFloatAsState(
        targetValue = if (showLyrics) LYRICS_DIM else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "coverLyricsDim"
    )
    val dimColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(artworkModifier)
            // One tap shows the lyrics, two like, a long press opens speed and pitch. Controls
            // drawn over the cover take their own touches; only bare cover reaches this.
            .coverTapGestures(doubleTapLike, onTap = { toggleLyrics() }, onLongPress = { openSpeedPitch() })
    ) {
        if (artworkSlot != null) {
            artworkSlot(track.artworkUrl, track.title)
        } else {
            Artwork(
                url = track.artworkUrl,
                contentDescription = track.title,
                sizeDp = OwnArtworkSize,
                shape = RectangleShape,
                crossfade = true,
                modifier = Modifier.fillMaxSize()
            )
        }
        // The lyrics' backdrop: the cover dimmed toward the surface the lyric colours are chosen
        // against, rather than hidden. Drawn, so the fade never recomposes.
        Box(modifier = Modifier.fillMaxSize().drawBehind { drawRect(dimColor, alpha = dim.value) })
        CoverHeartOverlay(doubleTapLike)
    }
}

/** How far the cover dims under the lyrics: still the same picture, no longer competing with them. */
private const val LYRICS_DIM = 0.78f

/** Decode size for the cover this draws itself when no slot is passed; see [Artwork]. */
private val OwnArtworkSize = 360.dp
