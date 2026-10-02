package com.wander.android.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.wander.android.R
import com.wander.android.core.database.dao.PlaylistDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.notification.WorkEta
import com.wander.android.core.notification.WorkProgressNotification
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.TrackMatcher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Matches the [SourceType.UNRESOLVED] placeholders of an imported playlist to tracks in the user's
 * active sources, one at a time.
 *
 * Room is the checkpoint. Each match is written the moment it is found — the real track is
 * upserted, then the placeholder's id in the playlist is swapped for it — so the playlist screen
 * fills in as this runs, and a run that is killed or cancelled simply leaves the remaining
 * placeholders for the next one. Nothing here is held in memory that a restart would lose.
 *
 * A track no source has is a normal outcome, not a failure: its placeholder stays, and the
 * playlist screen shows it as not found with the option to try again.
 */
@HiltWorker
class PlaylistImportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
    private val trackMatcher: TrackMatcher,
    private val notifications: WorkProgressNotification
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val playlistId = inputData.getString(KEY_PLAYLIST_ID) ?: return@withContext Result.failure()
        // Deleted while queued: nothing left to resolve.
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return@withContext Result.success()

        val ids = playlist.trackIds.split(',').filter { it.isNotBlank() }
        val placeholders = trackDao.getTracksByIds(ids)
            .filter { it.source == SourceType.UNRESOLVED }
            .sortedBy { ids.indexOf(it.id) }
        if (placeholders.isEmpty()) return@withContext Result.success()

        val total = placeholders.size
        val eta = WorkEta(System.currentTimeMillis())
        showProgress(notifying(playlist.name, eta, 0, total))

        placeholders.forEachIndexed { index, placeholder ->
            val match = trackMatcher.match(placeholder.title, placeholder.artist, placeholder.durationMs)
            if (match != null) resolve(playlistId, placeholder, match)

            val done = index + 1
            setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
            showProgress(notifying(playlist.name, eta, done, total))
            // Politeness towards the search backends: a long playlist is hundreds of queries.
            delay(SEARCH_SPACING_MS)
        }

        // Placeholders swapped out above, and any whose playlist was deleted meanwhile.
        trackDao.deleteUnreferencedUnresolved()
        Result.success()
    }

    /**
     * The real track goes into Room before the playlist points at it, so a reader that lands
     * between the two never sees an id with no row behind it.
     */
    private suspend fun resolve(playlistId: String, placeholder: TrackEntity, match: UnifiedTrack) {
        trackDao.upsertTracks(listOf(TrackEntity.fromUnifiedTrack(match, isLibrary = true)))
        playlistDao.replaceTrackId(playlistId, oldId = placeholder.id, newId = match.id)
    }

    private fun notifying(name: String, eta: WorkEta, done: Int, total: Int) =
        notifications.foregroundInfo(
            kind = WorkProgressNotification.Kind.PLAYLIST_IMPORT,
            title = applicationContext.getString(R.string.notif_importing_playlist, name),
            text = listOfNotNull(
                applicationContext.getString(R.string.notif_import_progress, done, total),
                eta.describe(done, total, System.currentTimeMillis())
            ).joinToString(" · "),
            done = done,
            total = total
        )

    /**
     * Matching carries on without the notification when the system will not let this worker run
     * as a foreground service right now (Android 12+ refuses it for some background starts).
     */
    private suspend fun showProgress(info: ForegroundInfo) {
        try {
            setForeground(info)
        } catch (_: IllegalStateException) {
            // Deliberately ignored: only the progress notification is lost, never the matching.
        }
    }

    companion object {
        const val KEY_PLAYLIST_ID = "playlist_id"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"

        private const val SEARCH_SPACING_MS = 250L
    }
}
