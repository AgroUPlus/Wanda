package com.wander.android.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.core.update.UpdateCheckResult
import com.wander.android.data.repository.SyncProgress
import com.wander.android.data.sources.agro.AgroSyncedSettings

/**
 * One snapshot of everything the settings tabs read.
 *
 * Collected in a single place because six tabs sharing one ViewModel would otherwise each re-derive
 * their own slice, and because a tab is a `LazyListScope` extension — it cannot collect anything
 * itself.
 */
@Immutable
internal data class SettingsUiState(
    val navidrome: Boolean,
    val youTube: Boolean,
    val youTubeAccount: String,
    val localReady: Boolean,
    val syncedNavidrome: AgroSyncedSettings?,
    val monet: Boolean,
    val amoled: Boolean,
    val offline: Boolean,
    val preloadNext: Boolean,
    val indexOnMobileData: Boolean,
    val measuringPaused: Boolean,
    val downloadingPaused: Boolean,
    val incognito: Boolean,
    val cacheBytes: Long,
    val agroPaired: Boolean,
    val agroPairing: AgroPairingState,
    val agroConnection: AgroConnectionState,
    val agroVisibility: com.wander.android.data.sources.agro.AgroVisibility?,
    val agroSyncSettings: Boolean,
    val agroProxyEnabled: Boolean,
    val librarySync: Boolean,
    val p2pSync: Boolean,
    val serverArchive: Boolean,
    val popularityContribution: Boolean,
    val canArchive: Boolean,
    val syncProgress: SyncProgress,
    val pendingUploads: Int,
    val syncedTracks: Int,
    val localTracks: Int,
    val localScanFolder: String?,
    val serverTotalTracks: Int,
    val shareDomain: String,
    val agroShareDomain: String,
    val appVersion: String,
    val navidromeServer: String,
    val agroDevicePetname: String,
    val agroServer: String,
    /** Whether this device can delete its own audio files at all; see `LocalFileDeleter`. */
    val canDeleteLocalFiles: Boolean,
    val updateCheck: UpdateCheckResult?,
    val isCheckingForUpdate: Boolean,
    val autoUpdateCheckEnabled: Boolean,
    val releaseNotificationsEnabled: Boolean
) {
    /**
     * Whether the "free up space" row has anything to offer.
     *
     * Both halves matter: a device that cannot delete its own files must not be told it can, and
     * with nothing confirmed on the server there is nothing safe to remove.
     */
    val canDelete: Boolean get() = canDeleteLocalFiles && syncedTracks > 0
}

