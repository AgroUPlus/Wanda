package com.wander.android.core.database.dao

import androidx.room.Query
import com.wander.android.core.database.entity.TrackEntity
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

    /** Every file a row points at, which is what keeps a download from being swept as an orphan. */
    @Query("SELECT localFilePath FROM tracks WHERE localFilePath IS NOT NULL")
    suspend fun localFilePaths(): List<String>

    @Query("SELECT id FROM tracks WHERE source = 'LOCAL'")
    suspend fun localTrackIds(): List<String>

    /** At most [MAX_BOUND_IDS] ids per call — see [TrackDao.applyLocalScan]. */
    @Query("SELECT contentHash FROM tracks WHERE source = 'LOCAL' AND id IN (:ids) AND contentHash IS NOT NULL")
    suspend fun localContentHashesOf(ids: List<String>): List<String>

    /** At most [MAX_BOUND_IDS] ids per call — see [TrackDao.applyLocalScan]. */
    @Query("DELETE FROM tracks WHERE source = 'LOCAL' AND id IN (:ids)")
    suspend fun deleteLocalTracks(ids: List<String>): Int

    /**
     * Refreshes the file facts a rescan reads, which [TrackDao.upsertTracks] leaves alone on an
     * existing row. A size that changed means the bytes did, so the content hash and the server's
     * confirmation of it no longer describe this file and are cleared for the hasher to redo.
     * SQLite evaluates every right-hand side against the row as it was, so the comparison sees the
     * old size.
     */
    @Query(
        """
        UPDATE tracks SET
            contentHash = CASE
                WHEN sizeBytes IS NOT NULL AND :sizeBytes IS NOT NULL AND sizeBytes != :sizeBytes THEN NULL
                ELSE contentHash
            END,
            syncedAt = CASE
                WHEN sizeBytes IS NOT NULL AND :sizeBytes IS NOT NULL AND sizeBytes != :sizeBytes THEN NULL
                ELSE syncedAt
            END,
            sizeBytes = COALESCE(:sizeBytes, sizeBytes),
            fileExtension = COALESCE(:fileExtension, fileExtension),
            albumArtist = :albumArtist
        WHERE id = :id
        """
    )
    suspend fun refreshLocalFileFields(id: String, sizeBytes: Long?, fileExtension: String?, albumArtist: String?)

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
