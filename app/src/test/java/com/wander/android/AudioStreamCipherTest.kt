package com.wander.android

import com.wander.android.core.security.AudioStreamCipher
import com.wander.android.core.security.AudioStreamKeys
import org.bouncycastle.crypto.InvalidCipherTextException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stream cipher's guarantees, stated as tests.
 *
 * These are the properties the relay design depends on and cannot check at runtime: the server is
 * a byte pipe that could drop, reorder or repeat anything, and the only thing standing between
 * that and the audio is this.
 */
class AudioStreamCipherTest {

    private val roomKey = ByteArray(32) { it.toByte() }
    private val session = "session-1"

    private fun keys() = AudioStreamKeys.derive(roomKey, session)

    @Test
    fun `a chunk opens to what was sealed`() {
        val sender = AudioStreamCipher(keys())
        val receiver = AudioStreamCipher(keys())
        val audio = "the quick brown fox".toByteArray()

        val sealed = sender.seal(audio)
        assertArrayEquals(audio, receiver.open(sealed.seq, sealed.ciphertext))
    }

    @Test
    fun `a whole stream opens in order`() {
        val sender = AudioStreamCipher(keys())
        val receiver = AudioStreamCipher(keys())
        val chunks = (0 until 50).map { "chunk $it".toByteArray() }

        val sealed = chunks.map(sender::seal)
        sealed.forEachIndexed { index, chunk ->
            assertEquals(index.toLong(), chunk.seq)
            assertArrayEquals(chunks[index], receiver.open(chunk.seq, chunk.ciphertext))
        }
    }

    /** The failure the counter exists to prevent: identical plaintext must not encrypt alike. */
    @Test
    fun `the same audio sealed twice produces different ciphertext`() {
        val sender = AudioStreamCipher(keys())
        val audio = "same bytes".toByteArray()

        val first = sender.seal(audio)
        val second = sender.seal(audio)

        assertNotEquals(first.seq, second.seq)
        assertTrue(
            "nonce reuse would make these identical",
            !first.ciphertext.contentEquals(second.ciphertext)
        )
    }

    @Test
    fun `a replayed chunk is refused`() {
        val sender = AudioStreamCipher(keys())
        val receiver = AudioStreamCipher(keys())
        val first = sender.seal("first".toByteArray())
        val second = sender.seal("second".toByteArray())

        receiver.open(first.seq, first.ciphertext)
        receiver.open(second.seq, second.ciphertext)

        assertThrows(AudioStreamCipher.ReplayedChunk::class.java) {
            receiver.open(second.seq, second.ciphertext)
        }
    }

    /** A relay that held a chunk back and delivered it late must not be able to insert it. */
    @Test
    fun `a chunk delivered out of order is refused`() {
        val sender = AudioStreamCipher(keys())
        val receiver = AudioStreamCipher(keys())
        val first = sender.seal("first".toByteArray())
        val second = sender.seal("second".toByteArray())

        receiver.open(second.seq, second.ciphertext)

        assertThrows(AudioStreamCipher.ReplayedChunk::class.java) {
            receiver.open(first.seq, first.ciphertext)
        }
    }

    /** Position is authenticated, so a chunk cannot be moved elsewhere in the stream. */
    @Test
    fun `a chunk opened at the wrong position fails to authenticate`() {
        val sender = AudioStreamCipher(keys())
        val receiver = AudioStreamCipher(keys())
        sender.seal("first".toByteArray())
        val second = sender.seal("second".toByteArray())

        assertThrows(InvalidCipherTextException::class.java) {
            receiver.open(second.seq + 1, second.ciphertext)
        }
    }

    @Test
    fun `an altered chunk fails to authenticate`() {
        val sender = AudioStreamCipher(keys())
        val receiver = AudioStreamCipher(keys())
        val sealed = sender.seal("audio".toByteArray())
        val tampered = sealed.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }

        assertThrows(InvalidCipherTextException::class.java) {
            receiver.open(sealed.seq, tampered)
        }
    }

    /** The relay is one room's; another room's key must not open it. */
    @Test
    fun `another room's key does not open the stream`() {
        val sender = AudioStreamCipher(keys())
        val stranger = AudioStreamCipher(AudioStreamKeys.derive(AudioStreamKeys.newRoomKey(), session))
        val sealed = sender.seal("audio".toByteArray())

        assertThrows(InvalidCipherTextException::class.java) {
            stranger.open(sealed.seq, sealed.ciphertext)
        }
    }

    /** Two tracks in one room derive independently, so a key reused across them still cannot collide. */
    @Test
    fun `a different session derives different material from the same room key`() {
        val first = AudioStreamKeys.derive(roomKey, "session-1")
        val second = AudioStreamKeys.derive(roomKey, "session-2")

        assertNotEquals(first, second)
        assertThrows(InvalidCipherTextException::class.java) {
            val sealed = AudioStreamCipher(first).seal("audio".toByteArray())
            AudioStreamCipher(second).open(sealed.seq, sealed.ciphertext)
        }
    }

    /** Derivation must be a function of its inputs, or the two sides cannot agree. */
    @Test
    fun `derivation is deterministic`() {
        assertEquals(AudioStreamKeys.derive(roomKey, session), AudioStreamKeys.derive(roomKey, session))
    }

    @Test
    fun `a room key round-trips through its encoding`() {
        val key = AudioStreamKeys.newRoomKey()
        assertArrayEquals(key, AudioStreamKeys.decodeRoomKey(AudioStreamKeys.encodeRoomKey(key)))
    }

    @Test
    fun `a room key of the wrong size is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            AudioStreamKeys.decodeRoomKey(java.util.Base64.getEncoder().encodeToString(ByteArray(16)))
        }
    }
}
