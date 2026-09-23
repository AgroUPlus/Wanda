package com.wander.android

import com.wander.android.core.backup.toBackup
import com.wander.android.core.backup.toEntity
import com.wander.android.core.database.entity.EpisodeProgressEntity
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.playback.EpisodeJumps
import com.wander.android.data.model.EpisodeState
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.EpisodeProgressRepository.Companion.stateOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeSupportTest {

    private fun progress(positionMs: Long, durationMs: Long = 3_600_000L) =
        EpisodeProgressEntity(trackId = "yt:1", positionMs = positionMs, durationMs = durationMs, updatedAt = 0L)

    @Test
    fun `an episode opened and barely heard counts as unplayed`() {
        assertEquals(EpisodeState.UNPLAYED, stateOf(null))
        assertEquals(EpisodeState.UNPLAYED, stateOf(progress(9_999L)))
    }

    @Test
    fun `ten seconds in is in progress`() {
        assertEquals(EpisodeState.IN_PROGRESS, stateOf(progress(10_000L)))
    }

    @Test
    fun `the last thirty seconds count as finished`() {
        assertEquals(EpisodeState.IN_PROGRESS, stateOf(progress(3_569_999L)))
        assertEquals(EpisodeState.FINISHED, stateOf(progress(3_570_000L)))
    }

    @Test
    fun `an unknown duration is never finished`() {
        assertEquals(EpisodeState.IN_PROGRESS, stateOf(progress(5_000_000L, durationMs = 0L)))
    }

    @Test
    fun `jumps stay inside the episode`() {
        assertEquals(0L, EpisodeJumps.target(4_000L, -EpisodeJumps.BACK_MS, 60_000L))
        assertEquals(60_000L, EpisodeJumps.target(50_000L, EpisodeJumps.FORWARD_MS, 60_000L))
        assertEquals(40_000L, EpisodeJumps.target(10_000L, EpisodeJumps.FORWARD_MS, 60_000L))
        // Duration not known yet: only the start is a hard edge.
        assertEquals(80_000L, EpisodeJumps.target(50_000L, EpisodeJumps.FORWARD_MS, 0L))
    }

    @Test
    fun `the episode flag survives Room and backups`() {
        val episode = UnifiedTrack(id = "${SourceType.YTMUSIC.idPrefix}abc", source = SourceType.YTMUSIC, title = "Ep", artist = "Show", isEpisode = true)
        val entity = TrackEntity.fromUnifiedTrack(episode)

        assertTrue(entity.toUnifiedTrack().isEpisode)
        assertTrue(entity.toBackup().toEntity()!!.isEpisode)
    }
}
