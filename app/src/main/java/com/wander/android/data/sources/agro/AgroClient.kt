package com.wander.android.data.sources.agro

import com.wander.android.core.security.SecureStorage
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Registration, handoff publishing and pairing. Transport lives in [AgroGraphQl]. */
@Singleton
class AgroClient @Inject constructor(
    private val login: AgroLogin,
    private val graphQl: AgroGraphQl,
    private val secureStorage: SecureStorage
) {
    val isConfigured: Boolean get() = graphQl.isConfigured

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (ignored: Exception) {}
        return null
    }

    /**
     * Battery-first one-shot registration: called on app launch or pairing only.
     * Never runs in an unconstrained background loop.
     */
    suspend fun registerNode(currentTrack: String? = null): Result<String?> {
        val lanAddress = getLocalIpAddress()?.let { "$it:8702" }
        val mutation = """
            mutation RegisterNode(${'$'}userId: String!, ${'$'}deviceId: String!, ${'$'}clientType: String!, ${'$'}deviceName: String, ${'$'}lanAddress: String, ${'$'}currentTrack: String) {
                registerNode(userId: ${'$'}userId, deviceId: ${'$'}deviceId, clientType: ${'$'}clientType, deviceName: ${'$'}deviceName, lanAddress: ${'$'}lanAddress, currentTrack: ${'$'}currentTrack) {
                    petname
                }
            }
        """.trimIndent()

        val variables = buildJsonObject {
            put("userId", secureStorage.agroUsername)
            put("deviceId", secureStorage.agroDeviceId)
            put("clientType", "wanda")
            secureStorage.agroDevicePetname.ifEmpty { null }?.let { put("deviceName", it) }
            lanAddress?.let { put("lanAddress", it) }
            currentTrack?.let { put("currentTrack", it) }
        }

        return graphQl.execute(mutation, variables).map { data ->
            val petname = data["registerNode"]?.jsonObject?.get("petname")?.jsonPrimitive?.contentOrNull
            if (!petname.isNullOrBlank()) secureStorage.setAgroDevicePetname(petname)
            petname
        }
    }

    /**
     * Event-driven handoff: called only on Media3 playback transitions, never on timers.
     * See [AgroHandoffPublisher], which is what decides an event is worth sending.
     */
    suspend fun sendHandoffState(
        trackUri: String,
        title: String,
        artist: String,
        album: String?,
        artworkUrl: String?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean
    ): Result<Unit> {
        val mutation = """
            mutation UpdateHandoff(${'$'}input: HandoffInput!) {
                updateHandoff(input: ${'$'}input)
            }
        """.trimIndent()

        val variables = buildJsonObject {
            put("input", buildJsonObject {
                put("userId", secureStorage.agroUsername)
                put("trackUri", trackUri)
                put("trackTitle", title)
                put("artistName", artist)
                album?.let { put("albumName", it) }
                // Optional in `HandoffInput`, but it is what lets the receiving client show the
                // right cover without looking the track up again.
                artworkUrl?.let { put("artworkUrl", it) }
                put("positionMs", positionMs)
                // What the position is measured against. Without it anything rendering this
                // session can only show an elapsed count — a progress bar needs both ends.
                put("durationMs", durationMs)
                put("isPlaying", isPlaying)
                put("deviceId", secureStorage.agroDeviceId)
            })
        }

        return graphQl.execute(mutation, variables).map { }
    }

    /**
     * Asks the server who this device's stored token belongs to.
     *
     * The only way to find out that a credential has stopped working. A revoked app password or a
     * suspended account produces no event on the device — every subsequent query simply fails, and
     * with nothing checking, Settings went on reporting a healthy pairing indefinitely. `me` is the
     * cheapest field that proves the whole chain: the token resolves, the account is active, and it
     * is the account whose name we have stored.
     */
    internal suspend fun verify(): Result<AgroIdentity> {
        val query = """
            query Me(${'$'}username: String!) {
                me(username: ${'$'}username) { username role state }
            }
        """.trimIndent()
        val variables = buildJsonObject { put("username", secureStorage.agroUsername) }

        return graphQl.execute(query, variables).mapCatching { data ->
            val me = data["me"]?.jsonObject
                ?: throw AgroAuthError.Rejected("This server has no account by that name")
            val state = me["state"]?.jsonPrimitive?.contentOrNull
            if (state != null && !state.equals("active", ignoreCase = true)) {
                throw AgroAuthError.NotActive("This account is $state")
            }
            AgroIdentity(
                username = me["username"]?.jsonPrimitive?.contentOrNull
                    ?: secureStorage.agroUsername,
                role = me["role"]?.jsonPrimitive?.contentOrNull ?: "member"
            )
        }
    }

    /** Unregisters this device from Agro on unpair or credential reset. */
    suspend fun unregisterNode(): Result<Unit> {
        val mutation = """
            mutation UnregisterNode(${'$'}userId: String!, ${'$'}deviceId: String!) {
                unregisterNode(userId: ${'$'}userId, deviceId: ${'$'}deviceId)
            }
        """.trimIndent()
        val variables = buildJsonObject {
            put("userId", secureStorage.agroUsername)
            put("deviceId", secureStorage.agroDeviceId)
        }
        return graphQl.execute(mutation, variables).map { }
    }

    companion object {
        const val DEFAULT_SERVER_URL = "https://agro.kolbxyz.xyz"
    }

    /**
     * `agro://connect?username=…&token=…&server=…`, as minted by the server's pairing QR.
     *
     * The QR used to carry `passphrase=`, and the app stored it as its bearer token because the
     * two were the same string. They are not any more: each scan mints its own revocable device
     * token, so photographing a code no longer hands over the account. `passphrase=` is still read
     * as a fallback and exchanged through [pairWithPassphrase] — an old QR is not a working
     * credential, but it is a thing a user can reasonably still be holding.
     */
    suspend fun parseQrCodePayload(qrString: String): Result<String?> {
        if (!qrString.startsWith("agro://connect")) {
            return Result.failure(IOException("Invalid QR: expected agro://connect"))
        }
        val uri = runCatching { android.net.Uri.parse(qrString) }.getOrNull()
            ?: return Result.failure(IOException("Invalid URI format in QR"))
        val user = uri.getQueryParameter("username").orEmpty()
        val server = uri.getQueryParameter("server")?.takeIf { it.isNotBlank() }
            ?: secureStorage.agroServerUrl.takeIf { it.isNotBlank() }
            ?: DEFAULT_SERVER_URL

        uri.getQueryParameter("token")?.takeIf { it.isNotBlank() }?.let { token ->
            return pairWithToken(server, user, token)
        }
        val legacy = uri.getQueryParameter("passphrase") ?: uri.getQueryParameter("key").orEmpty()
        if (legacy.isBlank()) {
            return Result.failure(IOException("QR code contains neither a token nor a passphrase"))
        }
        return pairWithPassphrase(server, user, legacy)
    }

    /**
     * Exchanges a passphrase for a device token, then pairs with it.
     *
     * This is what the manual Settings entry does. The passphrase is never stored: it buys a
     * credential scoped to this device — revocable on its own, without changing the passphrase
     * every other device is using — and then it is discarded.
     */
    suspend fun pairWithPassphrase(
        serverUrl: String,
        username: String,
        passphrase: String
    ): Result<String?> {
        val server = normalizeServerUrl(serverUrl)
        if (server == null || username.isBlank() || passphrase.isBlank()) {
            return Result.failure(IOException("Server, username and passphrase are all required"))
        }
        return login.exchange(server, username, passphrase).fold(
            onSuccess = { token -> pairWithToken(server, username, token) },
            onFailure = { Result.failure(it) }
        )
    }

    /**
     * Stores a device token and proves it by registering.
     *
     * Credentials are rolled back when registration fails: a stored-but-unusable server made the
     * app look paired while nothing it sent could ever arrive.
     */
    suspend fun pairWithToken(
        serverUrl: String,
        username: String,
        token: String
    ): Result<String?> {
        val server = normalizeServerUrl(serverUrl)
        if (server == null || username.isBlank() || token.isBlank()) {
            return Result.failure(IOException("Server, username and a token are all required"))
        }
        // Nothing worth keeping is only true when a *complete* pairing was already stored. A
        // half-written one — a server with no username, which no longer counts as configured — is
        // not a state to fall back to, so it is cleared like a first attempt.
        val hadPairing = secureStorage.agroServerUrl.isNotBlank() &&
            secureStorage.agroUsername.isNotBlank()
        // The device id is no longer derived here: `SecureStorage.agroDeviceId` generates one on
        // first use and keeps it. Deriving it from `Build.MODEL` gave two of the same phone the
        // same identity, which a per-device library index cannot survive.
        secureStorage.setAgroCredentials(server, username, token)
        return registerNode().onFailure { if (!hadPairing) secureStorage.clearAgroCredentials() }
    }

    /**
     * A bare hostname (`agro.example.com`, as it comes off a reverse proxy) is the common case, so
     * it gets `https://` rather than being rejected. An explicit scheme is always respected, which
     * is what keeps a plain-HTTP host on the LAN working.
     */
    private fun normalizeServerUrl(input: String): String? {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isBlank()) return null
        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val uri = runCatching { android.net.Uri.parse(withScheme) }.getOrNull() ?: return null
        if (uri.host.isNullOrBlank()) return null
        if (uri.scheme != "http" && uri.scheme != "https") return null
        return withScheme
    }
}

/** Who the stored device token resolves to, as the server sees it. */
internal data class AgroIdentity(val username: String, val role: String) {
    val isAdmin: Boolean get() = role.equals("admin", ignoreCase = true)
}
