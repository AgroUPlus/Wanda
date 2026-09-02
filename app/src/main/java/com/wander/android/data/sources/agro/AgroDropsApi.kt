package com.wander.android.data.sources.agro

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
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
    private val identityKeyManager: com.wander.android.core.security.IdentityKeyManager,
    private val secureStorage: com.wander.android.core.security.SecureStorage
) {
    suspend fun inbox(limit: Int = 100): Result<List<AgroDrop>> = graphQl.execute(
        """
        query Inbox(${'$'}limit: Int) { inbox(limit: ${'$'}limit) { $DROP_FIELDS } }
        """.trimIndent(),
        buildJsonObject { put("limit", limit) }
    ).map { data ->
        (data["inbox"] as? JsonArray).orEmpty().map {
            it.jsonObject.toDrop().decryptIfNeeded(identityKeyManager)
        }
    }

    suspend fun sent(limit: Int = 100): Result<List<AgroDrop>> = graphQl.execute(
        """
        query SentDrops(${'$'}limit: Int) { sentDrops(limit: ${'$'}limit) { $DROP_FIELDS } }
        """.trimIndent(),
        buildJsonObject { put("limit", limit) }
    ).map { data ->
        (data["sentDrops"] as? JsonArray).orEmpty().map {
            it.jsonObject.toDrop().decryptIfNeeded(identityKeyManager)
        }
    }

    suspend fun unreadCount(): Result<Long> = graphQl.execute(
        "{ unreadDropCount }",
        buildJsonObject { }
    ).map { data -> data["unreadDropCount"]?.jsonPrimitive?.longOrNull ?: 0L }

    /**
     * Hands a track to a friend, sealing the note to every device that should be able to read it.
     *
     * [recipientKeys] is the recipient's published device keys — one entry per phone, tablet or
     * desktop they have signed in on. Sealing to only one of them is what used to break the
     * account's other devices; sealing to none of them is what used to make a sent note unopenable
     * by its own sender.
     *
     * This device is added to that list before sealing, so the sender keeps a copy it can open.
     * `noteCiphertext` is still sent, holding the recipient's copy, because a server or a client
     * that predates the list reads that field and nothing else.
     *
     * An empty [recipientKeys] means the recipient has published no key at all, and the note goes
     * in clear — the same fallback as before. Refusing to send would be the wrong call: the user
     * wrote a message, and the alternative to a plaintext note is no note.
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
        recipientKeys: List<AgroDeviceKey> = emptyList()
    ): Result<AgroDrop> {
        val trimmedNote = note?.trim()?.takeIf { it.isNotEmpty() }
        val sealedCopies = if (trimmedNote != null && recipientKeys.isNotEmpty()) {
            sealFor(recipientKeys, trimmedNote)
        } else {
            emptyList()
        }
        val isEncrypted = sealedCopies.isNotEmpty()
        val plainNote = if (isEncrypted) null else trimmedNote

        // The recipient's own copy, duplicated into the legacy field. Their device ids are the ones
        // in `recipientKeys`; anything else in the list is this device's own copy.
        val recipientDeviceIds = recipientKeys.map { it.deviceId }.toSet()
        val sealedCiphertext = sealedCopies
            .firstOrNull { it.deviceId in recipientDeviceIds }
            ?.ciphertext

        return graphQl.execute(
            """
            mutation Drop(
                ${'$'}to: String!, ${'$'}trackTitle: String!, ${'$'}artistName: String!,
                ${'$'}albumName: String, ${'$'}artworkUrl: String, ${'$'}contentHash: String,
                ${'$'}trackUri: String, ${'$'}note: String, ${'$'}noteCiphertext: String,
                ${'$'}noteCiphertexts: [DeviceCiphertextInput!], ${'$'}isEncrypted: Boolean
            ) {
                dropTrack(
                    to: ${'$'}to, trackTitle: ${'$'}trackTitle, artistName: ${'$'}artistName,
                    albumName: ${'$'}albumName, artworkUrl: ${'$'}artworkUrl,
                    contentHash: ${'$'}contentHash, trackUri: ${'$'}trackUri,
                    note: ${'$'}note, noteCiphertext: ${'$'}noteCiphertext,
                    noteCiphertexts: ${'$'}noteCiphertexts, isEncrypted: ${'$'}isEncrypted
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
                put("noteCiphertexts", buildJsonArray {
                    sealedCopies.forEach { copy ->
                        add(buildJsonObject {
                            put("deviceId", copy.deviceId)
                            put("ciphertext", copy.ciphertext)
                        })
                    }
                })
                put("isEncrypted", isEncrypted)
            }
        ).mapCatching { data ->
            data["dropTrack"]?.jsonObject?.toDrop()?.let { it.decryptIfNeeded(identityKeyManager) }
                ?: error("the server accepted the drop but did not describe it")
        }
    }

    /**
     * Seals [note] for the recipient's devices and for this one.
     *
     * This device is added last and under its own id, so a recipient device that happens to share
     * an id with it — they are client-chosen strings, and nothing stops two accounts picking the
     * same one — is not overwritten by our own copy.
     */
    private fun sealFor(recipientKeys: List<AgroDeviceKey>, note: String): List<AgroSealedNote> {
        val keysByDevice = LinkedHashMap<String, String>()
        recipientKeys.forEach { key ->
            if (key.deviceId.isNotBlank() && key.publicKey.isNotBlank()) {
                keysByDevice[key.deviceId] = key.publicKey
            }
        }
        val ownDeviceId = secureStorage.agroDeviceId
        if (ownDeviceId !in keysByDevice) {
            keysByDevice[ownDeviceId] = identityKeyManager.getPublicKeyBase64()
        }
        return identityKeyManager.sealNoteToKeys(keysByDevice, note)
            .map { (deviceId, ciphertext) -> AgroSealedNote(deviceId, ciphertext) }
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
                it.jsonObject.toDrop().decryptIfNeeded(identityKeyManager)
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
