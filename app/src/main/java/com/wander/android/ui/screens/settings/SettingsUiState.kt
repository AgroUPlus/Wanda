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
    val incognito: Boolean,
    val cacheBytes: Long,
    val agroPaired: Boolean,
    val agroPairing: AgroPairingState,
    val agroConnection: AgroConnectionState,
    val agroVisibility: com.wander.android.data.sources.agro.AgroVisibility?,
    val agroSyncSettings: Boolean,
    val librarySync: Boolean,
    val syncProgress: SyncProgress,
    val pendingUploads: Int,
    val syncedTracks: Int,
    val localTracks: Int,
    val localScanFolder: String?,
    val storageUsage: com.wander.android.data.sources.agro.StorageUsage?,
    val shareDomain: String,
    val agroShareDomain: String,
    val appVersion: String,
    val updateCheck: UpdateCheckResult?,
    val isCheckingForUpdate: Boolean,
    val autoUpdateCheckEnabled: Boolean
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
    val incognito by viewModel.isIncognito.collectAsStateWithLifecycle()
    val cacheBytes by viewModel.cacheBytes.collectAsStateWithLifecycle()
    val agro by viewModel.agroConnected.collectAsStateWithLifecycle()
    val agroPairing by viewModel.agroPairing.collectAsStateWithLifecycle()
    val agroConnection by viewModel.agroConnection.collectAsStateWithLifecycle()
    val agroVisibility by viewModel.agroVisibility.collectAsStateWithLifecycle()
    val agroSyncSettings by viewModel.agroSyncSettings.collectAsStateWithLifecycle()
    val librarySync by viewModel.librarySyncEnabled.collectAsStateWithLifecycle()
    val syncProgress by viewModel.librarySyncProgress.collectAsStateWithLifecycle()
    val pendingUploads by viewModel.pendingUploads.collectAsStateWithLifecycle()
    val syncedTracks by viewModel.syncedTracks.collectAsStateWithLifecycle()
    val localTracks by viewModel.localTracks.collectAsStateWithLifecycle()
    val localScanFolder by viewModel.localScanFolder.collectAsStateWithLifecycle()
    val storageUsage by viewModel.storageUsage.collectAsStateWithLifecycle()
    val shareDomain by viewModel.shareDomain.collectAsStateWithLifecycle()
    val agroShareDomain by viewModel.agroShareDomain.collectAsStateWithLifecycle()
    val updateCheck by viewModel.updateCheck.collectAsStateWithLifecycle()
    val isCheckingForUpdate by viewModel.isCheckingForUpdate.collectAsStateWithLifecycle()
    val autoUpdateCheckEnabled by viewModel.isAutoUpdateCheckEnabled.collectAsStateWithLifecycle()

    return SettingsUiState(
        navidrome = navidrome,
        youTube = youTube,
        youTubeAccount = youTubeAccount,
        localReady = localReady,
        syncedNavidrome = syncedNavidrome,
        monet = monet,
        amoled = amoled,
        offline = offline,
        incognito = incognito,
        cacheBytes = cacheBytes,
        agroPaired = agro,
        agroPairing = agroPairing,
        agroConnection = agroConnection,
        agroVisibility = agroVisibility,
        agroSyncSettings = agroSyncSettings,
        librarySync = librarySync,
        syncProgress = syncProgress,
        pendingUploads = pendingUploads,
        syncedTracks = syncedTracks,
        localTracks = localTracks,
        localScanFolder = localScanFolder,
        storageUsage = storageUsage,
        shareDomain = shareDomain,
        agroShareDomain = agroShareDomain,
        appVersion = viewModel.appVersion,
        updateCheck = updateCheck,
        isCheckingForUpdate = isCheckingForUpdate,
        autoUpdateCheckEnabled = autoUpdateCheckEnabled
    )
}
