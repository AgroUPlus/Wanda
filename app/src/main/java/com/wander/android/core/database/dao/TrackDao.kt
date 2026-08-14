package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.database.entity.TrackSourceFields
import com.wander.android.data.model.SourceType
import kotlinx.coroutines.flow.Flow

/** SQLite returns this rowId from an INSERT OR IGNORE that hit an existing row. */
private const val CONFLICT_ROW_ID = -1L

@Dao
interface TrackDao {

    /**
     * The Library screen. Restricted to `isLibrary` rows so that searching, radio and Archive
     * browsing — all of which persist their results for offline use — do not grow the library.
     */
    @Query("SELECT * FROM tracks WHERE isLibrary = 1 ORDER BY title ASC")
    fun getAllTracksFlow(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isLibrary = 1 AND source = :source ORDER BY title ASC")
    fun getTracksBySourceFlow(source: SourceType): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isLiked = 1 ORDER BY lastPlayedTimestamp DESC")
    fun getLikedTracksFlow(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isDownloaded = 1 AND source != 'LOCAL' ORDER BY title ASC")
    fun getDownloadedTracksFlow(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE albumId = :albumId ORDER BY discNumber ASC, trackNumber ASC")
    fun getTracksByAlbumFlow(albumId: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE albumId = :albumId ORDER BY discNumber ASC, trackNumber ASC")
    suspend fun getTracksInAlbum(albumId: String): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE source = :source ORDER BY title ASC")
    suspend fun getTracksInSource(source: SourceType): List<TrackEntity>

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

    @Query("SELECT * FROM tracks WHERE source = :source ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomTracksInSource(source: SourceType, limit: Int): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE source = :source ORDER BY addedTimestamp DESC LIMIT :limit")
    suspend fun getRecentlyAddedInSource(source: SourceType, limit: Int): List<TrackEntity>

    @Query("SELECT * FROM tracks ORDER BY addedTimestamp DESC LIMIT :limit")
    suspend fun getRecentlyAddedTracks(limit: Int = 30): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE lastPlayedTimestamp IS NOT NULL ORDER BY lastPlayedTimestamp DESC LIMIT :limit")
    suspend fun getRecentlyPlayedTracks(limit: Int = 30): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE isLiked = 1 ORDER BY lastPlayedTimestamp DESC, addedTimestamp DESC LIMIT :limit")
    suspend fun getLikedTracksList(limit: Int = 30): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE playCount > 0 ORDER BY playCount DESC LIMIT :limit")
    suspend fun getTopPlayedTracks(limit: Int = 30): List<TrackEntity>

    /** Well-loved tracks the user has not returned to lately — the "forgotten favourites" mix. */
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

    @Query("SELECT * FROM tracks WHERE playCount = 0 ORDER BY addedTimestamp DESC LIMIT :limit")
    suspend fun getNeverPlayedTracks(limit: Int = 30): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE isLiked = 1 AND isDownloaded = 0 AND source != 'LOCAL' LIMIT :limit")
    suspend fun getLikedNotDownloaded(limit: Int): List<TrackEntity>

    /**
     * Insert-or-refresh that cannot destroy user state.
     *
     * A plain `@Insert(REPLACE)` is a DELETE followed by an INSERT, so refetching a track the user
     * had already downloaded wiped its `localFilePath`, `isLiked` and `playCount`. Instead: insert
     * only rows we do not have, then update the remaining ones through [TrackSourceFields], which
     * carries backend metadata and nothing else.
     */
    @Transaction
    suspend fun upsertTracks(tracks: List<TrackEntity>) {
        if (tracks.isEmpty()) return
        val rowIds = insertNewTracks(tracks)
        val existing = tracks.filterIndexed { index, _ -> rowIds[index] == CONFLICT_ROW_ID }
        if (existing.isNotEmpty()) {
            updateSourceFields(existing.map { it.toSourceFields() })
        }
    }

    /** Returns -1 for every row that already existed, leaving it untouched. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNewTracks(tracks: List<TrackEntity>): List<Long>

    @Update(entity = TrackEntity::class)
    suspend fun updateSourceFields(fields: List<TrackSourceFields>)

    /**
     * Promotes tracks into the library. Deliberately one-way: a track that is part of the user's
     * collection must not fall out of it just because a later search happened to return it.
     */
    @Query("UPDATE tracks SET isLibrary = 1 WHERE id IN (:trackIds)")
    suspend fun markAsLibrary(trackIds: List<String>)

    @Query("UPDATE tracks SET isLiked = :isLiked WHERE id = :trackId")
    suspend fun setLiked(trackId: String, isLiked: Boolean)

    @Query("UPDATE tracks SET isDownloaded = :isDownloaded, localFilePath = :localPath WHERE id = :trackId")
    suspend fun setDownloaded(trackId: String, isDownloaded: Boolean, localPath: String?)

    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayedTimestamp = :timestamp WHERE id = :trackId")
    suspend fun incrementPlayCount(trackId: String, timestamp: Long)

    @Query("DELETE FROM tracks WHERE source = :source")
    suspend fun clearBySource(source: SourceType)
}
