package com.wander.android.ui.screens.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.data.model.LyricLine
import com.wander.android.data.model.LyricsState
import com.wander.android.data.model.LyricsSyncType
import com.wander.android.data.model.syncType

/**
 * Which synced line is sounding at [songTimeMs], or -1 before the first one starts.
 *
 * One answer to "which line is at time T" for both places that ask it: the lyrics view, to light
 * the line being sung, and the seek bar's bubble, to show the line a scrub would land on.
 *
 * [offsetMs] is the track's timing correction (song time = lyric time + offset), so a track the
 * user has nudged into sync reads the same line in the bubble as in the lyrics. [leadMs] moves the
 * answer that much earlier; the lyrics view uses it so a line lights on the singer's attack rather
 * than just after it.
 */
internal fun lyricLineIndexAt(
    lines: List<LyricLine>,
    songTimeMs: Long,
    offsetMs: Long = 0L,
    leadMs: Long = 0L
): Int {
    val lyricTimeMs = songTimeMs - offsetMs + leadMs
    return lines.indexOfLast { it.timestampMs <= lyricTimeMs }
}

/**
 * The text of the synced line at [songTimeMs], for the scrub bubble; null when there is nothing
 * worth showing — no synced lyrics, a position before the first line, or a blank line (the gaps
 * that mark an instrumental break, where showing the line before it would name the wrong words).
 */
internal fun syncedLyricTextAt(lyrics: LyricsState, songTimeMs: Long, offsetMs: Long = 0L): String? {
    if (lyrics !is LyricsState.Present || lyrics.syncType() == LyricsSyncType.NONE) return null
    val lines = lyrics.lyrics.lines
    return lines.getOrNull(lyricLineIndexAt(lines, songTimeMs, offsetMs))?.text?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * The seek bar's "line at this position" lookup for the playing track, or null when the track has
 * no time-synced lyrics, so the bubble shows just the time.
 *
 * Reads only the lyrics the player already loaded for this track, so a scrub never asks anyone for
 * anything. Remembered on the lyrics and the offset, so the lambda handed to the seek bar is the
 * same instance between position ticks and does not by itself make it recompose.
 */
@Composable
internal fun rememberScrubLyricLookup(viewModel: NowPlayingViewModel, trackId: String): ((Long) -> String?)? {
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val offsetViewModel: LyricsOffsetViewModel = hiltViewModel()
    val offsets by offsetViewModel.offsets.collectAsStateWithLifecycle()
    val offsetMs = offsets[trackId] ?: 0L
    return remember(lyrics, offsetMs) {
        if (lyrics.syncType() == LyricsSyncType.NONE) {
            null
        } else {
            { ms: Long -> syncedLyricTextAt(lyrics, ms, offsetMs) }
        }
    }
}
