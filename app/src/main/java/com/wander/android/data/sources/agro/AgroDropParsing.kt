package com.wander.android.data.sources.agro

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Drop, feed and recap payloads, turned into models.
 *
 * Kept beside `AgroSocialParsing` and for the same reason: the drops API and the live-socket
 * handler both read a drop, so there is one definition of what a drop looks like rather than two
 * that can disagree.
 */

/** The fields every drop query selects. Written once so the queries cannot drift apart. */
internal const val DROP_FIELDS =
    "id fromUser toUser trackTitle artistName albumName artworkUrl " +
        "contentHash trackUri note createdAt readAt archived reaction"

internal const val FEED_FIELDS = "username at kind summary artist title count"

internal fun JsonObject.toDrop(): AgroDrop = AgroDrop(
    id = str("id").orEmpty(),
    fromUser = str("fromUser").orEmpty(),
    toUser = str("toUser").orEmpty(),
    trackTitle = str("trackTitle").orEmpty(),
    artistName = str("artistName").orEmpty(),
    albumName = str("albumName"),
    artworkUrl = str("artworkUrl"),
    contentHash = str("contentHash"),
    trackUri = str("trackUri"),
    note = str("note"),
    createdAt = str("createdAt").orEmpty(),
    readAt = str("readAt"),
    archived = bool("archived"),
    reaction = str("reaction")
)

/**
 * A drop as it arrives over the socket.
 *
 * The push frame is flatter than the query: it names the sender as `from` and carries no `toUser`
 * or `archived`, because it is only ever sent to the recipient and a just-created drop is neither
 * read nor archived. Filled in here rather than at the call site so the rest of the app only ever
 * handles one shape.
 */
internal fun JsonObject.toPushedDrop(recipient: String): AgroDrop = AgroDrop(
    id = str("id").orEmpty(),
    fromUser = str("from").orEmpty(),
    toUser = recipient,
    trackTitle = str("trackTitle").orEmpty(),
    artistName = str("artistName").orEmpty(),
    albumName = str("albumName"),
    artworkUrl = str("artworkUrl"),
    contentHash = str("contentHash"),
    trackUri = str("trackUri"),
    note = str("note"),
    createdAt = str("createdAt").orEmpty(),
    readAt = null,
    archived = false
)

internal fun JsonObject.toFeedItem(): AgroFeedItem = AgroFeedItem(
    username = str("username").orEmpty(),
    at = str("at").orEmpty(),
    kind = str("kind").orEmpty(),
    summary = str("summary").orEmpty(),
    artist = str("artist").orEmpty(),
    title = str("title"),
    count = long("count")
)

internal fun JsonObject.toRecap(): AgroRecap = AgroRecap(
    period = str("period").orEmpty(),
    members = strings("members"),
    anthem = obj("anthem")?.toAnthem(),
    topTracks = entries("topTracks"),
    topArtists = entries("topArtists"),
    trendsetter = obj("trendsetter")?.toTrendsetter(),
    matrix = objects("matrix").map { it.toMatrixEntry() }
)

private fun JsonObject.toAnthem(): AgroAnthem = AgroAnthem(
    title = str("title").orEmpty(),
    artist = str("artist").orEmpty(),
    plays = long("plays"),
    byMember = entries("byMember")
)

private fun JsonObject.toTrendsetter(): AgroTrendsetter = AgroTrendsetter(
    username = str("username").orEmpty(),
    firsts = long("firsts"),
    examples = strings("examples")
)

private fun JsonObject.toMatrixEntry(): AgroTasteMatrixEntry = AgroTasteMatrixEntry(
    a = str("a").orEmpty(),
    b = str("b").orEmpty(),
    score = long("score").toInt()
)

/** A list of plain strings, tolerating anything that is not one. */
private fun JsonObject.strings(key: String): List<String> =
    this[key]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
