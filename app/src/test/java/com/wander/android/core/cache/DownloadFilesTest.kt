package com.wander.android.core.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

class DownloadFilesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun dir(): File = tmp.newFolder("downloads")

    @Test
    fun `a finished download lands at its final name and leaves no part file`() {
        val dir = dir()
        val destination = File(dir, "ytm_abc.opus")

        DownloadFiles(dir).write(ByteArrayInputStream(byteArrayOf(1, 2, 3)), destination)

        assertEquals(3L, destination.length())
        assertEquals(listOf("ytm_abc.opus"), dir.list()!!.toList())
    }

    @Test
    fun `a dropped connection deletes the part and never touches the final file`() {
        val dir = dir()
        val destination = File(dir, "ytm_abc.opus").apply { writeBytes(byteArrayOf(9)) }
        val dropping = object : InputStream() {
            private var sent = 0
            override fun read(): Int = if (sent++ < 10) 0 else throw IOException("connection reset")
        }

        val result = runCatching { DownloadFiles(dir).write(dropping, destination) }

        assertTrue(result.isFailure)
        assertEquals("the earlier copy was overwritten", 1L, destination.length())
        assertEquals(listOf("ytm_abc.opus"), dir.list()!!.toList())
    }

    @Test
    fun `old unreferenced files are swept and referenced ones kept`() {
        val dir = dir()
        val now = System.currentTimeMillis()
        val old = now - ORPHAN_MIN_AGE_MS - 1_000
        val kept = File(dir, "ytm_kept.opus").apply { writeBytes(byteArrayOf(1)); setLastModified(old) }
        val orphan = File(dir, "ytm_gone.opus").apply { writeBytes(byteArrayOf(1)); setLastModified(old) }
        val stalePart = File(dir, "ytm_x.123.part").apply { writeBytes(byteArrayOf(1)); setLastModified(old) }

        // Another spelling of the same directory still counts as a reference.
        val deleted = DownloadFiles(dir).sweepOrphans(listOf("/data/user/0/app/files/downloads/ytm_kept.opus"), now)

        assertEquals(2, deleted)
        assertTrue(kept.exists())
        assertFalse(orphan.exists())
        assertFalse(stalePart.exists())
    }

    /** A concurrent run's file in progress is unreferenced but recent, and must survive. */
    @Test
    fun `a recent unreferenced file is left alone`() {
        val dir = dir()
        val inProgress = File(dir, "ytm_y.456.part").apply { writeBytes(byteArrayOf(1)) }

        val deleted = DownloadFiles(dir).sweepOrphans(emptyList(), System.currentTimeMillis())

        assertEquals(0, deleted)
        assertTrue(inProgress.exists())
    }
}
