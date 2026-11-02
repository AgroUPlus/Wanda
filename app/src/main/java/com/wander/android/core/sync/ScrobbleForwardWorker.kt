package com.wander.android.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wander.android.data.repository.ScrobbleForwarding
import com.wander.android.data.repository.SourceScrobbleOutbox
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Drains the scrobble outboxes: plays a source such as Navidrome missed, then the ListenBrainz and
 * Last.fm ones. Does nothing, successfully, when nothing is owed.
 */
@HiltWorker
internal class ScrobbleForwardWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val forwarding: ScrobbleForwarding,
    private val sourceOutbox: SourceScrobbleOutbox
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sourcesOwed = sourceOutbox.drain()
        val servicesOwed = forwarding.isAnyOn && forwarding.forward()
        return if (sourcesOwed || servicesOwed) Result.retry() else Result.success()
    }
}
