package com.wander.android.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wander.android.data.repository.LikeOutbox
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Sends the likes and unlikes their backends have not confirmed yet; see [LikeOutbox]. */
@HiltWorker
internal class LikeSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val outbox: LikeOutbox
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = if (outbox.drain()) Result.retry() else Result.success()
}
