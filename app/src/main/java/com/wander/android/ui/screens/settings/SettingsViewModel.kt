package com.wander.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.cache.AudioCacheManager
import com.wander.android.core.cache.DownloadScheduler
import com.wander.android.core.sync.LibrarySyncScheduler
import com.wander.android.core.sync.LocalFileDeleter
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.repository.LibrarySyncRepository
import com.wander.android.data.repository.SyncProgress
import com.wander.android.data.sources.agro.AgroClient
import com.wander.android.data.sources.agro.AgroSyncedSettings
import com.wander.android.data.sources.agro.AgroSessionApi
import com.wander.android.data.sources.local.LocalMusicSource
import com.wander.android.data.sources.navidrome.NavidromeSource
import com.wander.android.data.sources.ytmusic.GoogleAccountManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val cacheManager: AudioCacheManager,
    private val navidromeSource: NavidromeSource,
    private val accountManager: GoogleAccountManager,
    private val localSource: LocalMusicSource,
    private val downloadScheduler: DownloadScheduler,
    private val agroClient: AgroClient,
    private val sessionApi: AgroSessionApi,
    private val librarySync: LibrarySyncRepository,
    private val librarySyncScheduler: LibrarySyncScheduler,
    private val localFileDeleter: LocalFileDeleter
) : ViewModel() {

    val navidromeConnected: StateFlow<Boolean> = secureStorage.navidromeConfigured
    val youTubeConnected: StateFlow<Boolean> = accountManager.isLoggedIn
    val localAvailable: StateFlow<Boolean> = localSource.isConfigured

    val isMonetDynamic: StateFlow<Boolean> = secureStorage.isMonetDynamic
    val isAmoledBlack: StateFlow<Boolean> = secureStorage.isAmoledBlack
    val isOfflineMode: StateFlow<Boolean> = secureStorage.isOfflineMode
    val agroConnected: StateFlow<Boolean> = secureStorage.agroConfigured

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
    private val _agroPairing = MutableStateFlow<AgroPairingState>(AgroPairingState.Idle)
    val agroPairing: StateFlow<AgroPairingState> = _agroPairing.asStateFlow()

    fun pairAgro(server: String, username: String, passphrase: String) {
        _agroPairing.value = AgroPairingState.Connecting
        viewModelScope.launch {
            _agroPairing.value = agroClient.pair(server, username, passphrase).fold(
                onSuccess = { AgroPairingState.Paired(it ?: agroDevicePetname) },
                onFailure = { AgroPairingState.Failed(it.message ?: "Could not reach the server") }
            )
        }
    }

    fun resetAgroPairing() { _agroPairing.value = AgroPairingState.Idle }

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
        secureStorage.clearAgroCredentials()
        resetAgroPairing()
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

    fun disconnectYouTube() = accountManager.signOut()

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
        secureStorage.clearAllCredentials()
    }

    private fun refreshCacheSize() {
        viewModelScope.launch {
            _cacheBytes.value = withContext(Dispatchers.IO) { cacheManager.cacheSizeBytes() }
        }
    }
}
