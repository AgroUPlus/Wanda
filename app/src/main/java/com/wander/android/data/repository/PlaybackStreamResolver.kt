package com.wander.android.data.repository

import androidx.media3.common.MimeTypes
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.network.ConnectivityObserver
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.isOneShotTrackId
import com.wander.android.data.sources.IMusicSource
import com.wander.android.data.sources.StreamInfo
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Resolves playable [StreamInfo] for a given track id, checking ephemeral peer streams,
 * local downloads, Navidrome server substitution, and the original remote backend.
 */
internal class PlaybackStreamResolver(
    private val trackDao: TrackDao,
    private val sources: Set<IMusicSource>,
    private val secureStorage: SecureStorage,
    private val connectivity: ConnectivityObserver,
    private val recordingRules: RecordingRulesRepository
) {
    private val ephemeralStreams = ConcurrentHashMap<String, StreamInfo>()

    fun registerEphemeralStream(trackId: String, info: StreamInfo) {
        if (ephemeralStreams.size > MAX_EPHEMERAL_STREAMS) ephemeralStreams.clear()
        ephemeralStreams[trackId] = info
    }

    fun clearEphemeralStreams() = ephemeralStreams.clear()

    private fun sourceFor(type: SourceType) = sources.firstOrNull { it.sourceType == type }

    suspend fun getStreamInfo(trackId: String): Result<StreamInfo> = withContext(Dispatchers.IO) {
        ephemeralStreams[trackId]?.let { return@withContext Result.success(it) }

        if (isOneShotTrackId(trackId)) {
            trackDao.deleteOneShotTrackRows()
            return@withContext Result.failure(
                IllegalStateException("that transfer has ended; ask for the track again")
            )
        }

        val cached = trackDao.getTrackById(trackId)

        // Tier 1: Internal / Downloaded local file
        cached?.localFilePath?.takeIf { it.isNotBlank() }?.let { path ->
            return@withContext Result.success(StreamInfo(uri = path, isDirectFile = true))
        }
        if (cached != null && cached.source != SourceType.LOCAL) {
            val localMatch = sameRecordingAs(cached, trackDao.findLocalOrDownloadedCandidates(cached.title, TITLE_CANDIDATES))
            val localPath = localMatch?.localFilePath?.takeIf { it.isNotBlank() } ?: localMatch?.streamUri
            if (localPath != null && localPath.isNotBlank()) {
                return@withContext Result.success(StreamInfo(uri = localPath, isDirectFile = true))
            }
        }

        // Tier 2: Navidrome (Personal Server)
        if (cached != null && cached.source != SourceType.NAVIDROME && sourceFor(SourceType.NAVIDROME)?.isConfigured?.value == true) {
            withTimeoutOrNull(SUBSTITUTION_BUDGET_MS) { navidromeSubstituteFor(cached) }
                ?.let { return@withContext Result.success(it) }
        }

        // Tier 3: Original Source / YouTube Music
        val type = cached?.source ?: SourceType.entries.firstOrNull {
            trackId.startsWith(it.idPrefix)
        } ?: return@withContext Result.failure(
            IllegalArgumentException("Unrecognised track id: $trackId")
        )

        if (type != SourceType.LOCAL &&
            (!connectivity.isOnline.value || secureStorage.isOfflineMode.value)
        ) {
            return@withContext Result.failure(
                IOException(
                    if (secureStorage.isOfflineMode.value) {
                        "Offline mode — this track is not downloaded to this device"
                    } else {
                        "No network — this track is not available on this device"
                    }
                )
            )
        }
        val source = sourceFor(type)
            ?: return@withContext Result.failure(IllegalStateException("$type is unavailable"))
        source.getStreamInfo(trackId).onSuccess { info ->
            if (info.format == MimeTypes.APPLICATION_M3U8) trackDao.markLive(trackId)
        }
    }

    private suspend fun navidromeSubstituteFor(cached: TrackEntity): StreamInfo? {
        val navidrome = sourceFor(SourceType.NAVIDROME) ?: return null
        val known = sameRecordingAs(
            cached,
            trackDao.findNavidromeCandidates(cached.title, TITLE_CANDIDATES)
        )
        if (known != null) return navidrome.getStreamInfo(known.id).getOrNull()

        val found = navidrome.search("${cached.title} ${cached.artist}").getOrNull().orEmpty()
        val hit = recordingRules.current().substituteFor(cached.toUnifiedTrack(), found) ?: return null
        return navidrome.getStreamInfo(hit.id).getOrNull()
    }

    private suspend fun sameRecordingAs(
        wanted: TrackEntity,
        candidates: List<TrackEntity>
    ): TrackEntity? {
        if (candidates.isEmpty()) return null
        val chosen = recordingRules.current().substituteFor(
            wanted = wanted.toUnifiedTrack(),
            candidates = candidates.map(TrackEntity::toUnifiedTrack)
        ) ?: return null
        return candidates.first { it.id == chosen.id }
    }

    companion object {
        private const val MAX_EPHEMERAL_STREAMS = 256
        private const val TITLE_CANDIDATES = 20
        private const val SUBSTITUTION_BUDGET_MS = 1_500L
    }
}
