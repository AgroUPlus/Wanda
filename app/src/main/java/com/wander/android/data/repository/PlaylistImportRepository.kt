package com.wander.android.data.repository

import com.wander.android.core.database.dao.PlaylistDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.PlaylistEntity
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.importer.AppleMusicPlaylistParser
import com.wander.android.data.importer.DeezerPlaylistParser
import com.wander.android.data.importer.ImportProgress
import com.wander.android.data.importer.PlatformType
import com.wander.android.data.importer.RawImportPlaylist
import com.wander.android.data.importer.RawImportTrack
import com.wander.android.data.importer.SpotifyPlaylistParser
import com.wander.android.data.importer.TextPlaylistParser
import com.wander.android.data.importer.YouTubePlaylistParser
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

private val NOISE_SUFFIXES = Regex("""(?i)\s*[\(\[](?:official\s*(?:music)?\s*video|official\s*audio|lyrics?|audio|remaster(?:ed)?\s*\d*|video|explicit|clean|visualizer)[\)\]]""")
private val FEAT_REGEX = Regex("""(?i)\s*(?:feat\.?|ft\.?|featuring)\s+.*""")
private const val MIN_MATCH_SCORE = 100

@Singleton
class PlaylistImportRepository @Inject constructor(
    private val spotifyParser: SpotifyPlaylistParser,
    private val deezerParser: DeezerPlaylistParser,
    private val youtubeParser: YouTubePlaylistParser,
    private val appleMusicParser: AppleMusicPlaylistParser,
    private val textParser: TextPlaylistParser,
    private val musicRepository: MusicRepository,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao
) {
    private val _progress = MutableStateFlow<ImportProgress>(ImportProgress.Idle)
    val progress: StateFlow<ImportProgress> = _progress.asStateFlow()

    fun reset() {
        _progress.value = ImportProgress.Idle
    }

    suspend fun importPlaylist(input: String): Result<String> = withContext(Dispatchers.IO) {
        val platform = PlatformType.detect(input)
        _progress.value = ImportProgress.Fetching(platform)

        val rawPlaylistResult: Result<RawImportPlaylist> = when (platform) {
            PlatformType.SPOTIFY -> spotifyParser.parse(input)
            PlatformType.DEEZER -> deezerParser.parse(input)
            PlatformType.YOUTUBE -> youtubeParser.parse(input)
            PlatformType.APPLE_MUSIC -> appleMusicParser.parse(input)
            PlatformType.PLAIN_TEXT -> textParser.parse(input)
        }

        val rawPlaylist = rawPlaylistResult.getOrElse { error ->
            val msg = error.message ?: "Failed to read playlist from $platform"
            _progress.value = ImportProgress.Failed(msg)
            return@withContext Result.failure(error)
        }

        importParsedPlaylist(rawPlaylist)
    }

    suspend fun importParsedPlaylist(
        rawPlaylist: RawImportPlaylist,
        customTitle: String? = null,
        tracksToImport: List<RawImportTrack>? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val title = customTitle?.takeIf { it.isNotBlank() } ?: rawPlaylist.title
        val tracks = tracksToImport ?: rawPlaylist.tracks
        val total = tracks.size
        val matchedTracks = mutableListOf<UnifiedTrack>()

        tracks.forEachIndexed { index, rawTrack ->
            _progress.value = ImportProgress.Matching(
                current = index + 1,
                total = total,
                currentTrackName = "${rawTrack.artist} - ${rawTrack.title}",
                matchedCount = matchedTracks.size
            )

            val query = "${rawTrack.artist.cleanArtist()} ${rawTrack.title.cleanTitle()}".trim()
            var searchResults = musicRepository.searchAllSources(query)
            var bestMatch = findBestMatch(rawTrack.title, rawTrack.artist, rawTrack.durationMs, searchResults)

            if (bestMatch == null) {
                val titleQuery = rawTrack.title.cleanTitle()
                if (titleQuery.isNotBlank()) {
                    searchResults = musicRepository.searchAllSources(titleQuery)
                    bestMatch = findBestMatch(rawTrack.title, rawTrack.artist, rawTrack.durationMs, searchResults)
                }
            }

            if (bestMatch != null) {
                matchedTracks.add(bestMatch)
            }
        }

        if (matchedTracks.isEmpty()) {
            val msg = "Couldn't match any tracks from this playlist."
            _progress.value = ImportProgress.Failed(msg)
            return@withContext Result.failure(IllegalStateException(msg))
        }

        _progress.value = ImportProgress.Saving(title, matchedTracks.size)

        // Save matched tracks uniquely to Room so they are addressable locally
        val uniqueTracks = matchedTracks.distinctBy { it.id }
        trackDao.upsertTracks(
            uniqueTracks.map { TrackEntity.fromUnifiedTrack(it, isLibrary = true) }
        )

        val playlistId = "local:playlist:${UUID.randomUUID()}"
        val playlistEntity = PlaylistEntity(
            id = playlistId,
            name = title,
            comment = "Imported from ${rawPlaylist.platform.displayName} • ${matchedTracks.size}/$total matched",
            coverArtUrl = rawPlaylist.coverUrl ?: matchedTracks.firstOrNull()?.artworkUrl,
            trackIds = matchedTracks.map { it.id }.joinToString(",")
        )
        playlistDao.insertPlaylist(playlistEntity)

        _progress.value = ImportProgress.Success(
            playlistId = playlistId,
            playlistName = title,
            matchedCount = matchedTracks.size,
            totalCount = total
        )

        Result.success(playlistId)
    }

    private fun findBestMatch(
        rawTitle: String,
        rawArtist: String,
        durationMs: Long,
        candidates: List<UnifiedTrack>
    ): UnifiedTrack? {
        if (candidates.isEmpty()) return null
        val cleanTitle = rawTitle.cleanTitle()
        val normTitle = cleanTitle.normalize()
        val cleanArtist = rawArtist.cleanArtist()
        val normArtist = cleanArtist.normalize()

        val scored = candidates.mapNotNull { candidate ->
            val cTitleNorm = candidate.title.cleanTitle().normalize()
            val cArtistNorm = candidate.artist.cleanArtist().normalize()

            var score = 0
            score += when (candidate.source) {
                SourceType.LOCAL -> 150
                SourceType.NAVIDROME -> 100
                SourceType.YTMUSIC -> 50
            }

            val titleMatched = when {
                cTitleNorm == normTitle -> { score += 100; true }
                cTitleNorm.contains(normTitle) || normTitle.contains(cTitleNorm) -> { score += 60; true }
                else -> false
            }

            if (!titleMatched) return@mapNotNull null

            when {
                cArtistNorm == normArtist -> score += 80
                cArtistNorm.contains(normArtist) || normArtist.contains(cArtistNorm) -> score += 40
                normArtist.isBlank() || normArtist == "unknown" -> score += 20
            }

            if (durationMs > 0 && candidate.durationMs > 0) {
                val deltaSec = abs(candidate.durationMs - durationMs) / 1000
                if (deltaSec <= 3) score += 40
                else if (deltaSec <= 10) score += 20
                else if (deltaSec > 60) score -= 30
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
}
