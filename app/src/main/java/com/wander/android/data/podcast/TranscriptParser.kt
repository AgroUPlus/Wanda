package com.wander.android.data.podcast

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

data class TranscriptCue(val startMs: Long, val text: String)

/** Reads the three transcript formats Podcasting 2.0 feeds commonly offer: JSON, WebVTT and SubRip. */
object TranscriptParser {

    /** Declared in order of preference: JSON carries no markup to strip, SubRip has the least structure. */
    enum class Format { JSON, VTT, SRT }

    fun formatOf(mime: String?): Format? = when (mime?.substringBefore(';')?.trim()?.lowercase()) {
        "application/json" -> Format.JSON
        "text/vtt" -> Format.VTT
        "application/srt", "application/x-subrip", "text/srt" -> Format.SRT
        else -> null
    }

    /** The best transcript the app can read, or null when the feed offers only HTML or plain text. */
    fun pick(links: List<TranscriptLink>): TranscriptLink? =
        links.mapNotNull { link -> formatOf(link.mimeType)?.let { it to link } }
            .minByOrNull { it.first.ordinal }
            ?.second

    /** @throws IOException if a JSON transcript is malformed. */
    fun parse(format: Format, body: String): List<TranscriptCue> = when (format) {
        Format.JSON -> parseJson(body)
        Format.VTT, Format.SRT -> parseSubtitles(body)
    }

    private fun parseJson(body: String): List<TranscriptCue> {
        try {
            val segments = Json.parseToJsonElement(body).jsonObject["segments"]?.jsonArray
                ?: throw IOException("The transcript has no segments")
            return segments.mapNotNull { element ->
                val entry = element.jsonObject
                val start = entry["startTime"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val text = entry["body"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                TranscriptCue((start * 1000).toLong(), text)
            }.sortedBy { it.startMs }
        } catch (e: SerializationException) {
            throw IOException("The transcript is not valid JSON")
        } catch (e: IllegalArgumentException) {
            throw IOException("The transcript is not shaped like a transcript")
        }
    }

    /** WebVTT and SubRip share the shape that matters: blank-line separated blocks with a `a --> b` line. */
    private fun parseSubtitles(body: String): List<TranscriptCue> =
        body.replace("\r\n", "\n").split(BLOCK_BREAK).mapNotNull { block ->
            val lines = block.lines()
            val timing = lines.indexOfFirst { "-->" in it }
            if (timing < 0) return@mapNotNull null
            val start = parseTimestamp(lines[timing].substringBefore("-->")) ?: return@mapNotNull null
            val text = lines.drop(timing + 1).joinToString(" ") { it.trim() }
                .replace(TAG, "").replace(WHITESPACE, " ").trim()
            if (text.isEmpty()) null else TranscriptCue(start, text)
        }

    /** `HH:MM:SS,mmm` (SubRip) or `[HH:]MM:SS.mmm` (WebVTT). */
    private fun parseTimestamp(raw: String): Long? {
        val parts = raw.trim().replace(',', '.').split(':')
        if (parts.size !in 2..3) return null
        var seconds = 0.0
        for (part in parts) seconds = seconds * 60 + (part.toDoubleOrNull() ?: return null)
        return (seconds * 1000).toLong()
    }

    private val BLOCK_BREAK = Regex("\n{2,}")
    private val TAG = Regex("<[^>]*>")
    private val WHITESPACE = Regex("\\s+")
}
