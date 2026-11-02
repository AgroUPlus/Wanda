package com.wander.android.core.backup

import com.wander.android.core.database.entity.TrackEmbeddingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

/** The sections added later verify like the first ones, and leave older backups untouched. */
class BackupExtrasIntegrityTest {

    private val play = BackupPlay("t1", 1_000L)

    private fun sealed(document: BackupDocument, sections: Set<BackupSection>): ByteArray =
        document.copy(manifest = document.sectionDigests(BackupJson, sections)).toJsonBytes()

    @Test
    fun aBackupWithoutTheNewSectionsStillVerifiesAndEncodesNoneOfThem() {
        val bytes = sealed(BackupDocument(history = listOf(play)), setOf(BackupSection.HISTORY))
        val text = String(bytes, Charsets.UTF_8)
        listOf("canonical", "sharedPlaylists", "jamRecaps", "features", "embeddings").forEach {
            assertFalse("$it leaked into an old-shape backup", text.contains("\"$it\""))
        }
        assertEquals(listOf(play), bytes.toVerifiedDocument().history)
    }

    @Test
    fun everyNewSectionRoundTripsAndVerifies() {
        val document = BackupDocument(
            canonical = listOf(BackupCanonical("t1", "Title", null, null, "rec-1", 5L)),
            sharedPlaylists = listOf(
                BackupSharedPlaylist(
                    agroId = "a1", title = "Mix", ownerId = "o", visibility = "PRIVATE", editAccess = "OFF",
                    myRole = "OWNER", revision = 3L, publishedAt = 1L,
                    items = listOf(BackupSharedItem("i1", 0, "t1", "Title", "Artist"))
                )
            ),
            jamRecaps = listOf(BackupJamRecap("j1", "2026-10-01T00:00:00Z", "{}")),
            features = listOf(BackupFeatures("t1", 120f, 0.5f, 0.4f, 0.3f, 0.1f, -0.1f, 1, 9L)),
            embeddings = listOf(embedding().toBackup())
        )
        val sections = setOf(BackupSection.METADATA, BackupSection.SHARED, BackupSection.FEATURES, BackupSection.EMBEDDINGS)
        val restored = sealed(document, sections).toVerifiedDocument()

        assertEquals(sections, restored.includedSections)
        assertEquals(document.canonical, restored.canonical)
        assertEquals(document.sharedPlaylists, restored.sharedPlaylists)
        assertEquals(document.jamRecaps, restored.jamRecaps)
        assertEquals(document.features, restored.features)
        assertEquals(embedding(), restored.embeddings.single().toEntity())
    }

    @Test
    fun aNewSectionThatLostARecordIsRefused() {
        val document = BackupDocument(jamRecaps = listOf(BackupJamRecap("j1", "x", "{}"), BackupJamRecap("j2", "y", "{}")))
        val manifest = document.sectionDigests(BackupJson, setOf(BackupSection.SHARED))
        val short = document.copy(jamRecaps = document.jamRecaps.take(1), manifest = manifest).toJsonBytes()
        assertThrows(IOException::class.java) { short.toVerifiedDocument() }
    }

    @Test
    fun fingerprintsAreNotChosenByDefault() {
        assertFalse(BackupSection.EMBEDDINGS in BackupSection.defaults)
        assertEquals(BackupSection.entries.size - 1, BackupSection.defaults.size)
    }

    private fun embedding() = TrackEmbeddingEntity(
        trackId = "t1",
        vector = ByteArray(256) { it.toByte() },
        centroid = ByteArray(128) { (it * 3).toByte() },
        dim = 128,
        model = "nmfp-triplet",
        version = 2,
        computedAt = 42L
    )
}
