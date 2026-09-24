package com.wander.android.data.repository

import com.wander.android.core.security.IdentityKeyManager
import com.wander.android.data.sources.agro.AgroFriendNowPlaying
import com.wander.android.data.sources.agro.AgroJamNowPlaying
import com.wander.android.data.sources.agro.AgroLiveMessage
import com.wander.android.data.sources.agro.FriendEvent
import com.wander.android.data.sources.agro.openIfSealed
import com.wander.android.data.sources.agro.toPushedDrop
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Parses incoming live WebSocket message frames from an Agro server.
 */
internal object AgroLiveMessageParser {

    fun isAuthSuccess(text: String): Boolean = msgType(text) == "AUTH_SUCCESS"

    fun needsResync(text: String): Boolean {
        if (msgType(text) != "RESUMED") return false
        val payload = runCatching {
            Json.parseToJsonElement(text).jsonObject["payload"]?.jsonObject
        }.getOrNull()
        return payload?.get("resync_required")?.jsonPrimitive?.booleanOrNull == true
    }

    fun sequenceOf(text: String): Long? = runCatching {
        Json.parseToJsonElement(text).jsonObject["seq"]?.jsonPrimitive?.longOrNull
    }.getOrNull()

    private fun msgType(text: String): String? = runCatching {
        Json.parseToJsonElement(text).jsonObject["msg_type"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    fun resumeFrame(after: Long): String = buildJsonObject {
        put("msg_type", "RESUME")
        put("payload", buildJsonObject { put("last_seq", after) })
    }.toString()

    fun parse(
        text: String,
        userId: String,
        identityKeyManager: IdentityKeyManager
    ): AgroLiveMessage? {
        val envelope = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return null
        return when (envelope["msg_type"]?.jsonPrimitive?.contentOrNull) {
            "HANDOFF", "NODE_UPDATE" -> AgroLiveMessage.Session
            "SYNC_OFFER", "LIBRARY_UPDATED" -> {
                val payload = envelope["payload"] as? JsonObject
                AgroLiveMessage.Library(
                    newTrackCount = payload?.get("count")?.jsonPrimitive?.intOrNull ?: 0,
                    albums = payload?.get("albums")?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                        .orEmpty()
                )
            }
            "FRIEND_PRESENCE" -> {
                val payload = envelope["payload"] as? JsonObject
                val who = payload?.get("username")?.jsonPrimitive?.contentOrNull
                val sealedCopies = (payload?.get("encryptedPresence") as? JsonArray).orEmpty()
                    .mapNotNull { it.jsonObject["ciphertext"]?.jsonPrimitive?.contentOrNull }
                AgroLiveMessage.Friends(
                    presence = who?.let {
                        AgroFriendNowPlaying(
                            username = it,
                            trackUri = "",
                            trackTitle = payload["trackTitle"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            artistName = payload["artistName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            albumName = payload["albumName"]?.jsonPrimitive?.contentOrNull,
                            artworkUrl = payload["artworkUrl"]?.jsonPrimitive?.contentOrNull,
                            positionMs = 0L,
                            isPlaying = payload["isPlaying"]?.jsonPrimitive?.booleanOrNull ?: false,
                            updatedAt = payload["updatedAt"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        ).openIfSealed(identityKeyManager, sealedCopies)
                    }
                )
            }
            "FRIEND_REQUEST" -> {
                val payload = envelope["payload"] as? JsonObject
                val from = payload?.get("from")?.jsonPrimitive?.contentOrNull
                val acceptedBy = payload?.get("accepted_by")?.jsonPrimitive?.contentOrNull
                val declinedBy = payload?.get("declined_by")?.jsonPrimitive?.contentOrNull
                AgroLiveMessage.Friends(
                    when {
                        acceptedBy != null -> FriendEvent.Accepted(acceptedBy)
                        declinedBy != null -> FriendEvent.Declined(declinedBy)
                        from != null -> FriendEvent.Requested(from)
                        else -> null
                    }
                )
            }
            "JAM_UPDATED" -> AgroLiveMessage.JamUpdated
            "JAM_NOW_PLAYING" -> {
                val payload = envelope["payload"] as? JsonObject
                val stopped = payload?.get("stopped")?.jsonPrimitive?.booleanOrNull ?: false
                val trackId = payload?.get("trackId")?.jsonPrimitive?.contentOrNull
                AgroLiveMessage.JamNowPlayingFrame(
                    if (stopped || trackId == null) null else AgroJamNowPlaying(
                        trackId = trackId,
                        title = payload["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        artist = payload["artist"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        artworkUrl = payload["artworkUrl"]?.jsonPrimitive?.contentOrNull,
                        durationMs = payload["durationMs"]?.jsonPrimitive?.longOrNull ?: 0L,
                        positionMs = payload["positionMs"]?.jsonPrimitive?.longOrNull ?: 0L,
                        addedBy = payload["addedBy"]?.jsonPrimitive?.contentOrNull,
                        deviceId = payload["deviceId"]?.jsonPrimitive?.contentOrNull,
                        contentHash = payload["contentHash"]?.jsonPrimitive?.contentOrNull,
                        peerLanAddress = payload["peerLanAddress"]?.jsonPrimitive?.contentOrNull,
                        peerLanToken = payload["peerLanToken"]?.jsonPrimitive?.contentOrNull
                    )
                )
            }
            "LISTEN_ALONG" -> {
                val payload = envelope["payload"] as? JsonObject
                val host = payload?.get("host")?.jsonPrimitive?.contentOrNull.orEmpty()
                val sealedCopies = (payload?.get("encryptedPresence") as? JsonArray).orEmpty()
                    .mapNotNull { it.jsonObject["ciphertext"]?.jsonPrimitive?.contentOrNull }
                val opened = AgroFriendNowPlaying(
                    username = host,
                    trackUri = payload?.get("trackUri")?.jsonPrimitive?.contentOrNull.orEmpty(),
                    trackTitle = payload?.get("trackTitle")?.jsonPrimitive?.contentOrNull.orEmpty(),
                    artistName = payload?.get("artistName")?.jsonPrimitive?.contentOrNull.orEmpty(),
                    albumName = payload?.get("albumName")?.jsonPrimitive?.contentOrNull,
                    artworkUrl = payload?.get("artworkUrl")?.jsonPrimitive?.contentOrNull,
                    positionMs = payload?.get("positionMs")?.jsonPrimitive?.longOrNull ?: 0L,
                    isPlaying = payload?.get("isPlaying")?.jsonPrimitive?.booleanOrNull ?: false,
                    updatedAt = "",
                    deviceId = payload?.get("deviceId")?.jsonPrimitive?.contentOrNull,
                    contentHash = payload?.get("contentHash")?.jsonPrimitive?.contentOrNull
                ).openIfSealed(identityKeyManager, sealedCopies)
                AgroLiveMessage.ListenAlong(
                    host = host,
                    trackUri = opened.trackUri,
                    trackTitle = opened.trackTitle,
                    artistName = opened.artistName,
                    albumName = opened.albumName,
                    artworkUrl = opened.artworkUrl,
                    positionMs = opened.positionMs,
                    isPlaying = opened.isPlaying,
                    stopped = payload?.get("stopped")?.jsonPrimitive?.booleanOrNull ?: false,
                    deviceId = opened.deviceId,
                    contentHash = opened.contentHash,
                    peerLanAddress = payload?.get("peerLanAddress")?.jsonPrimitive?.contentOrNull,
                    peerLanToken = payload?.get("peerLanToken")?.jsonPrimitive?.contentOrNull,
                    isLocked = opened.isLocked
                )
            }
            "TRACK_DROP" -> (envelope["payload"] as? JsonObject)?.let { payload ->
                AgroLiveMessage.TrackDrop(payload.toPushedDrop(recipient = userId))
            }
            "SETTINGS_SYNC" -> AgroLiveMessage.Settings
            "P2P_GRANT" -> {
                val payload = envelope["payload"] as? JsonObject
                val token = payload?.get("token")?.jsonPrimitive?.contentOrNull
                val forUser = payload?.get("forUser")?.jsonPrimitive?.contentOrNull
                if (token != null && forUser != null) {
                    AgroLiveMessage.P2PGrant(
                        token = token,
                        forUser = forUser,
                        forKeys = (payload["forKeys"] as? JsonArray)
                            .orEmpty()
                            .mapNotNull { it.jsonPrimitive.contentOrNull }
                            .filter { it.isNotBlank() },
                        ttlSeconds = payload["ttlSeconds"]?.jsonPrimitive?.longOrNull ?: 600L
                    )
                } else null
            }
            "RELAY_REQUEST" -> {
                val payload = envelope["payload"] as? JsonObject
                val sessionId = payload?.get("sessionId")?.jsonPrimitive?.contentOrNull
                val contentHash = payload?.get("contentHash")?.jsonPrimitive?.contentOrNull
                val toDevice = payload?.get("toDevice")?.jsonPrimitive?.contentOrNull.orEmpty()
                val listenerKey = payload?.get("listenerPublicKey")?.jsonPrimitive?.contentOrNull
                if (sessionId != null && contentHash != null) {
                    AgroLiveMessage.RelayRequest(sessionId, contentHash, toDevice, listenerKey)
                } else null
            }
            else -> null
        }
    }
}
