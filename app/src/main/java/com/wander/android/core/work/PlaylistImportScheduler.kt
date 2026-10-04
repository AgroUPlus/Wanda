package com.wander.android.core.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.wander.android.core.notification.WorkProgressNotification
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What the playlist screen needs to know about a playlist's background matching. */
data class ImportWorkState(
    /** Queued or running: anything short of finished. */
    val isRunning: Boolean = false,
    /** Actually running. Queued and not yet started is Android's to decide, not this app's. */
    val isStarted: Boolean = false,
    val done: Int = 0,
    val total: Int = 0
)

@Singleton
class PlaylistImportScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    /**
     * Starts matching the placeholders of [playlistId], or does nothing if a run is already
     * queued or going. A finished run does not block a new one, so the same call is also "try
     * the tracks that were not found again".
     *
     * The user asked for this, so the only requirement is a connection.
     */
    fun enqueue(playlistId: String) {
        val request = OneTimeWorkRequestBuilder<PlaylistImportWorker>()
            .setInputData(workDataOf(PlaylistImportWorker.KEY_PLAYLIST_ID to playlistId))
            .addTag(WorkControls.tagFor(WorkProgressNotification.Kind.PLAYLIST_IMPORT))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(playlistId),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    /** Whether matching is queued or running for [playlistId], with its progress. */
    fun observe(playlistId: String): Flow<ImportWorkState> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(workName(playlistId))
            .map { infos ->
                val active = infos.firstOrNull { !it.state.isFinished }
                    ?: return@map ImportWorkState()
                ImportWorkState(
                    isRunning = true,
                    isStarted = active.state == WorkInfo.State.RUNNING,
                    done = active.progress.getInt(PlaylistImportWorker.KEY_DONE, 0),
                    total = active.progress.getInt(PlaylistImportWorker.KEY_TOTAL, 0)
                )
            }

    private fun workName(playlistId: String) = "wanda_playlist_import:$playlistId"
}
