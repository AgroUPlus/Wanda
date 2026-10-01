package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wander.android.core.database.entity.PodcastEntity
import com.wander.android.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {

    @Query("SELECT * FROM podcasts ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcasts")
    suspend fun getAll(): List<PodcastEntity>

    @Query("SELECT * FROM podcasts WHERE feedUrl = :feedUrl")
    suspend fun get(feedUrl: String): PodcastEntity?

    /** REPLACE: a re-import of the same feed refreshes its metadata and validators. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(podcast: PodcastEntity)

    /** IGNORE: a bulk OPML import must not reset validators of feeds already subscribed. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIfAbsent(podcasts: List<PodcastEntity>)

    @Query("DELETE FROM podcasts WHERE feedUrl = :feedUrl")
    suspend fun delete(feedUrl: String)

    /**
     * Unplayed episodes of subscribed feeds, newest first. An episode with no progress row has not
     * been started; one whose position reached its duration has been finished.
     */
    @Query(
        """
        SELECT t.* FROM tracks t
        LEFT JOIN episode_progress p ON p.trackId = t.id
        WHERE t.source = 'PODCAST' AND t.isEpisode = 1
          AND (p.trackId IS NULL OR p.positionMs < p.durationMs)
        ORDER BY t.addedTimestamp DESC
        """
    )
    fun observeInbox(): Flow<List<TrackEntity>>

    /**
     * Drops a feed's old episodes beyond the newest [keep], never one the listener has touched:
     * liked, downloaded, played or partly heard episodes stay however old they are.
     */
    @Query(
        """
        DELETE FROM tracks
        WHERE albumId = :albumId AND source = 'PODCAST'
          AND isLiked = 0 AND isDownloaded = 0 AND playCount = 0
          AND id NOT IN (SELECT trackId FROM episode_progress)
          AND id NOT IN (
              SELECT id FROM tracks WHERE albumId = :albumId ORDER BY addedTimestamp DESC LIMIT :keep
          )
        """
    )
    suspend fun pruneEpisodes(albumId: String, keep: Int)

    /** An unsubscribed feed's episodes, except the ones the listener has touched. */
    @Query(
        """
        DELETE FROM tracks
        WHERE albumId = :albumId AND source = 'PODCAST'
          AND isLiked = 0 AND isDownloaded = 0 AND playCount = 0
          AND id NOT IN (SELECT trackId FROM episode_progress)
        """
    )
    suspend fun deleteUntouchedEpisodes(albumId: String)
}
