package com.wander.android.data.sources.local.lyrics

import com.wander.android.data.model.LyricLine

/**
 * Lyrics from an ID3v2 tag: `SYLT` (synchronised) when it is timed in milliseconds, else `USLT`.
 *
 * Media3 does parse ID3, but only while a stream is being played, and it leaves both frames as
 * undecoded binary. Reading the lyrics panel's text must not wait for playback, so the tag is
 * walked here. Frames that are compressed or encrypted are skipped rather than guessed at.
 */
internal object Id3Lyrics {

    /** Where the audio after the tag starts, or null when [header] does not open an ID3v2 tag. */
    fun tagEnd(header: ByteArray): Long? {
        if (header.size < HEADER_BYTES || !header.startsWithAscii("ID3")) return null
        val footer = if (header.u8(5) and FLAG_FOOTER != 0) HEADER_BYTES else 0
        return HEADER_BYTES.toLong() + header.syncsafe(6) + footer
    }

    fun read(bytes: TagBytes): FileLyrics? {
        val header = bytes.read(0, HEADER_BYTES)
        if (tagEnd(header) == null) return null
        val major = header.u8(3)
        if (major !in 2..4) return null
        val flags = header.u8(5)
        val tagSize = header.syncsafe(6)
        if (tagSize <= 0 || tagSize > MAX_TAG_BYTES) return null
        var tag = bytes.read(HEADER_BYTES.toLong(), tagSize)
        // v2.2 and v2.3 unsynchronise the tag as a whole; v2.4 flags it frame by frame instead.
        if (major < 4 && flags and FLAG_UNSYNC != 0) tag = resync(tag)

        var pos = 0
        if (major >= 3 && flags and FLAG_EXTENDED != 0 && tag.size >= 4) {
            // v2.4 counts the size field itself; v2.3 does not.
            pos = if (major == 4) tag.syncsafe(0) else tag.int32(0) + 4
        }
        val idLength = if (major == 2) 3 else 4
        val frameHeader = if (major == 2) 6 else 10
        var unsynced: String? = null
        while (pos >= 0 && pos + frameHeader <= tag.size) {
            if (tag[pos] == 0.toByte()) break // padding: no frames follow
            val id = String(tag, pos, idLength, Charsets.ISO_8859_1)
            val size = when (major) {
                2 -> tag.int24(pos + 3)
                3 -> tag.int32(pos + 4)
                else -> tag.syncsafe(pos + 4)
            }
            val start = pos + frameHeader
            if (size < 0 || start + size > tag.size) break
            val format = if (major == 2) 0 else tag.u8(pos + 9)
            pos = start + size
            if (!isPlain(major, format)) continue
            var body = tag.copyOfRange(start, start + size)
            if (major == 4) {
                if (format and V4_DATA_LENGTH != 0) body = body.copyOfRange(minOf(4, body.size), body.size)
                if (format and V4_UNSYNC != 0) body = resync(body)
            }
            when (id) {
                "SYLT", "SLT" -> decodeSylt(body).takeIf { it.isNotEmpty() }?.let { return FileLyrics(timedLines = it) }
                "USLT", "ULT" -> if (unsynced == null) unsynced = decodeUslt(body)
            }
        }
        return FileLyrics.ofText(unsynced)
    }

    /** encoding(1) language(3) descriptor(terminated) text. */
    internal fun decodeUslt(body: ByteArray): String? {
        if (body.size < 5) return null
        val encoding = body.u8(0)
        val charset = charsetOf(encoding) ?: return null
        val descriptorEnd = terminator(body, 4, encoding)
        if (descriptorEnd < 0) return null
        val textStart = descriptorEnd + terminatorLength(encoding)
        if (textStart > body.size) return null
        return String(body, textStart, body.size - textStart, charset).trimEnd('\u0000')
    }

