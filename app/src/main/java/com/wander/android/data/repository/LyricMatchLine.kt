package com.wander.android.data.repository

/** The line of a song's lyrics that a search matched, and where it is sung when the lyrics are synced. */
internal fun findMatchingLine(
    plainLyrics: String,
    syncedLyrics: String?,
    query: String
): Pair<String, Long?> {
    val cleanQuery = query.trim().lowercase()
    if (!syncedLyrics.isNullOrBlank()) {
        val lines = LrcParser.parse(syncedLyrics)
        val match = lines.firstOrNull { it.text.lowercase().contains(cleanQuery) }
            ?: lines.firstOrNull { l ->
                val lineWords = l.text.lowercase().split(Regex("\\W+"))
                cleanQuery.split(Regex("\\W+")).all { w -> lineWords.contains(w) }
            }
        if (match != null) {
            return Pair(match.text, match.timestampMs)
        }
    }
    val plainLine = plainLyrics.lineSequence().firstOrNull { it.lowercase().contains(cleanQuery) }
        ?: plainLyrics.lineSequence().firstOrNull { l ->
            val lineWords = l.lowercase().split(Regex("\\W+"))
            cleanQuery.split(Regex("\\W+")).all { w -> lineWords.contains(w) }
        }
        ?: plainLyrics.lineSequence().firstOrNull() ?: ""
    return Pair(plainLine.trim(), null)
}
