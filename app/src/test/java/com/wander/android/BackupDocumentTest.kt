package com.wander.android

import com.wander.android.core.backup.BackupDocument
import com.wander.android.core.backup.BackupEntry
import com.wander.android.core.backup.BackupJson
import com.wander.android.core.backup.BackupSection
import com.wander.android.core.backup.includedSections
import com.wander.android.core.backup.sectionDigests
import com.wander.android.core.backup.verifyIntegrity
import com.wander.android.core.backup.BackupPlay
import com.wander.android.core.backup.BackupRecap
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/** What a backup carries survives the round trip whole, and a damaged one is refused. */
class BackupDocumentTest {

    @Test
    fun `history and recaps round-trip`() {
        val original = BackupDocument(
            history = listOf(
                BackupPlay("track-a", 1_700_000_000_000L),
                BackupPlay("track-b", 1_700_000_060_000L)
            ),
            recaps = listOf(recap(2025), recap(2026))
        )

        assertEquals(original, BackupJson.decodeFromString<BackupDocument>(BackupJson.encodeToString(original)))
    }

    @Test
    fun `a sealed manifest verifies after a round trip`() {
        val restored = BackupJson.decodeFromString<BackupDocument>(BackupJson.encodeToString(sealed()))

        restored.verifyIntegrity(BackupJson)
        assertEquals(setOf(BackupSection.SETTINGS, BackupSection.HISTORY), restored.includedSections)
    }

    /** A section that came back short must stop the restore before anything is written. */
    @Test(expected = IOException::class)
    fun `a section missing a record fails verification`() {
        val document = sealed()
        document.copy(history = document.history.drop(1)).verifyIntegrity(BackupJson)
    }

    @Test(expected = IOException::class)
    fun `a changed setting fails verification`() {
        val document = sealed()
        document.copy(entries = mapOf("key_amoled_black" to BackupEntry("bool", "false")))
            .verifyIntegrity(BackupJson)
    }

    @Test(expected = IOException::class)
    fun `a backup listing no sections is refused`() {
        BackupDocument(entries = mapOf("key_amoled_black" to BackupEntry("bool", "true"))).verifyIntegrity(BackupJson)
    }

    private fun sealed(): BackupDocument {
        val document = BackupDocument(
            entries = mapOf("key_amoled_black" to BackupEntry("bool", "true")),
            history = listOf(BackupPlay("a", 1L), BackupPlay("b", 2L))
        )
        return document.copy(
            manifest = document.sectionDigests(BackupJson, setOf(BackupSection.SETTINGS, BackupSection.HISTORY))
        )
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
