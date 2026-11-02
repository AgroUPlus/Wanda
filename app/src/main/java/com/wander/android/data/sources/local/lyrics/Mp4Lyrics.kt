package com.wander.android.data.sources.local.lyrics

/**
 * Lyrics from an MP4/M4A file's iTunes metadata: `moov/udta/meta/ilst/©lyr/data`.
 *
 * The atoms are walked by position rather than read whole, because `moov` is often at the end of
 * the file behind the audio, and `mdat` before it can be hundreds of megabytes.
 */
internal object Mp4Lyrics {

    fun read(bytes: TagBytes): FileLyrics? {
        val moov = child(bytes, 0, bytes.size, "moov") ?: return null
        // Usually under udta; some encoders put meta straight under moov.
        val meta = child(bytes, moov.first, moov.second, "udta")
            ?.let { child(bytes, it.first, it.second, "meta") }
            ?: child(bytes, moov.first, moov.second, "meta")
            ?: return null
        // iTunes writes meta as a full box (4 bytes of version and flags); QuickTime does not.
        val metaStart = if (bytes.read(meta.first + 4, 4).startsWithAscii("hdlr")) meta.first else meta.first + 4
        val ilst = child(bytes, metaStart, meta.second, "ilst") ?: return null
        val lyr = child(bytes, ilst.first, ilst.second, LYRICS_ATOM) ?: return null
        val data = child(bytes, lyr.first, lyr.second, "data") ?: return null
        val length = data.second - data.first
        if (length < DATA_PREFIX || length > MAX_LYRICS_BYTES) return null
        val payload = bytes.read(data.first, length.toInt())
        if (payload.size < DATA_PREFIX || payload.int32(0) and 0xFFFFFF != TYPE_UTF8) return null
        return FileLyrics.ofText(String(payload, DATA_PREFIX, payload.size - DATA_PREFIX, Charsets.UTF_8))
    }

    /** The payload range (start, end) of the first [type] atom directly within [start]..[end]. */
    private fun child(bytes: TagBytes, start: Long, end: Long, type: String): Pair<Long, Long>? {
        var pos = start
        while (pos + 8 <= end) {
            val header = bytes.read(pos, 16)
            if (header.size < 8) return null
            var size = header.uint32(0)
            var headerLength = 8
            when (size) {
                // A 64-bit size follows the type: how a large mdat is written.
                1L -> {
                    if (header.size < 16) return null
                    size = header.int64(8)
                    headerLength = 16
                }
                // Runs to the end of its parent.
                0L -> size = end - pos
            }
            if (size < headerLength) return null
            if (typeMatches(header, type)) return (pos + headerLength) to minOf(pos + size, end)
            pos += size
        }
        return null
    }

    private fun typeMatches(header: ByteArray, type: String): Boolean =
        if (type == LYRICS_ATOM) {
            header.u8(4) == 0xA9 && header.startsWithAscii("lyr", 5)
        } else {
            header.startsWithAscii(type, 4)
        }

    /** `©lyr`; the © is the single byte 0xA9, which is matched by value rather than as text. */
    private const val LYRICS_ATOM = "©lyr"

    /** type indicator(4) + locale(4) ahead of the text in a `data` atom. */
    private const val DATA_PREFIX = 8
    private const val TYPE_UTF8 = 1
    private const val MAX_LYRICS_BYTES = 1024L * 1024L
}
