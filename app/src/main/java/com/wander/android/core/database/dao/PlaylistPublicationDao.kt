package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wander.android.core.database.entity.PlaylistPublicationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistPublicationDao {

    @Query("SELECT * FROM playlist_publications WHERE playlistId = :playlistId")
    fun observe(playlistId: String): Flow<PlaylistPublicationEntity?>

    @Query("SELECT * FROM playlist_publications WHERE playlistId = :playlistId")
    suspend fun get(playlistId: String): PlaylistPublicationEntity?

    /** REPLACE: publishing again makes a new copy on the server, and that copy is the one to edit. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(publication: PlaylistPublicationEntity)

    @Query("DELETE FROM playlist_publications WHERE playlistId = :playlistId")
    suspend fun delete(playlistId: String)

    @Query("UPDATE playlist_publications SET visibility = :visibility WHERE playlistId = :playlistId")
    suspend fun setVisibility(playlistId: String, visibility: String)
}
