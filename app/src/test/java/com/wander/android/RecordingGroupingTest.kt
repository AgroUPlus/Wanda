package com.wander.android

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.SplitSet
import com.wander.android.data.repository.TrackDeduplicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /**
     * The user's veto. Two rows that agree on everything the matcher can measure still stay apart
     * once someone has said they are different performances — otherwise the override on the merge
     * preview writes a row and changes nothing.
     */
    @Test
    fun `a pinned pair stays apart however well it matches`() {
        val ytm = track("ytm:1", "All I Need", source = SourceType.YTMUSIC)
        val navidrome = track("navidrome:1", "All I Need", source = SourceType.NAVIDROME)

        val groups = TrackDeduplicator.groupRecordings(
            listOf(ytm, navidrome),
            SplitSet.of(listOf("ytm:1" to "navidrome:1"))
        )

        assertEquals(2, groups.size)
        assertTrue(groups.all { it.size == 1 })
    }

    /**
     * Pins are pairwise on purpose. Keeping a bad YouTube upload away from the file on the phone
     * must not also throw away the Navidrome copy that matches both — a group label could not
     * express that, and the user would have to re-pin everything to fix one row.
     */
    @Test
    fun `a pin between two rows leaves a third matching both`() {
        val groups = TrackDeduplicator.groupRecordings(
            listOf(
                track("ytm:1", "All I Need", source = SourceType.YTMUSIC),
                track("navidrome:1", "All I Need", source = SourceType.NAVIDROME),
                track("local:1", "All I Need", source = SourceType.LOCAL)
            ),
            SplitSet.of(listOf("ytm:1" to "navidrome:1"))
        )

        assertEquals(2, groups.size)
        // The pinned pair cannot share a group; the third row joins whichever it meets first.
        assertTrue(groups.none { group -> group.map { it.id }.containsAll(listOf("ytm:1", "navidrome:1")) })
        assertEquals(3, groups.sumOf { it.size })
    }

    /** No pins means exactly the behaviour that shipped: the defaulted parameter changes nothing. */
    @Test
    fun `an empty split set groups exactly as before`() {
        val tracks = listOf(
            track("ytm:1", "All I Need", source = SourceType.YTMUSIC),
            track("navidrome:1", "All I Need", source = SourceType.NAVIDROME)
        )

        assertEquals(
            TrackDeduplicator.groupRecordings(tracks).map { group -> group.map { it.id } },
            TrackDeduplicator.groupRecordings(tracks, SplitSet.EMPTY).map { group -> group.map { it.id } }
        )
    }

    /** The same veto, at the pairwise entry point the likes path uses. */
    @Test
    fun `a pinned pair is not the same recording`() {
        val ytm = track("ytm:1", "All I Need", source = SourceType.YTMUSIC)
        val navidrome = track("navidrome:1", "All I Need", source = SourceType.NAVIDROME)
        val splits = SplitSet.of(listOf("ytm:1" to "navidrome:1"))

        assertTrue(TrackDeduplicator.isSameRecording(ytm, navidrome))
        assertFalse(TrackDeduplicator.isSameRecording(ytm, navidrome, splits))
    }
}
