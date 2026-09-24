package com.wander.android.core.database.dao

import androidx.room.Query
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.SourceType
import kotlinx.coroutines.flow.Flow

/**
 * Queries related to local file scanning, hashing, sync status, and backup restoration.
 */
interface TrackSyncQueries {

    @Query(
        """
        SELECT * FROM tracks
        WHERE source = 'LOCAL' AND contentHash IS NULL AND streamUri IS NOT NULL
        ORDER BY addedTimestamp DESC
        LIMIT :limit
        """
    )
    suspend fun getUnhashedLocalTracks(limit: Int): List<TrackEntity>

    @Query(
        """
        SELECT * FROM tracks
        WHERE source != 'LOCAL'
          AND (contentHash IS NULL OR contentHash = '')
          AND isDownloaded = 1
          AND localFilePath IS NOT NULL
        ORDER BY addedTimestamp DESC
        LIMIT :limit
        """
    )
    suspend fun getUnhashedDownloads(limit: Int): List<TrackEntity>

    @Query("UPDATE tracks SET durationMs = :durationMs WHERE id = :trackId AND (durationMs IS NULL OR durationMs <= 0)")
    suspend fun fillMissingDuration(trackId: String, durationMs: Long)

    @Query("UPDATE tracks SET contentHash = :hash WHERE id = :trackId")
    suspend fun setContentHash(trackId: String, hash: String)

    @Query(
        """
        SELECT * FROM tracks
        WHERE source = 'LOCAL' AND contentHash IS NOT NULL AND syncedAt IS NULL
        ORDER BY addedTimestamp DESC
        LIMIT :limit
        """
    )
    suspend fun getUnsyncedLocalTracks(limit: Int): List<TrackEntity>

    @Query("UPDATE tracks SET syncedAt = :timestamp WHERE id = :trackId")
    suspend fun markSynced(trackId: String, timestamp: Long)

    @Query(
        "SELECT * FROM tracks WHERE source = 'LOCAL' AND syncedAt IS NOT NULL ORDER BY artist, album, trackNumber"
    )
    suspend fun getSyncedLocalTracks(): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM tracks WHERE source = 'LOCAL' AND contentHash IS NOT NULL AND syncedAt IS NULL")
    fun countPendingUploadFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tracks WHERE source = 'LOCAL' AND syncedAt IS NOT NULL")
    fun countSyncedFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tracks WHERE source = 'LOCAL'")
    fun countLocalFlow(): Flow<Int>

    @Query(
        """
        SELECT contentHash FROM tracks
        WHERE source = 'LOCAL' AND id NOT IN (:keepIds) AND contentHash IS NOT NULL
        """
    )
    suspend fun localContentHashesNotIn(keepIds: List<String>): List<String>

    @Query("DELETE FROM tracks WHERE source = 'LOCAL' AND id NOT IN (:keepIds)")
    suspend fun deleteLocalTracksNotIn(keepIds: List<String>): Int

    @Query("DELETE FROM tracks WHERE source = :source")
    suspend fun clearBySource(source: SourceType)

    @Query("SELECT * FROM tracks WHERE isLiked = 1 OR isLibrary = 1 OR playCount > 0")
    suspend fun tracksWithUserState(): List<TrackEntity>

    @Query(
        """
        UPDATE tracks SET
            isLiked = (isLiked OR :liked),
            isLibrary = (isLibrary OR :library),
            playCount = MAX(playCount, :playCount),
            lastPlayedTimestamp = CASE
                WHEN :lastPlayed IS NULL THEN lastPlayedTimestamp
                WHEN lastPlayedTimestamp IS NULL OR lastPlayedTimestamp < :lastPlayed THEN :lastPlayed
                ELSE lastPlayedTimestamp
            END
        WHERE id = :id
        """
    )
    suspend fun mergeRestoredState(id: String, liked: Boolean, library: Boolean, playCount: Int, lastPlayed: Long?)

    @Query("SELECT * FROM tracks WHERE contentHash = :hash LIMIT 1")
    suspend fun findByContentHash(hash: String): TrackEntity?
}
