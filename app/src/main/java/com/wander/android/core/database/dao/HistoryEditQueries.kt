package com.wander.android.core.database.dao

import androidx.room.Query

/**
 * The user's own edits to their listening history. Apart from [HistoryDao], which is at the
 * file-size cap.
 *
 * Both hide rather than delete — see `HistoryEntity.hidden`: a history row is also the scrobble
 * outbox, and a play removed from view must still reach the services that have not heard of it.
 */
interface HistoryEditQueries {

    /** Takes one play out of the History screen and Recently played. */
    @Query("UPDATE history SET hidden = 1 WHERE historyId = :historyId")
    suspend fun hidePlay(historyId: Long): Int

    /** Clears the History screen: every play still showing there is hidden. */
    @Query("UPDATE history SET hidden = 1 WHERE hidden = 0")
    suspend fun hideAllPlays(): Int
}
