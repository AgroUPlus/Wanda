package com.wander.android.core.cache

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.notification.WorkEta
import com.wander.android.core.notification.WorkProgressNotification
import com.wander.android.core.work.WorkControls
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
    private val okHttpClient: OkHttpClient,
    private val notifications: WorkProgressNotification,
    private val workControls: WorkControls
) : CoroutineWorker(context, params) {

    private val downloadsDir = File(context.filesDir, "downloads")

    private fun notifying(eta: WorkEta, done: Int, total: Int, title: String? = null) =
        notifications.foregroundInfo(
            kind = WorkProgressNotification.Kind.DOWNLOAD,
            title = "Downloading your music",
            text = listOfNotNull(
                "$done of $total",
                eta.describe(done, total, System.currentTimeMillis()),
                title
            ).joinToString(" · "),
            done = done,
            total = total
        )

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (workControls.isPaused(WorkProgressNotification.Kind.DOWNLOAD).value) {
            return@withContext Result.success()
        }

        // A single explicit request from the context menu, or the periodic liked-tracks sweep.
        val requestedId = inputData.getString(KEY_TRACK_ID)
        val pending = if (requestedId != null) {
            listOfNotNull(trackDao.getTrackById(requestedId)).filterNot { it.isDownloaded }
        } else {
            // Liked tracks, not most-played: the user explicitly asked to keep these.
            trackDao.getLikedNotDownloaded(BATCH_SIZE)
        }
        if (pending.isEmpty()) return@withContext Result.success()

        downloadsDir.mkdirs()
        var failures = 0

        // Downloading a batch of liked tracks is minutes of network, and it was doing it with no
        // way to tell it apart from nothing happening — and subject to the same ten-minute ceiling
        // as every other plain worker.
        val eta = WorkEta(System.currentTimeMillis())
        runCatching { setForeground(notifying(eta, 0, pending.size)) }

        for ((index, entity) in pending.withIndex()) {
            if (isStopped) break
            runCatching { setForeground(notifying(eta, index, pending.size, entity.title)) }
            val streamInfo = musicRepository.getStreamInfo(entity.id).getOrElse {
                failures++
                continue
            }
            val destination = File(downloadsDir, "${entity.id.replace(':', '_')}.${extensionFor(streamInfo.format)}")
            val downloaded = runCatching { download(streamInfo.uri, streamInfo.headers, destination) }
            if (downloaded.isSuccess) {
                trackDao.setDownloaded(entity.id, true, destination.absolutePath)
            } else {
                destination.delete()
                failures++
            }
        }

        // Retry later rather than reporting success we did not achieve.
        if (failures == pending.size) Result.retry() else Result.success()
    }

    private fun download(url: String, headers: Map<String, String>, destination: File) {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download failed with HTTP ${response.code}")
            }
            destination.outputStream().use { out -> response.body.byteStream().copyTo(out) }
        }
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

        private const val BATCH_SIZE = 10
    }
}
