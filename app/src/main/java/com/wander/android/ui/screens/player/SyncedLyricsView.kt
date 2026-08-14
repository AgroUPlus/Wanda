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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.playback.rememberPlaybackPosition
import com.wander.android.data.model.LyricsData

/** Distance from the top the active line settles at, so upcoming lines stay visible below it. */
private val ACTIVE_LINE_OFFSET = 96.dp

/**
 * Synced lyrics that follow playback and let the user tap a line to jump to it. Falls back to
 * plain scrollable text when the source has no timings, and says so plainly when there are none.
 */
@Composable
fun SyncedLyricsView(
    lyrics: LyricsData?,
    playerConnection: PlayerConnection,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (lyrics == null) {
        CenteredNotice("No lyrics found for this track.", modifier)
        return
    }

    if (!lyrics.isSynced || lyrics.lines.isEmpty()) {
        val plain = lyrics.plainLyrics?.takeIf { it.isNotBlank() }
        if (plain == null) {
            CenteredNotice("No lyrics found for this track.", modifier)
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

    val positionState = rememberPlaybackPosition(playerConnection, intervalMs = 250L)
    val listState = rememberLazyListState()

    // Reads positionState inside the derivation, so it actually recomputes as playback advances
    // while still only recomposing when the *line* changes rather than on every tick.
    val activeIndex by remember(lyrics) {
        derivedStateOf {
            lyrics.lines.indexOfLast { it.timestampMs <= positionState.value.positionMs }
                .coerceAtLeast(0)
        }
    }

    val offsetPx = with(LocalDensity.current) { -ACTIVE_LINE_OFFSET.roundToPx() }
    LaunchedEffect(activeIndex, offsetPx) {
        listState.animateScrollToItem(index = activeIndex, scrollOffset = offsetPx)
    }

    LazyColumn(state = listState, modifier = modifier) {
        itemsIndexed(
            items = lyrics.lines,
            key = { index, line -> "${line.timestampMs}-$index" }
        ) { index, line ->
            val isActive = index == activeIndex
            Text(
                text = line.text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                // Deliberately not animated per line: one animateColorAsState per lyric meant
                // dozens of concurrent animations in a scrolling list.
                color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeek(line.timestampMs) }
                    .padding(vertical = 8.dp, horizontal = 12.dp)
            )
        }
    }
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
