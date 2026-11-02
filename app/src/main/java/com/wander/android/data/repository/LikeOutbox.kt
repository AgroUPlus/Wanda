package com.wander.android.data.repository

import android.content.Context
import android.util.Log
import com.wander.android.R
import com.wander.android.core.database.dao.LikeSyncDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.PendingLikeEntity
import com.wander.android.core.database.entity.RemoteLikeEntity
import com.wander.android.core.network.loggableMessage
import com.wander.android.core.work.LikeSyncScheduler
import com.wander.android.data.model.SourceType
import com.wander.android.data.sources.IMusicSource
import com.wander.android.data.sources.navidrome.SubsonicServerException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Likes and unlikes on their way to the backend that holds the track.
 *
 * A like is Room's the moment it is made; the backend hears about it through here. Every wish is
 * written to `pending_likes` first and cleared only once the backend has confirmed it, so a like
 * made offline, or one the server dropped, is sent again by [LikeSyncWorker][com.wander.android.core.work.LikeSyncWorker]
 * on a backoff rather than lost. A wish that can never succeed — the track is gone from the
 * server, or it kept failing — is dropped and said once, through [failures].
 */
@Singleton
class LikeOutbox @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: LikeSyncDao,
    private val trackDao: TrackDao,
    private val sources: Set<@JvmSuppressWildcards IMusicSource>,
    private val scheduler: LikeSyncScheduler
) {
    private val _failures = MutableSharedFlow<String>(extraBufferCapacity = FAILURE_BUFFER)

    /** A like that will never reach its backend, said once, for the app's snackbar. */
    val failures: SharedFlow<String> = _failures.asSharedFlow()

    /** Queues [liked] for [trackId] and tries it at once; a miss is left to the worker. */
    suspend fun submit(trackId: String, liked: Boolean) {
        val wish = PendingLikeEntity(trackId, liked, attempts = 0, queuedAt = System.currentTimeMillis())
        dao.queue(wish)
        if (send(wish) == Outcome.RETRY) scheduler.syncSoon()
    }

    /** Has the worker send whatever is still owed, if anything is: a backend may be back. */
    internal suspend fun resumeIfOwed() {
        if (dao.pending(1).isNotEmpty()) scheduler.syncSoon()
    }

    /** Sends what is owed. Answers whether anything is worth trying again later. */
    internal suspend fun drain(): Boolean {
        var retry = false
        for (wish in dao.pending(BATCH)) {
            retry = send(wish) == Outcome.RETRY || retry
        }
        return retry
    }

    private suspend fun send(wish: PendingLikeEntity): Outcome {
        val source = sourceOf(wish.trackId)
        if (source == null || !source.capabilities.likes) {
            dao.settle(wish.trackId, wish.queuedAt)
            return Outcome.DONE
        }
        // Signed out: kept for when it is back, but not worth a retry loop until then.
        if (!source.isConfigured.value) return Outcome.WAITING
        val error = source.setLiked(wish.trackId, wish.liked).exceptionOrNull()
        if (error == null) {
            dao.settle(wish.trackId, wish.queuedAt)
            if (wish.liked) {
                dao.recordRemoteLikes(listOf(RemoteLikeEntity(wish.trackId, source.sourceType)))
            } else {
                dao.forgetRemoteLikes(listOf(wish.trackId))
            }
            return Outcome.DONE
        }
        Log.w(TAG, "like not synced to ${source.displayName}: ${error.loggableMessage()}")
        if (isPermanent(error) || wish.attempts + 1 >= MAX_ATTEMPTS) {
            dao.settle(wish.trackId, wish.queuedAt)
            reportGivenUp(wish, source)
            return Outcome.DONE
        }
        dao.countAttempt(wish.trackId, wish.queuedAt)
        return Outcome.RETRY
    }

    private suspend fun reportGivenUp(wish: PendingLikeEntity, source: IMusicSource) {
        val title = trackDao.getTrackById(wish.trackId)?.title
        val message = when {
            title == null -> context.getString(R.string.like_sync_gave_up, source.displayName)
            wish.liked -> context.getString(R.string.like_sync_gave_up_like, title, source.displayName)
            else -> context.getString(R.string.like_sync_gave_up_unlike, title, source.displayName)
        }
        _failures.tryEmit(message)
    }

    private fun sourceOf(trackId: String): IMusicSource? {
        val type = SourceType.entries.firstOrNull { trackId.startsWith(it.idPrefix) } ?: return null
        return sources.firstOrNull { it.sourceType == type }
    }

    /**
     * Failures no retry can mend: a source that cannot store likes at all, or a Subsonic server
     * answering that the song does not exist (code 70) — deleted since it was liked.
     */
    private fun isPermanent(error: Throwable): Boolean =
        error is UnsupportedOperationException ||
            (error is SubsonicServerException && error.code == SUBSONIC_NOT_FOUND)

    private enum class Outcome { DONE, RETRY, WAITING }

    private companion object {
        const val TAG = "LikeOutbox"
        const val BATCH = 100
        const val MAX_ATTEMPTS = 8
        const val FAILURE_BUFFER = 4
        const val SUBSONIC_NOT_FOUND = 70
    }
}
