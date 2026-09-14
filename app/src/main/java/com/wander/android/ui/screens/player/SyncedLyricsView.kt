package com.wander.android.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.playback.rememberPlaybackPosition
import com.wander.android.data.model.LyricsState
import com.wander.android.data.model.LyricsSyncType

/** Distance from the top the active line settles at, so upcoming lines stay visible below it. */
private val ACTIVE_LINE_OFFSET = 96.dp

/**
 * Synced lyrics that follow playback and let the user tap a line to jump to it. Falls back to
 * plain scrollable text when the source has no timings, and says so plainly when there are none.
 */
@Composable
fun SyncedLyricsView(
    state: LyricsState,
    playerConnection: PlayerConnection,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val lyrics = when (state) {
        is LyricsState.Present -> state.lyrics
        else -> {
            CenteredNotice(state.describe(), modifier)
            return
        }
    }

    if (!lyrics.isSynced || lyrics.lines.isEmpty()) {
        val plain = lyrics.plainLyrics?.takeIf { it.isNotBlank() }
        if (plain == null) {
            CenteredNotice(LyricsState.Absent.describe(), modifier)
            return
        }
        // Unsynced lyrics run long; without a scroll modifier everything past the fold was
        // clipped and unreachable.
        Text(
            text = plain,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        )
        return
    }

    val positionState = rememberPlaybackPosition(playerConnection, intervalMs = 40L)
    val listState = rememberLazyListState()

    // Reads positionState inside the derivation, so it recomputes as playback advances
    // while LazyColumn only re-indexes when the *line* changes. 120ms lead offset ensures
    // the line scroll and visual onset match vocalist attack.
    val activeIndex by remember(lyrics) {
        derivedStateOf {
            val playhead = positionState.value.positionMs + 120L
            lyrics.lines.indexOfLast { it.timestampMs <= playhead }
                .coerceAtLeast(0)
        }
    }

    val offsetPx = with(LocalDensity.current) { -ACTIVE_LINE_OFFSET.roundToPx() }
    LaunchedEffect(activeIndex, offsetPx) {
        listState.animateScrollToItem(index = activeIndex, scrollOffset = offsetPx)
    }

    Column(modifier = modifier) {
        SyncTypeLabel(lyrics.syncType)

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(
                items = lyrics.lines,
                key = { index, line -> "${line.timestampMs}-$index" }
            ) { index, line ->
                val isActive = index == activeIndex
                val nextLineTs = lyrics.lines.getOrNull(index + 1)?.timestampMs
                LyricLineItem(
                    line = line,
                    nextLineTimestampMs = nextLineTs,
                    isActive = isActive,
                    currentPositionMs = if (isActive) positionState.value.positionMs else 0L,
                    onSeek = onSeek
                )
            }
        }
    }
}

/**
 * Says, in words, how closely these lyrics follow the song.
 *
 * The app already knew this and only ever whispered it: the lyrics button's *icon* changes shape
 * with the sync type, and the difference between three glyphs is not something anyone reads. The
 * actual words lived in a `contentDescription`, which only a screen reader ever speaks — and in the
 * immersive layout that button is not drawn at all, so there was nothing to look at either way.
 *
 * Here instead, at the head of the thing it describes, where it is read once and answers the
 * question it is about: is this going to follow along with me, and how finely.
 */
@Composable
private fun SyncTypeLabel(syncType: LyricsSyncType) {
    val label = when (syncType) {
        LyricsSyncType.WORD_SYNCED -> "Synced · word by word"
        LyricsSyncType.LINE_SYNCED -> "Synced · line by line"
        LyricsSyncType.NONE -> "Not synced"
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    )
}

/**
 * What to say when there is nothing to show.
 *
 * Each variant gets its own sentence. Telling someone in a tunnel that their song has no lyrics
 * sends them looking for a fault in the track instead of waiting for signal.
 */
private fun LyricsState.describe(): String = when (this) {
    LyricsState.Loading -> "Looking for lyrics…"
    LyricsState.Absent -> "No lyrics found for this track."
    LyricsState.Instrumental -> "This recording is instrumental."
    LyricsState.Unreachable -> "Couldn't check for lyrics. Check your connection and try again."
    is LyricsState.Present -> ""
}

@Composable
private fun CenteredNotice(message: String, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
