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
        "contentHash trackUri note noteCiphertext noteCiphertexts { deviceId ciphertext } " +
        "isEncrypted createdAt readAt archived reaction"

internal const val FEED_FIELDS = "username at kind summary artist title count"

internal fun JsonObject.toDrop(): AgroDrop = AgroDrop(
    id = str("id") ?: error("drop has no id"),
    fromUser = str("fromUser") ?: error("drop has no fromUser"),
    toUser = str("toUser") ?: error("drop has no toUser"),
    trackTitle = str("trackTitle") ?: error("drop has no trackTitle"),
    artistName = str("artistName") ?: error("drop has no artistName"),
    albumName = str("albumName"),
    artworkUrl = str("artworkUrl"),
    contentHash = str("contentHash"),
    trackUri = str("trackUri"),
    note = str("note"),
    noteCiphertext = str("noteCiphertext"),
    noteCiphertexts = sealedNotes(),
    isEncrypted = bool("isEncrypted"),
    createdAt = str("createdAt") ?: error("drop has no createdAt"),
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
    id = str("id") ?: error("pushed drop has no id"),
    fromUser = str("from") ?: error("pushed drop has no from"),
    toUser = recipient,
    trackTitle = str("trackTitle") ?: error("pushed drop has no trackTitle"),
    artistName = str("artistName") ?: error("pushed drop has no artistName"),
    albumName = str("albumName"),
    artworkUrl = str("artworkUrl"),
    contentHash = str("contentHash"),
    trackUri = str("trackUri"),
    note = str("note"),
    noteCiphertext = str("noteCiphertext"),
    noteCiphertexts = sealedNotes(),
    isEncrypted = bool("isEncrypted"),
    createdAt = str("createdAt") ?: error("pushed drop has no createdAt"),
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

/**
 * The per-device sealed copies, or empty against a server that does not send them.
 *
 * Absent rather than empty is the case that matters: it means the server predates the list, and
 * the reader has to fall back to the single `noteCiphertext`.
 */
private fun JsonObject.sealedNotes(): List<AgroSealedNote> =
    this["noteCiphertexts"]?.jsonArray.orEmpty().mapNotNull { entry ->
        val obj = entry as? JsonObject ?: return@mapNotNull null
        val deviceId = obj.str("deviceId") ?: return@mapNotNull null
        val ciphertext = obj.str("ciphertext") ?: return@mapNotNull null
        AgroSealedNote(deviceId = deviceId, ciphertext = ciphertext)
    }

/** A list of plain strings, tolerating anything that is not one. */
private fun JsonObject.strings(key: String): List<String> =
    this[key]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }

/**
 * Decrypts the note in-place when the drop is end-to-end encrypted.
 *
 * Called at every point a drop arrives — from the REST endpoints and from the live socket. A
 * single definition here keeps the decryption semantics from drifting between those two paths.
 *
 * A decryption failure returns the drop unchanged (note still null, `isEncrypted` still true).
 * The UI reads `isEncrypted` to decide whether to show the lock icon, so the user can see that
 * a drop arrived but could not be opened rather than silently receiving an empty message.
 */
internal fun AgroDrop.decryptIfNeeded(
    identityKeyManager: com.wander.android.core.security.IdentityKeyManager
): AgroDrop {
    if (!isEncrypted) return this

    // Every copy, this device's own included. A note you *sent* has a copy sealed to you in here
    // and nowhere else — the single `noteCiphertext` is the recipient's, which is why your own
    // half of a conversation used to be unopenable rather than merely unopened.
    val candidates = noteCiphertexts.map { it.ciphertext } + listOfNotNull(noteCiphertext)
    if (candidates.isEmpty()) return this

    val opened = identityKeyManager.openAnyNote(candidates) ?: return this
    return copy(note = opened)
}
