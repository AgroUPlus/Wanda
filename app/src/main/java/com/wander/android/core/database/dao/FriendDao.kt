package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.wander.android.core.database.entity.FriendEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendDao {

    @Query("SELECT * FROM friends WHERE state = 'accepted' ORDER BY LOWER(COALESCE(displayName, username))")
    fun observeFriends(): Flow<List<FriendEntity>>

    @Query("SELECT * FROM friends WHERE state = 'pending' ORDER BY outgoing, LOWER(username)")
    fun observeRequests(): Flow<List<FriendEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(friends: List<FriendEntity>)

    @Query("DELETE FROM friends")
    suspend fun clear()

    /**
     * Replaces the whole cache in one transaction.
     *
     * The server's answer is the complete truth about the graph, so anything not in it is gone —
     * merging instead would leave an unfriended account on screen until someone noticed. One
     * transaction because an empty `friends` table, however briefly, is a visible flicker of
     * "you have no friends".
     */
    @Transaction
    suspend fun replaceAll(friends: List<FriendEntity>) {
        clear()
        upsert(friends)
    }
}
