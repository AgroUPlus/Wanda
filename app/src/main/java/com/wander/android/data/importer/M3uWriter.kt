package com.wander.android.data.importer

import com.wander.android.data.model.UnifiedTrack

/**
 * Writes a playlist as extended M3U that [TextPlaylistParser] reads back.
 *
 * Each track is only its `#EXTINF:<seconds>,<Artist - Title>` line. An address would be a stream
 * URL carrying a server token, or a path that means nothing on another device, so none is written:
 * whoever opens the file matches the tracks against their own sources, as with any other import.
 */
object M3uWriter {

    fun write(name: String, tracks: List<UnifiedTrack>): String = buildString {
        append("#EXTM3U\n")
        append("#PLAYLIST:").append(name.oneLine()).append('\n')
        tracks.forEach { track ->
            val seconds = if (track.durationMs > 0) track.durationMs / 1000 else -1
            val label = listOf(track.artist, track.title)
                .map { it.oneLine() }
                .filter { it.isNotBlank() }
                .joinToString(" - ")
            append("#EXTINF:").append(seconds).append(',').append(label).append('\n')
        }
    }

    /** A file name for [name]: what a file manager will accept, ending in `.m3u8`. */
    fun fileName(name: String): String =
        name.replace(UNSAFE, "_").trim().take(MAX_NAME).ifBlank { "playlist" } + ".m3u8"

    private fun String.oneLine() = replace(LINE_BREAK, " ").trim()

    private val LINE_BREAK = Regex("""[\r\n]+""")
    private val UNSAFE = Regex("""[\\/:*?"<>|\u0000-\u001F]""")
    private const val MAX_NAME = 100
}
