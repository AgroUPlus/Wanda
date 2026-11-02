package com.wander.android.data.repository

import com.wander.android.core.database.dao.HistoryDao
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.sources.IMusicSource
import com.wander.android.data.sources.navidrome.SubsonicServerException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays a source was not told about when they happened — a Navidrome server that was down, a
 * phone that was offline — sent later, at the time they were played.
 *
 * `history.scrobbled` was always written as an outbox, but only the immediate attempt in
 * `LikesAndHistoryRepository.recordPlay` ever read it, so a play missed then was missing from the
 * server's play counts for good.
 */
@Singleton
internal class SourceScrobbleOutbox @Inject constructor(
    private val historyDao: HistoryDao,
    private val sources: Set<@JvmSuppressWildcards IMusicSource>,
    private val secureStorage: SecureStorage
) {
    /** Sends what each scrobbling source is owed. Answers whether anything is worth trying again later. */
    suspend fun drain(): Boolean {
        // Incognito sends nothing; the plays stay pending rather than being marked as sent by a
        // source that quietly declined to send them.
        if (secureStorage.isIncognitoMode) return false
        var retry = false
        for (source in sources) {
            if (!source.capabilities.scrobble || !source.isConfigured.value) continue
            retry = drain(source) || retry
        }
        return retry
    }

    private suspend fun drain(source: IMusicSource): Boolean {
        repeat(MAX_BATCHES) {
            val plays = historyDao.getPendingScrobbles(source.sourceType.idPrefix, BATCH)
            if (plays.isEmpty()) return false
            val settled = mutableListOf<Long>()
            for (play in plays) {
                val error = source.scrobble(play.trackId, play.playedAt).exceptionOrNull()
                if (error != null && !isGone(error)) {
                    historyDao.markScrobbled(settled)
                    return true
                }
                settled += play.historyId
            }
            historyDao.markScrobbled(settled)
        }
        return true
    }

    /**
     * The server answered that the song does not exist (Subsonic error 70): it was deleted since,
     * and no retry will change that. Kept pending, it would block every play queued behind it.
     */
    private fun isGone(error: Throwable): Boolean =
        error is SubsonicServerException && error.code == SUBSONIC_NOT_FOUND

    private companion object {
        const val BATCH = 50
        const val MAX_BATCHES = 10
        const val SUBSONIC_NOT_FOUND = 70
    }
}
