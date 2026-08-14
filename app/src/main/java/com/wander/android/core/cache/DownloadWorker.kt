package com.wander.android.core.cache

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wander.android.core.database.dao.TrackDao
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
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(context, params) {

    private val downloadsDir = File(context.filesDir, "downloads")

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
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

        for (entity in pending) {
            if (isStopped) break
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
