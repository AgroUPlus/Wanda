package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.wander.android.core.database.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT :limit")
    fun getRecentHistoryFlow(limit: Int = 50): Flow<List<HistoryEntity>>

    /** Plays that could not be scrobbled yet — retried when the source comes back online. */
    @Query("SELECT * FROM history WHERE scrobbled = 0 ORDER BY playedAt ASC LIMIT :limit")
    suspend fun getPendingScrobbles(limit: Int = 100): List<HistoryEntity>

    @Query("UPDATE history SET scrobbled = 1 WHERE historyId IN (:ids)")
    suspend fun markScrobbled(ids: List<Long>)

    @Insert
    suspend fun recordHistory(entry: HistoryEntity): Long

    @Query("DELETE FROM history")
    suspend fun clearHistory()
}
