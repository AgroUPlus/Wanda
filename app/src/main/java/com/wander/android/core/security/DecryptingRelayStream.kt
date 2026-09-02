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
            // The diagnostic goes in the message, not into `android.util.Log`. This class is one of
            // the few in the audio path that can be constructed on the JVM, and reaching for the
            // Android logger here would throw "not mocked" in its own unit test — trading a tested
            // invariant for a log line. Whoever catches this logs it anyway.
            //
            // Three different failures used to render as one sentence and could not be told apart:
            // nothing arrived, an HTTP error body arrived where audio was expected, or real bytes
            // arrived without the framing that says they are encrypted. The first eight bytes
            // separate all three.
            val ascii = String(magic, 0, filled, Charsets.ISO_8859_1)
                .map { if (it.code in 32..126) it else '.' }
                .joinToString("")
            val hex = magic.copyOf(filled).joinToString("") { "%02x".format(it) }
            throw IOException(
                if (filled == 0) {
                    "The peer sent nothing for this track."
                } else {
                    "Relay stream is not an encrypted Wanda stream: " +
                        "read $filled of ${magic.size} bytes, hex=$hex ascii=\"$ascii\""
                }
            )
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
