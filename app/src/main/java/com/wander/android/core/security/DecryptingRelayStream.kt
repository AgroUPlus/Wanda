package com.wander.android.core.security

import java.io.IOException
import java.io.InputStream

/**
 * Turns a framed, encrypted relay stream back into audio.
 *
 * Presents itself as an ordinary [InputStream] so it can sit under a player that knows nothing
 * about any of this: it reads a frame at a time, opens it, and hands out the plaintext.
 *
 * Every failure below is deliberately fatal rather than skipped. A player handed audio with a
 * silently dropped chunk plays a glitch and carries on; the point of authenticating position is
 * that a missing or altered chunk is *known*, and the honest response is to end the stream and let
 * the resolver fall to another tier.
 */
internal class DecryptingRelayStream(
    private val source: InputStream,
    private val cipher: AudioStreamCipher
) : InputStream() {

    private var plaintext: ByteArray = EMPTY
    private var offset = 0
    private var finished = false

    init {
        val magic = ByteArray(RelayStreamFraming.MAGIC_BYTES)
        var filled = 0
        while (filled < magic.size) {
            val read = source.read(magic, filled, magic.size - filled)
            if (read < 0) break
            filled += read
        }
        if (filled < magic.size || !RelayStreamFraming.isFramed(magic)) {
            throw IOException("Relay stream is not an encrypted Wanda stream")
        }
    }

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xFF
    }

    override fun read(destination: ByteArray, destinationOffset: Int, length: Int): Int {
        if (length == 0) return 0
        if (!fill()) return -1
        val available = minOf(length, plaintext.size - offset)
        System.arraycopy(plaintext, offset, destination, destinationOffset, available)
        offset += available
        return available
    }

    /** Ensures a chunk of plaintext is waiting. False once the stream has genuinely ended. */
    private fun fill(): Boolean {
        while (offset >= plaintext.size) {
            if (finished) return false
            val frame = RelayStreamFraming.readFrame(source)
            if (frame == null) {
                finished = true
                return false
            }
            // Throws on a repeat, on a gap, and on any alteration — including a chunk moved to a
            // different position, since the position is part of what was authenticated.
            plaintext = cipher.open(frame.seq, frame.ciphertext)
            offset = 0
        }
        return true
    }

    override fun available(): Int = plaintext.size - offset

    override fun close() {
        source.close()
    }

    private companion object {
        val EMPTY = ByteArray(0)
    }
}
