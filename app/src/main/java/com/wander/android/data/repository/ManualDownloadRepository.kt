package com.wander.android.data.repository

import android.util.Log
import com.wander.android.core.cache.DownloadScheduler
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.dao.getTracksByIdsChunked
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Downloads the user asks for by name: one track from its menu, an album or playlist from its
 * header, or a selection.
 *
 * The tracks are written to Room first — a search result or a playlist row may have no row yet,
 * and the worker downloads from rows — then marked in [SecureStorage] and handed to
 * [DownloadScheduler.downloadManual], which follows the Wi-Fi-only setting; the worker follows the
 * storage limit. The mark is what keeps the cleanups away from them; see `manualTrackIds`.
 */
@Singleton
class ManualDownloadRepository @Inject constructor(
    private val trackDao: TrackDao,
    private val secureStorage: SecureStorage,
    private val scheduler: DownloadScheduler
) {
    val marks: Flow<DownloadMarks> = combine(
        trackDao.getDownloadedTrackIdsFlow(),
        secureStorage.downloads.manualTrackIds
    ) { downloaded, manual -> DownloadMarks.of(downloaded.toSet(), manual) }
        .flowOn(Dispatchers.Default)

    /** @return how many of [tracks] were queued; the rest cannot be downloaded or already are. */
    suspend fun download(tracks: List<UnifiedTrack>): Int = withContext(Dispatchers.IO) {
        val wanted = tracks.filter { ManualDownloadRules.canDownload(it) && !it.isDownloaded }.distinctBy { it.id }
        if (wanted.isEmpty()) return@withContext 0
        trackDao.upsertTracks(wanted.map { TrackEntity.fromUnifiedTrack(it) })
        secureStorage.downloads.markManual(wanted.map { it.id })
        scheduler.downloadManual()
        wanted.size
    }

    /**
     * Queues what is still waiting again, under the current Wi-Fi-only rule: after that setting
     * changed, or downloads were resumed. Nothing is queued when nothing is waiting.
     */
    suspend fun requeueWaiting() = withContext(Dispatchers.IO) {
        if (waitingIds().isNotEmpty()) scheduler.requeueManual()
    }

    /**
     * Takes back every download still waiting, for "Cancel": the job is gone, and the rows must
     * offer "Download" again rather than say they are waiting for one that is not coming.
     */
    suspend fun dropWaiting() = withContext(Dispatchers.IO) {
        secureStorage.downloads.unmarkManual(waitingIds())
    }

    /** Marked by hand and not downloaded yet; a mark whose row has gone counts as waiting. */
    private suspend fun waitingIds(): List<String> {
        val marked = secureStorage.downloads.manualTrackIds.value
        if (marked.isEmpty()) return emptyList()
        val downloaded = trackDao.getTracksByIdsChunked(marked.toList())
            .filter { it.isDownloaded }
            .mapTo(HashSet()) { it.id }
        return marked.filterNot { it in downloaded }
    }

    /**
     * Deletes the files of [tracks] and clears their rows, and takes back any still waiting. The
     * tracks stay where they are and stream again. A local file is never touched.
     *
     * @return how many downloads were removed or taken back.
     */
    suspend fun remove(tracks: List<UnifiedTrack>): Int = withContext(Dispatchers.IO) {
        val removed = tracks.filter { it.source != SourceType.LOCAL }.distinctBy { it.id }.filter { track ->
            val entity = trackDao.getTrackById(track.id)
            val path = entity?.localFilePath
            // A file that would not delete keeps its row and its mark, as `PodcastAutoDownloads.trim`
            // does: clearing them would leave an orphan that nothing points at.
            if (!path.isNullOrBlank() && !File(path).delete() && File(path).exists()) {
                Log.w(TAG, "Could not delete a downloaded file")
                return@filter false
            }
            if (entity?.isDownloaded == true) trackDao.setDownloaded(track.id, isDownloaded = false, localPath = null)
            true
        }
        secureStorage.downloads.unmarkManual(removed.map { it.id })
        removed.size
    }

    private companion object {
        const val TAG = "ManualDownloads"
    }
}
