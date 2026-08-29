package com.wander.android

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.SplitSet
import com.wander.android.data.repository.TrackDeduplicator
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Play counts and history belong to the recording, not to the copy that was played.
 *
 * The totalling itself is a `sumOf` over what [TrackDeduplicator.groupRecordings] returns, so what
 * is worth asserting is that the grouping puts the right rows together and that
 * [TrackDeduplicator.distinctRecordings] keeps the *first* copy rather than the best-ranked one —
 * a history is ordered by when, and swapping in a better copy moves the entry to the wrong moment.
 */
class RecordingPlayCountTest {

    private fun track(
        id: String,
        title: String = "All I Need",
        artist: String = "Radiohead",
        durationMs: Long = 240_000,
        source: SourceType = SourceType.YTMUSIC,
        playCount: Int = 0
    ) = UnifiedTrack(
        id = id,
        source = source,
        title = title,
        artist = artist,
        durationMs = durationMs,
        playCount = playCount
    )

    @Test
    fun `plays on two copies total onto one recording`() {
        val groups = TrackDeduplicator.groupRecordings(
            listOf(
                track("ytm:1", source = SourceType.YTMUSIC, playCount = 8),
                track("navidrome:1", source = SourceType.NAVIDROME, playCount = 12)
            )
        )

        assertEquals(1, groups.size)
        assertEquals(20, groups.single().sumOf { it.playCount })
    }

    /** The representative is the best-ranked copy, which is what the rest of the app shows. */
    @Test
    fun `the totalled entry is the best ranked copy`() {
        val groups = TrackDeduplicator.groupRecordings(
            listOf(
                track("ytm:1", source = SourceType.YTMUSIC, playCount = 8),
                track("navidrome:1", source = SourceType.NAVIDROME, playCount = 12)
            )
        )

        assertEquals("navidrome:1", groups.single().first().id)
    }

    /** A pinned pair is two recordings, so its plays stay apart. */
    @Test
    fun `a pinned pair does not have its plays totalled together`() {
        val groups = TrackDeduplicator.groupRecordings(
            listOf(
                track("ytm:1", source = SourceType.YTMUSIC, playCount = 8),
                track("navidrome:1", source = SourceType.NAVIDROME, playCount = 12)
            ),
            SplitSet.of(listOf("ytm:1" to "navidrome:1"))
        )

        assertEquals(2, groups.size)
        assertEquals(listOf(8, 12), groups.map { group -> group.sumOf { it.playCount } }.sorted())
    }

    @Test
    fun `a history keeps the first copy of a recording, not the best ranked one`() {
        val newestFirst = listOf(
            track("ytm:1", source = SourceType.YTMUSIC),
            track("navidrome:1", source = SourceType.NAVIDROME)
        )

        val collapsed = TrackDeduplicator.distinctRecordings(newestFirst)

        assertEquals(listOf("ytm:1"), collapsed.map { it.id })
    }

    @Test
    fun `a history keeps genuinely different recordings apart`() {
        val history = listOf(
            track("ytm:1", title = "All I Need"),
            track("ytm:2", title = "All I Need (Live)"),
            track("ytm:3", title = "Nude")
        )

        assertEquals(3, TrackDeduplicator.distinctRecordings(history).size)
    }

    @Test
    fun `a pinned pair stays as two history entries`() {
        val history = listOf(
            track("ytm:1", source = SourceType.YTMUSIC),
            track("navidrome:1", source = SourceType.NAVIDROME)
        )

        val collapsed = TrackDeduplicator.distinctRecordings(
            history,
            SplitSet.of(listOf("ytm:1" to "navidrome:1"))
        )

        assertEquals(2, collapsed.size)
    }
}
