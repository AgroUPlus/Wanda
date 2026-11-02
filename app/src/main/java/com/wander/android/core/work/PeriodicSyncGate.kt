package com.wander.android.core.work

import com.wander.android.core.database.dao.PodcastDao
import com.wander.android.core.database.dao.SharedPlaylistDao
import com.wander.android.core.security.SecureStorage
import com.wander.android.core.sync.LibrarySyncScheduler
import com.wander.android.core.sync.ScrobbleSyncScheduler
import com.wander.android.core.sync.SharedPlaylistSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps each periodic background pass scheduled exactly while it has something to do.
 *
 * Every one of these used to be enqueued at startup whatever the state: an unpaired phone was
 * still woken every two hours for scrobbles and every six for shared playlists and podcasts, only
 * for each worker to find nothing and exit. Following the state instead of the launch also covers
 * the cases a launch-time check missed — pairing, signing out, a first subscription, and a backup
 * restore that switches library sync on without the app being restarted.
 *
 * Each scheduler enqueues with KEEP, so a repeated "on" leaves the running interval alone.
 */
@Singleton
internal class PeriodicSyncGate @Inject constructor(
    private val secureStorage: SecureStorage,
    private val scrobbleSync: ScrobbleSyncScheduler,
    private val sharedPlaylistSync: SharedPlaylistSyncScheduler,
    private val podcastSync: PodcastSyncScheduler,
    private val librarySync: LibrarySyncScheduler,
    private val sharedPlaylistDao: SharedPlaylistDao,
    private val podcastDao: PodcastDao
) {

    fun start(scope: CoroutineScope) {
        val paired = secureStorage.agroConfigured

        // The worker uploads this phone's listening history to Agro; unpaired, there is nowhere to send it.
        follow(scope, paired, on = scrobbleSync::schedule, off = scrobbleSync::cancel)

        val hasSharedCopies = sharedPlaylistDao.observeAll().map { it.isNotEmpty() }
        follow(
            scope,
            combine(paired, hasSharedCopies) { isPaired, any -> isPaired && any },
            on = sharedPlaylistSync::schedule,
            off = sharedPlaylistSync::cancel
        )

        follow(
            scope,
            podcastDao.observeAll().map { it.isNotEmpty() },
            on = podcastSync::enable,
            off = podcastSync::disable
        )

        val librarySyncWanted = combine(
            secureStorage.agroCatalogTradeFlow,
            secureStorage.agroP2pSyncFlow,
            secureStorage.agroServerArchiveFlow
        ) { catalogTrade, p2pSync, serverArchive -> catalogTrade || p2pSync || serverArchive }
        follow(
            scope,
            combine(paired, librarySyncWanted) { isPaired, wanted -> isPaired && wanted },
            on = librarySync::enablePeriodicSync,
            off = librarySync::disablePeriodicSync
        )
    }

    private fun follow(scope: CoroutineScope, needed: Flow<Boolean>, on: () -> Unit, off: () -> Unit) {
        scope.launch {
            needed.distinctUntilChanged().collect { if (it) on() else off() }
        }
    }
}
