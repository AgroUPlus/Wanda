package com.wander.android.data.sources.agro

import com.wander.android.core.security.AgroVault
import com.wander.android.core.security.SecureStorage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The read side of Agro: who else is listening, what the current session is, and the portable
 * settings. [AgroClient] only ever writes, which is why none of this reached the app before.
 */
@Singleton
class AgroSessionApi @Inject constructor(
    private val graphQl: AgroGraphQl,
    private val secureStorage: SecureStorage
) {

    suspend fun activeNodes(): Result<List<AgroNode>> = graphQl.execute(
        """
        query ActiveNodes(${'$'}userId: String!) {
            activeNodes(userId: ${'$'}userId) {
                deviceId petname clientType currentTrack isOnline
            }
        }
        """.trimIndent(),
        buildJsonObject { put("userId", graphQl.userId) }
    ).map { data ->
        data["activeNodes"]?.jsonArray.orEmpty().map { it.jsonObject.toNode() }
    }

    suspend fun playbackHandoff(): Result<AgroHandoffState?> = graphQl.execute(
        """
        query PlaybackHandoff(${'$'}userId: String!) {
            playbackHandoff(userId: ${'$'}userId) {
                trackUri trackTitle artistName albumName artworkUrl
                positionMs isPlaying deviceId updatedAt queueIndex encryptedPayload
                queue { trackUri trackTitle artistName albumName artworkUrl }
            }
        }
        """.trimIndent(),
        buildJsonObject { put("userId", graphQl.userId) }
    ).map { data ->
        // Null is a normal answer here — it just means nothing has played yet.
        (data["playbackHandoff"] as? JsonObject)?.toHandoff()
    }

    suspend fun syncedSettings(): Result<AgroSyncedSettings?> = graphQl.execute(
        """
        query SyncedSettings(${'$'}userId: String!) {
            syncedSettings(userId: ${'$'}userId) {
                settingsBlob hasServerUrl shareDomain shareHosts shareEnabled
            }
        }
        """.trimIndent(),
        buildJsonObject { put("userId", graphQl.userId) }
    ).map { data ->
        (data["syncedSettings"] as? JsonObject)?.let { obj ->
            val blob = obj.string("settingsBlob")
            var serverUrl: String? = null
            var serverUsername: String? = null

            if (!blob.isNullOrBlank()) {
                val vaultKey = secureStorage.agroVaultKey
                if (vaultKey != null) {
                    runCatching {
                        val plaintext = AgroVault.openSettings(blob, vaultKey)
                        val parsed = Json.parseToJsonElement(plaintext).jsonObject
                        serverUrl = parsed["server_url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                            ?: parsed["serverUrl"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                        serverUsername = parsed["server_username"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                            ?: parsed["serverUsername"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    }
                }
            }

            AgroSyncedSettings(
                serverUrl = serverUrl,
                serverUsername = serverUsername,
                shareDomain = obj.string("shareDomain"),
                shareHosts = obj.string("shareHosts"),
                shareEnabled = obj.bool("shareEnabled")
            )
        }
    }

    /**
     * Only the Navidrome address travels. The token is a credential, and `SyncedSettingsInput` has
     * nowhere to put one anyway — the other device still signs in for itself.
     * Settings are sealed client-side under the account's vault key before sending.
     */
    suspend fun pushSyncedSettings(serverUrl: String, serverUsername: String): Result<Unit> {
        val vaultKey = secureStorage.agroVaultKey
            ?: return Result.failure(IllegalStateException("No vault key available to seal settings"))

        val plaintextJson = buildJsonObject {
            put("server_url", serverUrl)
            put("server_username", serverUsername)
        }.toString()

        val sealedBlob = AgroVault.sealSettings(plaintextJson, vaultKey)

        return graphQl.execute(
            """
            mutation UpdateSyncedSettings(${'$'}input: SyncedSettingsInput!) {
                updateSyncedSettings(input: ${'$'}input) { updatedAt }
            }
            """.trimIndent(),
            buildJsonObject {
                put("input", buildJsonObject {
                    put("userId", graphQl.userId)
                    put("settingsBlob", sealedBlob)
                    put("hasServerUrl", serverUrl.isNotBlank())
                })
            }
        ).map { }
    }

    /** Enrols the account's vault key envelope with the Agro server. */
    suspend fun enrolVaultKey(
        userId: String = graphQl.userId,
        vaultSalt: String,
        vaultKeyWrapped: String
    ): Result<Boolean> = graphQl.execute(
        """
        mutation EnrolVaultKey(${'$'}userId: String!, ${'$'}vaultSalt: String!, ${'$'}vaultKeyWrapped: String!) {
            enrolVaultKey(userId: ${'$'}userId, vaultSalt: ${'$'}vaultSalt, vaultKeyWrapped: ${'$'}vaultKeyWrapped)
        }
        """.trimIndent(),
        buildJsonObject {
            put("userId", userId)
            put("vaultSalt", vaultSalt)
            put("vaultKeyWrapped", vaultKeyWrapped)
        }
    ).map { data -> data["enrolVaultKey"]?.jsonPrimitive?.booleanOrNull ?: false }

    /** Unregisters a device node from the Agro server. */
    suspend fun unregisterNode(deviceId: String = graphQl.deviceId): Result<Unit> =
        graphQl.execute(
            """
            mutation UnregisterNode(${'$'}userId: String!, ${'$'}deviceId: String!) {
                unregisterNode(userId: ${'$'}userId, deviceId: ${'$'}deviceId)
            }
            """.trimIndent(),
            buildJsonObject {
                put("userId", graphQl.userId)
                put("deviceId", deviceId)
            }
        ).map { }

    private fun JsonObject.toNode() = AgroNode(
        deviceId = string("deviceId").orEmpty(),
        petname = string("petname").orEmpty(),
        clientType = string("clientType").orEmpty(),
        currentTrack = string("currentTrack"),
        isOnline = this["isOnline"]?.jsonPrimitive?.booleanOrNull == true
    )

    private fun JsonObject.toHandoff(): AgroHandoffState {
        // A sealed session carries placeholders in the plaintext fields; the real metadata is in
        // an envelope only a device with the account's vault key can open. Opened here so the rest
        // of the app — the resume card, "same track as playing" — sees a normal handoff.
        val sealed = string("encryptedPayload")?.let { openSealedHandoff(it) }
        return AgroHandoffState(
            trackUri = sealed?.string("trackUri") ?: string("trackUri").orEmpty(),
            trackTitle = sealed?.string("trackTitle") ?: string("trackTitle").orEmpty(),
            artistName = sealed?.string("artistName") ?: string("artistName").orEmpty(),
            albumName = sealed?.string("albumName") ?: string("albumName"),
            artworkUrl = sealed?.string("artworkUrl") ?: string("artworkUrl"),
            positionMs = this["positionMs"]?.jsonPrimitive?.longOrNull ?: 0L,
            isPlaying = this["isPlaying"]?.jsonPrimitive?.booleanOrNull == true,
            deviceId = string("deviceId").orEmpty(),
            updatedAt = string("updatedAt").orEmpty(),
            queue = this["queue"]?.jsonArray.orEmpty().map { it.jsonObject.toQueueTrack() },
            queueIndex = this["queueIndex"]?.jsonPrimitive?.intOrNull ?: -1
        )
    }

    /** Opens a sealed handoff envelope with the account's handoff subkey, or null if it cannot. */
    private fun openSealedHandoff(sealed: String): JsonObject? {
        val key = secureStorage.agroVaultKey?.let { AgroVault.getPresenceKey(it) } ?: return null
        return runCatching {
            val plaintext = AgroVault.openPayload(sealed, key, "handoff").toString(Charsets.UTF_8)
            Json.parseToJsonElement(plaintext).jsonObject
        }.getOrNull()
    }

    private fun JsonObject.toQueueTrack() = AgroHandoffTrack(
        trackUri = string("trackUri").orEmpty(),
        trackTitle = string("trackTitle").orEmpty(),
        artistName = string("artistName").orEmpty(),
        albumName = string("albumName"),
        artworkUrl = string("artworkUrl")
    )

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    /** Absent reads as false: a server too old to know the field has the feature switched off. */
    private fun JsonObject.bool(key: String): Boolean =
        this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
}
