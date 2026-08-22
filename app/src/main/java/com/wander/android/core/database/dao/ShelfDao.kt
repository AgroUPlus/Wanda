package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.wander.android.core.database.entity.ShelfEntity

@Dao
interface ShelfDao {

    @Query("SELECT * FROM shelves ORDER BY position ASC")
    suspend fun getShelves(): List<ShelfEntity>

    @Query("SELECT MAX(fetchedAt) FROM shelves")
    suspend fun lastFetchedAt(): Long?

    /**
     * Swaps in a whole feed at once.
     *
     * Replacing rather than upserting: a shelf the backend has stopped sending has to disappear,
     * and the feed's order is only meaningful as a whole. In a transaction so a failed write
     * cannot leave Home showing half of one feed and half of another.
     */
    @Transaction
    suspend fun replaceAll(shelves: List<ShelfEntity>) {
        clear()
        insert(shelves)
    }

    @Query("DELETE FROM shelves")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(shelves: List<ShelfEntity>)
}
