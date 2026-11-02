package com.wander.android.core.cache

import com.wander.android.core.cache.predict.OfflinePredictor
import com.wander.android.core.cache.predict.PastPlay
import com.wander.android.core.database.dao.HistoryDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.security.SecureDownloadPreferences
import java.time.ZoneId

/** What one [DownloadWorker] run is for. Split out of the worker for length. */
internal class DownloadCandidates(
    private val trackDao: TrackDao,
    private val historyDao: HistoryDao,
    private val prefs: SecureDownloadPreferences
) {
    /**
     * One explicit track (a podcast episode) when [requestedId] is set, the queue of tracks
     * downloaded by hand when [manual] is, or else the sweep of liked tracks topped up with what
     * this listener is likely to play next. [onRequest] is "Download liked tracks now".
     */
    suspend fun select(requestedId: String?, manual: Boolean, onRequest: Boolean): List<TrackEntity> {
        if (requestedId != null) {
            return listOfNotNull(trackDao.getTrackById(requestedId)).filterNot { it.isDownloaded }
        }
        if (manual) return manualQueue()
        // Liked tracks, not most-played: the user explicitly asked to keep these. "Download
        // liked tracks now" asks for them by name, so it does not wait on the auto switch.
        val wantLiked = prefs.autoDownloadLiked.value || onRequest
        val liked = if (wantLiked) trackDao.getLikedNotDownloaded(BATCH_SIZE) else emptyList()
        // Room left in the batch is spent on what this listener is likely to play next rather
        // than left idle, when the user allows it: the cost is a few tracks of data on songs
        // nobody picked.
        return if (liked.size < BATCH_SIZE && prefs.autoDownloadPredicted.value) {
            (liked + predictiveCandidates(BATCH_SIZE - liked.size)).distinctBy { it.id }
        } else {
            liked
        }
    }

    /** The manual queue, not batched: the user asked for every one of these. */
    private suspend fun manualQueue(): List<TrackEntity> {
        val ids = prefs.manualTrackIds.value
        val rows = ids.mapNotNull { trackDao.getTrackById(it) }
        // An id whose row has gone has nothing to download, and would read as waiting forever.
        val gone = ids - rows.map { it.id }.toSet()
        if (gone.isNotEmpty()) prefs.unmarkManual(gone)
        return rows.filterNot { it.isDownloaded }
    }

    /**
     * The tracks [OfflinePredictor] expects to be played next, from this device's own history and
     * library. Downloaded, local and one-shot tracks are excluded there; the storage cap is
     * enforced by the download loop like any other track's.
     */
    private suspend fun predictiveCandidates(limit: Int): List<TrackEntity> {
        val now = System.currentTimeMillis()
        val plays = historyDao.getPlaysBetween(now - OfflinePredictor.WINDOW_MS, now + 1)
            .map { PastPlay(it.trackId, it.playedAt) }
        val tracks = trackDao.tracksWithUserState()
        val byId = tracks.associateBy { it.id }
        return OfflinePredictor(ZoneId.systemDefault()).rank(tracks, plays, now, limit).mapNotNull { byId[it] }
    }

    private companion object {
        const val BATCH_SIZE = 10
    }
}
