package com.wander.android.core.playback

import androidx.media3.common.C
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeCheckpointTrackerTest {
    private fun track(id: String, episode: Boolean, durationMs: Long = 0L) = UnifiedTrack(
        id = id,
        source = SourceType.YTMUSIC,
        title = "t",
        artist = "a",
        durationMs = durationMs,
        isEpisode = episode
    )

    /** Runs [act] against a fresh tracker and returns every checkpoint it emitted. */
    private fun emitted(act: (EpisodeCheckpointTracker) -> Unit): List<EpisodeCheckpoint> {
        val tracker = EpisodeCheckpointTracker()
        val seen = mutableListOf<EpisodeCheckpoint>()
        runTest(UnconfinedTestDispatcher()) {
            val job = launch { tracker.episodeCheckpoints.toList(seen) }
            act(tracker)
            job.cancel()
        }
        return seen
    }

    @Test
    fun aSongIsNeverCheckpointed() {
        assertTrue(emitted { it.checkpoint(track("s", episode = false), 5_000, 60_000) }.isEmpty())
    }

    @Test
    fun noProgressMeansNothingToSave() {
        assertTrue(emitted { it.checkpoint(track("e", episode = true), 0, 60_000) }.isEmpty())
    }

    @Test
    fun anEpisodeIsSavedAtItsPositionWithThePlayersDuration() {
        val saved = emitted { it.checkpoint(track("e", episode = true), 5_000, 60_000) }

        assertEquals(listOf(EpisodeCheckpoint("e", 5_000, 60_000)), saved)
    }

    @Test
    fun aRememberedDurationBeatsTheOneReportedAtTheMomentOfSaving() {
        val episode = track("e", episode = true)
        val saved = emitted {
            it.rememberDuration(episode, 90_000)
            it.checkpoint(episode, 5_000, C.TIME_UNSET)
        }

        assertEquals(90_000L, saved.single().durationMs)
    }

    @Test
    fun aRememberedDurationBelongsToItsOwnEpisodeOnly() {
        val saved = emitted {
            it.rememberDuration(track("other", episode = true), 90_000)
            it.checkpoint(track("e", episode = true), 5_000, 60_000)
        }

        assertEquals(60_000L, saved.single().durationMs)
    }

    @Test
    fun anUnknownDurationFallsBackToTheTracksOwn() {
        val saved = emitted { it.checkpoint(track("e", episode = true, durationMs = 45_000), 5_000, C.TIME_UNSET) }

        assertEquals(45_000L, saved.single().durationMs)
    }

    @Test
    fun anUnsetDurationIsNeverRemembered() {
        val episode = track("e", episode = true, durationMs = 45_000)
        val saved = emitted {
            it.rememberDuration(episode, C.TIME_UNSET)
            it.rememberDuration(episode, 0)
            it.checkpoint(episode, 5_000, C.TIME_UNSET)
        }

        assertEquals(45_000L, saved.single().durationMs)
    }
}
