package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wander.android.core.database.entity.RecordingSplitEntity

@Dao
interface RecordingSplitDao {

    @Query("SELECT * FROM recording_splits")
    suspend fun getAllOnce(): List<RecordingSplitEntity>

    /**
     * `IGNORE`, not `REPLACE`: pinning a pair that is already pinned is not a new decision, and
     * keeping the original [RecordingSplitEntity.pinnedAt] is the honest record of when the user
     * actually made it.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun upsert(splits: List<RecordingSplitEntity>)

    @Query("DELETE FROM recording_splits WHERE idA = :idA AND idB = :idB")
    suspend fun delete(idA: String, idB: String)
}
