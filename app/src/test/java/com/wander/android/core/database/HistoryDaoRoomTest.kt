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
        val id = dao.recordHistory(HistoryEntity(trackId = "t1", playedAt = 1_000))

        assertEquals(listOf(id), dao.getPendingScrobbles().map { it.historyId })
    }

    @Test
    fun aScrobbledPlayLeavesTheOutbox() = runBlocking {
        val dao = db.historyDao()
        val first = dao.recordHistory(HistoryEntity(trackId = "t1", playedAt = 1_000))
        val second = dao.recordHistory(HistoryEntity(trackId = "t2", playedAt = 2_000))

        dao.markScrobbled(listOf(first))

        assertEquals(listOf(second), dao.getPendingScrobbles().map { it.historyId })
    }

    @Test
    fun theOutboxHonoursItsLimit() = runBlocking {
        val dao = db.historyDao()
        repeat(5) { dao.recordHistory(HistoryEntity(trackId = "t$it", playedAt = it.toLong())) }

        assertEquals(2, dao.getPendingScrobbles(limit = 2).size)
    }

    @Test
    fun anEmptyOutboxIsEmpty() = runBlocking {
        assertTrue(db.historyDao().getPendingScrobbles().isEmpty())
    }
}
