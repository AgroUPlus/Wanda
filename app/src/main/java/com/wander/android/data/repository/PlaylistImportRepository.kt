package com.wander.android.data.repository

import com.wander.android.core.database.dao.PlaylistDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.PlaylistEntity
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.work.PlaylistImportScheduler
import com.wander.android.core.work.PlaylistImportWorker
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

@Singleton
class PlaylistImportRepository @Inject constructor(
    private val spotifyParser: SpotifyPlaylistParser,
    private val deezerParser: DeezerPlaylistParser,
    private val youtubeParser: YouTubePlaylistParser,
    private val appleMusicParser: AppleMusicPlaylistParser,
    private val textParser: TextPlaylistParser,
    private val importScheduler: PlaylistImportScheduler,
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

    /**
     * Saves the playlist at once with every track as an [SourceType.UNRESOLVED] placeholder, then
     * hands matching to [PlaylistImportWorker]. Room is the source of truth, so the playlist is
     * usable and visible immediately and fills in as the worker resolves tracks.
     */
    suspend fun importParsedPlaylist(
        rawPlaylist: RawImportPlaylist,
        customTitle: String? = null,
        tracksToImport: List<RawImportTrack>? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val title = customTitle?.takeIf { it.isNotBlank() } ?: rawPlaylist.title
        val tracks = tracksToImport ?: rawPlaylist.tracks
        if (tracks.isEmpty()) {
            val msg = "This playlist has no tracks to import."
            _progress.value = ImportProgress.Failed(msg)
            return@withContext Result.failure(IllegalStateException(msg))
        }

        val placeholders = tracks.map { raw ->
            TrackEntity.fromUnifiedTrack(
                UnifiedTrack(
                    id = SourceType.UNRESOLVED.idPrefix + UUID.randomUUID(),
                    source = SourceType.UNRESOLVED,
                    title = raw.title,
                    artist = raw.artist,
                    album = raw.album?.takeIf { it.isNotBlank() },
                    durationMs = raw.durationMs
                )
            )
        }
        trackDao.upsertTracks(placeholders)

        val playlistId = "local:playlist:${UUID.randomUUID()}"
        playlistDao.insertPlaylist(
            PlaylistEntity(
                id = playlistId,
                name = title,
                comment = "Imported from ${rawPlaylist.platform.displayName}",
                coverArtUrl = rawPlaylist.coverUrl,
                trackIds = placeholders.joinToString(",") { it.id }
            )
        )
        importScheduler.enqueue(playlistId)

        _progress.value = ImportProgress.Success(playlistId, title, tracks.size)
        Result.success(playlistId)
    }
}
