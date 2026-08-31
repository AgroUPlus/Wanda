package com.wander.android.core.security

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import java.security.SecureRandom
import java.util.Base64

/**
 * The key material one jam's audio is encrypted under.
 *
 * A jam encrypts each chunk **once**, under a key the room shares, rather than once per listener.
 * That is what makes relaying affordable for the host: the alternative is re-encrypting and
 * re-uploading the same audio for every person in the room, which is a cost a phone pays in
 * battery and in upstream bandwidth it does not have.
 *
 * The room key itself is distributed with [IdentityKeyManager.sealNote] — 32 bytes sealed to each
 * member's identity key, one small message each. So there is no new key exchange here: the
 * expensive per-recipient step happens once per track, on a payload the size of a key, instead of
 * once per chunk on the audio.
 */
internal object AudioStreamKeys {

    /** Binds derived material to this protocol, so the same room key elsewhere derives elsewhere. */
    private const val INFO = "wanda-jam-audio-v1"

    private const val KEY_BYTES = 32
    private const val NONCE_PREFIX_BYTES = 4

    private val random = SecureRandom()

    /**
     * A fresh key for one room's audio.
     *
     * Per track rather than per session: a member who joins midway is given the key for what is
     * playing now and cannot decrypt what was relayed before they arrived.
     */
    fun newRoomKey(): ByteArray = ByteArray(KEY_BYTES).also(random::nextBytes)

    fun encodeRoomKey(roomKey: ByteArray): String = Base64.getEncoder().encodeToString(roomKey)

    fun decodeRoomKey(encoded: String): ByteArray =
        Base64.getDecoder().decode(encoded.trim()).also {
            require(it.size == KEY_BYTES) { "Room key must be $KEY_BYTES bytes, was ${it.size}" }
        }

    /**
     * Splits a room key into the material the cipher actually uses.
     *
     * HKDF rather than using the room key directly, and rather than the bare SHA-256 that
     * [IdentityKeyManager] uses for one-shot notes: this key encrypts many messages, so the
     * encryption key and the nonce prefix have to be independent of each other and bound to the
     * session. [sessionId] is the salt, so two tracks in one room never derive the same material
     * even if the room key were somehow reused.
     */
    fun derive(roomKey: ByteArray, sessionId: String): SessionKeys {
        require(roomKey.size == KEY_BYTES) { "Room key must be $KEY_BYTES bytes" }
        val generator = HKDFBytesGenerator(SHA256Digest())
        generator.init(
            HKDFParameters(
                roomKey,
                sessionId.toByteArray(Charsets.UTF_8),
                INFO.toByteArray(Charsets.UTF_8)
            )
        )
        val output = ByteArray(KEY_BYTES + NONCE_PREFIX_BYTES)
        generator.generateBytes(output, 0, output.size)
        return SessionKeys(
            key = output.copyOfRange(0, KEY_BYTES),
            noncePrefix = output.copyOfRange(KEY_BYTES, output.size)
        )
    }

    /**
     * @param noncePrefix Four bytes fixed for the session, ahead of the counter. It is what stops
     *   two *sessions* colliding in nonce space; the counter only guarantees uniqueness within one.
     */
    internal data class SessionKeys(val key: ByteArray, val noncePrefix: ByteArray) {
        // Arrays, so the generated equals would compare identity and quietly mean nothing.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SessionKeys) return false
            return key.contentEquals(other.key) && noncePrefix.contentEquals(other.noncePrefix)
        }

        override fun hashCode(): Int = 31 * key.contentHashCode() + noncePrefix.contentHashCode()
    }
}
