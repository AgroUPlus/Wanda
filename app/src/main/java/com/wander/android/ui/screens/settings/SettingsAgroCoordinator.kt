package com.wander.android.ui.screens.settings

import com.wander.android.core.security.SecureStorage
import com.wander.android.data.sources.agro.AgroAccountApi
import com.wander.android.data.sources.agro.AgroClient
import com.wander.android.data.sources.agro.AgroProfileApi
import com.wander.android.data.sources.agro.AgroSessionApi
import com.wander.android.data.sources.agro.AgroSyncedSettings
import com.wander.android.data.sources.agro.AgroVisibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Coordinates Agro account profile, pairing, synced settings, permissions, and network proxying.
 */
internal class SettingsAgroCoordinator @Inject constructor(
    private val secureStorage: SecureStorage,
    private val pairing: AgroPairingController,
    private val profileApi: AgroProfileApi,
    private val sessionApi: AgroSessionApi,
    private val accountApi: AgroAccountApi
) {
    internal val agroPairing: StateFlow<AgroPairingState> = pairing.state
    internal val agroConnection: StateFlow<AgroConnectionState> = pairing.connection

    val agroDefaultServer: String get() = AgroClient.DEFAULT_SERVER_URL

    fun pairAgro(server: String, username: String, passphrase: String, scope: CoroutineScope) {
        scope.launch { pairing.pair(server, username, passphrase) }
    }

    fun signUpAgro(server: String, username: String, inviteCode: String, scope: CoroutineScope) {
        scope.launch { pairing.signUp(server, username, inviteCode) }
    }

    fun resetAgroPairing() = pairing.reset()

    private val _agroVisibility = MutableStateFlow<AgroVisibility?>(null)
    internal val agroVisibility: StateFlow<AgroVisibility?> = _agroVisibility.asStateFlow()

    fun refreshAgroVisibility(scope: CoroutineScope) {
        if (!secureStorage.agroConfigured.value) {
            _agroVisibility.value = null
            return
        }
        scope.launch {
            _agroVisibility.value = profileApi.profile(secureStorage.agroUsername)
                .getOrNull()
                ?.let {
                    AgroVisibility(
                        it.showNowPlaying, it.showStats, it.discoverable,
                        popularOptIn = it.popularOptIn
                    )
                }
        }
    }

    fun setAgroVisibility(visibility: AgroVisibility, scope: CoroutineScope) {
        _agroVisibility.value = visibility
        scope.launch {
            profileApi.setVisibility(visibility).onSuccess { profile ->
                _agroVisibility.value = AgroVisibility(
                    profile.showNowPlaying, profile.showStats, profile.discoverable,
                    popularOptIn = profile.popularOptIn
                )
            }.onFailure { refreshAgroVisibility(scope) }
        }
    }

    fun setPopularityContribution(enabled: Boolean, scope: CoroutineScope) {
        secureStorage.setAgroPopularityContribution(enabled)
        _agroVisibility.value?.let { current ->
            if (current.popularOptIn != enabled) {
                setAgroVisibility(current.copy(popularOptIn = enabled), scope)
            }
        }
    }

    fun refreshAgroConnection(scope: CoroutineScope) {
        scope.launch { pairing.refreshConnection() }
    }

    private val _syncedNavidrome = MutableStateFlow<AgroSyncedSettings?>(null)
    val syncedNavidrome: StateFlow<AgroSyncedSettings?> = _syncedNavidrome.asStateFlow()

    fun refreshSyncedSettings(scope: CoroutineScope) {
        if (!secureStorage.agroSyncSettings.value) {
            _syncedNavidrome.value = null
            return
        }
        scope.launch {
            _syncedNavidrome.value = sessionApi.syncedSettings().getOrNull()
        }
    }

    fun setAgroSyncSettings(enabled: Boolean, scope: CoroutineScope) {
        secureStorage.setAgroSyncSettings(enabled)
        if (!enabled) return
        val server = secureStorage.navidromeServerUrl
        val user = secureStorage.navidromeUsername
        if (server.isBlank() || user.isBlank()) {
            refreshSyncedSettings(scope)
            return
        }
        scope.launch {
            sessionApi.pushSyncedSettings(server, user)
            refreshSyncedSettings(scope)
        }
    }

    private val _canArchive = MutableStateFlow(false)
    val canArchive: StateFlow<Boolean> = _canArchive.asStateFlow()

    fun refreshPermissions(scope: CoroutineScope) {
        if (!secureStorage.agroConfigured.value) return
        scope.launch {
            accountApi.permissions().onSuccess { _canArchive.value = it.canArchive }
        }
    }

    fun disconnectAgro(scope: CoroutineScope) {
        scope.launch {
            runCatching { sessionApi.unregisterNode() }
            secureStorage.clearAgroCredentials()
            resetAgroPairing()
        }
    }
}
