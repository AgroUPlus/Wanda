package com.wander.android.core.cache

import com.wander.android.R
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.wander.android.core.cache.predict.OfflinePredictor
import com.wander.android.core.cache.predict.PastPlay
import com.wander.android.core.database.dao.HistoryDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.notification.WorkEta
import com.wander.android.core.notification.WorkProgressNotification
import com.wander.android.core.playback.DeezerDecryptingDataSource
import com.wander.android.core.security.DeezerStreamCipher
import com.wander.android.core.security.SecureStorage
import com.wander.android.core.work.WorkControls
import com.wander.android.data.model.isOneShotTrackId
import com.wander.android.data.repository.MusicRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.time.ZoneId

/**
 * Downloads liked tracks for offline listening. Runs only on unmetered networks while charging
 * (see [DownloadScheduler]), so it never competes with the user for battery or data.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val trackDao: TrackDao,
    private val musicRepository: MusicRepository,
    private val historyDao: HistoryDao,
    private val okHttpClient: OkHttpClient,
    private val notifications: WorkProgressNotification,
    private val workControls: WorkControls,
    private val secureStorage: SecureStorage
) : CoroutineWorker(context, params) {

    private val downloadsDir = File(context.filesDir, "downloads")
    private val files = DownloadFiles(downloadsDir)

    private fun notifying(eta: WorkEta, done: Int, total: Int, title: String? = null) =
        notifications.foregroundInfo(
            kind = WorkProgressNotification.Kind.DOWNLOAD,
            title = applicationContext.getString(R.string.notif_downloading_music),
            text = listOfNotNull(
                applicationContext.getString(R.string.notif_import_progress, done, total),
                eta.describe(applicationContext.resources, done, total, System.currentTimeMillis()),
                title
            ).joinToString(" · "),
            done = done,
            total = total
        )

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (workControls.isPaused(WorkProgressNotification.Kind.DOWNLOAD).value) {
            return@withContext Result.success()
        }
        // Whatever an earlier run left behind: a part file from a killed process, or a download
        // whose row has since gone. Nothing else would ever delete them.
        files.sweepOrphans(trackDao.localFilePaths(), System.currentTimeMillis())

        val prefs = secureStorage.downloads
        // A single explicit request from the context menu, or the liked-tracks sweep.
        val requestedId = inputData.getString(KEY_TRACK_ID)
        val candidates = if (requestedId != null) {
            listOfNotNull(trackDao.getTrackById(requestedId)).filterNot { it.isDownloaded }
        } else {
            // Liked tracks, not most-played: the user explicitly asked to keep these. "Download
            // liked tracks now" asks for them by name, so it does not wait on the auto switch.
            val wantLiked = prefs.autoDownloadLiked.value || inputData.getBoolean(KEY_ON_REQUEST, false)
            val liked = if (wantLiked) trackDao.getLikedNotDownloaded(BATCH_SIZE) else emptyList()
            // Room left in the batch is spent on what this listener is likely to play next rather
            // than left idle, when the user allows it: the cost is a few tracks of data on songs
            // nobody picked.
            if (liked.size < BATCH_SIZE && prefs.autoDownloadPredicted.value) {
                (liked + predictiveCandidates(BATCH_SIZE - liked.size)).distinctBy { it.id }
            } else {
                liked
            }
        }
        // A peer's or relayed track is borrowed for one playback: its session serves once, and
        // writing it to disk would turn the loan into a copy (and save the relay's ciphertext).
        val pending = candidates.filterNot { isOneShotTrackId(it.id) }
        if (pending.isEmpty()) return@withContext Result.success()

        downloadsDir.mkdirs()
        // Retrying would not help: the user has to free space first.
        if (!files.hasRoomFor(0L)) return@withContext Result.failure(noSpace())
        var failures = 0

        // Downloading a batch of liked tracks is minutes of network, and it was doing it with no
        // way to tell it apart from nothing happening — and subject to the same ten-minute ceiling
        // as every other plain worker.
        val eta = WorkEta(System.currentTimeMillis())
        runCatching { setForeground(notifying(eta, 0, pending.size)) }

        val limitBytes = prefs.limitBytes.value
        for ((index, entity) in pending.withIndex()) {
            if (isStopped) break
            // Stop queueing at the cap rather than starting a track that is bound to overshoot it.
            if (!files.withinLimit(limitBytes, 0L)) break
            runCatching { setForeground(notifying(eta, index, pending.size, entity.title)) }
            val streamInfo = musicRepository.getStreamInfo(entity.id).getOrElse {
                failures++
                continue
            }
            val destination = File(downloadsDir, "${entity.id.replace(':', '_')}.${extensionFor(streamInfo.format)}")
            val downloaded = runCatching { download(streamInfo.uri, streamInfo.headers, destination) }
            if (downloaded.isSuccess) {
                trackDao.setDownloaded(entity.id, true, destination.absolutePath)
                prefs.setStorageFull(false)
            } else if (downloaded.exceptionOrNull() is InsufficientStorageException) {
                // Every later track would hit the same wall.
                return@withContext Result.failure(noSpace())
            } else if (downloaded.exceptionOrNull() is DownloadLimitReachedException) {
                // The user's own cap, not a fault: the rest of the batch waits for room.
                break
            } else {
                failures++
            }
        }

        // Retry later rather than reporting success we did not achieve.
        if (failures == pending.size) Result.retry() else Result.success()
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

    /**
     * Fetches [url] into [destination] as audio a plain file player can read.
     *
     * Playback decrypts a Deezer stream inside the Media3 chain, keyed on
     * [DeezerDecryptingDataSource.DEEZER_TRACK_ID_HEADER]; a download used to copy the bytes
     * straight to disk, ciphertext and all, and a downloaded file is played as a plain local file
     * that nothing decrypts. So it is decrypted here, and the marker header — meant for that data
     * source, not the CDN — is not sent.
     */
    private fun download(url: String, headers: Map<String, String>, destination: File) {
        val deezerTrackId = headers[DeezerDecryptingDataSource.DEEZER_TRACK_ID_HEADER]?.trim()?.takeIf { it.isNotEmpty() }
        val request = Request.Builder().url(url).apply {
            headers
                .filterKeys { it != DeezerDecryptingDataSource.DEEZER_TRACK_ID_HEADER }
                .forEach { (k, v) -> header(k, v) }
        }.build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download failed with HTTP ${response.code}")
            }
            // Checked against the real size where the server sends one, before any byte lands.
            files.requireRoomFor(response.body.contentLength())
            files.requireWithinLimit(secureStorage.downloads.limitBytes.value, response.body.contentLength())
            files.write(response.body.byteStream(), destination) { input, out ->
                if (deezerTrackId != null) {
                    DeezerStreamCipher(deezerTrackId).decryptStream(input, out)
                } else {
                    input.copyTo(out)
                }
            }
        }
    }

    /** Also recorded for Settings, which says downloads are paused until there is room. */
    private fun noSpace(): Data {
        secureStorage.downloads.setStorageFull(true)
        return workDataOf(KEY_FAILURE_REASON to FAILURE_INSUFFICIENT_STORAGE)
    }

    private fun extensionFor(format: String): String = when {
        format.contains("opus") || format.contains("webm") -> "opus"
        format.contains("flac") -> "flac"
        format.contains("mp4") || format.contains("m4a") -> "m4a"
        else -> "mp3"
    }

    companion object {
        /** Set to download one specific track instead of sweeping liked tracks. */
        const val KEY_TRACK_ID = "track_id"

        /** Set by "Download liked tracks now": liked tracks are fetched even with auto-download off. */
        const val KEY_ON_REQUEST = "on_request"

        /** Output key naming why a run failed; see [FAILURE_INSUFFICIENT_STORAGE]. */
        const val KEY_FAILURE_REASON = "failure_reason"

        /** The volume holding downloads is too full to fit a track plus the free-space reserve. */
        const val FAILURE_INSUFFICIENT_STORAGE = "insufficient_storage"

        private const val BATCH_SIZE = 10
    }
}
