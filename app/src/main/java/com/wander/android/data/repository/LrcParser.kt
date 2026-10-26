package com.wander.android.data.repository

import com.wander.android.data.model.LyricLine
import com.wander.android.data.model.LyricWord

/**
 * Parser for standard LRC and word-synced Enhanced LRC formats.
 *
 * Supports:
 * - Standard LRC: `[00:12.50]Line text`
 * - Enhanced LRC: `[00:12.50]<00:12.50>Word1 <00:13.00>Word2`
 * - Intelligent syllable/character cadence fallback when word tags are absent.
 * - Several timestamps on one line — `[00:12.00][00:45.00]Chorus` — the usual way a repeated
 *   chorus is written once. Only the first used to be read, so the second chorus never lit up
 *   and the line's text carried the leftover `[00:45.00]`, which read as lyrics running behind.
 * - The `[offset:±ms]` header, which shifts every timestamp; ignoring it left whole files early
 *   or late by exactly that much. Positive means the lyrics come sooner, per the LRC convention.
 * - One- to three-digit minutes and a `:` before the fraction (`[1:02:50]`), both seen in the wild.
 */
object LrcParser {
    private val timeTag = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")
    private val leadingTimeTags = Regex("""^(?:\s*\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?\])+""")
    private val offsetTag = Regex("""^\s*\[offset:\s*([+-]?\d+)\s*\]""", RegexOption.IGNORE_CASE)
    private val wordRegex = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>([^<]*)""")

    fun parse(lrcContent: String): List<LyricLine> {
        val offsetMs = lrcContent.lineSequence()
            .firstNotNullOfOrNull { offsetTag.find(it)?.groupValues?.get(1)?.toLongOrNull() } ?: 0L
        val lines = mutableListOf<LyricLine>()

        lrcContent.lineSequence().forEach { lineText ->
            val trimmed = lineText.trim()
            val tags = leadingTimeTags.find(trimmed)?.value ?: return@forEach
            val stamps = timeTag.findAll(tags).map {
                parseTimestamp(it.groupValues[1], it.groupValues[2], it.groupValues[3]) - offsetMs
            }.toList()
            val rawContent = trimmed.substring(tags.length).trim()
            val parsed = parseContent(rawContent, offsetMs) ?: return@forEach
            stamps.forEach { lineTs ->
                // Word timings belong to the first occurrence; a repeat gets the same words shifted.
                val shift = lineTs - stamps.first()
                val words = if (shift == 0L) {
                    parsed.second
                } else {
                    parsed.second.map { it.copy(startMs = it.startMs + shift, endMs = it.endMs + shift) }
                }
                lines.add(LyricLine(timestampMs = lineTs.coerceAtLeast(0L), text = parsed.first, words = words))
            }
        }

        return lines.sortedBy { it.timestampMs }
    }

    /** The line's text and its word timings, or null when there are no words to show. */
    private fun parseContent(rawContent: String, offsetMs: Long): Pair<String, List<LyricWord>>? {
        val wordMatches = wordRegex.findAll(rawContent).toList()
        if (wordMatches.isEmpty()) return rawContent.takeIf { it.isNotEmpty() }?.let { it to emptyList() }

        val words = mutableListOf<LyricWord>()
        val textBuilder = StringBuilder()
        for (i in wordMatches.indices) {
            val curr = wordMatches[i]
            val wordStart = parseTimestamp(curr.groupValues[1], curr.groupValues[2], curr.groupValues[3]) - offsetMs
            val wordText = curr.groupValues[4]
            textBuilder.append(wordText)

            val wordEnd = if (i + 1 < wordMatches.size) {
                val next = wordMatches[i + 1]
                parseTimestamp(next.groupValues[1], next.groupValues[2], next.groupValues[3]) - offsetMs
            } else {
                wordStart + 500L
            }

            val trimmedWord = wordText.trim()
            if (trimmedWord.isNotEmpty()) {
                words.add(LyricWord(text = trimmedWord, startMs = wordStart, endMs = wordEnd))
            }
        }
        val fullText = textBuilder.toString().trim()
        return fullText.takeIf { it.isNotEmpty() }?.let { it to words }
    }

    /**
     * Resolves word boundaries for a line. When explicit [LyricWord] tokens are present from
     * Enhanced LRC, they are returned directly. Otherwise, words are interpolated proportionally
     * by length across the line's audible window.
     */
    fun resolveWords(line: LyricLine, nextLineTimestampMs: Long?): List<LyricWord> {
        if (line.words.isNotEmpty()) return line.words

        val tokens = line.text.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()

        val rawGap = if (nextLineTimestampMs != null && nextLineTimestampMs > line.timestampMs) {
            nextLineTimestampMs - line.timestampMs
        } else {
            3200L
        }

        // Natural vocal pace estimation: ~200ms baseline per word, ~45ms per character, plus
        // ~120ms pause for commas. Prevents words from stretching across instrumental pauses.
        val estimatedSingingDuration = tokens.sumOf { token ->
            val commas = token.count { it == ',' }
            (token.length * 45L) + (commas * 120L) + 200L
        }
        val lineDuration = if (rawGap > estimatedSingingDuration + 400L) {
            estimatedSingingDuration.coerceIn(800L, rawGap - 200L)
        } else {
            (rawGap - 80L).coerceIn(800L, 7000L)
        }

        val totalWeight = tokens.sumOf { token ->
            val commas = token.count { it == ',' }
            token.length + 1 + (commas * 2)
        }
        var currentOffset = line.timestampMs

        return tokens.mapIndexed { idx, token ->
            val commas = token.count { it == ',' }
            val weight = token.length + 1 + (commas * 2)
            val duration = (lineDuration * weight) / totalWeight
            val start = currentOffset
            val end = if (idx == tokens.size - 1) line.timestampMs + lineDuration else currentOffset + duration
            currentOffset = end
            LyricWord(text = token, startMs = start, endMs = end)
        }
    }

    private fun parseTimestamp(minStr: String, secStr: String, fracStr: String?): Long {
        val min = minStr.toLongOrNull() ?: 0L
        val sec = secStr.toLongOrNull() ?: 0L
        // A fraction is a decimal one whatever its length: ".5" is half a second, ".05" a twentieth.
        val fracMs = if (fracStr.isNullOrEmpty()) 0L else fracStr.take(3).padEnd(3, '0').toLongOrNull() ?: 0L
        return (min * 60L * 1000L) + (sec * 1000L) + fracMs
    }
}
