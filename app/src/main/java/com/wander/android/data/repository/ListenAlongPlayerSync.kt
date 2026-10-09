package com.wander.android.data.repository

import android.os.SystemClock
import android.util.Log
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.sources.agro.AgroFriendNowPlaying
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

private const val TAG = "ListenAlong"
private const val DRIFT_TOLERANCE_MS = 2_000L

/**
 * Resolves mirrored tracks and synchronizes Media3 playback state, drift correction, and transport.
 */
@Singleton
internal class ListenAlongPlayerSync @Inject constructor(
    private val resolver: ListenAlongResolver,
    private val playerConnection: PlayerConnection
) {
    /** The track currently mirrored, so an unchanged frame does not restart it. */
    var playingKey: String? = null

    fun reset() {
        playingKey = null
    }

    suspend fun syncPlayback(
        now: AgroFriendNowPlaying,
        receivedAtMs: Long,
        onSessionUpdated: (resolvedFrom: ResolvedFrom?, unresolvable: String?) -> Unit,
        onStop: () -> Unit
    ) {
        // The id is part of the identity: a song and its video share title and artist.
        val key = now.trackUri + "|" + now.artistName + " " + now.trackTitle
        if (key == playingKey) {
            correctDrift(now, receivedAtMs)
            matchTransport(now)
            return
        }

        val resolved = resolver.resolve(
            title = now.trackTitle,
            artist = now.artistName,
            hostDevice = now.deviceId,
            hostLanAddress = now.peerLanAddress,
            hostLanToken = now.peerLanToken,
            contentHash = now.contentHash,
            hostTrackId = now.trackUri.takeIf { it.isNotBlank() }
        )
        if (resolved == null) {
            Log.i(TAG, "No source has that track")
            onSessionUpdated(null, now.trackTitle)
            playingKey = null
            return
        }

        playingKey = key
        onSessionUpdated(resolved.from, null)
        withContext(Dispatchers.Main) {
            playerConnection.setFollowing(false)
            // Resolving can take seconds, and the host kept playing meanwhile.
            playerConnection.play(listOf(resolved.track), startPositionMs = hostPositionMs(now, receivedAtMs))
            playerConnection.setFollowing(true) { onStop() }
            playerConnection.followerSetPlaying(now.isPlaying)
        }
    }

    private suspend fun correctDrift(now: AgroFriendNowPlaying, receivedAtMs: Long) = withContext(Dispatchers.Main) {
        if (playerConnection.state.value.isBuffering) return@withContext
        val here = playerConnection.controller.value?.currentPosition ?: return@withContext
        val target = hostPositionMs(now, receivedAtMs)
        if (abs(here - target) > DRIFT_TOLERANCE_MS) {
            playerConnection.followerSeek(target)
        }
    }

    /** Where the host is now: the reported position plus the time since the frame arrived. */
    private fun hostPositionMs(now: AgroFriendNowPlaying, receivedAtMs: Long): Long =
        estimateHostPosition(now.positionMs, now.isPlaying, SystemClock.elapsedRealtime() - receivedAtMs)

    private suspend fun matchTransport(now: AgroFriendNowPlaying) = withContext(Dispatchers.Main) {
        playerConnection.followerSetPlaying(now.isPlaying)
    }
}

/** A paused host has not moved since the frame; a playing one has moved on by the frame's age. */
internal fun estimateHostPosition(positionMs: Long, isPlaying: Boolean, ageMs: Long): Long =
    if (isPlaying) positionMs + ageMs.coerceAtLeast(0L) else positionMs
