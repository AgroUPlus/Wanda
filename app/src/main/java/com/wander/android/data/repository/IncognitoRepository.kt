package com.wander.android.data.repository

import com.wander.android.core.security.SecureStorage
import com.wander.android.data.sources.agro.AgroGraphQl
import com.wander.android.data.sources.agro.AgroProfileApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the account is listening quietly — held by the server when there is one.
 *
 * Incognito cannot be a per-device setting. Going quiet on a laptop while the phone in your pocket
 * keeps announcing the same account is not going quiet, and no amount of care on one device fixes
 * it. So when an Agro server is paired **the server owns the value**: this device asks it, writes
 * through it, and follows it when another device changes it. With no server there is nobody to be
 * quiet from except this device's own records, and the local flag is the whole truth.
 *
 * The value is mirrored into [SecureStorage.isIncognitoMode] rather than replacing it. Every hot
 * path that must not record — scrobbles, play counts, the handoff publisher — already reads that
 * flag synchronously, and they run where a suspending call to a server has no business being. The
 * mirror is what lets a server-owned setting be enforced in those places unchanged.
 *
 * **It fails closed.** A server that cannot be reached leaves the last known value in place; it
 * never falls back to "not incognito". Read the wrong way round, an unreachable server would start
 * recording exactly when the network is doing something unexpected.
 */
@Singleton
internal class IncognitoRepository @Inject constructor(
    private val secureStorage: SecureStorage,
    private val profileApi: AgroProfileApi,
    private val graphQl: AgroGraphQl
) {

    private val _isIncognito = MutableStateFlow(secureStorage.isIncognitoMode)

    /** For the UI, which needs to redraw when another device flips the switch. */
    val isIncognito: StateFlow<Boolean> = _isIncognito.asStateFlow()

    /** Whether the server, rather than this device, decides. */
    private val serverOwned: Boolean get() = graphQl.isConfigured

    /**
     * Brings this device in line with the server.
     *
     * Called on launch and whenever a `SETTINGS_SYNC` frame arrives, which is how a switch flipped
     * on another device reaches this one.
     */
    suspend fun refresh() {
        if (!serverOwned) {
            apply(secureStorage.isIncognitoMode)
            return
        }
        profileApi.incognito().onSuccess(::apply)
    }

    /**
     * Sets it, wherever it lives.
     *
     * Written to the server first and mirrored only on success, so a failed write leaves the app
     * showing what is actually in force rather than what was asked for. With no server the local
     * flag is written directly and always succeeds.
     */
    suspend fun set(enabled: Boolean): Result<Unit> {
        if (!serverOwned) {
            apply(enabled)
            return Result.success(Unit)
        }
        return profileApi.setIncognito(enabled).map(::apply)
    }

    private fun apply(enabled: Boolean) {
        secureStorage.isIncognitoMode = enabled
        _isIncognito.value = enabled
    }
}
