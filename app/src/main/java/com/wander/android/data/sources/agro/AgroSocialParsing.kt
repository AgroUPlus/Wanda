package com.wander.android.data.sources.agro

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The GraphQL payloads, turned into models.
 *
 * Kept apart from the two API classes because both of them read profiles and both read presence —
 * a shared parser is the only way those two stay one definition instead of drifting apart.
 */

/** The fields every profile query selects. Written once so the queries cannot disagree. */
internal const val PROFILE_FIELDS =
    "username displayName bio avatarUrl createdAt friendState outgoing " +
        "showNowPlaying showStats discoverable showActivity publicKey"

internal const val NOW_PLAYING_FIELDS =
    "username trackUri trackTitle artistName albumName artworkUrl positionMs isPlaying updatedAt " +
        "deviceId contentHash peerLanAddress peerLanToken encryptedPresence"

internal fun JsonObject.toProfile(): AgroProfile = AgroProfile(
    username = str("username") ?: error("profile has no username"),
    displayName = str("displayName"),
    bio = str("bio"),
    avatarUrl = str("avatarUrl"),
    createdAt = str("createdAt") ?: error("profile has no createdAt"),
    friendState = FriendState.parse(str("friendState")),
    outgoing = bool("outgoing"),
    showNowPlaying = bool("showNowPlaying"),
    showStats = bool("showStats"),
    discoverable = bool("discoverable"),
    showActivity = bool("showActivity"),
    publicKey = str("publicKey")
)

internal fun JsonObject.toNowPlaying(): AgroFriendNowPlaying = AgroFriendNowPlaying(
    username = str("username") ?: error("nowPlaying has no username"),
    trackUri = str("trackUri") ?: error("nowPlaying has no trackUri"),
    trackTitle = str("trackTitle") ?: error("nowPlaying has no trackTitle"),
    artistName = str("artistName") ?: error("nowPlaying has no artistName"),
    albumName = str("albumName"),
    artworkUrl = str("artworkUrl"),
    positionMs = long("positionMs"),
    isPlaying = bool("isPlaying"),
    updatedAt = str("updatedAt") ?: error("nowPlaying has no updatedAt"),
    deviceId = str("deviceId"),
    contentHash = str("contentHash"),
    peerLanAddress = str("peerLanAddress"),
    peerLanToken = str("peerLanToken"),
    encryptedPresence = str("encryptedPresence")
)

internal fun JsonObject.toFriend(): AgroFriend = AgroFriend(
    profile = obj("profile")?.toProfile() ?: toProfile(),
    nowPlaying = obj("nowPlaying")?.toNowPlaying()
)

internal fun JsonObject.toListenAlong(): AgroListenAlong = AgroListenAlong(
    host = str("host").orEmpty(),
    listeners = (this["listeners"] as? JsonArray)
        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
        .orEmpty(),
    nowPlaying = obj("nowPlaying")?.toNowPlaying()
)
