package com.wander.android.ui.agro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.repository.AgroSessionRepository
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.sources.agro.AgroHandoffState
import com.wander.android.data.sources.agro.AgroNode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Agro fleet as the UI sees it: which devices are listening, and the session this one can pick
 * up. Used by both the app-wide resume card and the Devices list in Settings — the state itself
 * lives in the singleton [AgroSessionRepository], so the two always agree.
 */
@HiltViewModel
class AgroSessionViewModel @Inject constructor(
    private val sessionRepository: AgroSessionRepository,
    private val musicRepository: MusicRepository,
    private val playerConnection: PlayerConnection
) : ViewModel() {

    val devices: StateFlow<List<AgroNode>> = sessionRepository.devices
    val incomingHandoff: StateFlow<AgroHandoffState?> = sessionRepository.incomingHandoff

    /** Ungated: whatever another device last played, resumable whenever the user asks. */
    val latestSession: StateFlow<AgroHandoffState?> = sessionRepository.latestSession

    private val _isResuming = MutableStateFlow(false)
    val isResuming: StateFlow<Boolean> = _isResuming.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Cover art for the session, looked up on this device.
     *
     * Wander sends no `artworkUrl` — its cover URLs carry its own server credentials, which have no
     * business travelling through Agro — so the session would otherwise always draw the placeholder.
     * Resolving the track here gets the real cover from whichever backend this device uses, and
     * costs one lookup per session rather than one per frame.
     */
    private val _sessionArtwork = MutableStateFlow<String?>(null)
    val sessionArtwork: StateFlow<String?> = _sessionArtwork.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRepository.latestSession.collectLatest { session ->
                _sessionArtwork.value = session?.artworkUrl
                if (session == null || !session.artworkUrl.isNullOrBlank()) return@collectLatest
                _sessionArtwork.value = musicRepository.resolveTrack(
                    id = session.trackUri,
                    title = session.trackTitle,
                    artist = session.artistName
                )?.artworkUrl
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { sessionRepository.refresh() }
    }

    /**
     * Collected under `repeatOnLifecycle(STARTED)` by the caller, so the socket is open only while
     * the app is on screen and nothing is held in the background.
     */
    suspend fun observeLiveUpdates() {
        sessionRepository.refresh()
        sessionRepository.liveUpdates().collectLatest { sessionRepository.refresh() }
    }

    /**
     * Picks the session up: the same track, at the same position, with the rest of the queue behind
     * it.
     *
     * The playing track is resolved and started **first**, then the rest of the queue is resolved
     * and appended. Resolving fifty tracks up front can mean fifty searches, and waiting for that
     * before any sound came out is the difference between "one click and it continues" and a
     * button that seems to hang.
     */
    fun resume(handoff: AgroHandoffState) {
        if (_isResuming.value) return
        _isResuming.value = true
        viewModelScope.launch {
            val track = musicRepository.resolveTrack(
                id = handoff.trackUri,
                title = handoff.trackTitle,
                artist = handoff.artistName
            )
            if (track == null) {
                _error.value = "\"${handoff.trackTitle}\" isn't available on this device."
                _isResuming.value = false
                return@launch
            }

            playerConnection.play(listOf(track), startPositionMs = handoff.positionMs)
            sessionRepository.consume(handoff)
            _isResuming.value = false

            appendRestOfQueue(handoff, resumedTrackId = track.id)
        }
    }

    /**
     * Everything after the current track, resolved one at a time so the queue fills in behind the
     * music that is already playing. Tracks this device cannot find are skipped rather than
     * failing the resume — a queue missing one entry is still the session.
     */
    private suspend fun appendRestOfQueue(handoff: AgroHandoffState, resumedTrackId: String) {
        val startAt = handoff.queueIndex.takeIf { it >= 0 }?.plus(1) ?: return
        val upcoming = handoff.queue.drop(startAt)
        if (upcoming.isEmpty()) return

        val resolved = upcoming.mapNotNull { entry ->
            musicRepository.resolveTrack(
                id = entry.trackUri,
                title = entry.trackTitle,
                artist = entry.artistName
            )
        }.filterNot { it.id == resumedTrackId }

        if (resolved.isNotEmpty()) playerConnection.addToQueue(resolved)
    }

    fun dismiss(handoff: AgroHandoffState) = sessionRepository.dismiss(handoff)

    /** See [AgroSessionRepository.allowReoffer] — called when the app is focused while idle. */
    fun allowReoffer() = sessionRepository.allowReoffer()

    fun clearError() { _error.value = null }
}
