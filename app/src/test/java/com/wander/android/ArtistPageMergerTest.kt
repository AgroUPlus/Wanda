package com.wander.android

import com.wander.android.data.model.ArtistAlbumSection
import com.wander.android.data.model.ArtistDetails
import com.wander.android.data.model.ArtistTrackSection
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.ArtistPageMerger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistPageMergerTest {

    private fun track(
        source: SourceType,
        title: String,
        durationMs: Long = 200_000L
    ) = UnifiedTrack(
        id = "${source.idPrefix}${title.hashCode()}",
        source = source,
        title = title,
        artist = "Mahito Yokota",
        durationMs = durationMs
    )

    private fun album(id: String, title: String) = UnifiedAlbum(
        id = id,
        source = SourceType.YTMUSIC,
        title = title,
        artist = "Mahito Yokota"
    )

    @Test
    fun `songs shelf and library tracks merge into one deduplicated bucket`() {
        val page = ArtistPageMerger.merge(
            details = ArtistDetails(
                id = "ytmusic:X",
                name = "Mahito Yokota",
                sections = listOf(
                    ArtistTrackSection("Songs", listOf(track(SourceType.YTMUSIC, "Gusty Garden")))
                )
            ),
            libraryAlbums = emptyList(),
            // The same recording, from a source the user actually owns.
            libraryTracks = listOf(track(SourceType.NAVIDROME, "Gusty Garden"))
        )

        assertEquals(1, page.topSongs.size)
        // TrackDeduplicator prefers the lowest-priority source, so the owned copy is the survivor.
        assertEquals(SourceType.NAVIDROME, page.topSongs.single().source)
    }

    @Test
    fun `library albums join the albums bucket without duplicating the shelf`() {
        val shared = album("ytmusic:A", "Galaxy OST")
        val page = ArtistPageMerger.merge(
            details = ArtistDetails(
                id = "ytmusic:X",
                name = "Mahito Yokota",
                sections = listOf(ArtistAlbumSection("Albums", listOf(shared)))
            ),
            libraryAlbums = listOf(shared, album("navidrome:B", "Odyssey OST")),
            libraryTracks = emptyList()
        )

        assertEquals(listOf("ytmusic:A", "navidrome:B"), page.albums?.albums?.map { it.id })
    }

    @Test
    fun `an unclassifiable shelf keeps its own heading rather than being dropped`() {
        val page = ArtistPageMerger.merge(
            details = ArtistDetails(
                id = "ytmusic:X",
                name = "Mahito Yokota",
                sections = listOf(
                    // What a non-English page looks like to this merger.
                    ArtistAlbumSection("Apparaît sur", listOf(album("ytmusic:C", "Smash Bros")))
                )
            ),
            libraryAlbums = emptyList(),
            libraryTracks = emptyList()
        )

        assertNull("an unmatched shelf must not become the Albums bucket", page.albums)
        assertEquals(1, page.otherShelves.size)
        assertEquals("Apparaît sur", page.otherShelves.single().title)
    }

    @Test
    fun `singles shelf lands in its own bucket and carries its more endpoint`() {
        val page = ArtistPageMerger.merge(
            details = ArtistDetails(
                id = "ytmusic:X",
                name = "Mahito Yokota",
                sections = listOf(
                    ArtistAlbumSection(
                        title = "Singles",
                        albums = listOf(album("ytmusic:D", "Rosalina")),
                        moreBrowseId = "ytmusic:MORE",
                        moreParams = "blob"
                    )
                )
            ),
            libraryAlbums = emptyList(),
            libraryTracks = emptyList()
        )

        assertNull(page.albums)
        assertEquals("ytmusic:MORE", page.singles?.moreBrowseId)
        assertEquals("blob", page.singles?.moreParams)
    }

    @Test
    fun `an artist nothing knows anything about produces an empty page, not empty sections`() {
        val page = ArtistPageMerger.merge(null, emptyList(), emptyList())

        assertTrue(page.isEmpty)
        assertNull(page.albums)
        assertNull(page.singles)
        assertTrue(page.otherShelves.isEmpty())
    }

    @Test
    fun `library-only artists still get an albums bucket`() {
        val page = ArtistPageMerger.merge(
            details = null,
            libraryAlbums = listOf(album("local:E", "Ripped CD")),
            libraryTracks = listOf(track(SourceType.LOCAL, "Track One"))
        )

        assertEquals("Albums", page.albums?.title)
        assertEquals(1, page.albums?.albums?.size)
        assertEquals(1, page.topSongs.size)
    }
}
