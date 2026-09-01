package com.wander.android

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.RecordingLinkSet
import com.wander.android.data.repository.SplitSet
import com.wander.android.data.repository.TrackDeduplicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the fingerprinter's verdict is allowed to do to a library.
 *
 * The links exist for rows the metadata rules can never join: an upload whose artist field holds
 * the channel's name and whose title carries half a sentence of decoration. If a link did not
 * outrank the tags, indexing a library would change nothing at all — which is the state this
 * replaces. If it outranked the user's pins, a wrong match would be unappealable.
 */
class RecordingLinkGroupingTest {

    private fun track(
        id: String,
        title: String,
        artist: String = "Radiohead",
        durationMs: Long = 240_000,
        source: SourceType = SourceType.YTMUSIC
    ) = UnifiedTrack(
        id = id,
        source = source,
        title = title,
        artist = artist,
        album = null,
        durationMs = durationMs
    )

    /** The case the fingerprints were built for: the tags agree about nothing, the audio does. */
    @Test
    fun `a link joins two rows whose metadata share no key`() {
        val tracks = listOf(
            track("ytm:1", "All I Need (Official Video) [HQ]", artist = "XLRecordings", source = SourceType.YTMUSIC),
            track("navidrome:1", "All I Need", artist = "Radiohead", source = SourceType.NAVIDROME)
        )

        assertEquals(
            "without a link the tags leave these apart",
            2,
            TrackDeduplicator.groupRecordings(tracks).size
        )

        val groups = TrackDeduplicator.groupRecordings(
            tracks,
            SplitSet.EMPTY,
            RecordingLinkSet.of(listOf("ytm:1" to "navidrome:1"))
        )
        assertEquals(1, groups.size)
        assertEquals(2, groups.first().size)
        // Still ordered by preference, so the rest of the app picks the same primary as before.
        assertEquals(SourceType.NAVIDROME, groups.first().first().source)
    }

    /** A link crosses the duration tolerance too — the samples answered what it stood in for. */
    @Test
    fun `a link joins rows further apart than the duration tolerance`() {
        val groups = TrackDeduplicator.groupRecordings(
            listOf(
                track("ytm:1", "All I Need", durationMs = 240_000),
                track("navidrome:1", "All I Need", durationMs = 249_000, source = SourceType.NAVIDROME)
            ),
            SplitSet.EMPTY,
            RecordingLinkSet.of(listOf("ytm:1" to "navidrome:1"))
        )

        assertEquals(1, groups.size)
    }

    /**
     * The order of authority, at the point where it matters.
     *
     * A fingerprint match on a mislabelled file is exactly where the user might disagree, so the
     * pin has to win — otherwise "not the same recording" would be a button that does nothing.
     */
    @Test
    fun `a pin beats a link`() {
        val tracks = listOf(
            track("ytm:1", "All I Need", source = SourceType.YTMUSIC),
            track("navidrome:1", "All I Need", source = SourceType.NAVIDROME)
        )
        val splits = SplitSet.of(listOf("ytm:1" to "navidrome:1"))
        val links = RecordingLinkSet.of(listOf("ytm:1" to "navidrome:1"))

        assertEquals(2, TrackDeduplicator.groupRecordings(tracks, splits, links).size)
        assertFalse(TrackDeduplicator.isSameRecording(tracks[0], tracks[1], splits, links))
    }

    /**
     * A pin refuses the join for the whole group, not just for its own pair.
     *
     * Groups are folded onto one row downstream, so merging on the strength of a different pair
     * would put the two pinned rows together by the back door.
     */
    @Test
    fun `a pin against one member refuses the whole join`() {
        val groups = TrackDeduplicator.groupRecordings(
            listOf(
                track("ytm:1", "All I Need"),
                track("ytm:2", "All I Need"),
                track("navidrome:1", "All I Need (Live at the BBC)", source = SourceType.NAVIDROME)
            ),
            SplitSet.of(listOf("ytm:1" to "navidrome:1")),
            RecordingLinkSet.of(listOf("ytm:2" to "navidrome:1"))
        )

        assertTrue(
            "the pinned pair must not end up in one group",
            groups.none { group -> group.map { it.id }.containsAll(listOf("ytm:1", "navidrome:1")) }
        )
    }

    /** Links compose: A to B and B to C is one recording, not two overlapping pairs. */
    @Test
    fun `links chain through a shared member`() {
        val groups = TrackDeduplicator.groupRecordings(
            listOf(
                track("ytm:1", "Weird Fishes", artist = "Uploader One"),
                track("local:1", "weird fishes arpeggi", artist = "unknown", source = SourceType.LOCAL),
                track("navidrome:1", "Weird Fishes / Arpeggi", artist = "Radiohead", source = SourceType.NAVIDROME)
            ),
            SplitSet.EMPTY,
            RecordingLinkSet.of(listOf("ytm:1" to "local:1", "local:1" to "navidrome:1"))
        )

        assertEquals(1, groups.size)
        assertEquals(3, groups.first().size)
    }

    /** No links means exactly the behaviour that shipped: the defaulted parameter changes nothing. */
    @Test
    fun `an empty link set groups exactly as before`() {
        val tracks = listOf(
            track("ytm:1", "All I Need", source = SourceType.YTMUSIC),
            track("navidrome:1", "All I Need", source = SourceType.NAVIDROME),
            track("ytm:2", "All I Need (Live)", source = SourceType.YTMUSIC)
        )

        assertEquals(
            TrackDeduplicator.groupRecordings(tracks).map { group -> group.map { it.id } },
            TrackDeduplicator.groupRecordings(tracks, SplitSet.EMPTY, RecordingLinkSet.EMPTY)
                .map { group -> group.map { it.id } }
        )
    }

    /** The pairwise entry point the likes path uses gets the same verdict. */
    @Test
    fun `a linked pair is the same recording however its tags read`() {
        val upload = track("ytm:1", "All I Need (Official Video)", artist = "XLRecordings")
        val tagged = track("navidrome:1", "All I Need", source = SourceType.NAVIDROME)

        assertFalse(TrackDeduplicator.isSameRecording(upload, tagged))
        assertTrue(
            TrackDeduplicator.isSameRecording(
                upload,
                tagged,
                SplitSet.EMPTY,
                RecordingLinkSet.of(listOf("ytm:1" to "navidrome:1"))
            )
        )
    }
}
