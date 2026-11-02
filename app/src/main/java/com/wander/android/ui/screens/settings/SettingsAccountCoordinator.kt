package com.wander.android.ui.screens.settings

import android.util.Log
import androidx.annotation.StringRes
import com.wander.android.R
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.repository.DropsRepository
import com.wander.android.data.repository.SocialRepository
import com.wander.android.data.sources.agro.AgroSessionApi
import com.wander.android.data.sources.local.LocalMusicSource
import com.wander.android.data.sources.navidrome.NavidromeSource
import com.wander.android.data.sources.ytmusic.GoogleAccountManager
import com.wander.android.data.sources.ytmusic.YTMusicSource
import kotlinx.coroutines.CancellationException
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
    private val deezerAccountManager: com.wander.android.data.sources.deezer.DeezerAccountManager,
    private val deezerStreamResolver: com.wander.android.data.sources.deezer.DeezerStreamResolver,
    private val localSource: LocalMusicSource,
    private val sessionApi: AgroSessionApi,
    private val socialRepository: SocialRepository,
    private val dropsRepository: DropsRepository,
    private val jamRecaps: com.wander.android.data.repository.JamRecapRepository,
    private val blends: com.wander.android.data.repository.BlendRepository,
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

    val deezerConnected: StateFlow<Boolean> = deezerAccountManager.isLoggedIn
    val deezerAccount: String get() = deezerAccountManager.accountName
    val deezerTier: String get() = deezerAccountManager.tier.name

    fun disconnectDeezer() {
        deezerAccountManager.signOut()
        deezerStreamResolver.invalidateSession()
    }

    fun rescanLocalLibrary(scope: CoroutineScope) {
        scope.launch { localSource.refresh(full = true) }
    }

    private val _forgetFailures = MutableStateFlow<List<Int>>(emptyList())

    /** The steps of the last [forgetEverything] that did not complete, as string resources. */
    val forgetFailures: StateFlow<List<Int>> = _forgetFailures.asStateFlow()

    fun dismissForgetFailures() {
        _forgetFailures.value = emptyList()
    }

    /**
     * Wipes every stored credential. Deliberately destructive and irreversible.
     *
     * Each step runs whether or not the one before it worked — a server that cannot be reached
     * must not leave a Deezer session behind — and every one that fails is reported in
     * [forgetFailures] rather than dropped. The streaming cache goes too: it holds Deezer and relay
     * audio already decrypted, fetched with the credentials being forgotten.
     */
    fun forgetEverything(scope: CoroutineScope, onResetAgroPairing: () -> Unit) {
        scope.launch {
            val failed = mutableListOf<Int>()
            if (secureStorage.agroConfigured.value) {
                failed.attempt(R.string.forget_step_agro_unregister) { sessionApi.unregisterNode().getOrThrow() }
            }
            if (secureStorage.navidromeConfigured.value) {
                failed.attempt(R.string.forget_step_navidrome) { navidromeSource.logout() }
            }
            // Signed in or not: an abandoned sign-in can leave WebView cookies behind that were
            // never captured, and both sign-outs are what clear CookieManager and WebStorage.
            failed.attempt(R.string.forget_step_youtube) { accountManager.signOut() }
            failed.attempt(R.string.forget_step_deezer) { deezerAccountManager.signOut() }
            deezerStreamResolver.invalidateSession()
            secureStorage.clearAllCredentials()
            failed.attempt(R.string.forget_step_friends) { socialRepository.clear() }
            failed.attempt(R.string.forget_step_drops) { dropsRepository.clear() }
            failed.attempt(R.string.forget_step_jam_recaps) { jamRecaps.clear() }
            blends.clear()
            failed.attempt(R.string.forget_step_audio_cache) {
                withContext(Dispatchers.IO) { cacheManager.clearCache() }
            }
            refreshCacheSize(scope)
            onResetAgroPairing()
            _forgetFailures.value = failed
        }
    }

    /**
     * Runs one step of [forgetEverything], recording [step] if it throws. Broad on purpose: the
     * steps are independent, and the failure is surfaced to the user, not swallowed.
     */
    private suspend fun MutableList<Int>.attempt(@StringRes step: Int, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "A forget-everything step failed: ${e.javaClass.simpleName}")
            add(step)
        }
    }

    private companion object {
        const val TAG = "SettingsAccount"
    }
}
