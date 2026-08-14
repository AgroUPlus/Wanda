package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wander.android.core.database.entity.AlbumEntity
import com.wander.android.data.model.SourceType
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {

    @Query("SELECT * FROM albums ORDER BY title ASC")
    fun getAllAlbumsFlow(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE source = :source ORDER BY title ASC")
    fun getAlbumsBySourceFlow(source: SourceType): Flow<List<AlbumEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbums(albums: List<AlbumEntity>)

    @Query("DELETE FROM albums WHERE source = :source")
    suspend fun clearBySource(source: SourceType)
}
