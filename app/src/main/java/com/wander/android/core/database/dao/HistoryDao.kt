package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.wander.android.core.database.entity.HistoryEntity
import com.wander.android.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT :limit")
    fun getRecentHistoryFlow(limit: Int = 50): Flow<List<HistoryEntity>>

    /**
     * What was played, most recent first, as tracks rather than as ids.
     *
     * `GROUP BY` rather than one row per play: a song put on four times in an evening is one thing
     * you listened to, and four identical rows in a row is a log, not a history. `MAX(playedAt)` is
     * what the ordering is then done on, so it is the most recent play that decides the position.
     *
     * `INNER JOIN` because a play whose track has since left the library has nothing to show.
     */
    @Query(
        """
        SELECT t.* FROM history h
        INNER JOIN tracks t ON t.id = h.trackId
        GROUP BY h.trackId
        ORDER BY MAX(h.playedAt) DESC
        LIMIT :limit
        """
    )
    fun getRecentlyPlayedTracksFlow(limit: Int = 200): Flow<List<TrackEntity>>

    /** Plays that could not be scrobbled yet — retried when the source comes back online. */
    @Query("SELECT * FROM history WHERE scrobbled = 0 ORDER BY playedAt ASC LIMIT :limit")
    suspend fun getPendingScrobbles(limit: Int = 100): List<HistoryEntity>

    @Query("UPDATE history SET scrobbled = 1 WHERE historyId IN (:ids)")
    suspend fun markScrobbled(ids: List<Long>)

    /**
     * Plays not yet reported to Agro, with the metadata the fleet's statistics need.
     *
     * Joined rather than stored on the history row: a play is a track id and a time, and copying
     * the title and artist into every row would be a second, staler copy of what `tracks` already
     * holds. `INNER JOIN` because a play whose track has since been deleted from the library has
     * nothing to report about.
     */
    @Query(
        """
        SELECT h.historyId AS historyId, h.playedAt AS playedAt, t.title AS title,
               t.artist AS artist, t.album AS album, t.genre AS genre, t.durationMs AS durationMs
        FROM history h
        INNER JOIN tracks t ON t.id = h.trackId
        WHERE h.agroSynced = 0
        ORDER BY h.playedAt ASC
        LIMIT :limit
        """
    )
    suspend fun getPendingAgroScrobbles(limit: Int = 200): List<PendingScrobble>

    @Query("UPDATE history SET agroSynced = 1 WHERE historyId IN (:ids)")
    suspend fun markAgroSynced(ids: List<Long>)

    /**
     * Every play since a point in time, with what the track was.
     *
     * The local half of the statistics screen, used when no Agro server is paired. Same projection
     * as [getPendingAgroScrobbles] and the same join for the same reason — the only difference is
     * that this one does not care whether a play has been reported anywhere.
     */
    @Query(
        """
        SELECT h.historyId AS historyId, h.playedAt AS playedAt, t.title AS title,
               t.artist AS artist, t.album AS album, t.genre AS genre, t.durationMs AS durationMs
        FROM history h
        INNER JOIN tracks t ON t.id = h.trackId
        WHERE h.playedAt >= :since
        ORDER BY h.playedAt ASC
        """
    )
    suspend fun getHistorySince(since: Long): List<PendingScrobble>

    @Insert
    suspend fun recordHistory(entry: HistoryEntity): Long

    @Query("DELETE FROM history")
    suspend fun clearHistory()
}
