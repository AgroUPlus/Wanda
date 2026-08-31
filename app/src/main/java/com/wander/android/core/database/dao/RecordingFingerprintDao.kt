package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.wander.android.core.database.entity.RecordingFingerprintEntity
import com.wander.android.core.database.entity.RecordingSubHashEntity

/** How many index hits a track needs before its full fingerprint is worth comparing. */
private const val MIN_CANDIDATE_HITS = 4

@Dao
interface RecordingFingerprintDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(fingerprint: RecordingFingerprintEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHalves(halves: List<RecordingSubHashEntity>)

    @Query("DELETE FROM recording_sub_hashes WHERE trackId = :trackId")
    suspend fun clearHalves(trackId: String)

    /**
     * Writes a fingerprint and its index together.
     *
     * One transaction because the two disagree destructively: an index left pointing at a
     * fingerprint that was replaced proposes candidates whose sequences no longer match, and the
     * confirmation step would reject them every time at the cost of reading them.
     */
    @Transaction
    suspend fun replace(
        fingerprint: RecordingFingerprintEntity,
        halves: List<RecordingSubHashEntity>
    ) {
        clearHalves(fingerprint.trackId)
        upsert(fingerprint)
        insertHalves(halves)
    }

    @Query("SELECT * FROM recording_fingerprints WHERE trackId = :trackId")
    suspend fun forTrack(trackId: String): RecordingFingerprintEntity?

    @Query("SELECT * FROM recording_fingerprints WHERE trackId IN (:trackIds)")
    suspend fun forTracks(trackIds: List<String>): List<RecordingFingerprintEntity>

    /**
     * Tracks sharing enough sub-hash halves with [halves] to be worth comparing in full.
     *
     * Ordered by how many they share, so the caller can stop early. [excludeTrackId] keeps a
     * track from proposing itself, which it would otherwise always win.
     */
    @Query(
        """
        SELECT trackId FROM recording_sub_hashes
        WHERE half IN (:halves) AND trackId != :excludeTrackId
        GROUP BY trackId
        HAVING COUNT(*) >= $MIN_CANDIDATE_HITS
        ORDER BY COUNT(*) DESC
        LIMIT :limit
        """
    )
    suspend fun candidates(
        halves: List<Int>,
        excludeTrackId: String,
        limit: Int = 20
    ): List<String>

    @Query("SELECT trackId FROM recording_fingerprints")
    suspend fun indexedTrackIds(): List<String>

    @Query("SELECT COUNT(*) FROM recording_fingerprints")
    suspend fun count(): Int

    @Query("DELETE FROM recording_fingerprints WHERE trackId = :trackId")
    suspend fun delete(trackId: String)
}
