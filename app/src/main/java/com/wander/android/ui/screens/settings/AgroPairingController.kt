package com.wander.android.ui.screens.settings

import com.wander.android.core.security.SecureStorage
import com.wander.android.data.sources.agro.AgroAuthError
import com.wander.android.data.sources.agro.explain
import com.wander.android.data.sources.agro.AgroClient
import com.wander.android.data.sources.agro.AgroLogin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything about getting — and staying — signed in to an Agro server.
 *
 * Split out of `SettingsViewModel` because it is a concern of its own with two pieces of state that
 * are easy to confuse: [state] is an attempt in progress, [connection] is whether the credential
 * already in storage still works. It holds no scope; the caller supplies one by calling the suspend
 * functions from theirs.
 */
@Singleton
internal class AgroPairingController @Inject constructor(
    private val agroClient: AgroClient,
    private val agroLogin: AgroLogin,
    private val secureStorage: SecureStorage
) {
    private val _state = MutableStateFlow<AgroPairingState>(AgroPairingState.Idle)
    val state: StateFlow<AgroPairingState> = _state.asStateFlow()

    private val _connection = MutableStateFlow<AgroConnectionState>(AgroConnectionState.Unpaired)
    val connection: StateFlow<AgroConnectionState> = _connection.asStateFlow()

    suspend fun pair(server: String, username: String, passphrase: String) {
        _state.value = AgroPairingState.Connecting
        _state.value = agroClient.pairWithPassphrase(server, username, passphrase).fold(
            onSuccess = { petname ->
                AgroPairingState.Paired(
                    username = secureStorage.agroUsername.ifEmpty { username },
                    petname = petname ?: secureStorage.agroDevicePetname.ifEmpty { "Wanda Android" }
                )
            },
            onFailure = { AgroPairingState.Failed(AgroAuthError.from(it)) }
        )
        refreshConnection()
    }

    /**
     * Creates an account, and stops there.
     *
     * Deliberately not chained into pairing: on a server that queues new accounts for approval the
     * pairing would fail, and the passphrase — which exists in exactly one place, the response we
     * have just received — would be buried under that failure instead of shown to the user.
     */
    suspend fun signUp(server: String, username: String, inviteCode: String) {
        _state.value = AgroPairingState.Connecting
        _state.value = agroLogin.signup(server, username, inviteCode).fold(
            onSuccess = { AgroPairingState.Registered(it) },
            onFailure = { AgroPairingState.Failed(AgroAuthError.from(it)) }
        )
    }

    fun reset() {
        _state.value = AgroPairingState.Idle
    }

    /**
     * Asks the server whether the stored credential still works.
     *
     * Nothing else does. A revoked device token or a suspended account produces no event on this
     * device, so without this the connection row reported a healthy pairing indefinitely while
     * every request it made was being refused.
     */
    suspend fun refreshConnection() {
        if (!secureStorage.agroConfigured.value) {
            _connection.value = AgroConnectionState.Unpaired
            return
        }
        _connection.value = AgroConnectionState.Checking
        _connection.value = agroClient.verify().fold(
            onSuccess = { AgroConnectionState.Connected(it.username, it.role) },
            onFailure = { error ->
                when (val typed = AgroAuthError.from(error)) {
                    is AgroAuthError.NotActive -> AgroConnectionState.NotActive(typed.explain())
                    is AgroAuthError.Rejected -> AgroConnectionState.Rejected
                    // A rate limit or an unreachable host says nothing about whether the
                    // credential is good, so neither may be reported as being signed out.
                    else -> AgroConnectionState.Unreachable
                }
            }
        )
    }
}
