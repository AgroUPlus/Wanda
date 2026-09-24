package com.wander.android.data.repository

import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.sources.agro.AgroFriendNowPlaying
import com.wander.android.data.sources.agro.AgroListenAlongApi
import com.wander.android.data.sources.agro.AgroLiveMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Follows a friend's playback across online Agro sessions and off-grid radio links.
 */
@Singleton
internal class ListenAlongController @Inject constructor(
    private val api: AgroListenAlongApi,
    private val playerConnection: PlayerConnection,
    private val suppression: ScrobbleSuppression,
    private val musicRepository: MusicRepository,
    private val offGridPoller: OffGridListenAlongPoller,
    private val playerSync: ListenAlongPlayerSync
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _session = MutableStateFlow<ListenAlongSession?>(null)
    val session: StateFlow<ListenAlongSession?> = _session.asStateFlow()

    private val followMutex = Mutex()
    private var pending: AgroFriendNowPlaying? = null

    suspend fun start(host: String): Result<Unit> = api.startListenAlong(host).map { state ->
        suppression.set(true)
        playerConnection.setFollowing(true) { scope.launch { stop() } }
        _session.value = ListenAlongSession(
            host = state.host,
            listenerCount = state.listeners.size,
            nowPlaying = state.nowPlaying,
            resolvedFrom = null
        )
        playerSync.reset()
        state.nowPlaying?.let { submit(it) }
        Unit
    }

    suspend fun stop(): Result<Unit> {
        val wasOffGrid = offGridPoller.isPolling()
        offGridPoller.stop()
        suppression.set(false)
        playerConnection.setFollowing(false)
        _session.value = null
        playerSync.reset()
        pending = null
        musicRepository.clearEphemeralStreams()
        if (wasOffGrid) return Result.success(Unit)
        return api.stopListenAlong().map { }
    }

    suspend fun startOffGrid(): Result<Unit> {
        suppression.set(true)
        playerConnection.setFollowing(true) { scope.launch { stop() } }
        _session.value = ListenAlongSession(
            host = OFF_GRID_HOST,
            listenerCount = 1,
            nowPlaying = null,
            resolvedFrom = null
        )
        playerSync.reset()

        return offGridPoller.startPolling(
            scope = scope,
            onReading = { submit(it) },
            onStop = { scope.launch { stop() } }
        )
    }

    private fun submit(now: AgroFriendNowPlaying) {
        pending = now
        scope.launch {
            followMutex.withLock {
                val target = pending ?: return@withLock
                pending = null
                follow(target)
            }
        }
    }

    fun onFrame(frame: AgroLiveMessage.ListenAlong) {
        val current = _session.value ?: return
        if (!frame.host.equals(current.host, ignoreCase = true)) return

        if (frame.stopped) {
            scope.launch { stop() }
            return
        }
        if (frame.trackTitle.isBlank()) return
        if (frame.isLocked) {
            _session.value = current.copy(
                nowPlaying = current.nowPlaying?.copy(isLocked = true),
                unresolvable = null
            )
            return
        }

        submit(
            AgroFriendNowPlaying(
                username = frame.host,
                trackUri = frame.trackUri,
                trackTitle = frame.trackTitle,
                artistName = frame.artistName,
                albumName = frame.albumName,
                artworkUrl = frame.artworkUrl,
                positionMs = frame.positionMs,
                isPlaying = frame.isPlaying,
                updatedAt = "",
                deviceId = frame.deviceId,
                contentHash = frame.contentHash,
                peerLanAddress = frame.peerLanAddress,
                peerLanToken = frame.peerLanToken
            )
        )
    }

    private suspend fun follow(now: AgroFriendNowPlaying) {
        _session.value = _session.value?.copy(nowPlaying = now)
        playerSync.syncPlayback(
            now = now,
            onSessionUpdated = { resolvedFrom, unresolvable ->
                _session.value = _session.value?.copy(
                    resolvedFrom = resolvedFrom,
                    unresolvable = unresolvable
                )
            },
            onStop = { scope.launch { stop() } }
        )
    }
}
