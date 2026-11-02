package com.wander.android.data.repository

import android.util.Log
import com.wander.android.core.cache.DownloadScheduler
import com.wander.android.core.database.dao.PodcastDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.podcast.PodcastEpisodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The per-show "download new episodes" setting: queues what a show publishes for download on
 * Wi-Fi, and lets go of its older downloads so a daily show does not fill the phone.
 *
 * Trimming covers every downloaded episode of an auto-downloading show, not only the ones queued
 * here — except one downloaded by hand, which is the one kind of download whose start is recorded.
 * A liked episode, or one the listener is partway through, is never let go either.
 */
@Singleton
class PodcastAutoDownloads @Inject constructor(
    private val podcastDao: PodcastDao,
    private val trackDao: TrackDao,
    private val downloads: DownloadScheduler,
    private val secureStorage: SecureStorage
) {
    /** Queues the newest of [episodes], at most [KEEP_DOWNLOADED], that are not on the device yet. */
    fun queue(episodes: List<TrackEntity>) {
        episodes.sortedByDescending { it.addedTimestamp }
            .take(KEEP_DOWNLOADED)
            .filterNot { it.isDownloaded }
            .forEach { downloads.downloadEpisode(it.id) }
    }

    /** Turns the setting on or off; turning it on also fetches the show's latest episodes. */
    suspend fun setEnabled(feedUrl: String, enabled: Boolean) {
        podcastDao.setAutoDownload(feedUrl, enabled)
        if (enabled) queue(podcastDao.newestEpisodes(PodcastEpisodes.albumIdOf(feedUrl), KEEP_DOWNLOADED))
    }

    /**
     * Removes the show's downloads beyond its newest [KEEP_DOWNLOADED]. The episodes stay listed.
     * An episode the listener downloaded by hand is kept: that one was chosen, not swept in.
     */
    suspend fun trim(feedUrl: String) = withContext(Dispatchers.IO) {
        val manual = secureStorage.downloads.manualTrackIds.value
        for (episode in podcastDao.downloadedEpisodesBeyond(PodcastEpisodes.albumIdOf(feedUrl), KEEP_DOWNLOADED)) {
            if (episode.id in manual) continue
            val path = episode.localFilePath
            // A file already gone is cleared too; one that would not delete keeps its row, so it is
            // tried again next pass instead of becoming an orphan nothing points at.
            if (!path.isNullOrBlank() && !File(path).delete() && File(path).exists()) {
                Log.w(TAG, "Could not delete an old episode download")
                continue
            }
            trackDao.setDownloaded(episode.id, isDownloaded = false, localPath = null)
        }
    }

    internal companion object {
        /** How many downloaded episodes an auto-downloading show keeps. */
        const val KEEP_DOWNLOADED = 5
        private const val TAG = "PodcastAutoDownloads"
    }
}
