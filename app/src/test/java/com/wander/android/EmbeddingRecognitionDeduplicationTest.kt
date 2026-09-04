package com.wander.android

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.EmbeddingRepository
import com.wander.android.data.repository.RecordingLinkSet
import com.wander.android.data.repository.SplitSet
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddingRecognitionDeduplicationTest {

    private fun track(
        id: String,
        title: String,
        artist: String = "Kyle Patrick Naval",
        durationMs: Long = 180_000L,
        source: SourceType = SourceType.NAVIDROME
    ) = UnifiedTrack(
        id = id,
        source = source,
        title = title,
        artist = artist,
        durationMs = durationMs
    )

    private fun match(trackId: String, similarity: Float, positionSeconds: Int = 10) =
        EmbeddingRepository.Match(trackId, similarity, positionSeconds)

    @Test
    fun `decide accepts clear winner over distant runner up`() {
        // winner 0.95 vs runnerUp 0.35 -> margin 0.60 >= 0.04 and sim >= 0.55
        assertTrue(EmbeddingRepository.decide(0.95f, 0.35f))
    }

    @Test
    fun `decide rejects winner below minimum similarity floor`() {
        assertFalse(EmbeddingRepository.decide(0.50f, 0.20f))
    }

    @Test
    fun `decide rejects close competitor of another song`() {
        // winner 0.95 vs close runnerUp 0.93 -> margin 0.02 < 0.04
        assertFalse(EmbeddingRepository.decide(0.95f, 0.93f))
    }

    @Test
    fun `runner up duplicate is skipped in favor of first distinct recording`() = runTest {
        val winner = track("navidrome:1", "Etiquette", "Kyle Patrick Naval")
        val duplicate = track("ytm:1", "Etiquette", "Kyle Patrick Naval", source = SourceType.YTMUSIC)
        val competitor = track("navidrome:2", "Different Song", "Kyle Patrick Naval")

        val trackMap = mapOf(
            "navidrome:1" to winner,
            "ytm:1" to duplicate,
            "navidrome:2" to competitor
        )

        val candidates = listOf(
            match("navidrome:1", 0.998f),
            match("ytm:1", 0.998f),
            match("navidrome:2", 0.370f)
        )

        val result = EmbeddingRepository.findCompetitor(
            bestUnified = winner,
            candidates = candidates,
            splits = SplitSet.EMPTY,
            links = RecordingLinkSet.EMPTY
        ) { id -> trackMap[id] }

        assertEquals("navidrome:2", result?.trackId)
        assertEquals(0.370f, result?.similarity ?: 0f, 0.001f)
    }

    @Test
    fun `runner up with distinct metadata is immediately selected`() = runTest {
        val winner = track("navidrome:1", "Etiquette", "Kyle Patrick Naval")
        val competitor = track("navidrome:2", "Alive Again", "Kyle Patrick Naval")

        val trackMap = mapOf(
            "navidrome:1" to winner,
            "navidrome:2" to competitor
        )

        val candidates = listOf(
            match("navidrome:1", 0.950f),
            match("navidrome:2", 0.400f)
        )

        val result = EmbeddingRepository.findCompetitor(
            bestUnified = winner,
            candidates = candidates
        ) { id -> trackMap[id] }

        assertEquals("navidrome:2", result?.trackId)
    }

    @Test
    fun `runner up linked by fingerprint is recognized as duplicate and skipped`() = runTest {
        val winner = track("navidrome:1", "Song Title", "Artist A")
        val duplicate = track("ytm:1", "Different Tag Name", "Artist B")
        val competitor = track("navidrome:2", "Another Song", "Artist C")

        val trackMap = mapOf(
            "navidrome:1" to winner,
            "ytm:1" to duplicate,
            "navidrome:2" to competitor
        )

        val candidates = listOf(
            match("navidrome:1", 0.980f),
            match("ytm:1", 0.975f),
            match("navidrome:2", 0.350f)
        )

        val links = RecordingLinkSet.of(listOf("navidrome:1" to "ytm:1"))

        val result = EmbeddingRepository.findCompetitor(
            bestUnified = winner,
            candidates = candidates,
            links = links
        ) { id -> trackMap[id] }

        assertEquals("navidrome:2", result?.trackId)
    }

    @Test
    fun `user split overrides duplicate status and keeps runner up as competitor`() = runTest {
        val winner = track("navidrome:1", "Etiquette", "Kyle Patrick Naval")
        val duplicatePinnedApart = track("ytm:1", "Etiquette", "Kyle Patrick Naval")

        val trackMap = mapOf(
            "navidrome:1" to winner,
            "ytm:1" to duplicatePinnedApart
        )

        val candidates = listOf(
            match("navidrome:1", 0.998f),
            match("ytm:1", 0.995f)
        )

        val splits = SplitSet.of(listOf("navidrome:1" to "ytm:1"))

        val result = EmbeddingRepository.findCompetitor(
            bestUnified = winner,
            candidates = candidates,
            splits = splits
        ) { id -> trackMap[id] }

        // Because of the split, ytm:1 is treated as a distinct performance / competitor
        assertEquals("ytm:1", result?.trackId)
    }

    @Test
    fun `returns null when all candidates are duplicates`() = runTest {
        val winner = track("navidrome:1", "Etiquette", "Kyle Patrick Naval")
        val duplicate = track("ytm:1", "Etiquette", "Kyle Patrick Naval")

        val trackMap = mapOf(
            "navidrome:1" to winner,
            "ytm:1" to duplicate
        )

        val candidates = listOf(
            match("navidrome:1", 0.998f),
            match("ytm:1", 0.998f)
        )

        val result = EmbeddingRepository.findCompetitor(
            bestUnified = winner,
            candidates = candidates
        ) { id -> trackMap[id] }

        assertNull(result)
    }
}
