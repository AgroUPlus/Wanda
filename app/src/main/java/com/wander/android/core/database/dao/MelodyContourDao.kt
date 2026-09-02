package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wander.android.core.database.entity.MelodyContourEntity

@Dao
interface MelodyContourDao {

    /**
     * Every contour, for one search to compare against.
     *
     * Read whole because that is what the search is: a hum has to be warped against each candidate
     * in turn, and no SQL predicate can narrow the field first — the whole difficulty is that the
     * query is a melody, not a value. At around 150 bytes a track this is a couple of megabytes for
     * a very large library.
     */
    @Query("SELECT * FROM melody_contours WHERE version = :version")
    suspend fun getAll(version: Int): List<MelodyContourEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contour: MelodyContourEntity)

    @Query("SELECT trackId FROM melody_contours WHERE version = :version")
    suspend fun indexedTrackIds(version: Int): List<String>

    /** The same set as a flow, for the hum half of the badge. See `FingerprintDao`. */
    @Query("SELECT trackId FROM melody_contours WHERE version = :version")
    fun indexedTrackIdsFlow(version: Int): kotlinx.coroutines.flow.Flow<List<String>>

    @Query("DELETE FROM melody_contours WHERE version != :version OR trackId NOT IN (SELECT id FROM tracks)")
    suspend fun prune(version: Int)

    @Query("DELETE FROM melody_contours")
    suspend fun clear()
}
