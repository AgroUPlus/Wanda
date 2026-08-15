package com.wander.android.data.sources.agro

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
    private val graphQl: AgroGraphQl
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
                positionMs isPlaying deviceId updatedAt queueIndex
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
            syncedSettings(userId: ${'$'}userId) { serverUrl serverUsername }
        }
        """.trimIndent(),
        buildJsonObject { put("userId", graphQl.userId) }
    ).map { data ->
        (data["syncedSettings"] as? JsonObject)?.let {
            AgroSyncedSettings(
                serverUrl = it.string("serverUrl"),
                serverUsername = it.string("serverUsername")
            )
        }
    }

    /**
     * Only the Navidrome address travels. The token is a credential, and `SyncedSettingsInput` has
     * nowhere to put one anyway — the other device still signs in for itself.
     */
    suspend fun pushSyncedSettings(serverUrl: String, serverUsername: String): Result<Unit> =
        graphQl.execute(
            """
            mutation UpdateSyncedSettings(${'$'}input: SyncedSettingsInput!) {
                updateSyncedSettings(input: ${'$'}input) { updatedAt }
            }
            """.trimIndent(),
            buildJsonObject {
                put("input", buildJsonObject {
                    put("userId", graphQl.userId)
                    put("serverUrl", serverUrl)
                    put("serverUsername", serverUsername)
                })
            }
        ).map { }

    private fun JsonObject.toNode() = AgroNode(
        deviceId = string("deviceId").orEmpty(),
        petname = string("petname").orEmpty(),
        clientType = string("clientType").orEmpty(),
        currentTrack = string("currentTrack"),
        isOnline = this["isOnline"]?.jsonPrimitive?.booleanOrNull == true
    )

    private fun JsonObject.toHandoff() = AgroHandoffState(
        trackUri = string("trackUri").orEmpty(),
        trackTitle = string("trackTitle").orEmpty(),
        artistName = string("artistName").orEmpty(),
        albumName = string("albumName"),
        artworkUrl = string("artworkUrl"),
        positionMs = this["positionMs"]?.jsonPrimitive?.longOrNull ?: 0L,
        isPlaying = this["isPlaying"]?.jsonPrimitive?.booleanOrNull == true,
        deviceId = string("deviceId").orEmpty(),
        updatedAt = string("updatedAt").orEmpty(),
        queue = this["queue"]?.jsonArray.orEmpty().map { it.jsonObject.toQueueTrack() },
        queueIndex = this["queueIndex"]?.jsonPrimitive?.intOrNull ?: -1
    )

    private fun JsonObject.toQueueTrack() = AgroHandoffTrack(
        trackUri = string("trackUri").orEmpty(),
        trackTitle = string("trackTitle").orEmpty(),
        artistName = string("artistName").orEmpty(),
        albumName = string("albumName"),
        artworkUrl = string("artworkUrl")
    )

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}
