package com.wander.android.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.wander.android.R
import com.wander.android.core.database.dao.PlaylistDao
import com.wander.android.core.database.dao.SharedPlaylistDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.notification.WorkEta
import com.wander.android.core.notification.WorkProgressNotification
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.TrackMatcher
import com.wander.android.data.repository.sharedplaylist.SharedPlaylistMirror
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Matches the [SourceType.UNRESOLVED] placeholders of an imported playlist — or of a shared one, whose
 * tracks arrive from Agro as titles and artists — to tracks in the user's active sources, one at a
 * time.
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
    private val sharedPlaylistDao: SharedPlaylistDao,
    private val trackDao: TrackDao,
    private val trackMatcher: TrackMatcher,
    private val notifications: WorkProgressNotification
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val playlistId = inputData.getString(KEY_PLAYLIST_ID) ?: return@withContext Result.failure()
        // Deleted or unfollowed while queued: nothing left to resolve.
        val (name, ids) = contents(playlistId) ?: return@withContext Result.success()

        val eta = WorkEta(System.currentTimeMillis())
        val tried = HashSet<String>()
        var done = 0
        var total = 0
        var batch = placeholdersOf(ids, tried)
        // A shared playlist can be rewritten while this runs — a blend is, whole — and its new
        // placeholders arrive while this run still holds the unique name that would have started
        // another. So the playlist is read again at the end, until nothing new is left to try.
        while (batch.isNotEmpty()) {
            total += batch.size
            // Reported before anything else, so the screen can tell a run that has started from one
            // still waiting to: "0 of 50" rather than an indefinite wait.
            setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
            showProgress(notifying(name, eta, done, total))
            batch.forEach { placeholder ->
                tried += placeholder.id
                // Bounded per track: one source that never answers must cost this track, not the run.
                val match = withTimeoutOrNull(MATCH_TIMEOUT_MS) {
                    trackMatcher.match(placeholder.title, placeholder.artist, placeholder.durationMs)
                }
                if (match != null) resolve(playlistId, placeholder, match)

                done++
                setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
                showProgress(notifying(name, eta, done, total))
                // Politeness towards the search backends: a long playlist is hundreds of queries.
                delay(SEARCH_SPACING_MS)
            }
            batch = contents(playlistId)?.second?.let { placeholdersOf(it, tried) }.orEmpty()
        }

        // Placeholders swapped out above, and any whose playlist was deleted meanwhile.
        trackDao.deleteUnreferencedUnresolved()
        Result.success()
    }

    /** The placeholders among [ids] not already tried by this run, in playlist order. */
    private suspend fun placeholdersOf(ids: List<String>, tried: Set<String>): List<TrackEntity> =
        trackDao.getTracksByIds(ids)
            .filter { it.source == SourceType.UNRESOLVED && it.id !in tried }
            .sortedBy { ids.indexOf(it.id) }

    /** The playlist's name and track ids in order: a Wanda playlist, or a followed one's copy. */
    private suspend fun contents(playlistId: String): Pair<String, List<String>>? {
        SharedPlaylistMirror.agroIdOf(playlistId)?.let { agroId ->
            val copy = sharedPlaylistDao.get(agroId) ?: return null
            return copy.title to sharedPlaylistDao.items(agroId).map { it.trackId }
        }
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return null
        return playlist.name to playlist.trackIds.split(',').filter { it.isNotBlank() }
    }

    /**
     * The real track goes into Room before the playlist points at it, so a reader that lands
     * between the two never sees an id with no row behind it. A shared copy holding the same
     * placeholder is swapped too, so a published playlist and its copy never disagree.
     */
    private suspend fun resolve(playlistId: String, placeholder: TrackEntity, match: UnifiedTrack) {
        trackDao.upsertTracks(listOf(TrackEntity.fromUnifiedTrack(match, isLibrary = true)))
        if (SharedPlaylistMirror.agroIdOf(playlistId) == null) {
            playlistDao.replaceTrackId(playlistId, oldId = placeholder.id, newId = match.id)
        }
        sharedPlaylistDao.replaceTrackId(oldId = placeholder.id, newId = match.id)
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

        /** Up to three searches, each against every source, with room for a slow one. */
        private const val MATCH_TIMEOUT_MS = 60_000L
    }
}
