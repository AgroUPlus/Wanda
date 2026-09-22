package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.wander.android.core.database.entity.HistoryEntity
import com.wander.android.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT :limit")
    fun getRecentHistoryFlow(limit: Int = 50): Flow<List<HistoryEntity>>

    /**
     * What was played, most recent first, as tracks rather than as ids.
     *
     * `GROUP BY` rather than one row per play: a song put on four times in an evening is one thing
     * you listened to, and four identical rows in a row is a log, not a history. `MAX(playedAt)` is
     * what the ordering is then done on, so it is the most recent play that decides the position.
     *
     * `INNER JOIN` because a play whose track has since left the library has nothing to show.
     */
    @Query(
        """
        SELECT t.* FROM history h
        INNER JOIN tracks t ON t.id = h.trackId
        GROUP BY h.trackId
        ORDER BY MAX(h.playedAt) DESC
        LIMIT :limit
        """
    )
    fun getRecentlyPlayedTracksFlow(limit: Int = 200): Flow<List<TrackEntity>>

    /** Plays that could not be scrobbled yet — retried when the source comes back online. */
    @Query("SELECT * FROM history WHERE scrobbled = 0 ORDER BY playedAt ASC LIMIT :limit")
    suspend fun getPendingScrobbles(limit: Int = 100): List<HistoryEntity>

    @Query("UPDATE history SET scrobbled = 1 WHERE historyId IN (:ids)")
    suspend fun markScrobbled(ids: List<Long>)

    /**
     * Plays not yet reported to Agro, with the metadata the fleet's statistics need.
     *
     * Joined rather than stored on the history row: a play is a track id and a time, and copying
     * the title and artist into every row would be a second, staler copy of what `tracks` already
     * holds. `INNER JOIN` because a play whose track has since been deleted from the library has
     * nothing to report about.
     */
    @Query(
        """
        SELECT h.historyId AS historyId, h.playedAt AS playedAt, t.title AS title,
               t.artist AS artist, t.album AS album, t.genre AS genre, t.durationMs AS durationMs
        FROM history h
        INNER JOIN tracks t ON t.id = h.trackId
        WHERE h.agroSynced = 0
        ORDER BY h.playedAt ASC
        LIMIT :limit
        """
    )
    suspend fun getPendingAgroScrobbles(limit: Int = 200): List<PendingScrobble>

    @Query("UPDATE history SET agroSynced = 1 WHERE historyId IN (:ids)")
    suspend fun markAgroSynced(ids: List<Long>)

    /**
     * Every play inside a window, with what the track was.
     *
     * Half-open on purpose — `>= since` and `< until` — so two adjacent windows partition the
     * history rather than sharing the play that lands exactly on the boundary. The statistics
     * screen compares a window against the one before it, and a play counted in both would show
     * up as growth that never happened.
     *
     * Wider than [getPendingAgroScrobbles]'s projection because this one feeds a screen rather
     * than an upload: the artwork and the track id are what let the top song be *shown* instead of
     * named.
     */
    @Query(
        """
        SELECT h.playedAt AS playedAt, t.id AS trackId, t.title AS title, t.artist AS artist,
               t.album AS album, t.artworkUrl AS artworkUrl, t.durationMs AS durationMs,
               t.genre AS genre
        FROM history h
        INNER JOIN tracks t ON t.id = h.trackId
        WHERE h.playedAt >= :since AND h.playedAt < :until
        ORDER BY h.playedAt ASC
        """
    )
    suspend fun getPlaysBetween(since: Long, until: Long): List<PlayedTrack>

    /**
     * The artists played before [before], and nothing else about them.
     *
     * Agro Replay needs to know which artists in the recap year were *new*, which is a question
     * about everything that came before it. Reading whole plays to answer it would mean loading a
     * lifetime of history to build one number; the distinct names are a few hundred rows at most.
     */
    @Query(
        """
        SELECT DISTINCT t.artist FROM history h
        INNER JOIN tracks t ON t.id = h.trackId
        WHERE h.playedAt < :before AND t.artist != ''
        """
    )
    suspend fun artistsPlayedBefore(before: Long): List<String>

    /** How many plays sit before [before] — what a purge would remove, counted before it runs. */
    @Query("SELECT COUNT(*) FROM history WHERE playedAt < :before AND agroSynced = 1")
    suspend fun countPurgeableBefore(before: Long): Int

    /**
     * Forgets the individual plays older than [before].
     *
     * `agroSynced = 1` is not an optimisation, it is the safety guard: a play still waiting in the
     * outbox ([getPendingAgroScrobbles]) has not been counted anywhere else yet, and deleting it
     * because it is old would lose it outright rather than merely forget when it happened.
     *
     * Deliberately bounded rather than a blanket delete: a recap offers to tidy the years it has
     * already summarised, never everything. (The unbounded `clearHistory` that used to sit here had
     * no caller and no guard, so it went rather than gaining one.)
     */
    @Query("DELETE FROM history WHERE playedAt < :before AND agroSynced = 1")
    suspend fun deleteSyncedBefore(before: Long): Int

    @Insert
    suspend fun recordHistory(entry: HistoryEntity): Long

    /**
     * Every play, as the two columns a backup carries.
     *
     * The projection rather than whole rows: `historyId` is this database's own numbering and means
     * nothing on another device, and the scrobble flags are decided by the restore, not copied.
     */
    @Query("SELECT trackId, playedAt FROM history ORDER BY playedAt ASC")
    suspend fun allPlays(): List<PlayIdentity>

    /**
     * Inserts restored plays.
     *
     * Deduplication happens in Kotlin against [allPlays] rather than with `OnConflictStrategy
     * .IGNORE`: the primary key is auto-generated, so every restored row is "new" as far as SQLite
     * is concerned and a conflict strategy would never fire. Adding a unique index on
     * `(trackId, playedAt)` would be the other way, but that is a migration that fails on any
     * database already holding a duplicate.
     */
    @Insert
    suspend fun insertPlays(entries: List<HistoryEntity>)
}

/** A play's identity across devices: which track, and when. */
data class PlayIdentity(
    val trackId: String,
    val playedAt: Long
)

