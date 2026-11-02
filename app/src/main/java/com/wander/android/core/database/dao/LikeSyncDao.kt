package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wander.android.core.database.entity.PendingLikeEntity
import com.wander.android.core.database.entity.RemoteLikeEntity
import com.wander.android.data.model.SourceType

/**
 * Likes between Wanda and the backends: the outbox of wishes a backend has not confirmed yet, and
 * the record of which likes a backend has.
 */
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordRemoteLikes(likes: List<RemoteLikeEntity>)

    @Query("DELETE FROM remote_likes WHERE trackId IN (:trackIds)")
    suspend fun forgetRemoteLikes(trackIds: List<String>)

    @Query("SELECT trackId FROM remote_likes WHERE source = :source")
    suspend fun remoteLikedIds(source: SourceType): List<String>

    /** Bound by [MAX_BOUND_IDS] like every id list; callers chunk. */
    @Query("UPDATE tracks SET isLiked = :liked WHERE id IN (:trackIds)")
    suspend fun setTracksLiked(trackIds: List<String>, liked: Boolean)
}
