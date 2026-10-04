package com.wander.android.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wander.android.data.repository.ScrobbleForwarding
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Drains the ListenBrainz and Last.fm outboxes. Does nothing, successfully, when neither is on. */
@HiltWorker
internal class ScrobbleForwardWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val forwarding: ScrobbleForwarding
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        if (forwarding.isAnyOn && forwarding.forward()) Result.retry() else Result.success()
}
