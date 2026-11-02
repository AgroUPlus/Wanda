package com.wander.android

import com.wander.android.core.audio.fingerprint.AudioEmbedder
import com.wander.android.data.repository.EmbeddingRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `coversTrack` decides whether the indexer may clear a track's attempts. It has to agree with
 * `TrackEmbeddingDao.needingIndex`, or a track it calls finished comes straight back next run.
 */
class EmbeddingCoverageTest {

    private fun segmentsFor(seconds: Int) = seconds * 1_000 / EmbeddingRepository.SEGMENT_HOP_MS

    @Test
    fun `a track embedded to its end is covered`() {
        assertTrue(EmbeddingRepository.coversTrack(declaredMs = 180_000L, segments = segmentsFor(180)))
    }

    @Test
    fun `a decode that stopped well short of the declared duration is not covered`() {
        assertFalse(EmbeddingRepository.coversTrack(declaredMs = 180_000L, segments = segmentsFor(120)))
    }

    @Test
    fun `the tolerance absorbs a container that over-declares by a few seconds`() {
        assertTrue(EmbeddingRepository.coversTrack(declaredMs = 183_000L, segments = segmentsFor(180)))
    }

    @Test
    fun `a track longer than the decode cap is covered once the cap is reached`() {
        val cap = AudioEmbedder.MAX_INDEXED_SECONDS
        assertTrue(EmbeddingRepository.coversTrack(declaredMs = 60 * 60_000L, segments = segmentsFor(cap)))
        assertFalse(EmbeddingRepository.coversTrack(declaredMs = 60 * 60_000L, segments = segmentsFor(cap / 2)))
    }

    @Test
    fun `a track with no declared duration is covered by any stored vectors`() {
        assertTrue(EmbeddingRepository.coversTrack(declaredMs = 0L, segments = 1))
        assertFalse(EmbeddingRepository.coversTrack(declaredMs = 0L, segments = 0))
    }
}
