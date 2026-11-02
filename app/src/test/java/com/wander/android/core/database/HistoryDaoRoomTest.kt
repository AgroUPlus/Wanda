package com.wander.android.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wander.android.core.database.entity.HistoryEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The scrobble outbox: a play waits here until a service has been told about it. */
@RunWith(KeystoreRobolectricRunner::class)
@Config(sdk = [35])
class HistoryDaoRoomTest {
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
    fun aNewPlayWaitsInTheOutbox() = runBlocking {
        val dao = db.historyDao()
        val id = dao.recordHistory(HistoryEntity(trackId = "navidrome:t1", playedAt = 1_000))

        assertEquals(listOf(id), dao.getPendingScrobbles(NAVIDROME).map { it.historyId })
    }

    @Test
    fun aScrobbledPlayLeavesTheOutbox() = runBlocking {
        val dao = db.historyDao()
        val first = dao.recordHistory(HistoryEntity(trackId = "navidrome:t1", playedAt = 1_000))
        val second = dao.recordHistory(HistoryEntity(trackId = "navidrome:t2", playedAt = 2_000))

        dao.markScrobbled(listOf(first))

        assertEquals(listOf(second), dao.getPendingScrobbles(NAVIDROME).map { it.historyId })
    }

    @Test
    fun theOutboxHonoursItsLimit() = runBlocking {
        val dao = db.historyDao()
        repeat(5) { dao.recordHistory(HistoryEntity(trackId = "navidrome:t$it", playedAt = it.toLong())) }

        assertEquals(2, dao.getPendingScrobbles(NAVIDROME, limit = 2).size)
    }

    /** Local and YouTube plays are never scrobbled to a source, so they must not queue for one. */
    @Test
    fun theOutboxHoldsOnlyThatSourcesPlays() = runBlocking {
        val dao = db.historyDao()
        dao.recordHistory(HistoryEntity(trackId = "local:t1", playedAt = 1_000))
        val ours = dao.recordHistory(HistoryEntity(trackId = "navidrome:t2", playedAt = 2_000))

        assertEquals(listOf(ours), dao.getPendingScrobbles(NAVIDROME).map { it.historyId })
    }

    /** Removing a play from History must not cancel a scrobble that has not gone yet. */
    @Test
    fun aHiddenPlayStaysInTheOutbox() = runBlocking {
        val dao = db.historyDao()
        val id = dao.recordHistory(HistoryEntity(trackId = "navidrome:t1", playedAt = 1_000))

        dao.hidePlay(id)
        assertEquals(0, dao.hideAllPlays())

        assertEquals(listOf(id), dao.getPendingScrobbles(NAVIDROME).map { it.historyId })
        assertEquals(1, dao.allPlays().size)
    }

    @Test
    fun clearingHidesEveryShowingPlay() = runBlocking {
        val dao = db.historyDao()
        repeat(3) { dao.recordHistory(HistoryEntity(trackId = "local:t$it", playedAt = it.toLong())) }

        assertEquals(3, dao.hideAllPlays())
        assertEquals(0, dao.hideAllPlays())
    }

    @Test
    fun anEmptyOutboxIsEmpty() = runBlocking {
        assertTrue(db.historyDao().getPendingScrobbles(NAVIDROME).isEmpty())
    }

    private companion object {
        const val NAVIDROME = "navidrome:"
    }
}
