package com.wander.android.data.repository

import com.wander.android.data.model.SearchKind
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import javax.inject.Inject
import javax.inject.Singleton

/** Where a listen-along track was found, so the UI can be honest about what is playing. */
internal enum class ResolvedFrom {
    /** A local file or your own Navidrome — the same recording, near enough. */
    YOUR_LIBRARY,

    /**
     * A YouTube Music search. It is the right song by name; it may not be the right *recording* —
     * a live take, a remaster, a different edit — so this is surfaced rather than hidden.
     */
    YOUTUBE_MUSIC
}

internal data class ResolvedTrack(val track: UnifiedTrack, val from: ResolvedFrom)

/**
 * Turns a friend's now-playing into something this device can actually play.
 *
 * The host's `trackUri` is an id in *their* Navidrome or *their* YouTube session and means nothing
 * here, so matching is by title and artist. That is a weaker key than an id, which is exactly why
 * the result carries where it came from: a name match across two different backends is a guess, and
 * the person listening should be able to see that it was one.
 *
 * Returns `null` rather than inventing something when nothing matches. A listen-along that silently
 * played a different song would be worse than one that admits it cannot.
 */
@Singleton
internal class ListenAlongResolver @Inject constructor(
    private val musicRepository: MusicRepository
) {
    suspend fun resolve(title: String, artist: String): ResolvedTrack? {
        if (title.isBlank()) return null
        val query = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ")

        // Your own library first, always. A file on this device or on your Navidrome is the right
        // recording and costs nothing to stream.
        musicRepository
            .searchAllSources(query, onlySources = PERSONAL_SOURCES, kind = SearchKind.TRACKS)
            .bestMatch(title, artist)
            ?.let { return ResolvedTrack(it, ResolvedFrom.YOUR_LIBRARY) }

        // Also query by title alone on personal sources if artist is present (handles local internal sounds/files with missing or divergent artist tags)
        if (artist.isNotBlank()) {
            musicRepository
                .searchAllSources(title, onlySources = PERSONAL_SOURCES, kind = SearchKind.TRACKS)
                .bestMatch(title, artist)
                ?.let { return ResolvedTrack(it, ResolvedFrom.YOUR_LIBRARY) }
        }

        // Then YouTube Music, so a friend playing something you do not own is still joinable.
        return musicRepository
            .searchAllSources(query, onlySources = setOf(SourceType.YTMUSIC), kind = SearchKind.TRACKS)
            .bestMatch(title, artist)
            ?.let { ResolvedTrack(it, ResolvedFrom.YOUTUBE_MUSIC) }
    }

    /**
     * The first result whose title and artist both actually match.
     *
     * Search engines answer *something* for almost any query, so taking the top hit unchecked is
     * how you end up playing a cover, a karaoke version, or an unrelated track that happened to
     * rank. Both fields must agree before a result is accepted.
     */
    private fun List<UnifiedTrack>.bestMatch(title: String, artist: String): UnifiedTrack? =
        firstOrNull { candidate ->
            candidate.title.matches(title) && (
                artist.isBlank()
                    || isGenericArtist(artist)
                    || candidate.artist.isBlank()
                    || isGenericArtist(candidate.artist)
                    || candidate.artist.matches(artist)
            )
        }

    /**
     * Loose enough for the differences that do not change the recording, strict enough to reject a
     * different song: punctuation, case, common audio extensions, and a trailing "(Remastered 2011)"
     * are ignored, but one title still has to contain the other.
     */
    private fun String.matches(other: String): Boolean {
        val a = normalise()
        val b = other.normalise()
        if (a.isEmpty() || b.isEmpty()) return false
        return a == b || a.contains(b) || b.contains(a)
    }

    private fun String.normalise(): String =
        lowercase()
            .replace(AUDIO_EXTENSIONS, "")
            .replace(BRACKETED, " ")
            .filter { it.isLetterOrDigit() || it.isWhitespace() }
            .trim()
            .replace(WHITESPACE, " ")

    private fun isGenericArtist(a: String): Boolean {
        val clean = a.trim().lowercase()
        return clean in GENERIC_ARTISTS || clean.startsWith("<") && clean.endsWith(">")
    }

    private companion object {
        val PERSONAL_SOURCES = setOf(SourceType.LOCAL, SourceType.NAVIDROME)
        val BRACKETED = Regex("""[\(\[].*?[\)\]]""")
        val WHITESPACE = Regex("""\s+""")
        val AUDIO_EXTENSIONS = Regex("""\.(mp3|flac|wav|ogg|m4a|aac|opus|wma|alac)$""", RegexOption.IGNORE_CASE)
        val GENERIC_ARTISTS = setOf("unknown", "unknown artist", "<unknown>", "various", "various artists", "various artist")
    }
}
