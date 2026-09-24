package com.wander.android.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Query
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.SourceType
import kotlinx.coroutines.flow.Flow

/**
 * Read queries for tracks in the library, albums, artists, search, and playback ranking.
 */
interface TrackLibraryQueries {

    @Query("SELECT * FROM tracks WHERE isLibrary = 1 ORDER BY title ASC")
    fun getAllTracksFlow(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isLibrary = 1 AND isEpisode = 0 ORDER BY title ASC")
    fun pagedTracks(): PagingSource<Int, TrackEntity>

    @Query("SELECT * FROM tracks WHERE isLibrary = 1 AND isEpisode = 0 AND source = :source ORDER BY title ASC")
    fun pagedTracksBySource(source: SourceType): PagingSource<Int, TrackEntity>

    @Query("SELECT id FROM tracks WHERE isLibrary = 1 AND isEpisode = 0 ORDER BY title ASC")
    suspend fun libraryTrackIds(): List<String>

    @Query("SELECT id FROM tracks WHERE isLibrary = 1 AND isEpisode = 0 AND source = :source ORDER BY title ASC")
    suspend fun libraryTrackIdsBySource(source: SourceType): List<String>

    @Query("SELECT * FROM tracks")
    suspend fun getAllTracksOnce(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE isLibrary = 1 AND source = :source ORDER BY title ASC")
    fun getTracksBySourceFlow(source: SourceType): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isLiked = 1 AND isEpisode = 0 ORDER BY lastPlayedTimestamp DESC")
    fun getLikedTracksFlow(): Flow<List<TrackEntity>>

    @Query("SELECT id FROM tracks WHERE isLiked = 1")
    fun getLikedTrackIdsFlow(): Flow<List<String>>

    @Query("SELECT * FROM tracks WHERE isDownloaded = 1 AND isEpisode = 0 AND source != 'LOCAL' ORDER BY title ASC")
    fun getDownloadedTracksFlow(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isDownloaded = 1 AND source != 'LOCAL'")
    suspend fun getDownloadedTracksOnce(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE isDownloaded = 1 OR source = 'LOCAL'")
    suspend fun getOfflineTracksOnce(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE albumId = :albumId ORDER BY discNumber ASC, trackNumber ASC")
    fun getTracksByAlbumFlow(albumId: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: String): TrackEntity?

    @Query(
        """
        SELECT artworkUrl FROM tracks
        WHERE title = :title COLLATE NOCASE AND artist = :artist COLLATE NOCASE
          AND artworkUrl IS NOT NULL
        LIMIT 1
        """
    )
    suspend fun artworkFor(title: String, artist: String): String?

    @Query("SELECT id FROM tracks WHERE id != :excludingId AND durationMs BETWEEN :minDurationMs AND :maxDurationMs")
    suspend fun getCandidateIdsByDuration(excludingId: String, minDurationMs: Long, maxDurationMs: Long): List<String>

    @Query("SELECT * FROM tracks WHERE albumId = :albumId ORDER BY discNumber ASC, trackNumber ASC")
    suspend fun getTracksInAlbum(albumId: String): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE source = :source ORDER BY title ASC")
    suspend fun getTracksInSource(source: SourceType): List<TrackEntity>

    @Query(
        """
        SELECT * FROM tracks
        WHERE artist = :artist COLLATE NOCASE
        ORDER BY playCount DESC, title ASC
        """
    )
    fun getTracksByArtistFlow(artist: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE artist = :artist COLLATE NOCASE")
    suspend fun getTracksByArtistOnce(artist: String): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE isLiked = 1")
    suspend fun getLikedTracksOnce(): List<TrackEntity>

    @Query(
        """
        SELECT * FROM tracks
        WHERE title LIKE '%' || :query || '%'
           OR artist LIKE '%' || :query || '%'
           OR album LIKE '%' || :query || '%'
        ORDER BY playCount DESC
        LIMIT :limit
        """
    )
    suspend fun searchTracks(query: String, limit: Int = 50): List<TrackEntity>

    @Query(
        """
        SELECT * FROM tracks
        WHERE source = :source
          AND (title LIKE '%' || :query || '%'
            OR artist LIKE '%' || :query || '%'
            OR album LIKE '%' || :query || '%')
        ORDER BY playCount DESC
        LIMIT :limit
        """
    )
    suspend fun searchTracksInSource(source: SourceType, query: String, limit: Int = 50): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE source = :source AND isEpisode = 0 ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomTracksInSource(source: SourceType, limit: Int): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE source = :source AND isEpisode = 0 ORDER BY addedTimestamp DESC LIMIT :limit")
    suspend fun getRecentlyAddedInSource(source: SourceType, limit: Int): List<TrackEntity>

    @Query("SELECT * FROM tracks ORDER BY addedTimestamp DESC LIMIT :limit")
    suspend fun getRecentlyAddedTracks(limit: Int = 30): List<TrackEntity>

    @Query(
        """
        SELECT albumId FROM tracks
        WHERE albumId IS NOT NULL
        GROUP BY albumId
        ORDER BY MAX(addedTimestamp) DESC
        LIMIT :limit
        """
    )
    fun observeRecentlyAddedAlbumIds(limit: Int = 12): Flow<List<String>>

    @Query("SELECT * FROM tracks WHERE lastPlayedTimestamp IS NOT NULL AND isEpisode = 0 ORDER BY lastPlayedTimestamp DESC LIMIT :limit")
    suspend fun getRecentlyPlayedTracks(limit: Int = 30): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE isLiked = 1 AND isEpisode = 0 ORDER BY lastPlayedTimestamp DESC, addedTimestamp DESC LIMIT :limit")
    suspend fun getLikedTracksList(limit: Int = 30): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE playCount > 0 AND isEpisode = 0 ORDER BY playCount DESC LIMIT :limit")
    suspend fun getTopPlayedTracks(limit: Int = 30): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE playCount > 0 AND isEpisode = 0")
    suspend fun getPlayedTracksOnce(): List<TrackEntity>

    @Query(
        """
        SELECT * FROM tracks
        WHERE playCount > 3
          AND (lastPlayedTimestamp IS NULL OR lastPlayedTimestamp < :thresholdTimestamp)
        ORDER BY playCount DESC
        LIMIT :limit
        """
    )
    suspend fun getForgottenFavorites(thresholdTimestamp: Long, limit: Int = 30): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE playCount = 0 AND isEpisode = 0 ORDER BY addedTimestamp DESC LIMIT :limit")
    suspend fun getNeverPlayedTracks(limit: Int = 30): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE isLiked = 1 AND isDownloaded = 0 AND source != 'LOCAL' LIMIT :limit")
    suspend fun getLikedNotDownloaded(limit: Int): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE isLive = 0 AND (isLibrary = 1 OR playCount > 0 OR lastPlayedTimestamp IS NOT NULL)")
    suspend fun getFingerprintableTracks(): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM tracks WHERE isLive = 0 AND (isLibrary = 1 OR playCount > 0 OR lastPlayedTimestamp IS NOT NULL)")
    suspend fun getFingerprintableTrackCount(): Int

    @Query("SELECT id FROM tracks WHERE isLive = 0 AND (isLibrary = 1 OR playCount > 0 OR lastPlayedTimestamp IS NOT NULL)")
    suspend fun getFingerprintableTrackIds(): List<String>

    @Query("SELECT * FROM tracks WHERE isLive = 0 AND (isLibrary = 1 OR playCount > 0 OR lastPlayedTimestamp IS NOT NULL)")
    fun getFingerprintableTracksFlow(): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT * FROM tracks 
        WHERE ((localFilePath IS NOT NULL AND localFilePath != '') OR source = 'LOCAL')
          AND title = :title COLLATE NOCASE 
        ORDER BY CASE WHEN (localFilePath IS NOT NULL AND localFilePath != '') THEN 0 ELSE 1 END
        LIMIT :limit
        """
    )
    suspend fun findLocalOrDownloadedCandidates(title: String, limit: Int): List<TrackEntity>

    @Query(
        """
        SELECT * FROM tracks 
        WHERE source = 'NAVIDROME' 
          AND title = :title COLLATE NOCASE 
        LIMIT :limit
        """
    )
    suspend fun findNavidromeCandidates(title: String, limit: Int): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun getTracksByIds(ids: List<String>): List<TrackEntity>
}
