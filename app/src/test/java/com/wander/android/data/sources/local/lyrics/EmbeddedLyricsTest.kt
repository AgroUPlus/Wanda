package com.wander.android.data.sources.local.lyrics

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedLyricsTest {

    private fun bytes(vararg parts: Any): ByteArray = ByteArrayOutputStream().apply {
        for (part in parts) {
            when (part) {
                is String -> write(part.toByteArray(Charsets.ISO_8859_1))
                is ByteArray -> write(part)
                is Int -> write(part)
                else -> error("unsupported part")
            }
        }
    }.toByteArray()

    private fun be32(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
    private fun le32(v: Int) = byteArrayOf(v.toByte(), (v ushr 8).toByte(), (v ushr 16).toByte(), (v ushr 24).toByte())
    private fun syncsafe(v: Int) = byteArrayOf(
        ((v shr 21) and 0x7F).toByte(), ((v shr 14) and 0x7F).toByte(), ((v shr 7) and 0x7F).toByte(), (v and 0x7F).toByte()
    )

    private fun id3v23(vararg frames: Pair<String, ByteArray>): ByteArray {
        val body =
            bytes(*frames.flatMap { (id, data) -> listOf(id, be32(data.size), byteArrayOf(0, 0), data) }.toTypedArray())
        return bytes("ID3", 3, 0, 0, syncsafe(body.size), body)
    }

    private fun read(data: ByteArray) = EmbeddedLyrics.read(ByteArrayTagBytes(data))

    @Test
    fun `mp3 USLT text is read past its language and descriptor`() {
        val uslt = bytes(3, "eng", "desc", 0, "First line\nSecond line".toByteArray(Charsets.UTF_8))
        val lyrics = read(id3v23("TIT2" to bytes(3, "Title"), "USLT" to uslt))!!
        assertEquals("First line\nSecond line", lyrics.text)
        assertFalse(lyrics.isSynced)
        val data = lyrics.toLyricsData("local:1", "Embedded")!!
        assertEquals(listOf("First line", "Second line"), data.lines.map { it.text })
    }

    @Test
    fun `utf-16 USLT with a byte-order mark is decoded`() {
        val text = "Ça va".toByteArray(Charsets.UTF_16) // writes a BOM
        val uslt = bytes(1, "fra", 0xFF, 0xFE, 0, 0, text)
        assertEquals("Ça va", read(id3v23("USLT" to uslt))!!.text)
    }

    @Test
    fun `SYLT in milliseconds wins over USLT and keeps its timings`() {
        val sylt = bytes(0, "eng", 2, 1, 0, "Hello", 0, be32(1500), "World", 0, be32(3000))
        val lyrics = read(id3v23("USLT" to bytes(0, "eng", 0, "plain"), "SYLT" to sylt))!!
        assertEquals(listOf(1500L to "Hello", 3000L to "World"), lyrics.timedLines.map { it.timestampMs to it.text })
    }

    @Test
    fun `SYLT written per syllable is joined into lines`() {
        val sylt = bytes(0, "eng", 2, 1, 0, "\nHel", 0, be32(100), "lo", 0, be32(200), "\nNext", 0, be32(900))
        val lines = Id3Lyrics.decodeSylt(sylt)
        assertEquals(listOf("Hello", "Next"), lines.map { it.text })
        assertEquals(100L, lines.first().timestampMs)
    }

    @Test
    fun `LRC in a plain tag reads as synced`() {
        val uslt = bytes(0, "eng", 0, "[00:01.00]One\n[00:02.50]Two")
        val data = read(id3v23("USLT" to uslt))!!.toLyricsData("local:1", "Embedded")!!
        assertTrue(data.isSynced)
        assertEquals(listOf(1000L, 2500L), data.lines.map { it.timestampMs })
    }

    private fun vorbisComments(vararg fields: String): ByteArray =
        bytes(
            le32(3), "lib", le32(fields.size),
            *fields.flatMap {
                val utf8 = it.toByteArray(Charsets.UTF_8)
                listOf(le32(utf8.size), utf8)
            }.toTypedArray()
        )

    @Test
    fun `flac LYRICS comment is read, and the key is case-insensitive`() {
        val comments = vorbisComments("TITLE=x", "lyrics=Sung words")
        val flac = bytes("fLaC", 0x00, 0, 0, 2, byteArrayOf(1, 2), 0x84, 0, 0, comments.size, comments)
        assertEquals("Sung words", read(flac)!!.text)
    }

    @Test
    fun `UNSYNCEDLYRICS is read when LYRICS is absent`() {
        val comments = vorbisComments("UNSYNCEDLYRICS=Plain words")
        assertEquals("Plain words", VorbisLyrics.lyricsIn(comments, 0)!!.text)
    }

    @Test
    fun `ogg opus comment header spanning two pages is reassembled`() {
        val packet = bytes("OpusTags", vorbisComments("LYRICS=" + "a".repeat(300)))
        fun page(serial: Int, segments: List<Int>, body: ByteArray) =
            bytes(
                "OggS", 0, 0, ByteArray(8), be32(serial), ByteArray(8), segments.size,
                segments.map {
                    it.toByte()
                }.toByteArray(),
                body
            )
        val head = page(7, listOf(19), bytes("OpusHead", ByteArray(11)))
        val first = packet.copyOfRange(0, 255)
        val rest = packet.copyOfRange(255, packet.size)
        val pages = bytes(head, page(7, listOf(255), first), page(7, listOf(rest.size), rest))
        assertEquals("a".repeat(300), read(pages)!!.text)
    }

    private fun atom(type: Any, vararg children: ByteArray): ByteArray {
        val body = bytes(*children)
        return bytes(be32(body.size + 8), type, body)
    }

    @Test
    fun `m4a lyr atom is found after mdat`() {
        val data = atom("data", be32(1), be32(0), "Words".toByteArray(Charsets.UTF_8))
        val lyr = atom(bytes(0xA9, "lyr"), data)
        val meta = atom("meta", be32(0), atom("hdlr", ByteArray(25)), atom("ilst", lyr))
        val file =
            bytes(atom("ftyp", "M4A ".toByteArray()), atom("mdat", ByteArray(64)), atom("moov", atom("udta", meta)))
        assertEquals("Words", read(file)!!.text)
    }

    @Test
    fun `an untagged or unknown file has no lyrics`() {
        assertNull(read(id3v23("TIT2" to bytes(3, "Title"))))
        assertNull(read(bytes("RIFF", ByteArray(32))))
    }
}