@Composable
internal fun rememberSettingsUiState(viewModel: SettingsViewModel): SettingsUiState {
    val navidrome by viewModel.navidromeConnected.collectAsStateWithLifecycle()
    val youTube by viewModel.youTubeConnected.collectAsStateWithLifecycle()
    val youTubeAccount by viewModel.youTubeAccount.collectAsStateWithLifecycle()
    val localReady by viewModel.localAvailable.collectAsStateWithLifecycle()
    val syncedNavidrome by viewModel.syncedNavidrome.collectAsStateWithLifecycle()
    val monet by viewModel.isMonetDynamic.collectAsStateWithLifecycle()
    val amoled by viewModel.isAmoledBlack.collectAsStateWithLifecycle()
    val offline by viewModel.isOfflineMode.collectAsStateWithLifecycle()
    val preloadNext by viewModel.isPreloadNextEnabled.collectAsStateWithLifecycle()
    val indexOnMobileData by viewModel.isIndexOnMobileDataEnabled.collectAsStateWithLifecycle()
    val measuringPaused by viewModel.isMeasuringPaused.collectAsStateWithLifecycle()
    val downloadingPaused by viewModel.isDownloadingPaused.collectAsStateWithLifecycle()
    val incognito by viewModel.isIncognito.collectAsStateWithLifecycle()
    val cacheBytes by viewModel.cacheBytes.collectAsStateWithLifecycle()
    val agro by viewModel.agroConnected.collectAsStateWithLifecycle()
    val agroPairing by viewModel.agroPairing.collectAsStateWithLifecycle()
    val agroConnection by viewModel.agroConnection.collectAsStateWithLifecycle()
    val agroVisibility by viewModel.agroVisibility.collectAsStateWithLifecycle()
    val agroSyncSettings by viewModel.agroSyncSettings.collectAsStateWithLifecycle()
    val agroProxyEnabled by viewModel.agroProxyEnabled.collectAsStateWithLifecycle()
    val librarySync by viewModel.librarySyncEnabled.collectAsStateWithLifecycle()
    val p2pSync by viewModel.p2pSyncEnabled.collectAsStateWithLifecycle()
    val serverArchive by viewModel.serverArchiveEnabled.collectAsStateWithLifecycle()
    val popularityContribution by viewModel.popularityEnabled.collectAsStateWithLifecycle()
    val canArchive by viewModel.canArchive.collectAsStateWithLifecycle()
    val syncProgress by viewModel.librarySyncProgress.collectAsStateWithLifecycle()
    val pendingUploads by viewModel.pendingUploads.collectAsStateWithLifecycle()
    val syncedTracks by viewModel.syncedTracks.collectAsStateWithLifecycle()
    val localTracks by viewModel.localTracks.collectAsStateWithLifecycle()
    val localScanFolder by viewModel.localScanFolder.collectAsStateWithLifecycle()
    val serverTotalTracks by viewModel.serverTotalTracks.collectAsStateWithLifecycle()
    val shareDomain by viewModel.shareDomain.collectAsStateWithLifecycle()
    val agroShareDomain by viewModel.agroShareDomain.collectAsStateWithLifecycle()
    val updateCheck by viewModel.updateCheck.collectAsStateWithLifecycle()
    val isCheckingForUpdate by viewModel.isCheckingForUpdate.collectAsStateWithLifecycle()
    val autoUpdateCheckEnabled by viewModel.isAutoUpdateCheckEnabled.collectAsStateWithLifecycle()
    val releaseNotificationsEnabled by
        viewModel.isReleaseNotificationEnabled.collectAsStateWithLifecycle()

    return SettingsUiState(
        navidrome = navidrome,
        youTube = youTube,
        youTubeAccount = youTubeAccount,
        localReady = localReady,
        syncedNavidrome = syncedNavidrome,
        monet = monet,
        amoled = amoled,
        offline = offline,
        preloadNext = preloadNext,
        indexOnMobileData = indexOnMobileData,
        measuringPaused = measuringPaused,
        downloadingPaused = downloadingPaused,
        incognito = incognito,
        cacheBytes = cacheBytes,
        agroPaired = agro,
        agroPairing = agroPairing,
        agroConnection = agroConnection,
        agroVisibility = agroVisibility,
        agroSyncSettings = agroSyncSettings,
        agroProxyEnabled = agroProxyEnabled,
        librarySync = librarySync,
        p2pSync = p2pSync,
        serverArchive = serverArchive,
        popularityContribution = popularityContribution,
        canArchive = canArchive,
        syncProgress = syncProgress,
        pendingUploads = pendingUploads,
        syncedTracks = syncedTracks,
        localTracks = localTracks,
        localScanFolder = localScanFolder,
        serverTotalTracks = serverTotalTracks,
        shareDomain = shareDomain,
        agroShareDomain = agroShareDomain,
        appVersion = viewModel.appVersion,
        navidromeServer = viewModel.navidromeServer,
        agroDevicePetname = viewModel.agroDevicePetname,
        agroServer = viewModel.agroServer,
        canDeleteLocalFiles = viewModel.canDeleteLocalFiles,
        updateCheck = updateCheck,
        isCheckingForUpdate = isCheckingForUpdate,
        autoUpdateCheckEnabled = autoUpdateCheckEnabled,
        releaseNotificationsEnabled = releaseNotificationsEnabled
    )
}
