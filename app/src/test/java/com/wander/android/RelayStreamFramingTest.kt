package com.wander.android

import com.wander.android.core.security.AudioStreamCipher
import com.wander.android.core.security.AudioStreamKeys
import com.wander.android.core.security.DecryptingRelayStream
import com.wander.android.core.security.RelayStreamFraming
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The framed stream end to end: what the host writes is what the listener plays, and anything the
 * relay does to it in between is detected rather than played.
 */
class RelayStreamFramingTest {

    private val roomKey = ByteArray(32) { (it * 7).toByte() }
    private fun keys() = AudioStreamKeys.derive(roomKey, "session-1")

    private fun encrypt(audio: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        RelayStreamFraming.encrypt(ByteArrayInputStream(audio), out, AudioStreamCipher(keys()))
        return out.toByteArray()
    }

    private fun decrypt(wire: ByteArray): ByteArray =
        DecryptingRelayStream(ByteArrayInputStream(wire), AudioStreamCipher(keys())).readBytes()

    @Test
    fun `audio survives a round trip`() {
        val audio = ByteArray(200_000) { (it % 251).toByte() }
        assertArrayEquals(audio, decrypt(encrypt(audio)))
    }

    /** More than one chunk, so the sequence actually advances. */
    @Test
    fun `a multi-chunk stream round trips`() {
        val audio = ByteArray(RelayStreamFraming.CHUNK_BYTES * 3 + 17) { it.toByte() }
        val wire = encrypt(audio)
        assertTrue("should be several frames", wire.size > RelayStreamFraming.CHUNK_BYTES * 3)
        assertArrayEquals(audio, decrypt(wire))
    }

    @Test
    fun `an empty stream round trips`() {
        assertArrayEquals(ByteArray(0), decrypt(encrypt(ByteArray(0))))
    }

    @Test
    fun `the wire bytes are not the audio`() {
        val audio = ByteArray(50_000) { 0x41 }
        val wire = encrypt(audio)
        assertTrue(
            "plaintext must not appear on the wire",
            !String(wire, Charsets.ISO_8859_1).contains("AAAAAAAAAAAAAAAA")
        )
    }

    @Test
    fun `an unframed stream is refused rather than played as noise`() {
        assertThrows(IOException::class.java) {
            DecryptingRelayStream(ByteArrayInputStream(ByteArray(64)), AudioStreamCipher(keys()))
        }
    }

    @Test
    fun `a truncated stream is an error, not a short read`() {
        val wire = encrypt(ByteArray(100_000) { it.toByte() })
        assertThrows(IOException::class.java) { decrypt(wire.copyOf(wire.size - 500)) }
    }

    @Test
    fun `an altered chunk ends the stream instead of glitching`() {
        val wire = encrypt(ByteArray(50_000) { it.toByte() })
        val tampered = wire.copyOf().also { it[it.size - 20] = (it[it.size - 20].toInt() xor 0x01).toByte() }
        assertThrows(Exception::class.java) { decrypt(tampered) }
    }

    @Test
    fun `another room cannot read the stream`() {
        val wire = encrypt(ByteArray(10_000) { it.toByte() })
        val stranger = AudioStreamCipher(AudioStreamKeys.derive(AudioStreamKeys.newRoomKey(), "session-1"))
        assertThrows(Exception::class.java) {
            DecryptingRelayStream(ByteArrayInputStream(wire), stranger).readBytes()
        }
    }

    /** The marker is what tells the receive path a stream needs decrypting at all. */
    @Test
    fun `a framed stream is recognisable from its first bytes`() {
        val wire = encrypt(ByteArray(1_000))
        assertTrue(RelayStreamFraming.isFramed(wire))
        assertTrue(!RelayStreamFraming.isFramed(ByteArray(16)))
    }

    @Test
    fun `frames carry their own position`() {
        val wire = encrypt(ByteArray(RelayStreamFraming.CHUNK_BYTES * 2))
        val source = ByteArrayInputStream(wire)
        source.skip(RelayStreamFraming.MAGIC_BYTES.toLong())

        assertEquals(0L, RelayStreamFraming.readFrame(source)?.seq)
        assertEquals(1L, RelayStreamFraming.readFrame(source)?.seq)
        assertEquals(null, RelayStreamFraming.readFrame(source))
    }
}
