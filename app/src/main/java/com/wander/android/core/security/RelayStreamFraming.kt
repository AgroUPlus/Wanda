package com.wander.android.core.security

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * How an encrypted relay stream is laid out on the wire.
 *
 * The relay is a byte pipe with no notion of messages, but [AudioStreamCipher] authenticates whole
 * chunks and needs to know which position each one holds. So the stream carries its own framing:
 *
 * ```
 * [8-byte magic]  once, at the start
 * repeated:
 *   [8-byte sequence, big-endian][4-byte ciphertext length, big-endian][ciphertext]
 * ```
 *
 * The sequence is on the wire rather than inferred from arrival order because inferring it would
 * defeat the point: a relay that dropped a chunk would silently renumber every chunk after it, and
 * each would still authenticate at its new position. Carrying it means a drop is detected at the
 * first chunk after the gap.
 *
 * Lengths are read before the body, so a truncated stream is an error rather than a short final
 * chunk that happens to decrypt.
 */
internal object RelayStreamFraming {

    /** Marks a stream as framed and encrypted, so an unencrypted one is not misread as garbage. */
    private val MAGIC = byteArrayOf(0x57, 0x41, 0x4E, 0x44, 0x41, 0x45, 0x32, 0x45) // "WANDAE2E"

    const val MAGIC_BYTES = 8

    /**
     * A ceiling on one chunk, so a corrupt length cannot make the reader allocate arbitrarily.
     * Comfortably above [CHUNK_BYTES] plus the tag.
     */
    private const val MAX_CHUNK_BYTES = 4 * 1024 * 1024

    /** What the sender reads at a time. Large enough to keep the per-chunk overhead negligible. */
    const val CHUNK_BYTES = 64 * 1024

    /** Whether [prefix] starts with the marker. Used to decide whether a stream needs decrypting. */
    fun isFramed(prefix: ByteArray): Boolean =
        prefix.size >= MAGIC_BYTES && prefix.copyOf(MAGIC_BYTES).contentEquals(MAGIC)

    /**
     * Seals everything in [source] into [sink].
     *
     * The cipher is advanced once per chunk, so [cipher] must be fresh and must not be shared.
     */
    fun encrypt(source: InputStream, sink: OutputStream, cipher: AudioStreamCipher) {
        sink.write(MAGIC)
        val buffer = ByteArray(CHUNK_BYTES)
        while (true) {
            val read = source.read(buffer)
            if (read <= 0) break
            val sealed = cipher.seal(buffer.copyOf(read))
            sink.write(longBytes(sealed.seq))
            sink.write(intBytes(sealed.ciphertext.size))
            sink.write(sealed.ciphertext)
        }
        sink.flush()
    }

    /**
     * Reads one frame, or null at a clean end of stream.
     *
     * @throws IOException if the stream ends part-way through a frame, or declares an impossible
     *   length — both mean the transfer was cut short rather than finished.
     */
    fun readFrame(source: InputStream): Frame? {
        val header = ByteArray(HEADER_BYTES)
        val got = source.readAsMuchAsPossible(header)
        if (got == 0) return null
        if (got < HEADER_BYTES) throw EOFException("Relay stream ended inside a frame header")

        val seq = longFrom(header, 0)
        val length = intFrom(header, Long.SIZE_BYTES)
        if (length <= 0 || length > MAX_CHUNK_BYTES) {
            throw IOException("Relay stream declared an implausible chunk of $length bytes")
        }

        val ciphertext = ByteArray(length)
        if (source.readAsMuchAsPossible(ciphertext) < length) {
            throw EOFException("Relay stream ended inside a chunk")
        }
        return Frame(seq, ciphertext)
    }

    /** Reads until [into] is full or the stream ends, returning how much was read. */
    private fun InputStream.readAsMuchAsPossible(into: ByteArray): Int {
        var filled = 0
        while (filled < into.size) {
            val read = read(into, filled, into.size - filled)
            if (read < 0) break
            filled += read
        }
        return filled
    }

    private const val HEADER_BYTES = Long.SIZE_BYTES + Int.SIZE_BYTES

    private fun longBytes(value: Long) = ByteArray(Long.SIZE_BYTES) { i ->
        (value ushr (8 * (Long.SIZE_BYTES - 1 - i))).toByte()
    }

    private fun intBytes(value: Int) = ByteArray(Int.SIZE_BYTES) { i ->
        (value ushr (8 * (Int.SIZE_BYTES - 1 - i))).toByte()
    }

    private fun longFrom(bytes: ByteArray, offset: Int): Long =
        (0 until Long.SIZE_BYTES).fold(0L) { acc, i ->
            (acc shl 8) or (bytes[offset + i].toLong() and 0xFF)
        }

    private fun intFrom(bytes: ByteArray, offset: Int): Int =
        (0 until Int.SIZE_BYTES).fold(0) { acc, i ->
            (acc shl 8) or (bytes[offset + i].toInt() and 0xFF)
        }

    internal data class Frame(val seq: Long, val ciphertext: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Frame) return false
            return seq == other.seq && ciphertext.contentEquals(other.ciphertext)
        }

        override fun hashCode(): Int = 31 * seq.hashCode() + ciphertext.contentHashCode()
    }
}
