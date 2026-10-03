package com.wander.android.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wander.android.data.repository.sharedplaylist.SharedPlaylistRunner
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Brings every shared playlist copy up to date and sends any edits still waiting. See
 * [SharedPlaylistRunner.syncAll]: one request says which copies changed, and only those are read.
 */
@HiltWorker
class SharedPlaylistSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runner: SharedPlaylistRunner
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = if (runner.syncAll()) Result.success() else Result.retry()
}
