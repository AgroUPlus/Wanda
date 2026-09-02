package com.wander.android.ui.screens.player

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.repository.RenditionFinder
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlaybackCoordinator
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.core.audio.fingerprint.FingerprintIndexWorker
import com.wander.android.data.repository.FingerprintStatus
import com.wander.android.data.repository.FingerprintStatusRepository
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.ShareRepository
import com.wander.android.data.repository.JamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class NowPlayingViewModel @Inject constructor(
    private val coordinator: PlaybackCoordinator,
    private val musicRepository: MusicRepository,
    private val shareRepository: ShareRepository,
    private val renditionFinder: RenditionFinder,
    private val playerConnection: PlayerConnection,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    fingerprintStatuses: FingerprintStatusRepository,
    jamRepository: JamRepository
) : ViewModel() {

    /** What has been measured about the track on screen. */
    val fingerprintStatus: StateFlow<Map<String, FingerprintStatus>> = fingerprintStatuses
        .statuses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Tracks already handed to the indexer this session, so a re-observation is not a re-request.
     *
     * `enqueueUniqueWork` with `KEEP` would already collapse duplicates, but the flow below emits
     * on every status change for the whole library — asking WorkManager a thousand times to ignore
     * us is not free, and this makes the intent local and obvious.
     */
    private val requested = mutableSetOf<String>()

    init {
        // Playing something unmeasured is what schedules it.
        //
        // The library sweep is bulk work that waits for Wi-Fi and a decent battery; this is one
        // track, about a minute of audio, for the song in your ears right now — so it is requested
        // directly rather than left to a sweep that may not reach it for hours. It is the reason
        // the red state is worth showing at all: it is not a complaint, it is a promise.
        viewModelScope.launch {
            combine(
                playerConnection.state.map { it.currentTrack?.id },
                fingerprintStatus
            ) { trackId, statuses -> trackId to statuses[trackId] }
                .distinctUntilChanged()
                .collect { (trackId, status) ->
                    if (trackId == null || status != null) return@collect
                    if (!requested.add(trackId)) return@collect
                    FingerprintIndexWorker.enqueueFor(context, trackId)
                }
        }
    }

    /**
     * Every source that has the playing recording, once the picker has asked.
     *
     * Empty until the label is tapped: finding renditions costs a search per backend, and the
     * answer is only ever looked at by a user who has opened the picker.
     */
    private val _renditions = MutableStateFlow<List<UnifiedTrack>>(emptyList())
    val renditions: StateFlow<List<UnifiedTrack>> = _renditions.asStateFlow()

    private val _isFindingRenditions = MutableStateFlow(false)
    val isFindingRenditions: StateFlow<Boolean> = _isFindingRenditions.asStateFlow()

    /**
     * [durationMs] comes from the player rather than the track — see [RenditionFinder.canSwitch].
     * Stamped onto the track before matching, so a rendition is compared against the length that is
     * actually playing instead of a zero the metadata never filled in.
     */
    fun findRenditions(track: UnifiedTrack, durationMs: Long) {
        // Seeded with what is playing so the picker opens with a row already in it, rather than
        // an empty panel that fills in a second later.
        val known = if (track.durationMs > 0L) track else track.copy(durationMs = durationMs)
        _renditions.value = listOf(known)
        _isFindingRenditions.value = true
        viewModelScope.launch {
            try {
                _renditions.value = renditionFinder.findRenditions(known)
            } finally {
                _isFindingRenditions.value = false
            }
        }
    }

    fun clearRenditions() {
        _renditions.value = emptyList()
        _isFindingRenditions.value = false
    }

    /**
     * Switches source without losing your place.
     *
     * The position is read before the swap and handed to the new rendition, so changing where a
     * song comes from mid-listen is a change of source and not a restart.
     */
    fun playFrom(rendition: UnifiedTrack, positionMs: Long) {
        playerConnection.play(listOf(rendition), startPositionMs = positionMs)
    }

    fun canSwitchSource(track: UnifiedTrack, durationMs: Long): Boolean =
        renditionFinder.canSwitch(track, durationMs)

    val lyrics = coordinator.lyrics
    val jam = jamRepository.jam

    /**
     * The playing track is a snapshot taken when it was queued, so its own `isLiked` never changes
     * while it plays. The heart reads Room instead.
     */
    val likedTrackIds: StateFlow<Set<String>> = musicRepository.getLikedTrackIdsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggleLike(track: UnifiedTrack) {
        viewModelScope.launch {
            musicRepository.toggleLike(track)
        }
    }

    /** Whether this track's backend can mint a public link — decides if the button is shown. */
    fun canShare(track: UnifiedTrack) = shareRepository.canShare(track)

    /**
     * The link is published on a shared flow and raised as a share sheet by `WanderApp`.
     *
     * Carries the current speed and pitch, which is why the player shares differently from every
     * other screen: this is the one place that knows a track is being played at 1.25x.
     */
    fun share(track: UnifiedTrack) {
        viewModelScope.launch {
            shareRepository.share(track, playerConnection.speedAndPitch.value)
        }
    }
}
