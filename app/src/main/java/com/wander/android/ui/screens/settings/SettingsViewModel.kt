package com.wander.android.ui.screens.settings

import android.content.Context
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.audio.fingerprint.FingerprintIndexing
import com.wander.android.core.audio.fingerprint.FingerprintProgress
import com.wander.android.core.cache.DownloadScheduler
import com.wander.android.core.i18n.AppLocaleStore
import com.wander.android.core.notification.WorkProgressNotification
import com.wander.android.core.security.SecureStorage
import com.wander.android.core.update.UpdateCheckResult
import com.wander.android.core.update.UpdateChecker
import com.wander.android.core.work.ArtistReleaseScheduler
import com.wander.android.core.work.WorkControls
import com.wander.android.data.repository.IncognitoRepository
import com.wander.android.data.repository.SyncProgress
import com.wander.android.data.sources.agro.AgroSyncedSettings
import com.wander.android.data.sources.agro.AgroVisibility
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val appLocaleStore: AppLocaleStore,
    private val downloadScheduler: DownloadScheduler,
    private val updateChecker: UpdateChecker,
    private val incognitoRepository: IncognitoRepository,
    private val artistReleaseScheduler: ArtistReleaseScheduler,
    private val workControls: WorkControls,
    private val fingerprintProgress: FingerprintProgress,
    private val agroCoordinator: SettingsAgroCoordinator,
    private val librarySyncCoordinator: SettingsLibrarySyncCoordinator,
    private val accountCoordinator: SettingsAccountCoordinator,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val appVersion: String get() = com.wander.android.BuildConfig.VERSION_NAME

    private val _updateCheck = MutableStateFlow<UpdateCheckResult?>(null)
    val updateCheck: StateFlow<UpdateCheckResult?> = _updateCheck.asStateFlow()

    private val _isCheckingForUpdate = MutableStateFlow(false)
    val isCheckingForUpdate: StateFlow<Boolean> = _isCheckingForUpdate.asStateFlow()

    fun checkForUpdate() {
        if (_isCheckingForUpdate.value) return
        viewModelScope.launch {
            _isCheckingForUpdate.value = true
            _updateCheck.value = updateChecker.checkForUpdate()
            _isCheckingForUpdate.value = false
        }
    }

    val isAutoUpdateCheckEnabled: StateFlow<Boolean> = secureStorage.isAutoUpdateCheckEnabled

    val isArtistReleaseNotificationEnabled: StateFlow<Boolean> =
        secureStorage.isArtistReleaseNotificationEnabled

    fun setArtistReleaseNotificationEnabled(enabled: Boolean) {
        secureStorage.setArtistReleaseNotificationEnabled(enabled)
        if (enabled) artistReleaseScheduler.enable() else artistReleaseScheduler.disable()
    }

    fun setAutoUpdateCheckEnabled(enabled: Boolean) = secureStorage.setAutoUpdateCheckEnabled(enabled)

    // ── Accounts (delegated to SettingsAccountCoordinator) ─────────────────────────────────

    val navidromeConnected: StateFlow<Boolean> = accountCoordinator.navidromeConnected
    val youTubeConnected: StateFlow<Boolean> = accountCoordinator.youTubeConnected
    val youTubeAccount: StateFlow<String> = accountCoordinator.youTubeAccount
    val localAvailable: StateFlow<Boolean> = accountCoordinator.localAvailable

    fun refreshYouTubeAccount() = accountCoordinator.refreshYouTubeAccount(viewModelScope)
    fun disconnectNavidrome() = accountCoordinator.disconnectNavidrome()
    fun disconnectYouTube() = accountCoordinator.disconnectYouTube()
    fun rescanLocalLibrary() = accountCoordinator.rescanLocalLibrary(viewModelScope)
    fun forgetEverything() = accountCoordinator.forgetEverything(viewModelScope) { resetAgroPairing() }

    // ── Display & Playback Preferences ─────────────────────────────────────────────────────

    val isMonetDynamic: StateFlow<Boolean> = secureStorage.isMonetDynamic
    val isAmoledBlack: StateFlow<Boolean> = secureStorage.isAmoledBlack
    val isBackBlurEnabled: StateFlow<Boolean> = secureStorage.isBackBlurEnabled
    val isImmersivePlayer: StateFlow<Boolean> = secureStorage.isImmersivePlayer
    val isCoverArtThemeEnabled: StateFlow<Boolean> = secureStorage.isCoverArtThemeEnabled
    val isReduceMotion: StateFlow<Boolean> = secureStorage.isReduceMotion
    val isLetterByLetterLyricsEnabled: StateFlow<Boolean> = secureStorage.isLetterByLetterLyricsEnabled
    val isOfflineMode: StateFlow<Boolean> = secureStorage.isOfflineMode
    val isPreloadNextEnabled: StateFlow<Boolean> = secureStorage.isPreloadNextEnabled
    val isSkipSilenceEnabled: StateFlow<Boolean> = secureStorage.isSkipSilenceEnabled
    val isIndexOnMobileDataEnabled: StateFlow<Boolean> = secureStorage.isIndexOnMobileDataEnabled

    fun setMonetDynamic(enabled: Boolean) = secureStorage.setMonetDynamic(enabled)
    fun setAmoledBlack(enabled: Boolean) = secureStorage.setAmoledBlack(enabled)
    fun setBackBlurEnabled(enabled: Boolean) = secureStorage.setBackBlurEnabled(enabled)
    fun setImmersivePlayer(enabled: Boolean) = secureStorage.setImmersivePlayer(enabled)
    fun setCoverArtThemeEnabled(enabled: Boolean) = secureStorage.setCoverArtThemeEnabled(enabled)
    fun setReduceMotion(enabled: Boolean) = secureStorage.setReduceMotion(enabled)
    fun setLetterByLetterLyricsEnabled(enabled: Boolean) =
        secureStorage.setLetterByLetterLyricsEnabled(enabled)
    fun setOfflineMode(enabled: Boolean) = secureStorage.setOfflineMode(enabled)
    fun setPreloadNextEnabled(enabled: Boolean) = secureStorage.setPreloadNextEnabled(enabled)
    fun setSkipSilenceEnabled(enabled: Boolean) = secureStorage.setSkipSilenceEnabled(enabled)

    fun setIndexOnMobileDataEnabled(enabled: Boolean) {
        secureStorage.setIndexOnMobileDataEnabled(enabled)
        FingerprintIndexing.schedulePeriodic(context, allowMobileData = enabled)
        if (enabled) indexFingerprintsNow()
    }

    // ── Work Controls & Background Tasks ───────────────────────────────────────────────────

    val isMeasuringPaused: StateFlow<Boolean> =
        workControls.isPaused(WorkProgressNotification.Kind.FINGERPRINT)
    val isDownloadingPaused: StateFlow<Boolean> =
        workControls.isPaused(WorkProgressNotification.Kind.DOWNLOAD)

    fun setMeasuringPaused(paused: Boolean) = setPaused(WorkProgressNotification.Kind.FINGERPRINT, paused)
    fun setDownloadingPaused(paused: Boolean) = setPaused(WorkProgressNotification.Kind.DOWNLOAD, paused)

    private fun setPaused(kind: WorkProgressNotification.Kind, paused: Boolean) {
        if (paused) {
            workControls.pause(kind)
        } else {
            if (kind == WorkProgressNotification.Kind.FINGERPRINT) fingerprintProgress.retryFailures()
            workControls.resume(kind)
        }
    }

    fun downloadLikedNow() = downloadScheduler.downloadNow()
    fun indexFingerprintsNow() {
        fingerprintProgress.retryFailures()
        FingerprintIndexing.enqueueNow(context)
    }

    // ── Agro Profile & Pairing (delegated to SettingsAgroCoordinator) ──────────────────────

    val agroConnected: StateFlow<Boolean> = secureStorage.agroConfigured
    val shareDomain: StateFlow<String> = secureStorage.shareDomain
    val agroShareDomain: StateFlow<String> = secureStorage.agroShareDomain
    fun setShareDomain(domain: String) = secureStorage.setShareDomain(domain)

    val isIncognito: StateFlow<Boolean> = incognitoRepository.isIncognito
    fun setIncognito(enabled: Boolean) { viewModelScope.launch { incognitoRepository.set(enabled) } }

    val navidromeServer: String get() = secureStorage.navidromeServerUrl
    val agroDevicePetname: String get() = secureStorage.agroDevicePetname.ifEmpty { "Wanda Android" }
    val agroDeviceId: String get() = secureStorage.agroDeviceId
    val agroServer: String get() = secureStorage.agroServerUrl

    internal val agroPairing: StateFlow<AgroPairingState> = agroCoordinator.agroPairing
    internal val agroConnection: StateFlow<AgroConnectionState> = agroCoordinator.agroConnection
    val agroDefaultServer: String get() = agroCoordinator.agroDefaultServer

    fun pairAgro(server: String, username: String, passphrase: String) =
        agroCoordinator.pairAgro(server, username, passphrase, viewModelScope)
    fun signUpAgro(server: String, username: String, inviteCode: String) =
        agroCoordinator.signUpAgro(server, username, inviteCode, viewModelScope)
    fun resetAgroPairing() = agroCoordinator.resetAgroPairing()

    internal val agroVisibility: StateFlow<AgroVisibility?> = agroCoordinator.agroVisibility
    fun refreshAgroVisibility() = agroCoordinator.refreshAgroVisibility(viewModelScope)
    fun setAgroVisibility(visibility: AgroVisibility) =
        agroCoordinator.setAgroVisibility(visibility, viewModelScope)
    fun refreshAgroConnection() = agroCoordinator.refreshAgroConnection(viewModelScope)

    val agroSyncSettings: StateFlow<Boolean> = secureStorage.agroSyncSettings
    val agroProxyEnabled: StateFlow<Boolean> = secureStorage.agroProxyEnabled
    val externalLyricsEnabled: StateFlow<Boolean> = secureStorage.isExternalLyricsEnabled

    val syncedNavidrome: StateFlow<AgroSyncedSettings?> = agroCoordinator.syncedNavidrome
    fun refreshSyncedSettings() = agroCoordinator.refreshSyncedSettings(viewModelScope)
    fun setAgroSyncSettings(enabled: Boolean) = agroCoordinator.setAgroSyncSettings(enabled, viewModelScope)
    fun disconnectAgro() = agroCoordinator.disconnectAgro(viewModelScope)
    val canArchive: StateFlow<Boolean> = agroCoordinator.canArchive

    fun setAgroProxyEnabled(enabled: Boolean) = secureStorage.setAgroProxyEnabled(enabled)
    fun setExternalLyricsEnabled(enabled: Boolean) = secureStorage.setExternalLyricsEnabled(enabled)

    // ── Library Sync (delegated to SettingsLibrarySyncCoordinator) ─────────────────────────

    val librarySyncEnabled: StateFlow<Boolean> = librarySyncCoordinator.librarySyncEnabled
    val p2pSyncEnabled: StateFlow<Boolean> = librarySyncCoordinator.p2pSyncEnabled
    val serverArchiveEnabled: StateFlow<Boolean> = librarySyncCoordinator.serverArchiveEnabled
    val popularityEnabled: StateFlow<Boolean> = librarySyncCoordinator.popularityEnabled
    val catalogTradeEnabled: StateFlow<Boolean> = librarySyncCoordinator.catalogTradeEnabled

    val fingerprintsShared: StateFlow<Int> = librarySyncCoordinator.fingerprintsShared(viewModelScope)
    val lyricsReceived: StateFlow<Int> = librarySyncCoordinator.lyricsReceived(viewModelScope)
    val librarySyncProgress: StateFlow<SyncProgress> = librarySyncCoordinator.librarySyncProgress
    val pendingUploads: StateFlow<Int> = librarySyncCoordinator.pendingUploads(viewModelScope)
    val syncedTracks: StateFlow<Int> = librarySyncCoordinator.syncedTracks(viewModelScope)
    val localTracks: StateFlow<Int> = librarySyncCoordinator.localTracks(viewModelScope)

    val serverTotalTracks: StateFlow<Int> = librarySyncCoordinator.serverTotalTracks
    fun refreshServerTotalTracks() = librarySyncCoordinator.refreshServerTotalTracks(viewModelScope)

    val localScanFolder: StateFlow<String?> = librarySyncCoordinator.localScanFolder
    fun setLocalScanFolder(path: String, label: String) =
        librarySyncCoordinator.setLocalScanFolder(path, label) { rescanLocalLibrary() }

    val canDeleteLocalFiles: Boolean get() = librarySyncCoordinator.canDeleteLocalFiles
    fun setP2pSync(enabled: Boolean) = librarySyncCoordinator.setP2pSync(enabled)
    fun setCatalogTrade(enabled: Boolean) = librarySyncCoordinator.setCatalogTrade(enabled)
    fun setServerArchive(enabled: Boolean) = librarySyncCoordinator.setServerArchive(enabled)
    fun setLibrarySync(enabled: Boolean) = librarySyncCoordinator.setLibrarySync(enabled)
    fun syncLibraryNow() = librarySyncCoordinator.syncLibraryNow()
    fun buildDeleteRequest(onReady: (IntentSender?) -> Unit) =
        librarySyncCoordinator.buildDeleteRequest(viewModelScope, onReady)

    fun setPopularityContribution(enabled: Boolean) =
        agroCoordinator.setPopularityContribution(enabled, viewModelScope)

    // ── Locale & Cache ─────────────────────────────────────────────────────────────────────

    val languageTag: String get() = appLocaleStore.tag
    val languageNeedsRecreate: Boolean get() = appLocaleStore.needsManualRecreate
    fun setLanguage(tag: String) { appLocaleStore.tag = tag }

    val cacheBytes: StateFlow<Long> = accountCoordinator.cacheBytes
    fun clearCache() = accountCoordinator.clearCache(viewModelScope)

    init {
        accountCoordinator.refreshCacheSize(viewModelScope)
        refreshSyncedSettings()
        viewModelScope.launch { incognitoRepository.refresh() }
        agroCoordinator.refreshPermissions(viewModelScope)
    }
}
