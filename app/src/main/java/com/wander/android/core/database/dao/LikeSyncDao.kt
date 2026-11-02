package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wander.android.core.database.entity.PendingLikeEntity

/** The like outbox: wishes a backend has not confirmed yet. */
@Dao
interface LikeSyncDao {

    /** Replaces any earlier wish for the same track: only the latest is worth sending. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun queue(like: PendingLikeEntity)

    @Query("SELECT * FROM pending_likes ORDER BY queuedAt ASC LIMIT :limit")
    suspend fun pending(limit: Int): List<PendingLikeEntity>

    @Query("SELECT trackId FROM pending_likes")
    suspend fun pendingTrackIds(): List<String>

    /** Clears the wish that was sent, and only that one: a newer toggle keeps its row. */
    @Query("DELETE FROM pending_likes WHERE trackId = :trackId AND queuedAt = :queuedAt")
    suspend fun settle(trackId: String, queuedAt: Long)

    @Query("UPDATE pending_likes SET attempts = attempts + 1 WHERE trackId = :trackId AND queuedAt = :queuedAt")
    suspend fun countAttempt(trackId: String, queuedAt: Long)
}
