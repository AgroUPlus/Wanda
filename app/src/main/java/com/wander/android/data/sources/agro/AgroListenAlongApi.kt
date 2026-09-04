package com.wander.android.data.sources.agro

import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starting, stopping and reading a listen-along session.
 *
 * Its own class rather than a corner of [AgroFriendsApi] because it is the one part of the social
 * surface that drives playback, and the thing that consumes it — `ListenAlongController` — has no
 * use for the rest of the graph.
 */
@Singleton
internal class AgroListenAlongApi @Inject constructor(
    private val graphQl: AgroGraphQl,
    private val identityKeyManager: com.wander.android.core.security.IdentityKeyManager,
    private val secureStorage: com.wander.android.core.security.SecureStorage
) {
    private val sessionFields = "host listeners nowPlaying { $NOW_PLAYING_FIELDS }"

    /**
     * Tunes in to a friend.
     *
     * Fails when the host is not a friend, or keeps their now-playing private — following someone's
     * playback is strictly more than seeing it, and the server refuses the larger thing whenever
     * the smaller one is closed.
     */
    suspend fun startListenAlong(host: String): Result<AgroListenAlong> = graphQl.execute(
        """
        mutation StartListenAlong(${'$'}host: String!, ${'$'}deviceId: String) {
            startListenAlong(host: ${'$'}host, deviceId: ${'$'}deviceId) { $sessionFields }
        }
        """.trimIndent(),
        buildJsonObject {
            put("host", host.trim().lowercase())
            put("deviceId", secureStorage.agroDeviceId)
        }
    ).mapCatching { data ->
        data["startListenAlong"]!!.jsonObject.toListenAlong().opened()
    }

    suspend fun stopListenAlong(): Result<Boolean> = graphQl.execute(
        "mutation { stopListenAlong }",
        buildJsonObject { }
    ).map { data -> data["stopListenAlong"]?.jsonPrimitive?.booleanOrNull ?: false }

    /** The session this device is in, if any. Used to recover one across a restart. */
    suspend fun listenAlong(): Result<AgroListenAlong?> = graphQl.execute(
        """
        query ListenAlong(${'$'}deviceId: String) {
            listenAlong(deviceId: ${'$'}deviceId) { $sessionFields }
        }
        """.trimIndent(),
        buildJsonObject { put("deviceId", secureStorage.agroDeviceId) }
    ).map { data -> data.obj("listenAlong")?.toListenAlong()?.opened() }

    /**
     * Opens the session's sealed metadata, if it arrived sealed.
     *
     * Following a session needs more of it than watching one does: the title to show, and the
     * content hash to find the file. Both live only inside the envelope once a host seals.
     */
    private fun AgroListenAlong.opened(): AgroListenAlong =
        copy(nowPlaying = nowPlaying?.openIfSealed(identityKeyManager))
}
