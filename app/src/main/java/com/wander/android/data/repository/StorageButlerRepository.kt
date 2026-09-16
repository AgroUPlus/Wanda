package com.wander.android.data.repository

import com.wander.android.core.cache.AudioCacheManager
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.SourceType
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How much a source's explicit downloads take up. */
data class SourceStorage(
    val source: SourceType,
    val trackCount: Int,
    val bytes: Long
)

data class StorageBreakdown(
    /** The streaming cache — replayed audio, not something the user chose to keep. */
    val streamCacheBytes: Long,
    val downloadsBySource: List<SourceStorage>,
    /** Downloads over [StorageButlerRepository.STALE_AFTER_DAYS] days old, unplayed, and unliked. */
    val staleDownloadCount: Int,
    val staleDownloadBytes: Long
)

/**
 * Where the device's storage is going, and the one cleanup most people actually want: downloads
 * that were fetched, never played, and are just sitting there — see [staleDownloads].
 *
 * Reuses [AudioCacheManager] for the streaming cache and [TrackDao] for downloads rather than
 * tracking either separately; this is a report over state those two already own, not a third
 * place bytes are counted.
 */
@Singleton
class StorageButlerRepository @Inject constructor(
    private val trackDao: TrackDao,
    private val audioCacheManager: AudioCacheManager
) {

    suspend fun breakdown(): StorageBreakdown = withContext(Dispatchers.IO) {
        val downloads = trackDao.getDownloadedTracksOnce()
        val stale = downloads.filter(::isStale)
        StorageBreakdown(
            streamCacheBytes = audioCacheManager.cacheSizeBytes(),
            downloadsBySource = downloads
                .groupBy { it.source }
                .map { (source, tracks) ->
                    SourceStorage(
                        source = source,
                        trackCount = tracks.size,
                        bytes = tracks.sumOf { it.sizeBytes ?: fileSizeOf(it.localFilePath) }
                    )
                }
                .sortedByDescending { it.bytes },
            staleDownloadCount = stale.size,
            staleDownloadBytes = stale.sumOf { it.sizeBytes ?: fileSizeOf(it.localFilePath) }
        )
    }

    /**
     * Deletes every stale download's file and clears its `isDownloaded` row.
     *
     * The track itself is not removed — only the local copy. It plays as a stream again exactly
     * as it did before it was downloaded, which is what makes this safe to offer with one tap
     * rather than a confirmation per track.
     */
    suspend fun cleanStaleDownloads(): Int = withContext(Dispatchers.IO) {
        val stale = trackDao.getDownloadedTracksOnce().filter(::isStale)
        stale.forEach { track ->
            track.localFilePath?.let { File(it).delete() }
            trackDao.setDownloaded(track.id, isDownloaded = false, localPath = null)
        }
        stale.size
    }

    /** Empties the streaming cache. Downloads are untouched — this is only replayable audio. */
    fun optimizeCacheSize() = audioCacheManager.clearCache()

    /**
     * `playCount` is lifetime, not "since this download" — there is no per-download counter, so a
     * track played heavily before ever being downloaded reads as "played" here even if it has sat
     * untouched since. That is the conservative direction to be wrong in: it undercounts what
     * cleanup would offer to delete rather than deleting something recently listened to.
     */
    private fun isStale(track: TrackEntity): Boolean =
        StorageButlerRules.isStale(track.downloadedAt, track.isLiked, track.playCount, System.currentTimeMillis())

    private fun fileSizeOf(path: String?): Long =
        path?.let { runCatching { File(it).length() }.getOrDefault(0L) } ?: 0L
}
