package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wander.android.core.database.entity.PlaylistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM local_playlists ORDER BY updatedAt DESC")
    suspend fun getAllPlaylists(): List<PlaylistEntity>

    /** Every Wanda playlist's id and when it last changed, again whenever one does. */
    @Query("SELECT id || ':' || updatedAt FROM local_playlists")
    fun observeVersions(): Flow<List<String>>

    @Query("SELECT * FROM local_playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylistById(id: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    /**
     * Swaps one id in a playlist's comma-joined `trackIds` in a single statement, so it is atomic
     * against the user editing the same playlist. `updatedAt` is left alone: resolving a track is
     * not an edit, and bumping it would reshuffle the playlist list while an import runs.
     * Returns 0 when the playlist no longer holds [oldId] (deleted or removed by the user).
     */
    @Query(
        """
        UPDATE local_playlists
        SET trackIds = substr(
            replace(',' || trackIds || ',', ',' || :oldId || ',', ',' || :newId || ','),
            2,
            length(trackIds) + length(:newId) - length(:oldId)
        )
        WHERE id = :playlistId AND instr(',' || trackIds || ',', ',' || :oldId || ',') > 0
        """
    )
    suspend fun replaceTrackId(playlistId: String, oldId: String, newId: String): Int

    @Query("DELETE FROM local_playlists WHERE id = :id")
    suspend fun deletePlaylist(id: String)
}
