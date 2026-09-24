package com.wander.android.data.repository

import com.wander.android.data.model.UnifiedTrack

/**
 * Fuzzy matching helpers for identifying tracks by title and artist across music sources.
 */
internal object ListenAlongMatcher {
    val BRACKETED = Regex("""[\(\[][^\)\]]*+[\)\]]""")
    val WHITESPACE = Regex("""\s+""")
    val AUDIO_EXTENSIONS = Regex("""\.(mp3|flac|wav|ogg|m4a|aac|opus|wma|alac)$""", RegexOption.IGNORE_CASE)
    val GENERIC_ARTISTS = setOf("unknown", "unknown artist", "<unknown>", "various", "various artists", "various artist")

    fun List<UnifiedTrack>.bestMatch(title: String, artist: String): UnifiedTrack? =
        firstOrNull { candidate ->
            candidate.title.matchesTrack(title) && (
                artist.isBlank()
                    || isGenericArtist(artist)
                    || candidate.artist.isBlank()
                    || isGenericArtist(candidate.artist)
                    || candidate.artist.matchesTrack(artist)
            )
        }

    fun String.matchesTrack(other: String): Boolean {
        val a = normalise()
        val b = other.normalise()
        if (a.isEmpty() || b.isEmpty()) return false
        return a == b || a.contains(b) || b.contains(a)
    }

    fun String.normalise(): String =
        lowercase()
            .replace(AUDIO_EXTENSIONS, "")
            .replace(BRACKETED, " ")
            .filter { it.isLetterOrDigit() || it.isWhitespace() }
            .trim()
            .replace(WHITESPACE, " ")

    fun isGenericArtist(a: String): Boolean {
        val clean = a.trim().lowercase()
        return clean in GENERIC_ARTISTS || (clean.startsWith("<") && clean.endsWith(">"))
    }
}
