package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wander.android.core.database.entity.RecordingLinkEntity

@Dao
interface RecordingLinkDao {

    @Query("SELECT * FROM recording_links")
    suspend fun getAllOnce(): List<RecordingLinkEntity>

    /**
     * `REPLACE`, unlike [RecordingSplitDao.upsert]: re-indexing a track is a fresh measurement of
     * the same question, and the newer similarity is the better answer. A pin records a decision a
     * person made once; this records what the audio currently says.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(links: List<RecordingLinkEntity>)

    /** Drops every link naming [trackId], for a track being re-indexed or forgotten. */
    @Query("DELETE FROM recording_links WHERE idA = :trackId OR idB = :trackId")
    suspend fun clearFor(trackId: String)
}
