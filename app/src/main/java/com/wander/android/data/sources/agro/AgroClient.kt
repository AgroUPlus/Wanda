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
    private val graphQl: AgroGraphQl,
    private val secureStorage: SecureStorage
) {
    val isConfigured: Boolean get() = graphQl.isConfigured

    /**
     * Battery-first one-shot registration: called on app launch or pairing only.
     * Never runs in an unconstrained background loop.
     */
    suspend fun registerNode(currentTrack: String? = null): Result<String?> {
        val mutation = """
            mutation RegisterNode(${'$'}userId: String!, ${'$'}deviceId: String!, ${'$'}clientType: String!, ${'$'}deviceName: String, ${'$'}currentTrack: String) {
                registerNode(userId: ${'$'}userId, deviceId: ${'$'}deviceId, clientType: ${'$'}clientType, deviceName: ${'$'}deviceName, currentTrack: ${'$'}currentTrack) {
                    petname
                }
            }
        """.trimIndent()

        val variables = buildJsonObject {
            put("userId", secureStorage.agroUsername)
            put("deviceId", secureStorage.agroDeviceId)
            put("clientType", "wanda")
            secureStorage.agroDevicePetname.ifEmpty { null }?.let { put("deviceName", it) }
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
                put("isPlaying", isPlaying)
                put("deviceId", secureStorage.agroDeviceId)
            })
        }

        return graphQl.execute(mutation, variables).map { }
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

    /** `agro://connect?username=…&passphrase=…&server=…`, as printed by the server's pairing QR. */
    suspend fun parseQrCodePayload(qrString: String): Boolean {
        if (!qrString.startsWith("agro://connect")) return false
        val uri = runCatching { android.net.Uri.parse(qrString) }.getOrNull() ?: return false
        val user = uri.getQueryParameter("username").orEmpty()
        val key = uri.getQueryParameter("passphrase") ?: uri.getQueryParameter("key").orEmpty()
        val server = uri.getQueryParameter("server") ?: return false
        return pair(server, user, key).isSuccess
    }

    /**
     * Stores credentials and proves them by registering. The QR path and the manual Settings entry
     * both land here, so a hostname typed by hand behaves exactly like a scanned one.
     *
     * Credentials are rolled back when registration fails: a stored-but-unusable server made the
     * app look paired while nothing it sent could ever arrive.
     */
    suspend fun pair(serverUrl: String, username: String, passphrase: String): Result<String?> {
        val server = normalizeServerUrl(serverUrl)
        if (server == null || username.isBlank() || passphrase.isBlank()) {
            return Result.failure(IOException("Server, username and passphrase are all required"))
        }
        val previous = secureStorage.agroServerUrl
        // The device id is no longer derived here: `SecureStorage.agroDeviceId` generates one on
        // first use and keeps it. Deriving it from `Build.MODEL` gave two of the same phone the
        // same identity, which a per-device library index cannot survive.
        secureStorage.setAgroCredentials(server, username, passphrase)
        return registerNode().onFailure { if (previous.isBlank()) secureStorage.clearAgroCredentials() }
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
