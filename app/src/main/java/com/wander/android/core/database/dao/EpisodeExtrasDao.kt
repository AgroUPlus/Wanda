package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wander.android.core.database.entity.EpisodeExtrasEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeExtrasDao {

    @Query("SELECT * FROM episode_extras WHERE trackId = :trackId")
    fun observe(trackId: String): Flow<EpisodeExtrasEntity?>

    /** REPLACE: a feed that moves its chapters file must be believed on the next sync. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(extras: List<EpisodeExtrasEntity>)

    /** Rows whose episode was pruned or unsubscribed away. */
    @Query("DELETE FROM episode_extras WHERE trackId NOT IN (SELECT id FROM tracks)")
    suspend fun deleteOrphans()
}
