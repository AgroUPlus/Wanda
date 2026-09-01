package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wander.android.core.database.entity.CanonicalMetadataEntity

@Dao
interface CanonicalMetadataDao {

    @Query("SELECT * FROM canonical_metadata")
    suspend fun getAllOnce(): List<CanonicalMetadataEntity>

    @Query("SELECT * FROM canonical_metadata WHERE trackId = :trackId")
    suspend fun forTrack(trackId: String): CanonicalMetadataEntity?

    /** `REPLACE`: a later catalogue entry is a better-informed answer to the same question. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<CanonicalMetadataEntity>)

    @Query("DELETE FROM canonical_metadata WHERE trackId = :trackId")
    suspend fun deleteFor(trackId: String)
}
