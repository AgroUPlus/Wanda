package com.wander.android.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wander.android.core.database.entity.AlbumEntity
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.LibrarySort
import com.wander.android.data.model.SourceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Library's chosen orders and source filter, as the browse queries apply them. */
@RunWith(KeystoreRobolectricRunner::class)
@Config(sdk = [35])
class LibraryBrowseDaoRoomTest {
    private lateinit var db: WanderDatabase

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WanderDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            db.trackDao().insertNewTracks(
                listOf(
                    track("local:1", "beta", artist = "Zed", album = "B", added = 1, plays = 5, played = 30),
                    track("local:2", "Alpha", artist = "abba", album = "C", added = 3, plays = 1, played = 10),
                    track("navidrome:3", "gamma", artist = "Mid", album = "A", added = 2, plays = 9, played = null)
                )
            )
        }
    }

    @After
    fun close() = db.close()

    private fun ids(sort: LibrarySort, sources: List<SourceType> = SourceType.entries) = runBlocking {
        db.libraryBrowseDao().libraryTrackIds(sort.name, sources)
    }

    @Test
    fun titleSortIgnoresCase() {
        assertEquals(listOf("local:2", "local:1", "navidrome:3"), ids(LibrarySort.TITLE))
    }

    @Test
    fun artistSortIgnoresCase() {
        assertEquals(listOf("local:2", "navidrome:3", "local:1"), ids(LibrarySort.ARTIST))
    }

    @Test
    fun albumSortOrdersByAlbumName() {
        assertEquals(listOf("navidrome:3", "local:1", "local:2"), ids(LibrarySort.ALBUM))
    }

    @Test
    fun dateAddedPutsTheNewestFirst() {
        assertEquals(listOf("local:2", "navidrome:3", "local:1"), ids(LibrarySort.DATE_ADDED))
    }

    @Test
    fun mostPlayedPutsTheMostPlayedFirst() {
        assertEquals(listOf("navidrome:3", "local:1", "local:2"), ids(LibrarySort.MOST_PLAYED))
    }

    /** Never played sorts last, not first: SQLite's NULL is the smallest value, so it ends a DESC. */
    @Test
    fun recentlyPlayedPutsTheNeverPlayedLast() {
        assertEquals(listOf("local:1", "local:2", "navidrome:3"), ids(LibrarySort.RECENTLY_PLAYED))
    }

    @Test
    fun theSourceFilterKeepsOnlyThoseSources() {
        assertEquals(listOf("navidrome:3"), ids(LibrarySort.TITLE, listOf(SourceType.NAVIDROME)))
    }

    @Test
    fun albumsSortByTheirTracksPlays() = runBlocking {
        db.albumDao().insertAlbums(
            listOf(album("local:B", "B"), album("local:C", "C"), album("navidrome:A", "A"))
        )
        val byPlays = db.libraryBrowseDao()
            .observeLibraryAlbumsByActivity(LibrarySort.MOST_PLAYED.name, SourceType.entries)
            .first()
            .map { it.id }
        assertEquals(listOf("navidrome:A", "local:B", "local:C"), byPlays)
    }

    private fun track(
        id: String,
        title: String,
        artist: String,
        album: String,
        added: Long,
        plays: Int,
        played: Long?
    ) = TrackEntity(
        id = id,
        sourceTrackId = id.substringAfter(':'),
        source = if (id.startsWith("local:")) SourceType.LOCAL else SourceType.NAVIDROME,
        title = title,
        artist = artist,
        album = album,
        albumId = (if (id.startsWith("local:")) "local:" else "navidrome:") + album,
        artistId = null,
        durationMs = 1_000,
        artworkUrl = null,
        streamUri = null,
        trackNumber = null,
        discNumber = null,
        year = null,
        genre = null,
        bitRateKbps = null,
        format = null,
        isLibrary = true,
        playCount = plays,
        lastPlayedTimestamp = played,
        addedTimestamp = added
    )

    private fun album(id: String, title: String) = AlbumEntity(
        id = id,
        sourceAlbumId = title,
        source = if (id.startsWith("local:")) SourceType.LOCAL else SourceType.NAVIDROME,
        title = title,
        artist = "",
        artistId = null,
        coverArtUrl = null,
        isLibrary = true
    )
}
