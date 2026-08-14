package com.wander.android

import com.wander.android.data.sources.archive.ArchiveFile
import com.wander.android.data.sources.archive.bestAudioPerRecording
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Archive items list the same recording in several formats. Search used to return one entry per
 * *item* with no playable file at all; these cover the flattening that replaced it.
 */
class ArchiveFormatsTest {

    @Test
    fun prefersLosslessAmongDuplicateFormats() {
        val files = listOf(
            ArchiveFile(name = "gd77-01.mp3", format = "VBR MP3", track = "1"),
            ArchiveFile(name = "gd77-01.flac", format = "Flac", track = "1"),
            ArchiveFile(name = "gd77-01.ogg", format = "Ogg Vorbis", track = "1")
        )

        val chosen = files.bestAudioPerRecording()

        assertEquals(1, chosen.size)
        assertEquals("gd77-01.flac", chosen.first().name)
    }

    @Test
    fun keepsOneEntryPerRecordingInTrackOrder() {
        val files = listOf(
            ArchiveFile(name = "b.mp3", format = "VBR MP3", track = "2"),
            ArchiveFile(name = "a.mp3", format = "VBR MP3", track = "1"),
            ArchiveFile(name = "notes.txt", format = "Text")
        )

        val chosen = files.bestAudioPerRecording()

        assertEquals(listOf("a.mp3", "b.mp3"), chosen.map { it.name })
    }

    @Test
    fun ignoresNonAudioFiles() {
        val files = listOf(
            ArchiveFile(name = "cover.jpg", format = "JPEG"),
            ArchiveFile(name = "meta.xml", format = "Metadata")
        )

        assertEquals(emptyList<ArchiveFile>(), files.bestAudioPerRecording())
    }
}
