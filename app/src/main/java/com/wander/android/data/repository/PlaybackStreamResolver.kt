package com.wander.android.data.repository

import android.content.res.Resources
import androidx.media3.common.MimeTypes
import com.wander.android.R
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.isOneShotTrackId
import com.wander.android.data.sources.IMusicSource
import com.wander.android.data.sources.StreamInfo
import com.wander.android.data.sources.StreamRoute
import java.io.File
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
    private val resources: Resources,
    private val trackDao: TrackDao,
    private val sources: Set<IMusicSource>,
    private val secureStorage: SecureStorage,
    private val reachability: SourceReachability,
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
        ephemeralStreams[trackId]?.let { return@withContext Result.success(it.copy(route = StreamRoute.EPHEMERAL)) }

        if (isOneShotTrackId(trackId)) {
            trackDao.deleteOneShotTrackRows()
            return@withContext Result.failure(
                IllegalStateException("that transfer has ended; ask for the track again")
            )
        }

        val cached = trackDao.getTrackById(trackId)

        localStreamFor(cached)?.let { return@withContext Result.success(it) }
        navidromeStreamFor(cached)?.let { return@withContext Result.success(it) }
        originalSourceStream(trackId, cached)
    }

    /** Tier 1: Internal / Downloaded local file, or a local copy of the same recording. */
    private suspend fun localStreamFor(cached: TrackEntity?): StreamInfo? {
        downloadedFileOf(cached)?.let { path ->
            return StreamInfo(uri = path, isDirectFile = true, route = StreamRoute.DOWNLOAD)
        }
        if (cached == null || cached.effectiveSource == SourceType.LOCAL || cached.effectiveSource == SourceType.PODCAST) return null
        val localMatch =
            sameRecordingAs(cached, trackDao.findLocalOrDownloadedCandidates(cached.title, TITLE_CANDIDATES))
        val localPath = downloadedFileOf(localMatch) ?: localMatch?.streamUri
        return localPath?.takeIf {
            it.isNotBlank()
        }?.let { StreamInfo(uri = it, isDirectFile = true, route = StreamRoute.LOCAL_COPY) }
    }

    /**
     * The track's downloaded file, if it is really there. A row can name a file that is gone — the
     * user cleared app storage, or an older backup restored another install's path — and handing
     * that to the player fails with ENOENT instead of streaming. The stale claim is cleared so the
     * track reads as not downloaded everywhere else too.
     */
    private suspend fun downloadedFileOf(track: TrackEntity?): String? {
        val path = track?.localFilePath?.takeIf { it.isNotBlank() } ?: return null
        if (File(path).exists()) return path
        trackDao.setDownloaded(track.id, isDownloaded = false, localPath = null)
        return null
    }

    /** Tier 2: Navidrome (Personal Server), within a time budget so a slow server can't stall playback. */
    private suspend fun navidromeStreamFor(cached: TrackEntity?): StreamInfo? {
        // An episode is one specific recording; a same-titled Navidrome song is never a substitute.
        if (cached == null || cached.effectiveSource == SourceType.NAVIDROME || cached.effectiveSource == SourceType.PODCAST) return null
        if (sourceFor(SourceType.NAVIDROME)?.isConfigured?.value != true) return null
        return withTimeoutOrNull(SUBSTITUTION_BUDGET_MS) { navidromeSubstituteFor(cached) }
            ?.copy(route = StreamRoute.NAVIDROME)
    }

    /** Tier 3: Original Source / YouTube Music. */
    private suspend fun originalSourceStream(trackId: String, cached: TrackEntity?): Result<StreamInfo> {
        val type = cached?.effectiveSource ?: SourceType.entries.firstOrNull {
            trackId.startsWith(it.idPrefix)
        } ?: return Result.failure( // NOSONAR: firstOrNull returns null for an unknown prefix, so this elvis is needed
            IllegalArgumentException("Unrecognised track id: $trackId")
        )

        if (type != SourceType.LOCAL &&
            (!reachability.isReachable(type) || secureStorage.isOfflineMode.value)
        ) {
            return Result.failure(
                IOException(
                    resources.getString(
                        if (secureStorage.isOfflineMode.value) {
                            R.string.playback_offline_not_downloaded
                        } else {
                            R.string.playback_offline_no_network
                        }
                    )
                )
            )
        }
        if (type == SourceType.PODCAST) return podcastStream(cached)
        val source = sourceFor(type) ?: return Result.failure(IllegalStateException("$type is unavailable"))
        if (!source.isStreamable.value) return connectedSubstituteFor(cached, type, source.displayName)
        return source.getStreamInfo(trackId).onSuccess { info ->
            if (info.format == MimeTypes.APPLICATION_M3U8) trackDao.markLive(trackId)
        }
    }

    /**
     * The track belongs to a source that cannot stream right now — Deezer signed out, say. Asking
     * it anyway only fails deep inside its resolver, so another streamable source's copy of the
     * same recording is used instead, judged by the same [RecordingRules] as every other
     * substitution here. Local files and Navidrome were already tried by the tiers above.
     *
     * When no connected source has it, the failure names the source the track needs rather than
     * whatever the unconnected backend happened to throw.
     */
    private suspend fun connectedSubstituteFor(
        cached: TrackEntity?,
        unavailable: SourceType,
        unavailableName: String
    ): Result<StreamInfo> {
        val substitute = cached?.let { track ->
            withTimeoutOrNull(CROSS_SOURCE_BUDGET_MS) { streamableCopyOf(track, unavailable) }
        }
        return substitute?.let { Result.success(it) } ?: Result.failure(
            IllegalStateException(resources.getString(R.string.playback_source_not_connected, unavailableName))
        )
    }

    private suspend fun streamableCopyOf(cached: TrackEntity, unavailable: SourceType): StreamInfo? {
        val wanted = cached.toUnifiedTrack()
        val rules = recordingRules.current()
        return sources
            .filter { source ->
                source.sourceType != unavailable &&
                    source.sourceType !in TRIED_BY_EARLIER_TIERS &&
                    source.capabilities.search &&
                    source.isSearchable.value &&
                    source.isStreamable.value
            }
            .sortedBy { it.sourceType.priority }
            .firstNotNullOfOrNull { source ->
                val found = source.search("${cached.title} ${cached.artist}").getOrNull().orEmpty()
                rules.substituteFor(wanted, found)?.let { hit -> source.getStreamInfo(hit.id).getOrNull() }
            }
    }

    /** Episodes carry their enclosure URL in Room; there is no backend to ask for a stream. */
    private fun podcastStream(cached: TrackEntity?): Result<StreamInfo> {
        val uri = cached?.streamUri?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("This episode has no audio URL"))
        return Result.success(StreamInfo(uri = uri, format = cached.format ?: MimeTypes.AUDIO_MPEG, route = StreamRoute.PODCAST))
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
        private const val CROSS_SOURCE_BUDGET_MS = 5_000L
        private val TRIED_BY_EARLIER_TIERS = setOf(SourceType.LOCAL, SourceType.NAVIDROME)
    }
}
