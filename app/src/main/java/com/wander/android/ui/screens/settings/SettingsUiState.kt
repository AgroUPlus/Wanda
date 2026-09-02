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
    val updateCheck: UpdateCheckResult?,
    val isCheckingForUpdate: Boolean,
    val autoUpdateCheckEnabled: Boolean,
    val releaseNotificationsEnabled: Boolean
)

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
        updateCheck = updateCheck,
        isCheckingForUpdate = isCheckingForUpdate,
        autoUpdateCheckEnabled = autoUpdateCheckEnabled,
        releaseNotificationsEnabled = releaseNotificationsEnabled
    )
}
