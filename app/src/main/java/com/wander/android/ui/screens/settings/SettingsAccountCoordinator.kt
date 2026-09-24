package com.wander.android.ui.screens.settings

import com.wander.android.core.security.SecureStorage
import com.wander.android.data.repository.DropsRepository
import com.wander.android.data.repository.SocialRepository
import com.wander.android.data.sources.agro.AgroSessionApi
import com.wander.android.data.sources.local.LocalMusicSource
import com.wander.android.data.sources.navidrome.NavidromeSource
import com.wander.android.data.sources.ytmusic.GoogleAccountManager
import com.wander.android.data.sources.ytmusic.YTMusicSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.wander.android.core.cache.AudioCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coordinates external service accounts (Navidrome, YouTube Music, Local) and total reset.
 */
internal class SettingsAccountCoordinator @Inject constructor(
    private val secureStorage: SecureStorage,
    private val navidromeSource: NavidromeSource,
    private val accountManager: GoogleAccountManager,
    private val ytMusicSource: YTMusicSource,
    private val localSource: LocalMusicSource,
    private val sessionApi: AgroSessionApi,
    private val socialRepository: SocialRepository,
    private val dropsRepository: DropsRepository,
    private val cacheManager: AudioCacheManager
) {
    private val _cacheBytes = MutableStateFlow(0L)
    val cacheBytes: StateFlow<Long> = _cacheBytes.asStateFlow()

    fun clearCache(scope: CoroutineScope) {
        scope.launch {
            withContext(Dispatchers.IO) { cacheManager.clearCache() }
            refreshCacheSize(scope)
        }
    }

    fun refreshCacheSize(scope: CoroutineScope) {
        scope.launch {
            _cacheBytes.value = withContext(Dispatchers.IO) { cacheManager.cacheSizeBytes() }
        }
    }
    val navidromeConnected: StateFlow<Boolean> = secureStorage.navidromeConfigured
    val youTubeConnected: StateFlow<Boolean> = accountManager.isLoggedIn

    private val _youTubeAccount = MutableStateFlow(accountManager.accountName)
    val youTubeAccount: StateFlow<String> = _youTubeAccount.asStateFlow()

    fun refreshYouTubeAccount(scope: CoroutineScope) {
        scope.launch { _youTubeAccount.value = ytMusicSource.accountName() }
    }

    val localAvailable: StateFlow<Boolean> = localSource.isConfigured

    fun disconnectNavidrome() = navidromeSource.logout()

    fun disconnectYouTube() {
        accountManager.signOut()
        _youTubeAccount.value = ""
    }

    fun rescanLocalLibrary(scope: CoroutineScope) {
        scope.launch { localSource.refresh(full = true) }
    }

    /** Wipes every stored credential. Deliberately destructive and irreversible. */
    fun forgetEverything(scope: CoroutineScope, onResetAgroPairing: () -> Unit) {
        scope.launch {
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
            runCatching { socialRepository.clear() }
            runCatching { dropsRepository.clear() }
            onResetAgroPairing()
        }
    }
}
