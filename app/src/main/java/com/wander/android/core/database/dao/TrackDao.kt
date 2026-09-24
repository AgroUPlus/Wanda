package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.database.entity.TrackSourceFields
import com.wander.android.data.model.isOneShotTrackId

/** SQLite returns this rowId from an INSERT OR IGNORE that hit an existing row. */
private const val CONFLICT_ROW_ID = -1L

@Dao
interface TrackDao : TrackLibraryQueries, TrackSyncQueries {

    /**
     * Records that a track turned out to be a livestream.
     */
    @Query("UPDATE tracks SET isLive = 1 WHERE id = :trackId AND isLive = 0")
    suspend fun markLive(trackId: String)

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
        val storable = tracks.filterNot { isOneShotTrackId(it.id) }
        if (storable.isEmpty()) return
        val rowIds = insertNewTracks(storable)
        val existing = storable.filterIndexed { index, _ -> rowIds[index] == CONFLICT_ROW_ID }
        if (existing.isNotEmpty()) {
            updateSourceFields(existing.map { it.toSourceFields() })
            existing.filter { it.isEpisode }.map { it.id }.takeIf { it.isNotEmpty() }?.let { markAsEpisodes(it) }
        }
    }

    @Query("UPDATE tracks SET isEpisode = 1 WHERE id IN (:trackIds)")
    suspend fun markAsEpisodes(trackIds: List<String>)

    /**
     * Deletes rows that should never have been written — see [upsertTracks].
     */
    @Query("DELETE FROM tracks WHERE id LIKE 'relay:%' OR id LIKE 'p2p:%'")
    suspend fun deleteOneShotTrackRows(): Int

    /** Returns -1 for every row that already existed, leaving it untouched. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNewTracks(tracks: List<TrackEntity>): List<Long>

    @Update(entity = TrackEntity::class)
    suspend fun updateSourceFields(fields: List<TrackSourceFields>)

    /**
     * Promotes tracks into the library.
     */
    @Query("UPDATE tracks SET isLibrary = 1 WHERE id IN (:trackIds)")
    suspend fun markAsLibrary(trackIds: List<String>)

    @Query("UPDATE tracks SET isLiked = :isLiked WHERE id = :trackId")
    suspend fun setLiked(trackId: String, isLiked: Boolean)

    /**
     * Writes corrected display metadata onto a row.
     */
    @Query("UPDATE tracks SET title = :title, artist = :artist, album = :album WHERE id = :trackId")
    suspend fun setDisplayMetadata(trackId: String, title: String, artist: String, album: String?)

    @Query(
        "UPDATE tracks SET isDownloaded = :isDownloaded, localFilePath = :localPath, " +
            "downloadedAt = :downloadedAt WHERE id = :trackId"
    )
    suspend fun setDownloaded(
        trackId: String,
        isDownloaded: Boolean,
        localPath: String?,
        downloadedAt: Long? = if (isDownloaded) System.currentTimeMillis() else null
    )

    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayedTimestamp = :timestamp WHERE id = :trackId")
    suspend fun incrementPlayCount(trackId: String, timestamp: Long)
}
