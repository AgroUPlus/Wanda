package com.wander.android.data.sources.local.lyrics

/**
 * The lyrics tagged into an audio file, whichever container it is: ID3 (MP3, and FLAC files a
 * tagger prefixed with ID3), FLAC, Ogg Vorbis/Opus, or MP4. The container is told by its magic
 * bytes, not by the file name, which is often wrong about m4a/mp4 and ogg/opus.
 */
internal object EmbeddedLyrics {

    fun read(bytes: TagBytes): FileLyrics? {
        val head = bytes.read(0, 12)
        return when {
            head.startsWithAscii("ID3") ->
                Id3Lyrics.read(bytes) ?: Id3Lyrics.tagEnd(head)?.let { VorbisLyrics.readFlac(bytes, it) }
            head.startsWithAscii("fLaC") -> VorbisLyrics.readFlac(bytes, 0)
            head.startsWithAscii("OggS") -> VorbisLyrics.readOgg(bytes)
            head.startsWithAscii("ftyp", 4) -> Mp4Lyrics.read(bytes)
            else -> null
        }
    }
}
