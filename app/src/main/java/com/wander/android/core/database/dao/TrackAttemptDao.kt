package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * Tracks indexer attempts and timestamps so background sweeps back off from unreachable tracks
 * across process deaths.
 */
@Dao
interface TrackAttemptDao {

    @Query("UPDATE tracks SET attempts = attempts + 1, lastAttemptAt = :now WHERE id = :trackId")
    suspend fun recordAttempt(trackId: String, now: Long)

    @Query("UPDATE tracks SET attempts = 0, lastAttemptAt = NULL WHERE id = :trackId")
    suspend fun clearAttempts(trackId: String)

    @Query("UPDATE tracks SET attempts = 0, lastAttemptAt = NULL")
    suspend fun clearAllAttempts()
}
