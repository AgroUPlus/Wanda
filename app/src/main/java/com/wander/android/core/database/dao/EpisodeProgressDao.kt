package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wander.android.core.database.entity.EpisodeProgressEntity

@Dao
interface EpisodeProgressDao {

    @Query("SELECT * FROM episode_progress WHERE trackId = :trackId")
    suspend fun get(trackId: String): EpisodeProgressEntity?

    /** REPLACE: a later listen is the truth about where someone got to. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: EpisodeProgressEntity)

    @Query("DELETE FROM episode_progress WHERE trackId = :trackId")
    suspend fun delete(trackId: String)

    /**
     * Drops rows untouched for [before].
     *
     * The table only ever grows otherwise: an episode played once leaves a row behind forever,
     * and nothing else would ever remove it.
     */
    @Query("DELETE FROM episode_progress WHERE updatedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
