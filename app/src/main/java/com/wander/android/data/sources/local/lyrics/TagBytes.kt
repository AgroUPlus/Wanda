package com.wander.android.data.sources.local.lyrics

import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Random access to a file's bytes, for the tag readers.
 *
 * Positional rather than a stream because the tags are not all at the front: an MP4's `moov` is
 * often at the end, behind the audio, and the readers jump straight to it.
 */
internal interface TagBytes {
    val size: Long

    /** Up to [length] bytes from [position]; fewer near the end of the file, none past it. */
    fun read(position: Long, length: Int): ByteArray
}

internal class ByteArrayTagBytes(private val bytes: ByteArray) : TagBytes {
    override val size: Long get() = bytes.size.toLong()

    override fun read(position: Long, length: Int): ByteArray {
        if (position < 0 || position >= bytes.size || length <= 0) return ByteArray(0)
        val end = minOf(bytes.size.toLong(), position + length).toInt()
        return bytes.copyOfRange(position.toInt(), end)
    }
}

internal class ChannelTagBytes(private val channel: FileChannel) : TagBytes {
    override val size: Long = channel.size()

    override fun read(position: Long, length: Int): ByteArray {
        if (position < 0 || position >= size || length <= 0) return ByteArray(0)
        val count = minOf(length.toLong(), size - position).toInt()
        val buffer = ByteBuffer.allocate(count)
        var at = position
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer, at)
            if (read < 0) break
            at += read
        }
        val array = buffer.array()
        return if (buffer.position() == count) array else array.copyOf(buffer.position())
    }
}

internal fun ByteArray.startsWithAscii(text: String, at: Int = 0): Boolean {
    if (at < 0 || at + text.length > size) return false
    return text.indices.all { this[at + it] == text[it].code.toByte() }
}

internal fun ByteArray.u8(at: Int): Int = this[at].toInt() and 0xFF

internal fun ByteArray.int24(at: Int): Int = (u8(at) shl 16) or (u8(at + 1) shl 8) or u8(at + 2)

internal fun ByteArray.int32(at: Int): Int =
    (u8(at) shl 24) or (u8(at + 1) shl 16) or (u8(at + 2) shl 8) or u8(at + 3)

internal fun ByteArray.uint32(at: Int): Long = int32(at).toLong() and 0xFFFF_FFFFL

internal fun ByteArray.int64(at: Int): Long = (uint32(at) shl 32) or uint32(at + 4)

/** Little-endian, as Vorbis comments write their lengths. */
internal fun ByteArray.uint32Le(at: Int): Long =
    (u8(at).toLong()) or (u8(at + 1).toLong() shl 8) or (u8(at + 2).toLong() shl 16) or (u8(at + 3).toLong() shl 24)

/** ID3's 28-bit "syncsafe" integer: seven bits per byte, so no byte can look like a sync word. */
internal fun ByteArray.syncsafe(at: Int): Int =
    ((u8(at) and 0x7F) shl 21) or ((u8(at + 1) and 0x7F) shl 14) or
        ((u8(at + 2) and 0x7F) shl 7) or (u8(at + 3) and 0x7F)
