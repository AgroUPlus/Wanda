package com.wander.android

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.TrackDeduplicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Grouping is the one piece of this whose mistakes are written to disk.
 *
 * `deduplicate` only decides what to draw, and a bad call there is a visible duplicate or a missing
 * row that comes back on the next refresh. `groupRecordings` decides what a *recording is*, and the
 * migration built on it folds likes and a year of play counts together permanently. So the cases
 * below are the ones that would ruin a library, not the ones that are easy to assert.
 */
class RecordingGroupingTest {

    private fun track(
        id: String,
        title: String,
        artist: String = "Radiohead",
        durationMs: Long = 240_000,
        source: SourceType = SourceType.YTMUSIC,
        album: String? = null
    ) = UnifiedTrack(
        id = id,
        source = source,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs
    )

    /** The point of the whole exercise: one song, held twice, is one recording. */
    @Test
    fun `the same song from two sources is one recording`() {
        val groups = TrackDeduplicator.groupRecordings(
            listOf(
                track("ytm:1", "All I Need", source = SourceType.YTMUSIC),
                track("navidrome:1", "All I Need", source = SourceType.NAVIDROME)
            )
        )

        assertEquals(1, groups.size)
        assertEquals(2, groups.first().size)
        // Renditions are ordered by preference, so the migration can pick a primary without
        // re-deriving the ranking.
        assertEquals(SourceType.NAVIDROME, groups.first().first().source)
    }

    /**
     * The failure that must never happen.
     *
     * A live take absorbing its studio original means a recording the user deliberately owns
     * disappears, and their like now points at a performance they did not choose.
     */
    @Test
    fun `a live take never merges into the studio cut`() {
        val groups = TrackDeduplicator.groupRecordings(
            listOf(
                track("ytm:studio", "Creep"),
                track("ytm:live", "Creep (Live)")
            )
        )

        assertEquals("live and studio must stay apart", 2, groups.size)
    }

    /** A remaster is the same performance, differently mastered. It should merge. */
    @Test
    fun `a remaster merges with its original`() {
        val groups = TrackDeduplicator.groupRecordings(
            listOf(
                track("navidrome:a", "Karma Police"),
                track("ytm:b", "Karma Police (Remastered 2011)")
            )
        )

        assertEquals(1, groups.size)
    }

    /** Same title, same artist, different arrangement — the lengths say so. */
    @Test
    fun `different lengths are different recordings`() {
        val groups = TrackDeduplicator.groupRecordings(
            listOf(
                track("ytm:short", "Paranoid Android", durationMs = 200_000),
                track("ytm:long", "Paranoid Android", durationMs = 383_000)
            )
        )

        assertEquals(2, groups.size)
    }

    /** Encoder and tagging drift is not a difference. */
    @Test
    fun `lengths within tolerance still merge`() {
        val groups = TrackDeduplicator.groupRecordings(
            listOf(
                track("local:a", "Nude", durationMs = 254_000, source = SourceType.LOCAL),
                track("ytm:b", "Nude", durationMs = 255_800)
            )
        )

        assertEquals(1, groups.size)
    }

    /**
     * An unknown length is not evidence of anything, and this is the function whose mistakes
     * persist — so a row without one is left alone rather than guessed at.
     */
    @Test
    fun `tracks with no duration are never merged`() {
        val groups = TrackDeduplicator.groupRecordings(
            listOf(
                track("ytm:a", "Reckoner", durationMs = 0),
                track("ytm:b", "Reckoner", durationMs = 0)
            )
        )

        assertEquals(2, groups.size)
    }

    /** Two different artists who share a song title are not one recording. */
    @Test
    fun `same title by different artists stays apart`() {
        val groups = TrackDeduplicator.groupRecordings(
            listOf(
                track("ytm:a", "Alive", artist = "Pearl Jam"),
                track("ytm:b", "Alive", artist = "Empire of the Sun")
            )
        )

        assertEquals(2, groups.size)
    }

    /** Nothing is lost: every input row belongs to exactly one group. */
    @Test
    fun `grouping partitions the input`() {
        val tracks = listOf(
            track("ytm:1", "All I Need", source = SourceType.YTMUSIC),
            track("navidrome:1", "All I Need", source = SourceType.NAVIDROME),
            track("ytm:2", "Creep (Live)"),
            track("ytm:3", "Nude", durationMs = 0),
            track("local:4", "Alive", artist = "Pearl Jam", source = SourceType.LOCAL)
        )

        val groups = TrackDeduplicator.groupRecordings(tracks)
        val regrouped = groups.flatten().map { it.id }.sorted()

        assertEquals(tracks.map { it.id }.sorted(), regrouped)
        assertTrue("no group may be empty", groups.none { it.isEmpty() })
    }
}
