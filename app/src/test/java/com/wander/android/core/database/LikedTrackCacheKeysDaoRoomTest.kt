package com.wander.android.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.SourceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The keys the streaming cache keeps for liked tracks, as [TrackDao.getLikedTrackCacheKeysFlow] reads them. */
@RunWith(KeystoreRobolectricRunner::class)
@Config(sdk = [35])
class LikedTrackCacheKeysDaoRoomTest {
    private lateinit var db: WanderDatabase

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WanderDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun close() = db.close()

    private fun keys() = runBlocking { db.trackDao().getLikedTrackCacheKeysFlow().first().toSet() }

    private fun insert(vararg tracks: TrackEntity) = runBlocking { db.trackDao().insertNewTracks(tracks.toList()) }

    @Test
    fun aDownloadedFileWinsOverTheStream() {
        insert(track("1", liked = true, stream = "https://a/1", file = "/music/1.flac"))
        assertEquals(setOf("/music/1.flac"), keys())
    }

    @Test
    fun theStreamStandsInWhenNothingIsDownloaded() {
        insert(track("1", liked = true, stream = "https://a/1"))
        assertEquals(setOf("https://a/1"), keys())
    }

    @Test
    fun onlyLikedSongsWithAKeyAreKept() {
        insert(
            track("1", liked = true, stream = "https://a/1"),
            track("2", liked = false, stream = "https://a/2"),
            track("3", liked = true, stream = "https://a/3", episode = true),
            track("4", liked = true, stream = null)
        )
        assertEquals(setOf("https://a/1"), keys())
    }

    /** More than one CursorWindow's worth (2 MB), the size the old full-row query crashed at. */
    @Test
    fun aListLargerThanOneCursorWindowReadsWhole() {
        val padding = "x".repeat(1_000)
        val liked = (1..3_000).map { track("$it", liked = true, stream = "https://a/$it?sig=$padding") }
        runBlocking { db.trackDao().insertNewTracks(liked) }
        assertEquals(liked.map { it.streamUri }.toSet(), keys())
    }

    private fun track(
        id: String,
        liked: Boolean,
        stream: String?,
        file: String? = null,
        episode: Boolean = false
    ) = TrackEntity(
        id = "navidrome:$id",
        sourceTrackId = id,
        source = SourceType.NAVIDROME,
        title = "Track $id",
        artist = "Artist",
        album = "Album",
        albumId = "navidrome:Album",
        artistId = null,
        durationMs = 1_000,
        artworkUrl = null,
        streamUri = stream,
        trackNumber = null,
        discNumber = null,
        year = null,
        genre = null,
        bitRateKbps = null,
        format = null,
        isEpisode = episode,
        isLiked = liked,
        localFilePath = file,
        addedTimestamp = id.toLong()
    )
}
