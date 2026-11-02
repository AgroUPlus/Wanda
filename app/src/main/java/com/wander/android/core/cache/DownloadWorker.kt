package com.wander.android.core.cache

import com.wander.android.R
import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.wander.android.core.database.dao.HistoryDao
import com.wander.android.core.database.dao.TrackDao
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
    private val candidates = DownloadCandidates(trackDao, historyDao, secureStorage.downloads)

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
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        // A peer's or relayed track is borrowed for one playback: its session serves once, and
        // writing it to disk would turn the loan into a copy (and save the relay's ciphertext).
        val pending = candidates.select(inputData.getString(KEY_TRACK_ID), manual, inputData.getBoolean(KEY_ON_REQUEST, false))
            .filterNot { isOneShotTrackId(it.id) }
        if (pending.isEmpty()) return@withContext Result.success()

        downloadsDir.mkdirs()
        // Retrying would not help: the user has to free space first.
        if (!files.hasRoomFor(0L)) return@withContext noSpace(manual, pending.map { it.id })
        val failed = mutableListOf<String>()
        val done = mutableSetOf<String>()
        var limitReached = false

        // Downloading a batch of liked tracks is minutes of network, and it was doing it with no
        // way to tell it apart from nothing happening — and subject to the same ten-minute ceiling
        // as every other plain worker.
        val eta = WorkEta(System.currentTimeMillis())
        runCatching { setForeground(notifying(eta, 0, pending.size)) }

        val limitBytes = prefs.limitBytes.value
        for ((index, entity) in pending.withIndex()) {
            if (isStopped) break
            // Taken back with "Remove download" while it was still waiting its turn.
            if (manual && entity.id !in prefs.manualTrackIds.value) continue
            // Stop queueing at the cap rather than starting a track that is bound to overshoot it.
            if (!files.withinLimit(limitBytes, 0L)) {
                limitReached = true
                break
            }
            runCatching { setForeground(notifying(eta, index, pending.size, entity.title)) }
            val streamInfo = musicRepository.getStreamInfo(entity.id).getOrElse {
                failed += entity.id
                continue
            }
            val destination = File(downloadsDir, "${entity.id.replace(':', '_')}.${extensionFor(streamInfo.format)}")
            val downloaded = runCatching { download(streamInfo.uri, streamInfo.headers, destination) }
            if (downloaded.isSuccess) {
                trackDao.setDownloaded(entity.id, true, destination.absolutePath)
                prefs.setStorageFull(false)
                done += entity.id
            } else if (downloaded.exceptionOrNull() is InsufficientStorageException) {
                // Every later track would hit the same wall.
                return@withContext noSpace(manual, pending.map { it.id } - done)
            } else if (downloaded.exceptionOrNull() is DownloadLimitReachedException) {
                // The user's own cap, not a fault: the rest of the batch waits for room.
                limitReached = true
                break
            } else {
                failed += entity.id
            }
        }

        when {
            manual -> finishManual(pending.map { it.id } - done, failed, limitReached)
            // Retry later rather than reporting success we did not achieve.
            failed.size == pending.size -> Result.retry()
            else -> Result.success()
        }
    }

    /**
     * The end of a manual run. A track that failed is tried again a few times; past that, and for
     * whatever the storage limit left behind, its mark is taken back, so the row stops saying it
     * is waiting for a download that is not coming and offers "Download" again instead.
     *
     * Success rather than failure when giving up: this job is a chain other requests append to,
     * and a failed link would fail every request queued behind it.
     */
    private fun finishManual(notDone: List<String>, failed: List<String>, limitReached: Boolean): Result {
        val prefs = secureStorage.downloads
        if (limitReached) {
            prefs.unmarkManual(notDone)
            return Result.success(workDataOf(KEY_FAILURE_REASON to FAILURE_LIMIT_REACHED))
        }
        if (failed.isEmpty()) return Result.success()
        if (runAttemptCount + 1 < MAX_MANUAL_ATTEMPTS) return Result.retry()
        Log.w(TAG, "Gave up on ${failed.size} manual downloads")
        prefs.unmarkManual(failed)
        return Result.success(workDataOf(KEY_FAILURE_REASON to FAILURE_GAVE_UP))
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

    /**
     * Also recorded for Settings, which says downloads are paused until there is room. A manual
     * run lets go of what it had not fetched, so those rows stop claiming to be on their way.
     */
    private fun noSpace(manual: Boolean, notDone: List<String>): Result {
        secureStorage.downloads.setStorageFull(true)
        if (manual) secureStorage.downloads.unmarkManual(notDone)
        return Result.failure(workDataOf(KEY_FAILURE_REASON to FAILURE_INSUFFICIENT_STORAGE))
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

        /** Set for the queue of tracks downloaded by hand; see [DownloadScheduler.downloadManual]. */
        const val KEY_MANUAL = "manual"

        /** Output key naming why a run failed; see [FAILURE_INSUFFICIENT_STORAGE]. */
        const val KEY_FAILURE_REASON = "failure_reason"

        /** The volume holding downloads is too full to fit a track plus the free-space reserve. */
        const val FAILURE_INSUFFICIENT_STORAGE = "insufficient_storage"

        /** A manual run stopped at the user's storage limit and let go of the rest. */
        const val FAILURE_LIMIT_REACHED = "limit_reached"

        /** A manual run kept failing on some tracks and stopped trying them. */
        const val FAILURE_GAVE_UP = "gave_up"

        private const val MAX_MANUAL_ATTEMPTS = 4
        private const val TAG = "DownloadWorker"

    }
}
