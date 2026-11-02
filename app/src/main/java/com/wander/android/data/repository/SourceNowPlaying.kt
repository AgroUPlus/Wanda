package com.wander.android.data.repository

import android.util.Log
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.IMusicSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells the track's own backend what is playing — Navidrome's "now playing" list, which its web UI
 * and other clients show — under the same rules as a play: nothing in incognito, nothing while
 * tuned in to a friend.
 */
@Singleton
internal class SourceNowPlaying @Inject constructor(
    private val sources: Set<@JvmSuppressWildcards IMusicSource>,
    private val secureStorage: SecureStorage,
    private val suppression: ScrobbleSuppression
) {
    /** Best effort, like the scrobbling services' now playing: a miss is logged and shown nowhere. */
    suspend fun announce(track: UnifiedTrack) {
        if (secureStorage.isIncognitoMode || suppression.isSuppressed) return
        val source = sources.firstOrNull {
            it.sourceType == track.source && it.capabilities.scrobble && it.isConfigured.value
        } ?: return
        source.nowPlaying(track.id).onFailure {
            Log.i(TAG, "${source.displayName} was not told what is playing: ${it.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "SourceNowPlaying"
    }
}
