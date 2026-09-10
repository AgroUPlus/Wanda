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
     * Every track's centroid — the shortlist a recognition pass ranks before it opens anything.
     *
     * The whole table at once, deliberately: a centroid is 512 bytes, so a 1400-track library is
     * ~700 KB and reads in a few tens of milliseconds. That is the entire point of the column —
     * the same question asked of the segment vectors reads 84 MB.
     *
     * Rows whose centroid is still null are returned too, with a null [Centroid.centroid]. A
     * caller must shortlist those rather than drop them: a row not yet backfilled is unmeasured,
     * not unmatchable, and excluding it would make the library quietly shrink for as long as the
     * backfill took.
     */
    @Query("SELECT trackId, centroid FROM track_embeddings WHERE model = :model AND version = :version")
    suspend fun centroids(model: String, version: Int): List<Centroid>

    /**
     * Embedded track ids after [after], in id order — the duplicate scan's cursor.
     *
     * Ordered and resumable because the scan is a sweep of the whole library and a run is bounded:
     * "which tracks have I examined for duplicates" is not answerable from `recording_links`,
     * where a track with no duplicate and a track never looked at are the same absence.
     */
    @Query(
        "SELECT trackId FROM track_embeddings WHERE model = :model AND version = :version " +
            "AND trackId > :after ORDER BY trackId LIMIT :limit"
    )
    suspend fun idsAfter(model: String, version: Int, after: String, limit: Int): List<String>

    /** Fills in a centroid for a row written before the column existed. */
    @Query("UPDATE track_embeddings SET centroid = :centroid WHERE trackId = :trackId")
    suspend fun setCentroid(trackId: String, centroid: ByteArray)


    @Query("SELECT * FROM track_embeddings WHERE trackId = :trackId AND model = :model AND version = :version LIMIT 1")
    suspend fun getForTrack(trackId: String, model: String, version: Int): TrackEmbeddingEntity?

    @Query("SELECT * FROM track_embeddings WHERE trackId IN (:trackIds) AND model = :model AND version = :version")
    suspend fun getForTracks(trackIds: List<String>, model: String, version: Int): List<TrackEmbeddingEntity>

    /**
     * Embeddings computed since [after], oldest first, for publishing to the catalogue.
     *
     * Ordered and limited rather than filtered in Kotlin: one row is ~1 KB per second of audio, so
     * reading the whole table to find the handful that are new would be the expensive way to
     * answer a cheap question. Oldest first so the caller can advance its cursor to the last row it
     * actually managed to send.
     */
    @Query(
        "SELECT * FROM track_embeddings WHERE model = :model AND version = :version " +
            "AND computedAt > :after ORDER BY computedAt LIMIT :limit"
    )
    suspend fun computedSince(
        after: Long,
        model: String,
        version: Int,
        limit: Int
    ): List<TrackEmbeddingEntity>

    @Query("SELECT COUNT(*) FROM track_embeddings WHERE model = :model AND version = :version")
    fun indexedTrackCountFlow(model: String, version: Int): Flow<Int>

    /**
     * How many fingerprints have gone to the catalogue.
     *
     * Derived from the publish cursor rather than counted as they are sent: the cursor is already
     * the record of what the server has taken, and a separate tally would be a second answer to
     * the same question, free to drift from the first.
     */
    @Query(
        "SELECT COUNT(*) FROM track_embeddings WHERE model = :model AND version = :version " +
            "AND computedAt <= :publishedThrough"
    )
    fun publishedCountFlow(model: String, version: Int, publishedThrough: Long): Flow<Int>

    /**
     * Which tracks have a current neural fingerprint, for the badge and the Fingerprints screen.
     *
     * Ids rather than rows: the caller wants set membership, and a vector is ~1 KB per second of
     * audio — reading the whole table to answer "is this one done" would be megabytes per redraw.
     */
    @Query("SELECT trackId FROM track_embeddings WHERE model = :model AND version = :version")
    fun indexedTrackIdsFlow(model: String, version: Int): Flow<List<String>>

    /** REPLACE, not IGNORE: a recomputation is a correction and must win. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(embedding: TrackEmbeddingEntity)

    /**
     * Tracks with no current embedding — or with one that stops short of the end of the track.
     *
     * Mirrors [TrackFeatureDao.needingMeasurement]: no local-file gate — that is what kept the
     * older measurements reasoning about a fraction of a streamed library — and livestreams are
     * excluded because they have no fixed content to fingerprint.
     *
     * ## Why coverage is part of the question
     *
     * Indexing used to stop at the first 60 seconds of every track, so a match could only ever be
     * found in, and a position only ever reported from, a song's first minute — on a library
     * averaging three minutes a track, two thirds of the music was not indexed at all, and the
     * position shown for a chorus was a number from somewhere in the intro. Asking only "does a
     * row exist" would leave every one of those truncated rows in place for ever, because the row
     * does exist. A track whose vectors do not reach its own end is unfinished work, and is
     * offered again until they do.
     *
     * [coverageToleranceMs] absorbs the half-second the segmentation rounds off and the ordinary
     * disagreement between a container's declared duration and its decoded length. A track with no
     * declared duration is judged on existence alone; there is nothing to compare against.
     */
    @Query(
        """
        SELECT t.id FROM tracks t
        LEFT JOIN track_embeddings e
               ON e.trackId = t.id AND e.model = :model AND e.version = :version
        WHERE t.isLive = 0 AND (t.isLibrary = 1 OR t.playCount > 0)
          AND (
                e.trackId IS NULL
                OR (
                    t.durationMs > 0
                    AND (length(e.vector) / :bytesPerSegment) * :segmentHopMs
                        < t.durationMs - :coverageToleranceMs
                )
              )
        LIMIT :limit
        """
    )
    suspend fun needingIndex(
        model: String,
        version: Int,
        limit: Int,
        bytesPerSegment: Int,
        segmentHopMs: Int,
        coverageToleranceMs: Int
    ): List<String>

    /** Drops rows for departed tracks and for every superseded model or version. */
    @Query(
        "DELETE FROM track_embeddings WHERE model != :model OR version != :version " +
            "OR trackId NOT IN (SELECT id FROM tracks)"
    )
    suspend fun prune(model: String, version: Int)

    @Query("DELETE FROM track_embeddings")
    suspend fun clear()
}

/** A track and its mean vector, or null where the row predates the column. */
data class Centroid(val trackId: String, val centroid: ByteArray?) {
    // Same reason [TrackEmbeddingEntity] declares these: Room compares a ByteArray by identity.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Centroid) return false
        return trackId == other.trackId && centroid.contentEquals(other.centroid)
    }

    override fun hashCode(): Int = 31 * trackId.hashCode() + (centroid?.contentHashCode() ?: 0)
}
