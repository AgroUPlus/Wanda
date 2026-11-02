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

    /** A subscription that has moved to [fetchUrl], so subscribing to its new address is not a duplicate. */
    @Query("SELECT * FROM podcasts WHERE fetchUrl = :fetchUrl LIMIT 1")
    suspend fun getByFetchUrl(fetchUrl: String): PodcastEntity?

    @Query("UPDATE podcasts SET autoDownload = :enabled WHERE feedUrl = :feedUrl")
    suspend fun setAutoDownload(feedUrl: String, enabled: Boolean)

    /** A feed's newest episodes, newest first. */
    @Query("SELECT * FROM tracks WHERE albumId = :albumId AND source = 'PODCAST' ORDER BY addedTimestamp DESC LIMIT :limit")
    suspend fun newestEpisodes(albumId: String, limit: Int): List<TrackEntity>

    /**
     * A feed's downloaded episodes beyond the newest [keep] downloads, for an auto-downloading show
     * to let go of. Never a liked episode, nor one the listener is partway through.
     */
    @Query(
        """
        SELECT * FROM tracks
        WHERE albumId = :albumId AND source = 'PODCAST' AND isDownloaded = 1 AND isLiked = 0
          AND id NOT IN (SELECT trackId FROM episode_progress WHERE positionMs > 0 AND positionMs < durationMs)
          AND id NOT IN (
              SELECT id FROM tracks WHERE albumId = :albumId AND isDownloaded = 1
              ORDER BY addedTimestamp DESC LIMIT :keep
          )
        """
    )
    suspend fun downloadedEpisodesBeyond(albumId: String, keep: Int): List<TrackEntity>

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
