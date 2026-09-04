package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wander.android.core.database.entity.TrackEmbeddingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackEmbeddingDao {

    /**
     * Every stored embedding, for one recognition pass to compare against.
     *
     * Read whole for the same reason [MelodyContourDao.getAll] is: the query is an audio clip, not
     * a value, and no SQL predicate narrows the field first. At ~30 KB a track (a 60 s track is
     * ~120 segments of 128 float32) a very large library is tens of megabytes — held only for the
     * seconds a match takes, not resident.
     */
    @Query("SELECT * FROM track_embeddings WHERE model = :model AND version = :version")
    suspend fun getAll(model: String, version: Int): List<TrackEmbeddingEntity>

    @Query("SELECT * FROM track_embeddings WHERE trackId = :trackId AND model = :model AND version = :version LIMIT 1")
    suspend fun getForTrack(trackId: String, model: String, version: Int): TrackEmbeddingEntity?

    @Query("SELECT * FROM track_embeddings WHERE trackId IN (:trackIds) AND model = :model AND version = :version")
    suspend fun getForTracks(trackIds: List<String>, model: String, version: Int): List<TrackEmbeddingEntity>

    @Query("SELECT COUNT(*) FROM track_embeddings WHERE model = :model AND version = :version")
    fun indexedTrackCountFlow(model: String, version: Int): Flow<Int>

    /**
     * Which tracks have a current neural fingerprint, for the badge and the Fingerprints screen.
     *
     * Ids rather than rows: the caller wants set membership, and the vectors are ~30 KB each —
     * reading the whole table to answer "is this one done" would be megabytes per redraw.
     */
    @Query("SELECT trackId FROM track_embeddings WHERE model = :model AND version = :version")
    fun indexedTrackIdsFlow(model: String, version: Int): Flow<List<String>>

    /** REPLACE, not IGNORE: a recomputation is a correction and must win. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(embedding: TrackEmbeddingEntity)

    /**
     * Tracks with no current embedding, from anywhere in the library.
     *
     * Mirrors [TrackFeatureDao.needingMeasurement]: no local-file gate — that is what kept the
     * older measurements reasoning about a fraction of a streamed library — and livestreams are
     * excluded because they have no fixed content to fingerprint.
     */
    @Query(
        """
        SELECT t.id FROM tracks t
        LEFT JOIN track_embeddings e
               ON e.trackId = t.id AND e.model = :model AND e.version = :version
        WHERE t.isLive = 0 AND e.trackId IS NULL
        LIMIT :limit
        """
    )
    suspend fun needingIndex(model: String, version: Int, limit: Int): List<String>

    /** Drops rows for departed tracks and for every superseded model or version. */
    @Query(
        "DELETE FROM track_embeddings WHERE model != :model OR version != :version " +
            "OR trackId NOT IN (SELECT id FROM tracks)"
    )
    suspend fun prune(model: String, version: Int)

    @Query("DELETE FROM track_embeddings")
    suspend fun clear()
}
