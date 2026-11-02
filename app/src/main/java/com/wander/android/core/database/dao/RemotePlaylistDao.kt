package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.wander.android.core.database.entity.RemotePlaylistEntity
import com.wander.android.data.model.SourceType
import kotlinx.coroutines.flow.Flow

@Dao
interface RemotePlaylistDao {

    @Query("SELECT * FROM remote_playlists ORDER BY position ASC")
    fun observeAll(): Flow<List<RemotePlaylistEntity>>

    @Query("SELECT * FROM remote_playlists ORDER BY position ASC")
    suspend fun getAll(): List<RemotePlaylistEntity>

    @Query("SELECT * FROM remote_playlists WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RemotePlaylistEntity?

    @Query("SELECT * FROM remote_playlists WHERE source = :source")
    suspend fun getBySource(source: SourceType): List<RemotePlaylistEntity>

    /**
     * Swaps in [source]'s whole list as the backend just answered it. A playlist it no longer
     * lists goes; one it still lists keeps the track ids it was last opened with, since the list
     * call does not carry them.
     */
    @Transaction
    suspend fun replaceSource(source: SourceType, playlists: List<RemotePlaylistEntity>) {
        val knownTracks = getBySource(source).associate { it.id to it.trackIds }
        deleteSource(source)
        insertAll(playlists.map { it.copy(trackIds = it.trackIds ?: knownTracks[it.id]) })
    }

    @Query("UPDATE remote_playlists SET trackIds = :trackIds, songCount = :songCount WHERE id = :id")
    suspend fun setTracks(id: String, trackIds: String, songCount: Int)

    @Query("DELETE FROM remote_playlists WHERE source = :source")
    suspend fun deleteSource(source: SourceType)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(playlists: List<RemotePlaylistEntity>)
}
