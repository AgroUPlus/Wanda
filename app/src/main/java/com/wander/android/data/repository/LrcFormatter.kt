package com.wander.android.data.repository

import com.wander.android.data.model.LyricLine
import java.util.Locale

/**
 * Writes timed lines back out as LRC, the one synced form `track_lyrics` stores.
 *
 * Sources such as Navidrome hand over lyrics already split into timed lines. Storing only their
 * plain text threw the timing away, so a track that scrolled in time on first open read as static
 * text from the cache ever after. Milliseconds are kept to three digits, which [LrcParser] reads
 * back exactly. Lines without words are skipped, as [LrcParser] would skip them on the way back.
 */
object LrcFormatter {
    fun format(lines: List<LyricLine>): String =
        lines.filter { it.text.isNotBlank() }.joinToString("\n") { "${timeTag(it.timestampMs)}${it.text}" }

    private fun timeTag(timestampMs: Long): String {
        val ms = timestampMs.coerceAtLeast(0L)
        val minutes = ms / 60_000L
        val seconds = (ms / 1000L) % 60L
        val millis = ms % 1000L
        // Root locale: a device set to Arabic or Persian would otherwise write non-ASCII digits.
        return String.format(Locale.ROOT, "[%02d:%02d.%03d]", minutes, seconds, millis)
    }
}
