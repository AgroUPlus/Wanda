package com.wander.android.data.importer

import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a playlist from pasted text or an `.m3u` / `.m3u8` file.
 *
 * Extended M3U keeps what matters in the `#EXTINF:<seconds>,<Artist - Title>` line and follows it
 * with an address that means nothing on another device (a file path, a `content://` URI). So when
 * `#EXTINF` is present the label is the track and the address under it is ignored; a plain file
 * of paths falls back to the file's name; and anything else is read as one `Artist - Title` per line.
 */
@Singleton
class TextPlaylistParser @Inject constructor() {

    fun parse(text: String): Result<RawImportPlaylist> = runCatching {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        val tracks = if (lines.any { it.startsWith(EXTINF, ignoreCase = true) }) extended(lines) else plain(lines)
        require(tracks.isNotEmpty()) { "No tracks found in the provided text." }

        val name = lines.firstOrNull { it.startsWith(PLAYLIST_TAG, ignoreCase = true) }
            ?.substring(PLAYLIST_TAG.length)?.trim()?.takeIf { it.isNotEmpty() }

        RawImportPlaylist(
            platform = PlatformType.PLAIN_TEXT,
            title = name ?: "Imported Playlist (${tracks.size} tracks)",
            tracks = tracks
        )
    }

    private fun extended(lines: List<String>): List<RawImportTrack> {
        val tracks = mutableListOf<RawImportTrack>()
        var pending: Pair<Long, String>? = null
        for (line in lines) {
            when {
                line.startsWith(EXTINF, ignoreCase = true) -> {
                    pending?.let { tracks += track(it.second, it.first) }
                    val info = line.substring(EXTINF.length)
                    val seconds = info.substringBefore(',').trim().substringBefore(' ').toLongOrNull() ?: -1L
                    pending = seconds.coerceAtLeast(0L) * 1000L to info.substringAfter(',', "").trim()
                }
                line.startsWith("#") -> Unit
                else -> {
                    val current = pending
                    pending = null
                    if (current != null && current.second.isNotBlank()) tracks += track(current.second, current.first)
                    else line.asLabel()?.let { tracks += track(it, 0L) }
                }
            }
        }
        pending?.takeIf { it.second.isNotBlank() }?.let { tracks += track(it.second, it.first) }
        return tracks
    }

    private fun plain(lines: List<String>): List<RawImportTrack> =
        lines.filterNot { it.startsWith("#") }.mapNotNull { it.asLabel() }.map { track(it, 0L) }

    /** The line as an `Artist - Title` label: a path or URL becomes its file name, or nothing if it has none. */
    private fun String.asLabel(): String? {
        if (!looksLikeAddress()) return this
        val name = substringBefore('?').substringAfterLast('/').substringAfterLast('\\')
            .let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
            .replace(EXTENSION, "").replace(TRACK_NUMBER, "").trim()
        // A media-store id or a hash says nothing about the song.
        return name.takeIf { it.isNotBlank() && !it.all { c -> c.isDigit() } }
    }

    private fun String.looksLikeAddress() =
        contains("://") || startsWith("/") || contains('\\') || WINDOWS_DRIVE.containsMatchIn(this) || EXTENSION.containsMatchIn(this)

    private fun track(label: String, durationMs: Long): RawImportTrack = when {
        label.contains(" - ") -> label.split(" - ", limit = 2).let { RawImportTrack(artist = it[0].trim(), title = it[1].trim(), durationMs = durationMs) }
        label.contains(" – ") -> label.split(" – ", limit = 2).let { RawImportTrack(artist = it[0].trim(), title = it[1].trim(), durationMs = durationMs) }
        label.contains(" by ", ignoreCase = true) ->
            label.split(Regex(" by ", RegexOption.IGNORE_CASE), limit = 2)
                .let { RawImportTrack(title = it[0].trim(), artist = it[1].trim(), durationMs = durationMs) }
        else -> RawImportTrack(title = label, artist = "Unknown Artist", durationMs = durationMs)
    }

    private companion object {
        const val EXTINF = "#EXTINF:"
        const val PLAYLIST_TAG = "#PLAYLIST:"
        val EXTENSION = Regex("""\.(mp3|m4a|m4b|mp4|aac|flac|alac|ogg|oga|opus|wav|wma|aiff?)$""", RegexOption.IGNORE_CASE)
        val WINDOWS_DRIVE = Regex("""^[A-Za-z]:[\\/]""")
        val TRACK_NUMBER = Regex("""^\d{1,3}\s*[.\-_)]\s*""")
    }
}
