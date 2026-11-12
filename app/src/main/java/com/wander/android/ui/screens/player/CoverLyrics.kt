package com.wander.android.ui.screens.player

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.LyricsState

/**
 * The synced lyrics laid over the dimmed cover, which is what tapping the cover now shows.
 *
 * The same [SyncedLyricsView] the full-screen lyrics use — same hoisted [listState], same per-track
 * timing offset — so opening the full screen from here, or coming back, finds the same line. A tap
 * between the lines puts the cover back, the way a tap on the cover brought them up; a tap *on* a
 * line still seeks to it, since the line consumes that tap before it gets here.
 */
@Composable
internal fun CoverLyrics(
    lyrics: LyricsState,
    playerConnection: PlayerConnection,
    trackId: String,
    listState: LazyListState,
    letterByLetterEnabled: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val offsetViewModel: LyricsOffsetViewModel = hiltViewModel()
    val offsetMs = offsetViewModel.offsets.collectAsStateWithLifecycle().value[trackId] ?: 0L
    // The detector is keyed on `Unit` and must not restart mid-gesture, so it reads the current
    // callback through this rather than the one it first saw.
    val close by rememberUpdatedState(onClose)

    Box(modifier = modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { close() }) }) {
        SyncedLyricsView(
            state = lyrics,
            playerConnection = playerConnection,
            onSeek = playerConnection::seekTo,
            listState = listState,
            letterByLetterEnabled = letterByLetterEnabled,
            // Ragged-right, like a lyric sheet, and like the panel's own left-aligned title.
            textAlign = TextAlign.Start,
            offsetMs = offsetMs,
            modifier = Modifier
                .fillMaxSize()
                // Only an edge with more beyond it dissolves; read at draw time, so scrolling never
                // recomposes the list for it.
                .fadeVerticalEdges(
                    top = { if (listState.canScrollBackward) 1f else 0f },
                    bottom = { if (listState.canScrollForward) 1f else 0f }
                )
        )
    }
}
