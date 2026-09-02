package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wander.android.core.database.entity.FingerprintEntity

@Dao
interface FingerprintDao {

    /**
     * Bulk insert. `IGNORE`, not `REPLACE`: the same landmark can legitimately be produced twice
     * for one track, and the row would be identical either way — ignoring skips a needless delete
     * and re-insert on a table this large.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(fingerprints: List<FingerprintEntity>)

    /**
     * Every landmark matching any of [hashes].
     *
     * The whole match is this one query. `IN` over a few hundred hashes against an indexed column
     * is a handful of B-tree descents, which is what keeps recognition inside a second even with a
     * large library behind it.
     */
    @Query("SELECT * FROM fingerprints WHERE hash IN (:hashes)")
    suspend fun matching(hashes: List<Int>): List<FingerprintEntity>

    @Query("DELETE FROM fingerprints WHERE trackId = :trackId")
    suspend fun deleteTrack(trackId: String)

    /** The tracks already indexed, so a rebuild does only what is left to do. */
    @Query("SELECT DISTINCT trackId FROM fingerprints")
    suspend fun indexedTrackIds(): List<String>

    /**
     * How deep into each track its landmarks reach, in frames.
     *
     * The last anchor is the end of what has been indexed, which is not the same question as
     * whether a track has been indexed at all. A track measured before the indexer read past the
     * first minute has landmarks — so it looks done — and yet cannot be recognised from anywhere
     * after them.
     */
    @Query("SELECT trackId, MAX(anchorFrame) AS lastFrame FROM fingerprints GROUP BY trackId")
    suspend fun indexedDepth(): List<IndexedDepth>

    @Query("SELECT COUNT(DISTINCT trackId) FROM fingerprints")
    fun indexedTrackCountFlow(): kotlinx.coroutines.flow.Flow<Int>

    /**
     * The ids that have landmarks, as a flow, for the badge on a row to follow.
     *
     * Ids rather than a per-row lookup: a list draws thirty rows at a scroll and a query each would
     * be thirty round trips per frame. This is one query the whole screen shares, and at a few
     * thousand short strings it is a set the UI can hold.
     */
    @Query("SELECT DISTINCT trackId FROM fingerprints")
    fun indexedTrackIdsFlow(): kotlinx.coroutines.flow.Flow<List<String>>

    @Query("DELETE FROM fingerprints")
    suspend fun clear()
}

/** One track, and the frame of the last landmark written for it. */
data class IndexedDepth(val trackId: String, val lastFrame: Int)
