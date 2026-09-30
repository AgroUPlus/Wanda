package com.wander.android.ui.screens.settings

import android.content.IntentSender
import com.wander.android.core.security.SecureStorage
import com.wander.android.core.sync.LibrarySyncScheduler
import com.wander.android.core.sync.LocalFileDeleter
import com.wander.android.data.repository.CatalogSyncRepository
import com.wander.android.data.repository.LibrarySyncRepository
import com.wander.android.data.repository.SyncProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Coordinates library sync toggles, upload/sync progress, local file deletion, and catalog trade.
 */
internal class SettingsLibrarySyncCoordinator @Inject constructor(
    private val secureStorage: SecureStorage,
    private val librarySync: LibrarySyncRepository,
    private val librarySyncScheduler: LibrarySyncScheduler,
    private val localFileDeleter: LocalFileDeleter,
    private val catalogSync: CatalogSyncRepository
) {
    val librarySyncEnabled: StateFlow<Boolean> = secureStorage.agroLibrarySyncFlow
    val p2pSyncEnabled: StateFlow<Boolean> = secureStorage.agroP2pSyncFlow
    val serverArchiveEnabled: StateFlow<Boolean> = secureStorage.agroServerArchiveFlow
    val popularityEnabled: StateFlow<Boolean> = secureStorage.agroPopularityContributionFlow
    val catalogTradeEnabled: StateFlow<Boolean> = secureStorage.agroCatalogTradeFlow

    fun fingerprintsShared(scope: CoroutineScope): StateFlow<Int> =
        catalogSync.fingerprintsShared
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    fun lyricsReceived(scope: CoroutineScope): StateFlow<Int> =
        catalogSync.lyricsReceived
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    val librarySyncProgress: StateFlow<SyncProgress> = librarySync.progress

    fun pendingUploads(scope: CoroutineScope): StateFlow<Int> =
        librarySync.pendingUploadCount
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    fun syncedTracks(scope: CoroutineScope): StateFlow<Int> =
        librarySync.syncedCount
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    fun localTracks(scope: CoroutineScope): StateFlow<Int> =
        librarySync.localTrackCount
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _serverTotalTracks = MutableStateFlow(0)
    val serverTotalTracks: StateFlow<Int> = _serverTotalTracks.asStateFlow()

    fun refreshServerTotalTracks(scope: CoroutineScope) {
        if (!secureStorage.agroLibrarySync) return
        scope.launch {
            _serverTotalTracks.value = librarySync.stats().getOrNull()?.trackCount ?: 0
        }
    }

    private val _localScanFolder = MutableStateFlow(secureStorage.localScanFolderLabel)
    val localScanFolder: StateFlow<String?> = _localScanFolder.asStateFlow()

    fun setLocalScanFolder(path: String, label: String, onRescan: () -> Unit) {
        secureStorage.localScanFolder = path
        secureStorage.localScanFolderLabel = label
        secureStorage.localScanWatermark = 0L
        _localScanFolder.value = label
        onRescan()
    }

    val canDeleteLocalFiles: Boolean get() = localFileDeleter.isSupported

    fun setP2pSync(enabled: Boolean) {
        secureStorage.setAgroP2pSync(enabled)
        updatePeriodicSync(enabled || secureStorage.agroServerArchive || secureStorage.agroCatalogTrade)
    }

    fun setCatalogTrade(enabled: Boolean) {
        secureStorage.setAgroCatalogTrade(enabled)
        updatePeriodicSync(enabled || secureStorage.agroP2pSync || secureStorage.agroServerArchive)
    }

    fun setServerArchive(enabled: Boolean) {
        secureStorage.setAgroServerArchive(enabled)
        updatePeriodicSync(enabled || secureStorage.agroP2pSync || secureStorage.agroCatalogTrade)
    }

    fun setLibrarySync(enabled: Boolean) {
        secureStorage.setAgroLibrarySync(enabled)
        updatePeriodicSync(enabled)
    }

    private fun updatePeriodicSync(enabled: Boolean) {
        if (enabled) {
            librarySyncScheduler.enablePeriodicSync()
            librarySyncScheduler.syncNow()
        } else {
            librarySyncScheduler.disablePeriodicSync()
        }
    }

    fun syncLibraryNow() = librarySyncScheduler.syncNow()

    fun buildDeleteRequest(scope: CoroutineScope, onReady: (IntentSender?) -> Unit) {
        scope.launch {
            val uris = librarySync.deletableLocalTracks().mapNotNull { it.streamUri }
            onReady(localFileDeleter.buildTrashRequest(uris))
        }
    }
}
