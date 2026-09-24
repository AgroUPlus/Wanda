package com.wander.android.core.sync

import android.util.Base64
import android.util.Log
import com.wander.android.core.p2p.OffGridBeacon
import com.wander.android.core.security.IdentityKeyManager
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A peer that paired with this device.
 */
internal data class PairedPeer(
    val publicKeyB64: String,
    val fingerprint: ByteArray,
    val pairedAtMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val that = other as? PairedPeer ?: return false
        return publicKeyB64 == that.publicKeyB64 && pairedAtMs == that.pairedAtMs
    }

    override fun hashCode(): Int = 31 * publicKeyB64.hashCode() + pairedAtMs.hashCode()
}

private data class Grant(
    val forUser: String,
    val boundKeys: List<String>,
    val expiresAtMs: Long,
    val origin: Origin
)

private enum class Origin {
    AGRO,
    PAIRING
}

/**
 * Manages access tokens and authentication grants for P2P audio streaming and pairing.
 */
internal class P2PGrantManager(
    private val identityKeyManager: IdentityKeyManager
) {
    private val grants = ConcurrentHashMap<String, Grant>()
    private val _pairedPeers = MutableStateFlow<List<PairedPeer>>(emptyList())
    val pairedPeers: StateFlow<List<PairedPeer>> = _pairedPeers.asStateFlow()

    fun isAuthorised(request: String): Boolean {
        val token = tokenOf(request)
        if (token.isEmpty()) return false
        val grant = grants[token] ?: return false
        if (grant.expiresAtMs <= System.currentTimeMillis()) {
            grants.remove(token)
            return false
        }
        return true
    }

    fun mintPairingGrant(requesterPublicKeyB64: String): String? {
        if (requesterPublicKeyB64.isBlank()) return null
        val token = ByteArray(PAIR_TOKEN_BYTES)
            .also { SecureRandom().nextBytes(it) }
            .let { Base64.encodeToString(it, Base64.NO_WRAP) }
        val sealed = runCatching {
            identityKeyManager.sealNote(requesterPublicKeyB64, token)
        }.getOrNull() ?: return null

        record(
            token = token,
            forUser = requesterPublicKeyB64,
            forKeys = listOf(requesterPublicKeyB64),
            ttlSeconds = PAIR_GRANT_TTL_SECONDS,
            origin = Origin.PAIRING
        )
        rememberPairedPeer(requesterPublicKeyB64)
        return sealed
    }

    fun acceptGrant(token: String, forUser: String, forKeys: List<String>, ttlSeconds: Long) =
        record(token, forUser, forKeys, ttlSeconds, Origin.AGRO)

    fun clearPairingGrants() {
        grants.entries.removeAll { it.value.origin == Origin.PAIRING }
        _pairedPeers.value = emptyList()
    }

    fun unpair(publicKeyB64: String) {
        if (publicKeyB64.isBlank()) return
        grants.entries.removeAll {
            it.value.origin == Origin.PAIRING && it.value.forUser == publicKeyB64
        }
        _pairedPeers.value = _pairedPeers.value.filterNot { it.publicKeyB64 == publicKeyB64 }
    }

    fun revokePairing(deviceId: Int) {
        val peer = _pairedPeers.value.firstOrNull {
            OffGridBeacon.deviceIdFrom(
                runCatching {
                    Base64.decode(it.publicKeyB64, Base64.NO_WRAP)
                }.getOrDefault(ByteArray(0))
            ) == deviceId
        } ?: return
        unpair(peer.publicKeyB64)
    }

    fun sealingKeyFor(token: String, headerKey: String?): String? {
        val grant = grants[token] ?: run {
            Log.w(TAG, "Sealing refused: no grant for the token presented")
            return null
        }
        val key = GrantBinding.sealingKey(grant.boundKeys, headerKey)
        Log.i(
            TAG,
            "Sealing to " + when {
                key == null -> "nothing — neither the grant nor the request named a key"
                key == headerKey -> "the requester's own header key"
                else -> "a key the grant names, ignoring the header's"
            }
        )
        return key
    }

    fun tokenOf(request: String): String = request.lineSequence()
        .firstOrNull { it.startsWith("Authorization:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        ?.removePrefix("Bearer ")
        ?.trim()
        .orEmpty()

    fun identityKeyOf(request: String): String? = request.lineSequence()
        .firstOrNull { it.startsWith(IDENTITY_HEADER, ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    private fun record(
        token: String,
        forUser: String,
        forKeys: List<String>,
        ttlSeconds: Long,
        origin: Origin
    ) {
        if (token.isBlank()) return
        grants.entries.removeAll { it.value.expiresAtMs <= System.currentTimeMillis() }
        grants[token] = Grant(
            forUser = forUser,
            boundKeys = forKeys.filter { it.isNotBlank() },
            expiresAtMs = System.currentTimeMillis() + ttlSeconds * 1000L,
            origin = origin
        )
    }

    private fun rememberPairedPeer(publicKeyB64: String) {
        val fingerprint = runCatching {
            OffGridBeacon.fingerprintFrom(
                Base64.decode(publicKeyB64, Base64.NO_WRAP)
            )
        }.getOrNull() ?: return
        val peer = PairedPeer(publicKeyB64, fingerprint, System.currentTimeMillis())
        _pairedPeers.value = _pairedPeers.value.filterNot { it.publicKeyB64 == publicKeyB64 } + peer
    }

    companion object {
        private const val TAG = "P2PGrantManager"
        const val IDENTITY_HEADER = "X-Wanda-Identity"
        private const val PAIR_TOKEN_BYTES = 32
        private const val PAIR_GRANT_TTL_SECONDS = 30L * 60L
    }
}
