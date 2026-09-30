package com.wander.android.data.repository

import com.wander.android.core.p2p.OffGridNowPlaying
import com.wander.android.core.p2p.OffGridNowPlayingClient
import com.wander.android.core.p2p.OffGridTransport
import com.wander.android.data.sources.agro.AgroFriendNowPlaying
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

internal const val OFF_GRID_HOST = "Nearby device"
private const val POLL_INTERVAL_MS = 2_000L
private const val MISSED_POLLS_BEFORE_STOP = 3

/**
 * Polls a connected peer's now-playing status over off-grid transport when following without internet.
 */
@Singleton
internal class OffGridListenAlongPoller @Inject constructor(
    private val offGrid: OffGridTransport,
    private val offGridNowPlaying: OffGridNowPlayingClient
) {
    private var pollJob: Job? = null

    fun isPolling(): Boolean = pollJob != null

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    fun startPolling(
        scope: CoroutineScope,
        onReading: (AgroFriendNowPlaying) -> Unit,
        onStop: () -> Unit
    ): Result<Unit> {
        val base = offGrid.connectedBaseUrl()
            ?: return Result.failure(IllegalStateException("No device is linked over the radio."))
        val token = offGrid.grantToken()
            ?: return Result.failure(IllegalStateException("That link has no grant to read with."))

        stop()
        pollJob = scope.launch {
            var missed = 0
            while (currentCoroutineContext().isActive) {
                val reading = offGridNowPlaying.read(base, token)
                if (reading == null) {
                    if (++missed >= MISSED_POLLS_BEFORE_STOP) {
                        onStop()
                        return@launch
                    }
                } else {
                    missed = 0
                    if (reading.idle || reading.title.isBlank()) {
                        onStop()
                        return@launch
                    }
                    onReading(reading.toFrame())
                }
                delay(POLL_INTERVAL_MS)
            }
        }
        return Result.success(Unit)
    }

    private fun OffGridNowPlaying.toFrame() = AgroFriendNowPlaying(
        username = OFF_GRID_HOST,
        trackUri = trackId.orEmpty(),
        trackTitle = title,
        artistName = artist,
        albumName = album,
        artworkUrl = null,
        positionMs = positionMs,
        isPlaying = isPlaying,
        updatedAt = "",
        deviceId = null,
        contentHash = contentHash,
        peerLanAddress = null,
        peerLanToken = null
    )
}
