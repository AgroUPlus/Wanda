package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Tracks indexer attempts and timestamps so background sweeps back off from unreachable tracks
 * across process deaths.
 */
@Dao
interface TrackAttemptDao {

    @Query("UPDATE tracks SET attempts = attempts + 1, lastAttemptAt = :now WHERE id = :trackId")
    suspend fun recordAttempt(trackId: String, now: Long)

    @Query("UPDATE tracks SET attempts = 0, lastAttemptAt = NULL WHERE id = :trackId")
    suspend fun clearAttempts(trackId: String)

    @Query("UPDATE tracks SET attempts = 0, lastAttemptAt = NULL")
    suspend fun clearAllAttempts()

    /**
     * Every measurable track, reduced to what choosing a batch needs.
     *
     * The whole library rather than a page of it: the backoff is decided in Kotlin, so a `LIMIT`
     * applied before it lets backed-off tracks fill the page and hide every track behind them. A
     * four-column projection over ten thousand rows is a few hundred kilobytes, where the full
     * entities would be megabytes and an `IN (...)` over the pending ids would hit SQLite's
     * parameter limit.
     */
    @Query(
        "SELECT id, attempts, lastAttemptAt, " +
            "(localFilePath IS NOT NULL OR isDownloaded = 1) AS onDevice FROM tracks " +
            "WHERE isLive = 0 AND (isLibrary = 1 OR playCount > 0 OR lastPlayedTimestamp IS NOT NULL)"
    )
    suspend fun indexCandidates(): List<IndexCandidate>

    /**
     * Measurable tracks the indexer tried and failed on, and is backing off from.
     *
     * Shown on the Fingerprints screen, so a count that stops moving has a visible reason.
     */
    @Query(
        "SELECT COUNT(*) FROM tracks WHERE attempts > 0 AND isLive = 0 " +
            "AND (isLibrary = 1 OR playCount > 0 OR lastPlayedTimestamp IS NOT NULL)"
    )
    fun failedCountFlow(): Flow<Int>
}

/** One measurable track as the batch planner sees it. */
data class IndexCandidate(
    val id: String,
    val attempts: Int,
    val lastAttemptAt: Long?,
    /** A local file or a completed download: decoded without touching the network. */
    val onDevice: Boolean
)
