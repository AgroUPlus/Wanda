package com.wander.android.data.sources.agro

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Songs handed between friends.
 *
 * Like the rest of the social API, none of these name an account to read. The inbox is the one the
 * token proved, and there is no argument through which another could be asked for.
 *
 * Sending is gated on friendship alone. The server refuses a stranger with exactly the same message
 * it uses for an account that does not exist, so a failure here is never worth interpreting more
 * finely than "that did not go".
 */
@Singleton
internal class AgroDropsApi @Inject constructor(
    private val graphQl: AgroGraphQl,
    private val identityKeyManager: com.wander.android.core.security.IdentityKeyManager
) {
    suspend fun inbox(limit: Int = 100): Result<List<AgroDrop>> = graphQl.execute(
        """
        query Inbox(${'$'}limit: Int) { inbox(limit: ${'$'}limit) { $DROP_FIELDS } }
        """.trimIndent(),
        buildJsonObject { put("limit", limit) }
    ).map { data ->
        (data["inbox"] as? JsonArray).orEmpty().map {
            decryptDropIfNeeded(it.jsonObject.toDrop())
        }
    }

    suspend fun sent(limit: Int = 100): Result<List<AgroDrop>> = graphQl.execute(
        """
        query SentDrops(${'$'}limit: Int) { sentDrops(limit: ${'$'}limit) { $DROP_FIELDS } }
        """.trimIndent(),
        buildJsonObject { put("limit", limit) }
    ).map { data ->
        (data["sentDrops"] as? JsonArray).orEmpty().map {
            decryptDropIfNeeded(it.jsonObject.toDrop())
        }
    }

    suspend fun unreadCount(): Result<Long> = graphQl.execute(
        "{ unreadDropCount }",
        buildJsonObject { }
    ).map { data -> data["unreadDropCount"]?.jsonPrimitive?.longOrNull ?: 0L }

    /**
     * Hands a track to a friend, encrypting notes with recipient's public key if available.
     */
    suspend fun drop(
        to: String,
        trackTitle: String,
        artistName: String,
        albumName: String? = null,
        artworkUrl: String? = null,
        contentHash: String? = null,
        trackUri: String? = null,
        note: String? = null,
        recipientPublicKey: String? = null
    ): Result<AgroDrop> {
        val trimmedNote = note?.trim()?.takeIf { it.isNotEmpty() }
        val (sealedCiphertext, plainNote, isEncrypted) = if (trimmedNote != null) {
            val sealed = if (!recipientPublicKey.isNullOrBlank()) {
                try {
                    identityKeyManager.sealNote(recipientPublicKey, trimmedNote)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
            if (sealed != null) {
                Triple(sealed, null, true)
            } else {
                Triple(null, trimmedNote, false)
            }
        } else {
            Triple(null, null, false)
        }

        return graphQl.execute(
            """
            mutation Drop(
                ${'$'}to: String!, ${'$'}trackTitle: String!, ${'$'}artistName: String!,
                ${'$'}albumName: String, ${'$'}artworkUrl: String, ${'$'}contentHash: String,
                ${'$'}trackUri: String, ${'$'}note: String, ${'$'}noteCiphertext: String, ${'$'}isEncrypted: Boolean
            ) {
                dropTrack(
                    to: ${'$'}to, trackTitle: ${'$'}trackTitle, artistName: ${'$'}artistName,
                    albumName: ${'$'}albumName, artworkUrl: ${'$'}artworkUrl,
                    contentHash: ${'$'}contentHash, trackUri: ${'$'}trackUri,
                    note: ${'$'}note, noteCiphertext: ${'$'}noteCiphertext, isEncrypted: ${'$'}isEncrypted
                ) { $DROP_FIELDS }
            }
            """.trimIndent(),
            buildJsonObject {
                put("to", to.trim().lowercase())
                put("trackTitle", trackTitle)
                put("artistName", artistName)
                put("albumName", albumName)
                put("artworkUrl", artworkUrl)
                put("contentHash", contentHash)
                put("trackUri", trackUri)
                put("note", plainNote)
                put("noteCiphertext", sealedCiphertext)
                put("isEncrypted", isEncrypted)
            }
        ).mapCatching { data ->
            data["dropTrack"]?.jsonObject?.toDrop()?.let { decryptDropIfNeeded(it) }
                ?: error("the server accepted the drop but did not describe it")
        }
    }

    /**
     * Everything exchanged with one person, oldest first, both directions in one list.
     */
    suspend fun conversation(username: String, limit: Int = 200): Result<List<AgroDrop>> =
        graphQl.execute(
            """
            query Conversation(${'$'}with: String!, ${'$'}limit: Int) {
              conversation(with: ${'$'}with, limit: ${'$'}limit) { $DROP_FIELDS }
            }
            """.trimIndent(),
            buildJsonObject {
                put("with", username)
                put("limit", limit)
            }
        ).mapCatching { data ->
            (data["conversation"] as? JsonArray).orEmpty().map {
                decryptDropIfNeeded(it.jsonObject.toDrop())
            }
        }

    private fun decryptDropIfNeeded(drop: AgroDrop): AgroDrop {
        if (!drop.isEncrypted || drop.noteCiphertext.isNullOrBlank()) return drop
        return try {
            val decrypted = identityKeyManager.openNote(drop.noteCiphertext)
            drop.copy(note = decrypted)
        } catch (_: Exception) {
            drop.copy(note = "[Encrypted Note]")
        }
    }

    /**
     * Reacts to a received drop. A null or blank [emoji] clears the reaction, so tapping the same
     * one twice undoes it.
     */
    suspend fun react(id: String, emoji: String?): Result<Boolean> = graphQl.execute(
        """
        mutation React(${'$'}id: String!, ${'$'}emoji: String) {
          reactToDrop(id: ${'$'}id, emoji: ${'$'}emoji)
        }
        """.trimIndent(),
        buildJsonObject {
            put("id", id)
            put("emoji", emoji)
        }
    ).map { data -> data["reactToDrop"]?.jsonPrimitive?.booleanOrNull ?: false }

    suspend fun markRead(id: String): Result<Boolean> = idMutation("markDropRead", id)

    suspend fun archive(id: String): Result<Boolean> = idMutation("archiveDrop", id)

    /** The two id-taking mutations differ only in their name, so they share one body. */
    private suspend fun idMutation(field: String, id: String): Result<Boolean> = graphQl.execute(
        """
        mutation Drop(${'$'}id: String!) { $field(id: ${'$'}id) }
        """.trimIndent(),
        buildJsonObject { put("id", id) }
    ).map { data -> data[field]?.jsonPrimitive?.booleanOrNull ?: false }
}
