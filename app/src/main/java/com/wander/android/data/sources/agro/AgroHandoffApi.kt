package com.wander.android.data.sources.agro

import com.wander.android.core.security.AgroVault
import com.wander.android.core.security.SecureStorage
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishing what this device is playing, and taking it back down again.
 *
 * Split out of [AgroClient], which is pairing and identity. This is the one part of that class
 * that has a second audience: a handoff is read by this account's other devices *and*, through the
 * social feed, by friends — and the two read it through different keys. That distinction is the
 * whole content of this file.
 */
@Singleton
internal class AgroHandoffApi @Inject constructor(
    private val graphQl: AgroGraphQl,
    private val secureStorage: SecureStorage
) {

    /**
     * Event-driven handoff: called only on Media3 playback transitions, never on timers.
     * See [AgroHandoffPublisher], which is what decides an event is worth sending.
     */
    internal suspend fun sendHandoffState(
        trackUri: String,
        title: String,
        artist: String,
        album: String?,
        artworkUrl: String?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
        contentHash: String? = null,
        presenceCopies: List<AgroPresenceCopy>? = null
    ): Result<Unit> {
        val mutation = """
            mutation UpdateHandoff(${'$'}input: HandoffInput!) {
                updateHandoff(input: ${'$'}input)
            }
        """.trimIndent()

        // When a vault key is enrolled, the real metadata travels only inside an authenticated
        // envelope the server cannot open, and the plaintext fields below are reduced to a
        // placeholder. Another of this account's devices unseals it with the same subkey; the
        // server and its database see nothing but ciphertext, a position and a play/pause flag.
        val handoffKey = secureStorage.agroVaultKey?.let { AgroVault.getPresenceKey(it) }
        val encryptedPayload = handoffKey?.let { key ->
            // Wiped rather than left for the collector. The subkey is derived fresh on every call
            // and has no other holder, so its lifetime can be exactly this block — which is the
            // only part of it this code controls. The root key lives in `SecureStorage` and stays.
            try {
                runCatching {
                    AgroVault.sealPayload(
                        sealedMetadata(trackUri, title, artist, album, artworkUrl, contentHash),
                        key
                    )
                }.getOrNull()
            } finally {
                AgroVault.wipe(key)
            }
        }
        val private = encryptedPayload != null

        val variables = buildJsonObject {
            put("input", buildJsonObject {
                put("userId", secureStorage.agroUsername)
                // Suppressed under a sealed session: sending the real values here would defeat the
                // envelope. `HandoffInput` requires these three, so they carry a placeholder.
                put("trackUri", if (private) "encrypted" else trackUri)
                put("trackTitle", if (private) PRIVATE_SESSION_TITLE else title)
                put("artistName", if (private) "" else artist)
                if (!private) {
                    album?.let { put("albumName", it) }
                    // Optional in `HandoffInput`, but it is what lets the receiving client show the
                    // right cover without looking the track up again.
                    artworkUrl?.let { put("artworkUrl", it) }
                }
                put("positionMs", positionMs)
                // What the position is measured against. Without it anything rendering this
                // session can only show an elapsed count — a progress bar needs both ends.
                put("durationMs", durationMs)
                put("isPlaying", isPlaying)
                put("deviceId", secureStorage.agroDeviceId)
                // Only sent when this device actually has the file and has hashed it, and never
                // under a sealed session: the hash identifies the track as surely as its name.
                if (!private) {
                    contentHash?.takeIf { it.isNotBlank() }?.let { put("contentHash", it) }
                }
                encryptedPayload?.let { put("encryptedPayload", it) }
                // Omitted entirely on a heartbeat, which is not the same as sending none. The
                // server leaves the stored copies alone when the field is absent and clears them
                // when it is present and empty, so a heartbeat does not re-seal metadata that has
                // not changed and the end of a session does not leave a stale envelope behind.
                presenceCopies?.let { copies ->
                    put("presenceCiphertexts", buildJsonArray {
                        copies.forEach { copy ->
                            add(buildJsonObject {
                                put("recipientUserId", copy.recipientUserId)
                                put("recipientDeviceId", copy.recipientDeviceId)
                                put("ciphertext", copy.ciphertext)
                            })
                        }
                    })
                }
            })
        }

        return graphQl.execute(mutation, variables).discardPayload()
    }

    /**
     * Drops the sealed copies this device published, leaving the handoff row itself alone.
     *
     * Sent when a session ends. The row is durable on purpose — it is what "resume where you left
     * off" reads hours later — but the copies sealed to friends describe a track that has stopped,
     * and the feed showing them has already moved on. An empty set is the instruction to clear.
     */
    internal suspend fun clearPresenceCopies(): Result<Unit> {
        val mutation = """
            mutation UpdateHandoff(${'$'}input: HandoffInput!) {
                updateHandoff(input: ${'$'}input)
            }
        """.trimIndent()
        val variables = buildJsonObject {
            put("input", buildJsonObject {
                put("userId", secureStorage.agroUsername)
                // `HandoffInput` requires these, and this call is not reporting a track. The row
                // being updated already holds whatever the last real handoff said; the placeholders
                // here exist only to satisfy the input type while `presenceCiphertexts` does the
                // work.
                put("trackUri", "")
                put("trackTitle", PRIVATE_SESSION_TITLE)
                put("artistName", "")
                put("positionMs", 0L)
                put("isPlaying", false)
                put("deviceId", secureStorage.agroDeviceId)
                put("presenceCiphertexts", buildJsonArray {})
            })
        }
        return graphQl.execute(mutation, variables).discardPayload()
    }


    /**
     * The metadata a sealed session carries, as bytes.
     *
     * One definition for both envelopes. The copy sealed to this account's own vault key and
     * the copies sealed to friends' device keys have to describe the same track in the same
     * shape, or a friend and a laptop resuming the same session disagree about what is playing.
     */
    fun sealedMetadata(
        trackUri: String,
        title: String,
        artist: String,
        album: String?,
        artworkUrl: String?,
        contentHash: String?
    ): ByteArray = buildJsonObject {
        put("trackUri", trackUri)
        put("trackTitle", title)
        put("artistName", artist)
        album?.let { put("albumName", it) }
        artworkUrl?.let { put("artworkUrl", it) }
        // Suppressed from the plaintext column — the hash identifies the track as surely as its
        // name — but it belongs *inside* the envelope. Someone following along resolves the file by
        // hash, and a listener who can open the copy is by definition already allowed to know what
        // is playing. Without it, a sealed session is followable in name only: the label above the
        // transfer is right and the transfer no longer finds anything.
        contentHash?.takeIf { it.isNotBlank() }?.let { put("contentHash", it) }
    }.toString().toByteArray(Charsets.UTF_8)

    private companion object {
        /** Placeholder title on a sealed handoff — the real one is inside the envelope. */
        const val PRIVATE_SESSION_TITLE = "Private Session"
    }
}
