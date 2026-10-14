package com.wander.android.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wander.android.core.database.entity.PlaylistEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Room is the offline source of truth, so the schema itself has to build: opening the database
 * creates every table, index and FTS shadow table in one go and fails if any of them is invalid.
 * Wanda's real `Application` starts under Robolectric, with [FakeAndroidKeyStore] standing in for the keystore
 * (installed by [KeystoreRobolectricRunner]).
 */
@RunWith(KeystoreRobolectricRunner::class)
@Config(sdk = [36])
class PlaylistDaoRoomTest {

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

    @Test
    fun aPlaylistRoundTripsAndCountsItsTracks() = runBlocking {
        val dao = db.playlistDao()
        dao.insertPlaylist(PlaylistEntity(id = "p1", name = "Road", trackIds = "a,b,,c"))

        val stored = checkNotNull(dao.getPlaylistById("p1"))

        assertEquals("Road", stored.name)
        assertEquals(3, stored.toUnifiedPlaylist().songCount)
    }

    @Test
    fun anEmptyTrackListCountsAsZero() = runBlocking {
        db.playlistDao().insertPlaylist(PlaylistEntity(id = "p2", name = "Empty"))

        assertEquals(0, checkNotNull(db.playlistDao().getPlaylistById("p2")).toUnifiedPlaylist().songCount)
    }

    @Test
    fun replacingTheSameIdKeepsOneRow() = runBlocking {
        val dao = db.playlistDao()
        dao.insertPlaylist(PlaylistEntity(id = "p3", name = "Old"))
        dao.insertPlaylist(PlaylistEntity(id = "p3", name = "New"))

        assertEquals(listOf("New"), dao.getAllPlaylists().map { it.name })
    }

    @Test
    fun deletingAPlaylistRemovesIt() = runBlocking {
        val dao = db.playlistDao()
        dao.insertPlaylist(PlaylistEntity(id = "p4", name = "Gone"))
        dao.deletePlaylist("p4")

        assertNull(dao.getPlaylistById("p4"))
    }
}
