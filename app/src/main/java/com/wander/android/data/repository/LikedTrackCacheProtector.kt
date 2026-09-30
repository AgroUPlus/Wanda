package com.wander.android.data.repository

import com.wander.android.core.cache.AudioCacheManager
import com.wander.android.core.database.dao.TrackDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps [AudioCacheManager]'s eviction-proof set in step with what's liked.
 *
 * A liked track played once and not since used to be evicted from the streaming cache exactly as
 * readily as anything else — the "keep what I like available offline-ish" expectation a heart icon
 * implies was never actually kept by the cache underneath it. [start] is called once, from
 * [com.wander.android.WanderApplication], and runs for the process's lifetime — the same shape as
 * that class's other live-flow wiring (the P2P server, following its own setting).
 */
@Singleton
class LikedTrackCacheProtector @Inject constructor(
    private val trackDao: TrackDao,
    private val audioCacheManager: AudioCacheManager
) {
    fun start(scope: CoroutineScope) {
        trackDao.getLikedTracksFlow()
            .map { tracks -> tracks.mapNotNull { it.localFilePath ?: it.streamUri }.toSet() }
            .onEach(audioCacheManager::updateProtectedStreamUris)
            .launchIn(scope)
    }
}
