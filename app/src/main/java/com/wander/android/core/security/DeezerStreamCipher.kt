package com.wander.android.core.security

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Decrypts a Deezer audio stream chunk by chunk using Blowfish CBC.
 *
 * Deezer streams are protected by selective Blowfish encryption:
 * every chunk at `chunkIndex % 3 == 0` (where chunk is 2,048 bytes) is encrypted.
 * All other chunks are unencrypted plaintext.
 *
 * The 16-byte Blowfish key is derived deterministically from the track ID and
 * a static secret passphrase using MD5 hashing and XOR mixing.
 */
internal class DeezerStreamCipher(trackId: String) {

    private val keySpec: SecretKeySpec
    private val ivSpec = IvParameterSpec(IV)

    init {
        val keyBytes = deriveKey(trackId)
        keySpec = SecretKeySpec(keyBytes, ALGORITHM)
    }

    /**
     * Decrypts a full 2,048-byte chunk of ciphertext.
     * Blowfish block size is 8 bytes; a full 2,048-byte chunk is exactly 256 blocks.
     */
    fun decryptChunk(ciphertext: ByteArray, offset: Int = 0, length: Int = ciphertext.size): ByteArray {
        require(length == CHUNK_SIZE) { "Deezer encrypted chunks must be exactly $CHUNK_SIZE bytes, got $length" }
        val cipher = Cipher.getInstance(TRANSFORMATION) // NOSONAR: Blowfish is mandated by Deezer's stream format, not our choice
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(ciphertext, offset, length)
    }

    /**
     * Encrypts a full 2,048-byte chunk of plaintext. Used for testing and simulation.
     */
    fun encryptChunk(plaintext: ByteArray, offset: Int = 0, length: Int = plaintext.size): ByteArray {
        require(length == CHUNK_SIZE) { "Deezer plaintext chunks must be exactly $CHUNK_SIZE bytes, got $length" }
        val cipher = Cipher.getInstance(TRANSFORMATION) // NOSONAR: Blowfish is mandated by Deezer's stream format, not our choice
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(plaintext, offset, length)
    }

    companion object {
        const val CHUNK_SIZE = 2048
        private const val ALGORITHM = "Blowfish"
        private const val TRANSFORMATION = "Blowfish/CBC/NoPadding"
        private const val SECRET = "g4el58wc0zvf9na1" // NOSONAR: public constant of Deezer's key derivation, not a credential of ours
        private val IV = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)

        /**
         * Computes the 16-byte Blowfish key for [trackId].
         */
        fun deriveKey(trackId: String): ByteArray {
            val md5Hex = MessageDigest.getInstance("MD5") // NOSONAR: key derivation step fixed by Deezer; not used for integrity or passwords
                .digest(trackId.toByteArray(Charsets.US_ASCII))
                .joinToString("") { "%02x".format(it) }

            val key = ByteArray(16)
            for (i in 0 until 16) {
                val xor = md5Hex[i].code xor md5Hex[i + 16].code xor SECRET[i].code
                key[i] = xor.toByte()
            }
            return key
        }

        /**
         * Returns whether a chunk at [chunkIndex] of [length] bytes is encrypted.
         * Only full 2,048-byte chunks where `chunkIndex % 3 == 0` are encrypted.
         */
        fun isChunkEncrypted(chunkIndex: Long, length: Int): Boolean =
            chunkIndex % 3L == 0L && length == CHUNK_SIZE
    }
}