    /**
     * encoding(1) language(3) timestamp-format(1) content-type(1) descriptor, then text/time pairs.
     *
     * Only millisecond stamps (format 2) are read: MPEG-frame stamps need the stream's frame rate,
     * which this does not know. Taggers write either one entry per line or one per syllable, the
     * latter marking each new line with a leading newline; both come out as whole lines.
     */
    internal fun decodeSylt(body: ByteArray): List<LyricLine> {
        if (body.size < 7) return emptyList()
        val encoding = body.u8(0)
        val charset = charsetOf(encoding) ?: return emptyList()
        if (body.u8(4) != TIMESTAMP_MS) return emptyList()
        val termLength = terminatorLength(encoding)
        val descriptorEnd = terminator(body, 6, encoding)
        if (descriptorEnd < 0) return emptyList()
        var pos = descriptorEnd + termLength
        val entries = mutableListOf<Pair<Long, String>>()
        while (pos < body.size) {
            val end = terminator(body, pos, encoding)
            if (end < 0 || end + termLength + 4 > body.size) break
            entries += body.uint32(end + termLength) to String(body, pos, end - pos, charset)
            pos = end + termLength + 4
        }
        val bySyllable = entries.any { (_, text) -> text.startsWith('\n') || text.startsWith('\r') }
        val lines = mutableListOf<LyricLine>()
        for ((time, text) in entries) {
            val newLine = !bySyllable || lines.isEmpty() || text.startsWith('\n') || text.startsWith('\r')
            if (newLine) {
                lines += LyricLine(timestampMs = time, text = text.trim())
            } else {
                val last = lines.removeAt(lines.lastIndex)
                lines += last.copy(text = (last.text + text).trim())
            }
        }
        return lines.filter { it.text.isNotEmpty() }.sortedBy { it.timestampMs }
    }

    private fun isPlain(major: Int, format: Int): Boolean = when (major) {
        3 -> format and (V3_COMPRESSED or V3_ENCRYPTED) == 0
        4 -> format and (V4_COMPRESSED or V4_ENCRYPTED) == 0
        else -> true
    }

    private fun charsetOf(encoding: Int) = when (encoding) {
        0 -> Charsets.ISO_8859_1
        1 -> Charsets.UTF_16 // reads the byte-order mark
        2 -> Charsets.UTF_16BE
        3 -> Charsets.UTF_8
        else -> null
    }

    private fun terminatorLength(encoding: Int) = if (encoding == 1 || encoding == 2) 2 else 1

    /** Index of the string terminator at or after [from], or -1. UTF-16 ones sit on even offsets. */
    private fun terminator(body: ByteArray, from: Int, encoding: Int): Int {
        if (terminatorLength(encoding) == 1) {
            for (i in from until body.size) if (body[i] == 0.toByte()) return i
            return -1
        }
        var i = from
        while (i + 1 < body.size) {
            if (body[i] == 0.toByte() && body[i + 1] == 0.toByte()) return i
            i += 2
        }
        return -1
    }

    /** Undoes unsynchronisation: every `FF 00` was written for a lone `FF`. */
    private fun resync(data: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(data.size)
        var i = 0
        while (i < data.size) {
            out.write(data[i].toInt())
            if (data.u8(i) == 0xFF && i + 1 < data.size && data[i + 1] == 0.toByte()) i++
            i++
        }
        return out.toByteArray()
    }

    private const val HEADER_BYTES = 10
    private const val FLAG_UNSYNC = 0x80
    private const val FLAG_EXTENDED = 0x40
    private const val FLAG_FOOTER = 0x10
    private const val V3_COMPRESSED = 0x80
    private const val V3_ENCRYPTED = 0x40
    private const val V4_COMPRESSED = 0x08
    private const val V4_ENCRYPTED = 0x04
    private const val V4_UNSYNC = 0x02
    private const val V4_DATA_LENGTH = 0x01
    private const val TIMESTAMP_MS = 2

    /** Big enough for a tag with cover art; a larger one is more likely damage than lyrics. */
    private const val MAX_TAG_BYTES = 32 * 1024 * 1024
}
