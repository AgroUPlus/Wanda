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

    /**
     * A picture for an artist named only by text, when no artist page has been fetched: the cover
     * of their most played track this device knows. Null rather than a wrong face.
     */
    @Query(
        """
        SELECT artworkUrl FROM tracks
        WHERE artist = :artist COLLATE NOCASE AND artworkUrl IS NOT NULL
        ORDER BY playCount DESC
        LIMIT 1
        """
    )
    suspend fun trackArtworkFor(artist: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(artist: ArtistEntity)
}
