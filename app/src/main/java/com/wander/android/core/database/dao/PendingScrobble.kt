package com.wander.android.core.database.dao

/**
 * One unreported play, joined with what the track was.
 *
 * A projection rather than an entity: nothing owns these rows, they exist for the length of one
 * upload batch. See [HistoryDao.getPendingAgroScrobbles].
 */
data class PendingScrobble(
    val historyId: Long,
    val playedAt: Long,
    val title: String,
    val artist: String,
    val album: String?,
    val genre: String?,
    val durationMs: Long
)
