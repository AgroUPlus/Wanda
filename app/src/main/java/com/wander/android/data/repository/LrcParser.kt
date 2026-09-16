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
 */
object LrcParser {
    private val lineRegex = Regex("""\[(\d{2}):(\d{2})(?:\.(\d{2,3}))?\](.*)""")
    private val wordRegex = Regex("""<(\d{2}):(\d{2})(?:\.(\d{2,3}))?>([^<]*)""")

    fun parse(lrcContent: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()

        lrcContent.lineSequence().forEach { lineText ->
            val match = lineRegex.find(lineText.trim())
            if (match != null) {
                val lineTs = parseTimestamp(
                    match.groupValues[1],
                    match.groupValues[2],
                    match.groupValues[3]
                )
                val rawContent = match.groupValues[4].trim()

                val wordMatches = wordRegex.findAll(rawContent).toList()
                if (wordMatches.isNotEmpty()) {
                    val words = mutableListOf<LyricWord>()
                    val textBuilder = StringBuilder()

                    for (i in wordMatches.indices) {
                        val curr = wordMatches[i]
                        val wordStart = parseTimestamp(
                            curr.groupValues[1],
                            curr.groupValues[2],
                            curr.groupValues[3]
                        )
                        val wordText = curr.groupValues[4]
                        textBuilder.append(wordText)

                        val wordEnd = if (i + 1 < wordMatches.size) {
                            parseTimestamp(
                                wordMatches[i + 1].groupValues[1],
                                wordMatches[i + 1].groupValues[2],
                                wordMatches[i + 1].groupValues[3]
                            )
                        } else {
                            wordStart + 500L
                        }

                        val trimmedWord = wordText.trim()
                        if (trimmedWord.isNotEmpty()) {
                            words.add(LyricWord(text = trimmedWord, startMs = wordStart, endMs = wordEnd))
                        }
                    }

                    val fullText = textBuilder.toString().trim()
                    if (fullText.isNotEmpty()) {
                        lines.add(LyricLine(timestampMs = lineTs, text = fullText, words = words))
                    }
                } else if (rawContent.isNotEmpty()) {
                    lines.add(LyricLine(timestampMs = lineTs, text = rawContent))
                }
            }
        }

        return lines.sortedBy { it.timestampMs }
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
        val fracMs = when {
            fracStr.isNullOrEmpty() -> 0L
            fracStr.length == 2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
            else -> fracStr.take(3).padEnd(3, '0').toLongOrNull() ?: 0L
        }
        return (min * 60L * 1000L) + (sec * 1000L) + fracMs
    }
}
