package com.wander.android.core.security

import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.nio.ByteBuffer

/**
 * Encrypts one relayed audio stream, chunk by chunk.
 *
 * [IdentityKeyManager] seals one-shot messages, each under a fresh ephemeral key with a random
 * nonce. That construction is wrong for a stream: thousands of chunks under one key make a random
 * 96-bit nonce a birthday problem, and nothing about a sealed note says where it belongs in an
 * order, so a relay could drop, reorder or repeat chunks undetectably.
 *
 * So each chunk is numbered, and the number is *both* the nonce counter and authenticated data.
 * Nonces cannot repeat while the counter does not, and a chunk cannot be moved to another position
 * in the stream without failing to authenticate — the position is part of what was signed.
 *
 * A cipher instance belongs to one direction of one session. It is not thread-safe: the sequence
 * is a plain counter, and two threads sealing at once is exactly the nonce reuse this exists to
 * prevent.
 */
internal class AudioStreamCipher(private val keys: AudioStreamKeys.SessionKeys) {

    /** The next sequence number to seal with. Chunks are numbered from zero. */
    private var nextSeq: Long = 0

    /**
     * The highest sequence number opened so far, or -1 before the first.
     *
     * The receive side accepts strictly increasing numbers and nothing else. A window that
     * tolerated gaps would be the right answer for a datagram protocol; this rides a TCP stream
     * that already delivers in order, so anything out of order is evidence of tampering rather
     * than of the network, and accepting it would be accepting a replay.
     */
    private var lastOpened: Long = -1

    /** Seals [plaintext] as the next chunk in the stream. */
    fun seal(plaintext: ByteArray): SealedChunk {
        val seq = nextSeq++
        return SealedChunk(seq, encrypt(seq, plaintext))
    }

    /**
     * Opens [chunk], or throws if it is not the next thing this stream should have received.
     *
     * @throws ReplayedChunk if [seq] has already been seen, or arrives out of order.
     * @throws org.bouncycastle.crypto.InvalidCipherTextException if the chunk was altered.
     */
    fun open(seq: Long, ciphertext: ByteArray): ByteArray {
        if (seq <= lastOpened) throw ReplayedChunk(seq, lastOpened)
        val plaintext = decrypt(seq, ciphertext)
        // Only after it authenticates: a forged number must not be able to advance the window and
        // lock out the genuine chunk that follows.
        lastOpened = seq
        return plaintext
    }

    private fun encrypt(seq: Long, plaintext: ByteArray): ByteArray {
        val cipher = cipher(seq, forEncryption = true)
        val out = ByteArray(cipher.getOutputSize(plaintext.size))
        val written = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
        cipher.doFinal(out, written)
        return out
    }

    private fun decrypt(seq: Long, ciphertext: ByteArray): ByteArray {
        val cipher = cipher(seq, forEncryption = false)
        val out = ByteArray(cipher.getOutputSize(ciphertext.size))
        val written = cipher.processBytes(ciphertext, 0, ciphertext.size, out, 0)
        val total = written + cipher.doFinal(out, written)
        return if (total == out.size) out else out.copyOf(total)
    }

    private fun cipher(seq: Long, forEncryption: Boolean): ChaCha20Poly1305 {
        require(seq >= 0) { "Sequence numbers start at zero" }
        return ChaCha20Poly1305().apply {
            init(
                forEncryption,
                AEADParameters(
                    KeyParameter(keys.key),
                    MAC_BITS,
                    nonceFor(seq),
                    // The sequence number, authenticated but not encrypted: the relay has to read
                    // it to order chunks, and must not be able to change it.
                    associatedData(seq)
                )
            )
        }
    }

    /**
     * The session's four fixed bytes, then the counter.
     *
     * Twelve bytes exactly, which ChaCha20-Poly1305 requires. The counter half cannot wrap in any
     * real stream: it would take 2^64 chunks.
     */
    private fun nonceFor(seq: Long): ByteArray =
        ByteBuffer.allocate(NONCE_BYTES)
            .put(keys.noncePrefix)
            .putLong(seq)
            .array()

    private fun associatedData(seq: Long): ByteArray =
        ByteBuffer.allocate(Long.SIZE_BYTES).putLong(seq).array()

    /** A chunk and the number it must be opened with. */
    internal data class SealedChunk(val seq: Long, val ciphertext: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SealedChunk) return false
            return seq == other.seq && ciphertext.contentEquals(other.ciphertext)
        }

        override fun hashCode(): Int = 31 * seq.hashCode() + ciphertext.contentHashCode()
    }

    /** A chunk arrived that this stream has already accepted, or that is older than one it has. */
    internal class ReplayedChunk(seq: Long, lastOpened: Long) : IllegalStateException(
        "Chunk $seq is not after $lastOpened"
    )

    private companion object {
        const val NONCE_BYTES = 12
        const val MAC_BITS = 128
    }
}
