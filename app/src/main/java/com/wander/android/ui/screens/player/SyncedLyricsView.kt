package com.wander.android.ui.screens.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.playback.rememberPlaybackPosition
import com.wander.android.data.model.LyricsState
import com.wander.android.data.model.LyricsSyncType

/** Distance from the top the active line settles at, so upcoming lines stay visible below it. */
private val ACTIVE_LINE_OFFSET = 96.dp

/**
 * Synced lyrics that follow playback and let the user tap a line to jump to it. Falls back to
 * plain scrollable text when the source has no timings, and says so plainly when there are none.
 *
 * [listState] is hoisted rather than `remember`ed here: this view is mounted inside an
 * `AnimatedVisibility`/`AnimatedContent` that fully disposes it when lyrics are hidden, so any
 * state owned locally — scroll position among it — was lost every time the panel closed and
 * reopened. The caller (`NowPlayingScreen`, which stays composed across the toggle) owns it
 * instead, so reopening lyrics finds them exactly where they were left.
 */
@Composable
fun SyncedLyricsView(
    state: LyricsState,
    playerConnection: PlayerConnection,
    onSeek: (Long) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    letterByLetterEnabled: Boolean = true
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

    // Off the moment a finger touches the list, not when a drag threshold is crossed — a tap that
    // turns into a fling should not fight the sync a beat later than the touch that started it.
    var autoScrollEnabled by remember { mutableStateOf(true) }

    val offsetPx = with(LocalDensity.current) { -ACTIVE_LINE_OFFSET.roundToPx() }
    LaunchedEffect(activeIndex, offsetPx, autoScrollEnabled) {
        if (autoScrollEnabled) {
            listState.animateScrollToItem(index = activeIndex, scrollOffset = offsetPx)
        }
    }

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            SyncTypeLabel(lyrics.syncType)

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    // A raw pointer-down is enough to mean "the user is taking over" — waiting for
                    // Compose's own drag-start would let one more `animateScrollToItem` frame run
                    // first, which is exactly the frame that fought the finger and made the list
                    // feel like it was resisting the touch.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            autoScrollEnabled = false
                            waitForUpOrCancellation()
                        }
                    }
            ) {
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
                        onSeek = onSeek,
                        letterByLetterEnabled = letterByLetterEnabled
                    )
                }
            }
        }

        if (!autoScrollEnabled) {
            Surface(
                onClick = { autoScrollEnabled = true },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Rounded.SyncAlt, contentDescription = null)
                    Text(
                        text = stringResource(R.string.lyrics_sync_back),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
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
        LyricsSyncType.WORD_SYNCED -> stringResource(R.string.lyrics_synced_word)
        else -> null
    } ?: return
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
@Composable
private fun LyricsState.describe(): String = when (this) {
    LyricsState.Loading -> stringResource(R.string.lyrics_loading)
    LyricsState.Absent -> stringResource(R.string.lyrics_absent)
    LyricsState.Instrumental -> stringResource(R.string.lyrics_instrumental)
    LyricsState.Unreachable -> stringResource(R.string.lyrics_unreachable)
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
