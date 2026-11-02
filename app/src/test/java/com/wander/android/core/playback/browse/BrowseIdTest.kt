package com.wander.android.core.playback.browse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowseIdTest {

    private fun roundTrip(id: BrowseId) = assertEquals(id, BrowseId.decode(id.encode()))

    @Test
    fun everyFixedFolderRoundTrips() {
        listOf(
            BrowseId.Root, BrowseId.Home, BrowseId.Library, BrowseId.Recent, BrowseId.MostPlayed,
            BrowseId.Liked, BrowseId.Playlists, BrowseId.Albums, BrowseId.Downloads, BrowseId.Podcasts
        ).forEach(::roundTrip)
    }

    @Test
    fun foldersNamingARowRoundTrip() {
        roundTrip(BrowseId.Mix("personal_radio"))
        roundTrip(BrowseId.Playlist("local:playlist-1"))
        roundTrip(BrowseId.Album("navidrome:al-42"))
        roundTrip(BrowseId.Search("daft punk"))
    }

    @Test
    fun aTrackKeepsTheFolderItWasPickedFrom() {
        roundTrip(BrowseId.Track(BrowseId.Liked, "ytm:abc"))
        roundTrip(BrowseId.Track(BrowseId.Album("navidrome:al-42"), "navidrome:tr-7"))
    }

    @Test
    fun separatorsInsideIdsDoNotSplitThem() {
        // Podcast guids are often URLs.
        roundTrip(BrowseId.Track(BrowseId.Podcasts, "podcast:https://example.org/ep/1?x=a/b#c"))
        roundTrip(BrowseId.Album("a/t/b"))
        roundTrip(BrowseId.Track(BrowseId.Search("t"), "t"))
    }

    @Test
    fun idsFromElsewhereAreNotTreeIds() {
        assertNull(BrowseId.decode("navidrome:tr-7"))
        assertNull(BrowseId.decode("wanda://track/x"))
        assertNull(BrowseId.decode("wanda/unknown"))
        assertNull(BrowseId.decode("wanda/album"))
        assertNull(BrowseId.decode("wanda/%zz"))
        assertNull(BrowseId.decode(""))
    }
}
