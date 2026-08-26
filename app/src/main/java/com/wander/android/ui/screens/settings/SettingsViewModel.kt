package com.wander.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.cache.AudioCacheManager
import com.wander.android.core.cache.DownloadScheduler
import com.wander.android.core.security.SecureStorage
import com.wander.android.core.sync.LibrarySyncScheduler
import com.wander.android.core.sync.LocalFileDeleter
import com.wander.android.core.update.UpdateCheckResult
import com.wander.android.core.update.UpdateChecker
import com.wander.android.data.repository.LibrarySyncRepository
import com.wander.android.data.repository.SyncProgress
import com.wander.android.data.sources.agro.AgroClient
import com.wander.android.data.sources.agro.AgroProfileApi
import com.wander.android.data.sources.agro.AgroSessionApi
import com.wander.android.data.sources.agro.AgroSyncedSettings
import com.wander.android.data.sources.agro.AgroVisibility
import com.wander.android.data.sources.agro.StorageUsage
import com.wander.android.data.sources.local.LocalMusicSource
import com.wander.android.data.sources.navidrome.NavidromeSource
import com.wander.android.data.sources.ytmusic.GoogleAccountManager
import com.wander.android.data.sources.ytmusic.YTMusicSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val cacheManager: AudioCacheManager,
    private val navidromeSource: NavidromeSource,
    private val accountManager: GoogleAccountManager,
    private val ytMusicSource: YTMusicSource,
    private val localSource: LocalMusicSource,
    private val downloadScheduler: DownloadScheduler,
    private val agroClient: AgroClient,
    private val pairing: AgroPairingController,
    private val profileApi: AgroProfileApi,
    private val sessionApi: AgroSessionApi,
    private val librarySync: LibrarySyncRepository,
    private val librarySyncScheduler: LibrarySyncScheduler,
    private val localFileDeleter: LocalFileDeleter,
    private val updateChecker: UpdateChecker
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
    fun setAutoUpdateCheckEnabled(enabled: Boolean) = secureStorage.setAutoUpdateCheckEnabled(enabled)

    val navidromeConnected: StateFlow<Boolean> = secureStorage.navidromeConfigured
    val youTubeConnected: StateFlow<Boolean> = accountManager.isLoggedIn

    /**
     * Who is signed in to YouTube Music.
     *
     * Empty until it is known, which the row renders as a plain "Signed in" — a name that has not
     * arrived yet must not make the row read as though nobody is.
     */
    private val _youTubeAccount = MutableStateFlow(accountManager.accountName)
    val youTubeAccount: StateFlow<String> = _youTubeAccount.asStateFlow()

    fun refreshYouTubeAccount() {
        viewModelScope.launch { _youTubeAccount.value = ytMusicSource.accountName() }
    }
    val localAvailable: StateFlow<Boolean> = localSource.isConfigured

    val isMonetDynamic: StateFlow<Boolean> = secureStorage.isMonetDynamic
    val isAmoledBlack: StateFlow<Boolean> = secureStorage.isAmoledBlack
    val isOfflineMode: StateFlow<Boolean> = secureStorage.isOfflineMode
    val agroConnected: StateFlow<Boolean> = secureStorage.agroConfigured

    /** Blank until the user names one; see [SecureStorage.shareDomain]. */
    val shareDomain: StateFlow<String> = secureStorage.shareDomain

    /**
     * The domain a paired Agro server publishes, blank if it has none or the feature is off.
     * When it is set it takes precedence, and the settings row says so rather than showing a
     * local value that is not the one being used.
     */
    val agroShareDomain: StateFlow<String> = secureStorage.agroShareDomain

    fun setShareDomain(domain: String) = secureStorage.setShareDomain(domain)

    private val _isIncognito = MutableStateFlow(secureStorage.isIncognitoMode)
    val isIncognito: StateFlow<Boolean> = _isIncognito.asStateFlow()

    private val _cacheBytes = MutableStateFlow(0L)
    val cacheBytes: StateFlow<Long> = _cacheBytes.asStateFlow()

    val navidromeServer: String get() = secureStorage.navidromeServerUrl
    val agroDevicePetname: String get() = secureStorage.agroDevicePetname.ifEmpty { "Wanda Android" }
    val agroDeviceId: String get() = secureStorage.agroDeviceId
    val agroServer: String get() = secureStorage.agroServerUrl

    /**
     * Manual pairing, for the case the QR cannot cover: the server behind a reverse proxy on a
     * domain the QR was never told about, or a QR printed with a `localhost` address that means
     * something else entirely on a phone.
     */
    internal val agroPairing: StateFlow<AgroPairingState> = pairing.state
    internal val agroConnection: StateFlow<AgroConnectionState> = pairing.connection

    /** The address a blank server field means, so the dialog and the client agree on one default. */
    val agroDefaultServer: String get() = AgroClient.DEFAULT_SERVER_URL

    fun pairAgro(server: String, username: String, passphrase: String) {
        viewModelScope.launch { pairing.pair(server, username, passphrase) }
    }

    fun signUpAgro(server: String, username: String, inviteCode: String) {
        viewModelScope.launch { pairing.signUp(server, username, inviteCode) }
    }

    fun resetAgroPairing() = pairing.reset()

    private val _agroVisibility = MutableStateFlow<AgroVisibility?>(null)
    internal val agroVisibility: StateFlow<AgroVisibility?> = _agroVisibility.asStateFlow()

    /**
     * Reads the switches back from the server rather than caching them here.
     *
     * They are the server's state, not this device's: another of your devices can change them, and
     * a stale local copy would show the user a privacy setting that is not the one in force.
     */
    fun refreshAgroVisibility() {
        if (!secureStorage.agroConfigured.value) {
            _agroVisibility.value = null
            return
        }
        viewModelScope.launch {
            _agroVisibility.value = profileApi.profile(secureStorage.agroUsername)
                .getOrNull()
                ?.let { AgroVisibility(it.showNowPlaying, it.showStats, it.discoverable) }
        }
    }

    fun setAgroVisibility(visibility: AgroVisibility) {
        // Shown immediately, then confirmed. A privacy switch that lags a round trip behind the
        // finger invites a second tap that puts it back where it started.
        _agroVisibility.value = visibility
        viewModelScope.launch {
            profileApi.setVisibility(visibility).onSuccess { profile ->
                _agroVisibility.value =
                    AgroVisibility(profile.showNowPlaying, profile.showStats, profile.discoverable)
            }.onFailure { refreshAgroVisibility() }
        }
    }

    fun refreshAgroConnection() {
        viewModelScope.launch { pairing.refreshConnection() }
    }

    val agroSyncSettings: StateFlow<Boolean> = secureStorage.agroSyncSettings

    /**
     * What Agro currently holds for Navidrome. Surfaced rather than silently applied: signing in
     * needs the password, which Agro deliberately never carries, so the most this can do is tell
     * the user which server to sign into and prefill it for them.
     */
    private val _syncedNavidrome = MutableStateFlow<AgroSyncedSettings?>(null)
    val syncedNavidrome: StateFlow<AgroSyncedSettings?> = _syncedNavidrome.asStateFlow()

    fun refreshSyncedSettings() {
        if (!secureStorage.agroSyncSettings.value) {
            _syncedNavidrome.value = null
            return
        }
        viewModelScope.launch {
            _syncedNavidrome.value = sessionApi.syncedSettings().getOrNull()
        }
    }

    /**
     * Turning sync on publishes what this device already knows, so the other clients pick it up
     * without waiting for the next Navidrome reconnection.
     */
    fun setAgroSyncSettings(enabled: Boolean) {
        secureStorage.setAgroSyncSettings(enabled)
        if (!enabled) return
        val server = secureStorage.navidromeServerUrl
        val user = secureStorage.navidromeUsername
        if (server.isBlank() || user.isBlank()) {
            // Nothing of ours to publish yet, so pull instead: the other device may already have
            // signed in somewhere this one has not.
            refreshSyncedSettings()
            return
        }
        viewModelScope.launch {
            sessionApi.pushSyncedSettings(server, user)
            refreshSyncedSettings()
        }
    }

    // ── Library sync ────────────────────────────────────────────────────────────────────────

    val librarySyncEnabled: StateFlow<Boolean> = secureStorage.agroLibrarySyncFlow
    val librarySyncProgress: StateFlow<SyncProgress> = librarySync.progress

    val pendingUploads: StateFlow<Int> = librarySync.pendingUploadCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val syncedTracks: StateFlow<Int> = librarySync.syncedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** How much local audio exists at all, so a zero can explain itself rather than just sit there. */
    val localTracks: StateFlow<Int> = librarySync.localTrackCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Storage used against the account's quota, or null when it could not be asked.
     *
     * Refreshed when Settings is opened rather than observed: nothing else on the device changes
     * it, and a poll would be a network call per interval for a number that moves when a sync
     * runs. Null is left null on failure — an unknown quota drawn as an empty bar would be a
     * claim, and a wrong one.
     */
    private val _storageUsage = MutableStateFlow<StorageUsage?>(null)
    val storageUsage: StateFlow<StorageUsage?> = _storageUsage.asStateFlow()

    fun refreshStorageUsage() {
        if (!secureStorage.agroLibrarySync) return
        viewModelScope.launch {
            _storageUsage.value = librarySync.storageUsage().getOrNull()
        }
    }

    /**
     * The one folder the on-device scan looks in, or null for the whole device.
     *
     * Setting it clears the incremental watermark: the previous scan only ever saw the old
     * folder, so continuing from where it left off would leave the new folder's files missing
     * until something in them happened to be modified.
     */
    private val _localScanFolder = MutableStateFlow(secureStorage.localScanFolderLabel)
    val localScanFolder: StateFlow<String?> = _localScanFolder.asStateFlow()

    fun setLocalScanFolder(path: String, label: String) {
        secureStorage.localScanFolder = path
        secureStorage.localScanFolderLabel = label
        secureStorage.localScanWatermark = 0L
        _localScanFolder.value = label
        rescanLocalLibrary()
    }

    /** Whether this device can delete other apps' media at all — API 30+ only. */
    val canDeleteLocalFiles: Boolean get() = localFileDeleter.isSupported

    /**
     * Turning it on schedules the background pass *and* kicks one off now, so the user sees
     * something happen rather than waiting for the next time the phone is charging on Wi-Fi.
     */
    fun setLibrarySync(enabled: Boolean) {
        secureStorage.setAgroLibrarySync(enabled)
        if (enabled) {
            librarySyncScheduler.enablePeriodicSync()
            librarySyncScheduler.syncNow()
        } else {
            librarySyncScheduler.disablePeriodicSync()
        }
    }

    fun syncLibraryNow() = librarySyncScheduler.syncNow()

    /**
     * Builds the system's delete prompt for every local file the server has confirmed.
     *
     * Returns the `IntentSender` for the Activity to launch — a ViewModel has no business holding
     * one, and the system dialog cannot be raised from anywhere else.
     */
    fun buildDeleteRequest(onReady: (android.content.IntentSender?) -> Unit) {
        viewModelScope.launch {
            val uris = librarySync.deletableLocalTracks().mapNotNull { it.streamUri }
            onReady(localFileDeleter.buildTrashRequest(uris))
        }
    }

    fun disconnectAgro() {
        viewModelScope.launch {
            runCatching { sessionApi.unregisterNode() }
            secureStorage.clearAgroCredentials()
            resetAgroPairing()
        }
    }

    init {
        refreshCacheSize()
        refreshSyncedSettings()
    }

    fun setMonetDynamic(enabled: Boolean) = secureStorage.setMonetDynamic(enabled)
    fun setAmoledBlack(enabled: Boolean) = secureStorage.setAmoledBlack(enabled)
    fun setOfflineMode(enabled: Boolean) = secureStorage.setOfflineMode(enabled)

    fun setIncognito(enabled: Boolean) {
        secureStorage.isIncognitoMode = enabled
        _isIncognito.value = enabled
    }

    fun disconnectNavidrome() = navidromeSource.logout()

    fun disconnectYouTube() {
        accountManager.signOut()
        _youTubeAccount.value = ""
    }

    fun rescanLocalLibrary() {
        viewModelScope.launch { localSource.refresh(full = true) }
    }

    fun downloadLikedNow() = downloadScheduler.downloadNow()

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { cacheManager.clearCache() }
            refreshCacheSize()
        }
    }

    /** Wipes every stored credential. Deliberately destructive and irreversible. */
    fun forgetEverything() {
        viewModelScope.launch {
            if (secureStorage.agroConfigured.value) {
                runCatching { sessionApi.unregisterNode() }
            }
            if (secureStorage.navidromeConfigured.value) {
                runCatching { navidromeSource.logout() }
            }
            if (accountManager.isLoggedIn.value) {
                runCatching { accountManager.signOut() }
            }
            secureStorage.clearAllCredentials()
            resetAgroPairing()
        }
    }

    private fun refreshCacheSize() {
        viewModelScope.launch {
            _cacheBytes.value = withContext(Dispatchers.IO) { cacheManager.cacheSizeBytes() }
        }
    }
}
