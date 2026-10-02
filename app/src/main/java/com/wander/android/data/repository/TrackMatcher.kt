package com.wander.android.data.repository

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Bracketed editorial noise on an imported title. One term per line because the list is the part
 * that gets edited; the pattern around it never changes.
 */
private val NOISE_SUFFIX_TERMS = listOf(
    """official\s*(?:music)?\s*video""",
    """official\s*audio""",
    """lyrics?""",
    "audio",
    """remaster(?:ed)?\s*\d*""",
    "video",
    "explicit",
    "clean",
    "visualizer"
)

private val NOISE_SUFFIXES =
    Regex("""(?i)\s*[\(\[](?:${NOISE_SUFFIX_TERMS.joinToString("|")})[\)\]]""")
private val FEAT_REGEX = Regex("""(?i)\s*(?:feat\.?|ft\.?|featuring)\s+.*""")
internal const val MIN_MATCH_SCORE = 100

/**
 * Finds the playable track in the user's active sources that best matches a title and artist read
 * off another platform. Shared by every importer so a playlist resolves the same way wherever the
 * link came from.
 */
@Singleton
class TrackMatcher @Inject constructor(private val musicRepository: MusicRepository) {

    /** The best match, or null when no active source has one — a normal outcome, not an error. */
    suspend fun match(title: String, artist: String, durationMs: Long): UnifiedTrack? {
        val query = "${artist.cleanArtist()} ${title.cleanTitle()}".trim()
        findBestMatch(title, artist, durationMs, musicRepository.searchAllSources(query))
            ?.let { return it }

        val titleQuery = title.cleanTitle()
        if (titleQuery.isBlank()) return null
        return findBestMatch(title, artist, durationMs, musicRepository.searchAllSources(titleQuery))
    }
}

internal fun findBestMatch(
    rawTitle: String,
    rawArtist: String,
    durationMs: Long,
    candidates: List<UnifiedTrack>
): UnifiedTrack? {
    if (candidates.isEmpty()) return null
    val normTitle = rawTitle.cleanTitle().normalize()
    val normArtist = rawArtist.cleanArtist().normalize()

    val scored = candidates.mapNotNull { candidate ->
        val cTitleNorm = candidate.title.cleanTitle().normalize()
        val cArtistNorm = candidate.artist.cleanArtist().normalize()

        var score = when (candidate.source) {
            SourceType.LOCAL -> 150
            SourceType.NAVIDROME -> 100
            SourceType.YTMUSIC -> 50
            SourceType.DEEZER, SourceType.PODCAST -> 0
            // A stub is what is being resolved; it can never be what it resolves to.
            SourceType.UNRESOLVED -> return@mapNotNull null
        }

        when {
            cTitleNorm == normTitle -> score += 100
            cTitleNorm.contains(normTitle) || normTitle.contains(cTitleNorm) -> score += 60
            else -> return@mapNotNull null
        }

        when {
            cArtistNorm == normArtist -> score += 80
            cArtistNorm.contains(normArtist) || normArtist.contains(cArtistNorm) -> score += 40
            normArtist.isBlank() || normArtist == "unknown" -> score += 20
        }

        if (durationMs > 0 && candidate.durationMs > 0) {
            val deltaSec = abs(candidate.durationMs - durationMs) / 1000
            when {
                deltaSec <= 3 -> score += 40
                deltaSec <= 10 -> score += 20
                deltaSec > 60 -> score -= 30
            }
        }

        if (score >= MIN_MATCH_SCORE) candidate to score else null
    }

    return scored.maxByOrNull { it.second }?.first
}

private fun String.cleanTitle(): String =
    replace(NOISE_SUFFIXES, "").replace(FEAT_REGEX, "").trim()

private fun String.cleanArtist(): String =
    replace(FEAT_REGEX, "").trim()

private fun String.normalize(): String =
    lowercase().filter { it.isLetterOrDigit() || it.isWhitespace() }.trim()
