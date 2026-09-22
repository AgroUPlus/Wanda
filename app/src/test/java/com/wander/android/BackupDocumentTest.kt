package com.wander.android

import com.wander.android.core.backup.BackupDocument
import com.wander.android.core.backup.BackupPlay
import com.wander.android.core.backup.BackupRecap
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backup file's compatibility contract, in both directions.
 *
 * Version 2 added listening history and saved recaps. Neither may break a file written by a build
 * that predates them, and a file written *by* this build has to stay readable by one — which is
 * what the optional fields and the uncompressed payload are for.
 *
 * The `Json` here is configured the same way `SettingsBackupStore` configures its own. That is the
 * one thing this test cannot prove and has to restate: `ignoreUnknownKeys` is what makes a file
 * from a newer build survive, and it is set at the only call site.
 */
class BackupDocumentTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** Exactly what a version 1 backup's decrypted payload looked like. */
    private val version1 =
        """{"version":1,"entries":{"key_amoled_black":{"type":"bool","value":"true"},""" +
            """"key_agro_user":{"type":"string","value":"ana"}}}"""

    @Test
    fun `a version 1 backup still restores`() {
        val document = json.decodeFromString<BackupDocument>(version1)

        assertEquals(1, document.version)
        assertEquals(2, document.entries.size)
        assertEquals("ana", document.entries["key_agro_user"]?.value)
        assertTrue("it simply carried no history", document.history.isEmpty())
        assertTrue(document.recaps.isEmpty())
    }

    /** A field this build has never heard of must be ignored, not fatal. */
    @Test
    fun `a backup from a newer build decodes as far as it can`() {
        val fromTheFuture =
            """{"version":9,"entries":{},"history":[],"recaps":[],"somethingNew":{"a":1}}"""

        val document = json.decodeFromString<BackupDocument>(fromTheFuture)

        assertEquals(9, document.version)
        assertTrue(document.entries.isEmpty())
    }

    @Test
    fun `a version 2 backup round-trips its history and recaps`() {
        val original = BackupDocument(
            entries = emptyMap(),
            history = listOf(
                BackupPlay("track-a", 1_700_000_000_000L),
                BackupPlay("track-b", 1_700_000_060_000L)
            ),
            recaps = listOf(recap(2025), recap(2026))
        )

        val restored = json.decodeFromString<BackupDocument>(json.encodeToString(original))

        assertEquals(2, restored.version)
        assertEquals(2, restored.history.size)
        assertEquals("track-a", restored.history.first().trackId)
        assertEquals(1_700_000_060_000L, restored.history[1].playedAt)
        assertEquals(listOf(2025, 2026), restored.recaps.map { it.year })
        assertEquals(original, restored)
    }

    /**
     * The payload stays plain JSON, which is what lets an older build decrypt a file written here
     * and read the settings out of it. Compressing it would turn that into a damaged-file error.
     */
    @Test
    fun `a version 2 payload is readable text an older build can parse`() {
        val encoded = json.encodeToString(
            BackupDocument(entries = emptyMap(), history = listOf(BackupPlay("a", 1L)))
        )

        assertTrue(encoded.trimStart().startsWith("{"))
        assertTrue(encoded.contains("\"entries\""))
        assertTrue("and the new fields are simply extra keys", encoded.contains("\"history\""))
    }

    private fun recap(year: Int) = BackupRecap(
        year = year,
        generatedAt = 1_700_000_000_000L,
        isFleetWide = true,
        totalMinutes = 4_000,
        totalPlays = 900,
        longestStreakDays = 12,
        newArtistsCount = 40,
        payloadJson = """{"year":$year}"""
    )
}
