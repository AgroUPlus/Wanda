package com.wander.android.ui.screens.player

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wander.android.core.playback.PlaybackState
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.LyricsState
import com.wander.android.data.model.syncType
import com.wander.android.data.model.LyricsSyncType
import kotlin.coroutines.cancellation.CancellationException

/**
 * The lyrics, on their own screen, over everything.
 *
 * They used to swap in where the cover was — a square in the middle of a player, which is a small
 * window on the one thing you open lyrics to do, which is read. Full screen gives the lines the
 * width they need to not wrap mid-phrase, and leaves only the controls you would actually reach for
 * while reading: play/pause and the position.
 *
 * A [Dialog] rather than a nav destination, and deliberately. The player is itself a sheet drawn
 * above the nav host, so a destination would appear *behind* the thing it was opened from; a dialog
 * is the only surface in the app that is reliably above it. It also means the player underneath
 * keeps its state — closing this returns to exactly the player that was there, because it never
 * went anywhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FullScreenLyrics(
    lyrics: LyricsState,
    state: PlaybackState,
    playerConnection: PlayerConnection,
    onDismiss: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    letterByLetterEnabled: Boolean = true
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            // The platform's dialog width is a card in the middle of the screen. This is a screen.
            usePlatformDefaultWidth = false,
            // The window draws its own insets, the way the rest of the app does — see the
            // edge-to-edge note in the project's conventions.
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            // Inherits the cover tint: the dialog is composed inside `NowPlayingScreen`'s
            // `CoverTintedTheme`, so this is the same washed background the player has.
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) {
            LyricsScaffold(
                lyrics = lyrics,
                state = state,
                playerConnection = playerConnection,
                onDismiss = onDismiss,
                listState = listState,
                letterByLetterEnabled = letterByLetterEnabled
            )
        }
    }
}

@Composable
private fun LyricsScaffold(
    lyrics: LyricsState,
    state: PlaybackState,
    playerConnection: PlayerConnection,
    onDismiss: () -> Unit,
    listState: LazyListState,
    letterByLetterEnabled: Boolean
) {
    // Offered only when there is a choice to make. A track whose lyrics are plain to begin with has
    // one rendering, and a toggle with one working side is a control that lies about what it does.
    val hasSynced = lyrics.syncType() != LyricsSyncType.NONE
    var showStatic by remember(lyrics) { mutableStateOf(false) }

    // Predictive back, the same shape as `PlayerSheet`'s and `InboxScreen`'s: the screen shrinks
    // and fades under the finger so the player behind it is already visible before the gesture is
    // committed, and springs back if it is abandoned. The dialog would dismiss on back anyway —
    // this is what makes it a gesture you can see the result of rather than a switch.
    var backProgress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = true) { progressFlow ->
        try {
            progressFlow.collect { backEvent -> backProgress = backEvent.progress }
            onDismiss()
        } catch (e: CancellationException) {
            backProgress = 0f
        } finally {
            backProgress = 0f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val eased = BackScaleFloor + (1f - BackScaleFloor) * (1f - backProgress)
                scaleX = eased
                scaleY = eased
                alpha = 1f - backProgress * BackFadeDepth
            }
            .safeDrawingPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                FilledIconButton(
                    onClick = onDismiss,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close lyrics")
                }
                Text(
                    text = "Lyrics",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                // Balances the back button so the title sits on the screen's centre rather than the
                // centre of what is left over beside it.
                Box(modifier = Modifier.size(BackButtonSlot))
            }

            if (hasSynced) {
                SyncedStaticToggle(
                    showStatic = showStatic,
                    onSelect = { showStatic = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Runs all the way to the bottom of the screen, *behind* the transport below, and is
            // faded out down there rather than stopping short of it. Ending the list above the bar
            // left a band of dead background between the last visible line and the controls, and
            // made the fade look like the lyrics had simply run out.
            // Only fade an edge there is something beyond. At the first line there is nothing
            // above to dissolve into, and a band of half-transparent text at the top of a verse
            // that has not started scrolling just looks like the title is broken; same at the last
            // line. Animated so the fade arrives with the scroll rather than snapping on.
            val scrollState = rememberScrollState()
            val atTop = if (showStatic) !scrollState.canScrollBackward else !listState.canScrollBackward
            val atEnd = if (showStatic) !scrollState.canScrollForward else !listState.canScrollForward
            val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            val topFade by animateFloatAsState(if (atTop) 0f else 1f, effects, label = "lyricsTopFade")
            val bottomFade by animateFloatAsState(if (atEnd) 0f else 1f, effects, label = "lyricsBottomFade")

            SyncedLyricsView(
                state = lyrics,
                playerConnection = playerConnection,
                onSeek = playerConnection::seekTo,
                listState = listState,
                scrollState = scrollState,
                letterByLetterEnabled = letterByLetterEnabled,
                // The toggle's whole job: read the same lyrics as an unsynced block.
                forcePlain = showStatic,
                // Ragged-right, like a lyric sheet. Centred lines have no common edge for the eye
                // to return to, which is the thing that makes a verse scan.
                textAlign = TextAlign.Start,
                // So the last verse can still be pulled clear of the bar it would otherwise end
                // behind. Matches the space the transport actually occupies.
                contentPadding = PaddingValues(bottom = TransportReserve),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Lambdas, so the running fade is read at draw time rather than recomposing
                    // the whole list on every frame of a scroll.
                    .fadeVerticalEdges(top = { topFade }, bottom = { bottomFade })
            )
        }

        LyricsTransport(
            state = state,
            playerConnection = playerConnection,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

/**
 * Fades the content out at the top and bottom edges instead of cutting it.
 *
 * Drawn as a mask rather than a gradient laid over the top: an overlay would have to be painted in
 * the background's own colour to hide anything, and there is no one such colour here — the
 * background is whatever the cover tinted it to this track. Masking the content's own alpha works
 * against any background because it never paints anything.
 *
 * `CompositingStrategy.Offscreen` is what makes that possible: `BlendMode.DstIn` needs a layer to
 * blend against, and without it the blend has nothing to erase from.
 */
private fun Modifier.fadeVerticalEdges(
    top: () -> Float,
    bottom: () -> Float
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val topFactor = top()
        val bottomFactor = bottom()
        val topHeight = TopFadeHeight.toPx().coerceAtMost(size.height / 3f) * topFactor
        // Deeper at the foot, and measured from *below* the bar: the lines should still be
        // legible as they pass behind the transport and be gone by the time they reach the
        // screen's edge, rather than dissolving before they get there.
        val bottomHeight = BottomFadeHeight.toPx().coerceAtMost(size.height / 2f) * bottomFactor
        if (topHeight > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startY = 0f,
                    endY = topHeight
                ),
                blendMode = BlendMode.DstIn
            )
        }
        if (bottomHeight > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = size.height - bottomHeight,
                    endY = size.height
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }

/** Matches `FilledIconButton`'s default footprint, so the title centres against the real gap. */
private val BackButtonSlot = 40.dp

/** What the floating transport occupies, reserved inside the lyrics' own scroll. */
private val TransportReserve = 190.dp

/** How far the screen shrinks and dims at a fully-dragged predictive back. */
private const val BackScaleFloor = 0.88f
private const val BackFadeDepth = 0.35f

private val TopFadeHeight = 56.dp
private val BottomFadeHeight = 220.dp
