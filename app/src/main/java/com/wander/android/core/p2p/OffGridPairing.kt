package com.wander.android.core.p2p

import android.util.Base64
import com.wander.android.core.security.IdentityKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asking a peer, face to face, for permission to fetch its audio.
 *
 * Every other tier gets its grant from Agro, which is exactly what off-grid cannot do: two phones
 * in a car have no server between them and may never have had one. So the peer issues the grant
 * itself, over the link that was just raised, and this is the caller's half of that exchange.
 *
 * ## Why the answer is checked and not just used
 *
 * A Wi-Fi Direct group is formed with whatever the framework negotiates. The beacon is ten bytes
 * and cannot carry a MAC address, so there is no way to *ask* for a particular peer — the
 * link comes up and only then can this device find out whose it is. Rather than pretend to choose,
 * this verifies afterwards: the peer returns its identity key, the beacon's fingerprint is
 * recomputed from it, and a mismatch means the link reached somebody else and must be dropped.
 *
 * That check is also what makes the grant worth anything. The peer seals the token to this
 * device's public key, so only this device can read it, and the key it sealed to is the key whose
 * fingerprint was just verified — which is the binding `P2PServer.writeEncrypted` notes as missing.
 */
@Singleton
internal class OffGridPairing @Inject constructor(
    private val identityKeyManager: IdentityKeyManager
) {

    /**
     * A short timeout on purpose: the peer is one radio hop away, and a user who has just tapped a
     * name is waiting. A link that cannot answer in seconds is not a link worth waiting on.
     */
    private val client = OkHttpClient.Builder()
        .connectTimeout(PAIR_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(PAIR_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    /** Why an exchange did not produce a usable grant. Each is a different thing to tell the user. */
    internal sealed interface Failure {
        /** The peer did not answer, or answered with something that is not a pairing response. */
        data object Unreachable : Failure

        /** The link reached a device other than the one that was chosen. */
        data object WrongPeer : Failure

        /** The grant was sealed to somebody else, or is not readable by this device's key. */
        data object Unreadable : Failure
    }

    /**
     * Trades this device's identity for a bearer grant on [baseUrl].
     *
     * [expected] is the beacon of the peer the user actually picked. Returns the grant token, or a
     * [Failure] saying which of the three ways it went wrong — the caller drops the link on any of
     * them, but they do not mean the same thing and should not read as if they did.
     */
    suspend fun pair(
        baseUrl: String,
        expected: OffGridBeacon
    ): Result<String> = withContext(Dispatchers.IO) {
        val myKey = identityKeyManager.getPublicKeyBase64()
        val url = "$baseUrl/p2p/pair?key=" + java.net.URLEncoder.encode(myKey, "UTF-8")

        val body = runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        }.getOrNull() ?: return@withContext Result.failure(PairingException(Failure.Unreachable))

        // Two lines: the peer's identity key, then the grant sealed to ours.
        val lines = body.trim().lines()
        if (lines.size < 2) return@withContext Result.failure(PairingException(Failure.Unreachable))
        val peerKeyB64 = lines[0].trim()
        val sealed = lines[1].trim()

        if (!matchesBeacon(peerKeyB64, expected)) {
            return@withContext Result.failure(PairingException(Failure.WrongPeer))
        }

        val token = runCatching { identityKeyManager.openNote(sealed) }.getOrNull()
        if (token.isNullOrBlank()) {
            return@withContext Result.failure(PairingException(Failure.Unreadable))
        }
        Result.success(token)
    }

    /**
     * Whether the peer at [baseUrl] is still answering.
     *
     * `/p2p/ping` is the one endpoint that needs no grant, which is what makes it usable as a
     * liveness check: a 403 would be indistinguishable from a dead link, and the question here is
     * only whether anything is still there.
     */
    suspend fun ping(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url("$baseUrl/p2p/ping").build()).execute()
                .use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /**
     * Tells [baseUrl] this device is done, so the peer stops listing it and stops honouring its
     * grant.
     *
     * Best effort by design, and the caller does not wait on the answer to tear its own side down.
     * The link is often being dropped *because* the peer has gone, and a teardown that could be
     * blocked by an unreachable peer would be a teardown that hangs exactly when it is needed. The
     * grant expires on its own either way; this only makes the common case immediate.
     */
    suspend fun unpair(baseUrl: String): Unit = withContext(Dispatchers.IO) {
        val myKey = identityKeyManager.getPublicKeyBase64()
        val url = "$baseUrl/p2p/unpair?key=" + java.net.URLEncoder.encode(myKey, "UTF-8")
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().close()
        }
        Unit
    }

    /**
     * Whether [peerKeyB64] is the key the chosen beacon was advertising.
     *
     * The fingerprint is the first eight bytes of the key (`OffGridBeacon.fingerprintFrom`), so
     * recomputing it here and comparing is the whole check. Eight bytes is short for a collision
     * guarantee, but it is not being asked for one: it distinguishes the handful of devices in a
     * room, and the grant it gates is worth one shared listen.
     */
    private fun matchesBeacon(peerKeyB64: String, expected: OffGridBeacon): Boolean {
        val peerKey = runCatching {
            Base64.decode(peerKeyB64, Base64.NO_WRAP)
        }.getOrNull() ?: return false
        if (peerKey.size < OffGridBeacon.FINGERPRINT_SIZE) return false
        return OffGridBeacon.fingerprintFrom(peerKey).contentEquals(expected.fingerprint)
    }

    /** Carries a [Failure] through `Result`, so callers can say which way it failed. */
    internal class PairingException(val failure: Failure) : Exception(failure.toString())

    private companion object {
        const val PAIR_TIMEOUT_MS = 5_000L
    }
}
