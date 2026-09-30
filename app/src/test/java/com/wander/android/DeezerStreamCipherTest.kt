package com.wander.android

import com.wander.android.core.security.DeezerStreamCipher
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeezerStreamCipherTest {

    private val testTrackId = "3135556"

    @Test
    fun `deriveKey returns 16-byte key deterministically`() {
        val key1 = DeezerStreamCipher.deriveKey(testTrackId)
        val key2 = DeezerStreamCipher.deriveKey(testTrackId)

        assertEquals(16, key1.size)
        assertArrayEquals(key1, key2)
    }

    @Test
    fun `chunk encryption and decryption round trips successfully`() {
        val cipher = DeezerStreamCipher(testTrackId)
        val plaintext = ByteArray(DeezerStreamCipher.CHUNK_SIZE) { (it % 256).toByte() }

        val ciphertext = cipher.encryptChunk(plaintext)
        assertEquals(DeezerStreamCipher.CHUNK_SIZE, ciphertext.size)

        val decrypted = cipher.decryptChunk(ciphertext)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `chunk size validation rejects non-2048-byte chunks`() {
        val cipher = DeezerStreamCipher(testTrackId)
        val shortBuffer = ByteArray(1024)

        assertThrows(IllegalArgumentException::class.java) {
            cipher.decryptChunk(shortBuffer)
        }
    }

    @Test
    fun `isChunkEncrypted only targets full chunks at index modulo 3 == 0`() {
        // Chunk 0, 3, 6, 9 with size 2048 are encrypted
        assertTrue(DeezerStreamCipher.isChunkEncrypted(0, DeezerStreamCipher.CHUNK_SIZE))
        assertTrue(DeezerStreamCipher.isChunkEncrypted(3, DeezerStreamCipher.CHUNK_SIZE))
        assertTrue(DeezerStreamCipher.isChunkEncrypted(6, DeezerStreamCipher.CHUNK_SIZE))

        // Chunk 1, 2, 4, 5 are plaintext
        assertFalse(DeezerStreamCipher.isChunkEncrypted(1, DeezerStreamCipher.CHUNK_SIZE))
        assertFalse(DeezerStreamCipher.isChunkEncrypted(2, DeezerStreamCipher.CHUNK_SIZE))
        assertFalse(DeezerStreamCipher.isChunkEncrypted(4, DeezerStreamCipher.CHUNK_SIZE))
        assertFalse(DeezerStreamCipher.isChunkEncrypted(5, DeezerStreamCipher.CHUNK_SIZE))

        // Trailing short chunk at EOF is never encrypted, even if index % 3 == 0
        assertFalse(DeezerStreamCipher.isChunkEncrypted(0, 1500))
        assertFalse(DeezerStreamCipher.isChunkEncrypted(3, 512))
    }
}
