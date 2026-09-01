package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wander.android.core.database.entity.TrackFeatureEntity

@Dao
interface TrackFeatureDao {

    @Query("SELECT * FROM track_features WHERE trackId = :trackId")
    suspend fun get(trackId: String): TrackFeatureEntity?

    /**
     * Every vector held, for the radio to rank against.
     *
     * Read whole rather than queried per candidate: a library of ten thousand tracks is six floats
     * each, which is well under a megabyte, and the alternative is a query per candidate on the
     * path that has to answer before the current song ends.
     */
    @Query("SELECT * FROM track_features WHERE version = :version")
    suspend fun getAll(version: Int): List<TrackFeatureEntity>

    /** REPLACE, not IGNORE: a remeasurement is a correction and must win. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(features: TrackFeatureEntity)

    /** Tracks with a file on this device and no current measurement. */
    @Query(
        """
        SELECT t.id FROM tracks t
        LEFT JOIN track_features f ON f.trackId = t.id AND f.version = :version
        WHERE t.localFilePath IS NOT NULL AND t.localFilePath != '' AND f.trackId IS NULL
        LIMIT :limit
        """
    )
    suspend fun needingMeasurement(version: Int, limit: Int): List<String>

    /** Drops rows for tracks that have left the library, and every stale-version row with them. */
    @Query("DELETE FROM track_features WHERE version != :version OR trackId NOT IN (SELECT id FROM tracks)")
    suspend fun prune(version: Int)
}
