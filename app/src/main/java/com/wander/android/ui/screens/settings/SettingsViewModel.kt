package com.wander.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.cache.AudioCacheManager
import com.wander.android.core.cache.DownloadScheduler
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.sources.local.LocalMusicSource
import com.wander.android.data.sources.navidrome.NavidromeSource
import com.wander.android.data.sources.ytmusic.GoogleAccountManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val downloadScheduler: DownloadScheduler
) : ViewModel() {

    val navidromeConnected: StateFlow<Boolean> = secureStorage.navidromeConfigured
    val youTubeConnected: StateFlow<Boolean> = accountManager.isLoggedIn
    val localAvailable: StateFlow<Boolean> = localSource.isConfigured

    val isMonetDynamic: StateFlow<Boolean> = secureStorage.isMonetDynamic
    val isAmoledBlack: StateFlow<Boolean> = secureStorage.isAmoledBlack
    val isOfflineMode: StateFlow<Boolean> = secureStorage.isOfflineMode

    private val _isIncognito = MutableStateFlow(secureStorage.isIncognitoMode)
    val isIncognito: StateFlow<Boolean> = _isIncognito.asStateFlow()

    private val _cacheBytes = MutableStateFlow(0L)
    val cacheBytes: StateFlow<Long> = _cacheBytes.asStateFlow()

    val navidromeServer: String get() = secureStorage.navidromeServerUrl

    init {
        refreshCacheSize()
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
