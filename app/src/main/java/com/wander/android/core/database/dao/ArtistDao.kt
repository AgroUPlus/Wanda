package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wander.android.core.database.entity.ArtistEntity

@Dao
interface ArtistDao {

    @Query("SELECT * FROM artists WHERE nameKey = :nameKey LIMIT 1")
    suspend fun getByName(nameKey: String): ArtistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(artist: ArtistEntity)
}
