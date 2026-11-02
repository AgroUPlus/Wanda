package com.wander.android.data.sources.local.lyrics

/**
 * Lyrics from Vorbis comments — FLAC's `VORBIS_COMMENT` block, and the comment header of an Ogg
 * Vorbis or Opus stream. `LYRICS` is the field taggers write (often as LRC); `UNSYNCEDLYRICS` is
 * the one foobar2000 and Mp3tag use for plain text, read when `LYRICS` is absent.
 */
internal object VorbisLyrics {

    /** A FLAC stream starting at [start] — after any ID3 tag a tagger left in front of it. */
    fun readFlac(bytes: TagBytes, start: Long): FileLyrics? {
        if (!bytes.read(start, 4).startsWithAscii("fLaC")) return null
        var pos = start + 4
        while (true) {
            val header = bytes.read(pos, 4)
            if (header.size < 4) return null
            val type = header.u8(0) and 0x7F
            val length = header.int24(1)
            pos += 4
            if (type == BLOCK_VORBIS_COMMENT) return lyricsIn(bytes.read(pos, length), 0)
            if (header.u8(0) and LAST_BLOCK != 0) return null
            pos += length
        }
    }

    /**
     * The second packet of the first logical stream, reassembled from as many pages as it spans —
     * a comment header carrying cover art easily runs past one page.
     */
    fun readOgg(bytes: TagBytes): FileLyrics? {
        var pos = 0L
        var serial: Int? = null
        var packetIndex = 0
        val packet = java.io.ByteArrayOutputStream()
        while (packetIndex < 2) {
            val header = bytes.read(pos, OGG_HEADER_BYTES)
            if (header.size < OGG_HEADER_BYTES || !header.startsWithAscii("OggS")) return null
            val segments = header.u8(26)
            val table = bytes.read(pos + OGG_HEADER_BYTES, segments)
            if (table.size < segments) return null
            var bodyPos = pos + OGG_HEADER_BYTES + segments
            val pageSerial = header.int32(14)
            val ours = serial == null || serial == pageSerial
            if (serial == null) serial = pageSerial
            for (i in 0 until segments) {
                val length = table.u8(i)
                if (ours && packetIndex == 1) {
                    if (packet.size() + length > MAX_COMMENT_BYTES) return null
                    packet.write(bytes.read(bodyPos, length))
                }
                bodyPos += length
                // A segment shorter than 255 bytes ends its packet.
                if (ours && length < 255) packetIndex++
                if (packetIndex >= 2) break
            }
            pos = bodyPos
        }
        val comment = packet.toByteArray()
        return when {
            comment.startsWithAscii(VORBIS_COMMENT_MAGIC) -> lyricsIn(comment, VORBIS_COMMENT_MAGIC.length)
            comment.startsWithAscii("OpusTags") -> lyricsIn(comment, 8)
            else -> null
        }
    }

    /** The lyrics among the comments starting at [offset], or null. */
    internal fun lyricsIn(block: ByteArray, offset: Int): FileLyrics? {
        val fields = comments(block, offset)
        return FileLyrics.ofText(fields[LYRICS]) ?: FileLyrics.ofText(fields[UNSYNCED_LYRICS])
    }

    /**
     * vendor-length(LE32) vendor count(LE32), then length-prefixed `KEY=value` pairs. Keys are
     * case-insensitive by the spec; the first of a repeated one is kept. A truncated block yields
     * what was read before the damage.
     */
    private fun comments(block: ByteArray, offset: Int): Map<String, String> {
        val out = HashMap<String, String>()
        if (offset + 4 > block.size) return out
        var pos = offset.toLong() + 4 + block.uint32Le(offset)
        if (pos + 4 > block.size) return out
        val count = block.uint32Le(pos.toInt())
        pos += 4
        var read = 0L
        while (read < count && pos + 4 <= block.size) {
            val length = block.uint32Le(pos.toInt())
            pos += 4
            if (pos + length > block.size) break
            val field = String(block, pos.toInt(), length.toInt(), Charsets.UTF_8)
            pos += length
            read++
            val equals = field.indexOf('=')
            if (equals <= 0) continue
            val key = field.substring(0, equals).uppercase()
            if (key == LYRICS || key == UNSYNCED_LYRICS) out.putIfAbsent(key, field.substring(equals + 1))
        }
        return out
    }

    private const val BLOCK_VORBIS_COMMENT = 4
    private const val LAST_BLOCK = 0x80
    private const val OGG_HEADER_BYTES = 27
    private const val VORBIS_COMMENT_MAGIC = "\u0003vorbis"
    private const val LYRICS = "LYRICS"
    private const val UNSYNCED_LYRICS = "UNSYNCEDLYRICS"

    /** Room for embedded cover art ahead of the text; past it the file is not worth reading on. */
    private const val MAX_COMMENT_BYTES = 16 * 1024 * 1024
}
