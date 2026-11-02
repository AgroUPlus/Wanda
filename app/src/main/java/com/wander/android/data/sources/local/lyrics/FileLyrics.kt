package com.wander.android.data.sources.local.lyrics

import com.wander.android.data.model.LyricLine
import com.wander.android.data.model.LyricsData
import com.wander.android.data.repository.LrcParser

/**
 * Lyrics as a file holds them: [timedLines] when the tag itself carries timings (ID3 `SYLT`),
 * otherwise [text] — which is often LRC written into a plain tag, and is read as such.
 */
internal data class FileLyrics(
    val text: String = "",
    val timedLines: List<LyricLine> = emptyList()
) {
    /** Whether these come with timings, read either from the tag or from LRC in its text. */
    val isSynced: Boolean get() = timedLines.isNotEmpty() || LrcParser.parse(text).isNotEmpty()

    /**
     * As the lyrics panel takes them, or null when there are no words. Unsynced text becomes
     * untimed lines, the shape Navidrome's unsynced lyrics already arrive in.
     */
    fun toLyricsData(trackId: String, source: String): LyricsData? {
        val lines = timedLines.ifEmpty { LrcParser.parse(text) }
        if (lines.isNotEmpty()) {
            return LyricsData(
                trackId = trackId,
                isSynced = true,
                plainLyrics = lines.joinToString("\n") { it.text },
                lines = lines,
                source = source
            )
        }
        val plain = text.trim()
        val untimed = plain.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
            .map { LyricLine(timestampMs = 0L, text = it) }.toList()
        if (untimed.isEmpty()) return null
        return LyricsData(trackId = trackId, isSynced = false, plainLyrics = plain, lines = untimed, source = source)
    }

    companion object {
        /** [text] as lyrics, or null when it is blank. A leading byte-order mark is dropped. */
        fun ofText(text: String?): FileLyrics? =
            text?.removePrefix("\uFEFF")?.takeIf { it.isNotBlank() }?.let { FileLyrics(text = it) }
    }
}
