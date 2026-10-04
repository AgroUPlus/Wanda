package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.wander.android.core.database.entity.JamRecapEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JamRecapDao {

    @Query("SELECT * FROM jam_recaps ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<JamRecapEntity>>

    /**
     * Makes the table exactly what the server holds.
     *
     * Replaced wholesale rather than merged: a recap dismissed on another device, or swept by the
     * server's retention, has to disappear here too, and the server's list is the whole truth.
     */
    @Transaction
    suspend fun replaceAll(recaps: List<JamRecapEntity>) {
        clear()
        insertAll(recaps)
    }

    @Query("DELETE FROM jam_recaps WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM jam_recaps")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(recaps: List<JamRecapEntity>)
}
