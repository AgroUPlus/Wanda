package com.wander.android.ui.agro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.notification.FriendNotifier
import com.wander.android.data.repository.AgroSessionRepository
import com.wander.android.data.repository.JamPlaybackController
import com.wander.android.data.repository.DropsRepository
import com.wander.android.data.repository.JamRepository
import com.wander.android.data.repository.ListenAlongController
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.SocialRepository
import com.wander.android.data.sources.agro.AgroHandoffState
import com.wander.android.data.sources.agro.AgroLiveMessage
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.agro.AgroNode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Agro fleet as the UI sees it: which devices are listening, and the session this one can pick
 * up. Used by both the app-wide resume card and the Devices list in Settings — the state itself
 * lives in the singleton [AgroSessionRepository], so the two always agree.
 */
@HiltViewModel
internal class AgroSessionViewModel @Inject constructor(
    private val sessionRepository: AgroSessionRepository,
    private val musicRepository: MusicRepository,
    private val playerConnection: PlayerConnection,
    private val socialRepository: SocialRepository,
    private val listenAlong: ListenAlongController,
    private val jamRepository: JamRepository,
    private val jamPlayback: JamPlaybackController,
    private val dropsRepository: DropsRepository,
    private val friendNotifier: FriendNotifier
) : ViewModel() {

    val devices: StateFlow<List<AgroNode>> = sessionRepository.devices

    /**
     * The offer, silenced while this device is playing.
     *
     * Held here rather than left to the card: the repository refreshes on every socket message, so
     * without this the offer flickered in and out underneath whatever was on screen. Music playing
     * here means the answer is already "no" — pausing makes it a question again.
     */
    val incomingHandoff: StateFlow<AgroHandoffState?> = combine(
        sessionRepository.incomingHandoff,
        playerConnection.state
    ) { handoff, playback ->
        handoff?.takeIf { offer ->
            // Nothing is playing here, and the offer is not the track already loaded. Offering to
            // resume the very song this device is sitting on is an offer to do nothing — the same
            // track, from the same place — and it reads as the app not knowing what it is playing.
            !playback.isPlaying && !offer.isSameTrackAs(playback.currentTrack)
        }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
    suspend fun observeLiveUpdates(onLibraryChanged: () -> Unit = {}) {
        sessionRepository.refresh()
        sessionRepository.liveUpdates().collectLatest { message ->
            when (message) {
                is AgroLiveMessage.Session -> sessionRepository.refresh()
                // Library messages used to be dropped on the floor, so music uploaded from another
                // device only appeared when this one was next foregrounded.
                is AgroLiveMessage.Library -> onLibraryChanged()
                // These two used to fall through to `Unit` on the belief that the repositories
                // collected the socket themselves. Nothing did: the frames were parsed and thrown
                // away, so a friend's now-playing only moved when the app was restarted and a
                // listen-along session never followed the host at all. This is the only place the
                // socket is collected, so it is the only place they can be dispatched from.
                is AgroLiveMessage.Friends -> {
                    // Presence first and synchronously, so the feed moves the instant the frame
                    // lands rather than after a round trip that the next frame could cancel.
                    message.presence?.let(socialRepository::applyPresence)
                    // Only a change to the *graph* needs re-reading; presence is already applied.
                    message.event?.let { event ->
                        friendNotifier.notify(event)
                        socialRepository.refresh()
                    }
                }
                is AgroLiveMessage.ListenAlong -> listenAlong.onFrame(message)
                is AgroLiveMessage.JamUpdated -> jamRepository.refresh()
                is AgroLiveMessage.JamNowPlayingFrame -> {
                    // Acted on immediately, and the queue re-read after: the frame is what decides
                    // playback, and waiting for a round trip would put this device behind the room.
                    jamPlayback.onNowPlaying(message.nowPlaying?.toApi())
                    jamRepository.refresh()
                }
                is AgroLiveMessage.TrackDrop -> {
                    // Stored from the frame rather than re-fetched. The socket closes when the app
                    // leaves the screen, and a round trip is one more thing that might not finish
                    // before it does — the frame already carries the whole drop.
                    dropsRepository.onPushed(message.drop)
                    friendNotifier.notifyDrop(message.drop)
                }
            }
        }
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


/**
 * Whether a handoff describes the track this device already has loaded.
 *
 * Matched on title and artist rather than on the uri: the two devices may have reached the same
 * recording through different backends, and a uri from someone else's Navidrome never equals one
 * of ours even when it is the same song.
 */
private fun AgroHandoffState.isSameTrackAs(track: UnifiedTrack?): Boolean {
    val here = track ?: return false
    return trackTitle.equals(here.title, ignoreCase = true) &&
        artistName.equals(here.artist, ignoreCase = true)
}


/** The live frame's shape, as the API models it. Same fields, two layers that do not import each other. */
private fun com.wander.android.data.sources.agro.AgroJamNowPlaying.toApi() =
    com.wander.android.data.sources.agro.JamNowPlaying(
        trackId = trackId,
        title = title,
        artist = artist,
        artworkUrl = artworkUrl,
        durationMs = durationMs,
        positionMs = positionMs,
        // The frame is an instruction to play, not a description of the room's opinion of the
        // track. Skip counts come from the jam itself, which is re-read on the same event.
        skipVotes = 0,
        skipsNeeded = 1,
        youSkipped = false
    )
