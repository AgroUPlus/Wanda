package com.wander.android.core.playback

import com.wander.android.core.audio.visualizer.AudioFftProcessor
import com.wander.android.core.audio.visualizer.VisualizerMode
import com.wander.android.data.model.LyricsData
import com.wander.android.data.repository.LyricsRepository
import com.wander.android.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cross-cutting playback behaviour that does not belong in the service: lyrics for the current
 * track, endless-radio queue top-up, and the visualizer/offload trade-off.
 */
import com.wander.android.data.repository.JamRepository

@Singleton
internal class PlaybackCoordinator @Inject constructor(
    private val connection: PlayerConnection,
    private val musicRepository: MusicRepository,
    private val lyricsRepository: LyricsRepository,
    private val fftProcessor: AudioFftProcessor,
    private val jamRepository: JamRepository
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _lyrics = MutableStateFlow<LyricsData?>(null)
    val lyrics: StateFlow<LyricsData?> = _lyrics.asStateFlow()

    private val _visualizerMode = MutableStateFlow(VisualizerMode.OFF)
    val visualizerMode: StateFlow<VisualizerMode> = _visualizerMode.asStateFlow()

    init {
        connection.state
            .map { it.currentTrack }
            .distinctUntilChanged { old, new -> old?.id == new?.id }
            .onEach { track ->
                _lyrics.value = null
                if (track == null) return@onEach
                _lyrics.value = lyricsRepository.getLyrics(
                    trackId = track.id,
                    trackTitle = track.title,
                    artistName = track.artist,
                    albumName = track.album,
                    durationSeconds = track.durationMs / 1000
                )
            }
            .launchIn(scope)

        // Endless radio: top up before the user hits the end, never mid-track twice.
        // In a Jam, the queue belongs to the room rather than personal radio top-up.
        connection.state
            .filterNotNull()
            .map { RadioTrigger(it.isRadioMode, it.currentIndex, it.queue.size) }
            .distinctUntilChanged()
            .onEach { trigger ->
                if (!trigger.enabled) return@onEach
                if (jamRepository.jam.value != null) return@onEach
                if (trigger.size - trigger.index > RADIO_LOOKAHEAD) return@onEach
                val seed = connection.state.value.currentTrack ?: return@onEach
                val more = musicRepository.generateRadio(seed, RADIO_BATCH)
                if (more.isNotEmpty()) scope.launch(Dispatchers.Main) { connection.addToQueue(more) }
            }
            .launchIn(scope)

        // Offload follows the visualizer, on every controller — not just when the mode is changed.
        //
        // `PlayerFactory` builds every player with offload *enabled*, and a new controller resets
        // the track-selection parameters to that default. So a visualizer chosen in an earlier
        // session came back with offload on, no decoded PCM reaching the tap, and a wave that
        // simply never drew. It looked source-specific because it is: offload is used for the
        // compressed streams YouTube Music serves and often not for local FLAC, so the same
        // setting killed the visualizer on one and left it working on the other.
        connection.controller
            .filterNotNull()
            .onEach { applyOffload() }
            .launchIn(scope)

        // And on whether the current track is live, for a reason that has nothing to do with the
        // visualizer — see `applyOffload`.
        connection.state
            .map { it.currentTrack?.isLive == true }
            .distinctUntilChanged()
            .onEach { applyOffload() }
            .launchIn(scope)
    }

    /**
     * The single place that decides whether offload is on, because two unrelated things want it off.
     *
     * A visualizer needs decoded PCM, which offload bypasses — that is the original trade-off, and
     * the flag on the processor is the truth of it rather than the stored mode, so a visualizer
     * paused because nobody is looking takes the battery win back.
     *
     * A livestream wants it off for a different reason: offload hands a fixed buffer to the DSP and
     * expects a track that ends, and an HLS live window is neither. With it on, a live stream
     * played to the end of the first window and the player called the item finished — which is what
     * the "it plays for a bit then skips to the next song" report was.
     *
     * On the main thread, always. `setOffloadEnabled` writes `trackSelectionParameters` on the
     * MediaController, and Media3 throws outright when a controller is touched from anywhere else —
     * this scope is [Dispatchers.Default], so calling it directly crashed the app.
     */
    private fun applyOffload() {
        val live = connection.state.value.currentTrack?.isLive == true
        val offload = !fftProcessor.isVisualizerActive && !live
        scope.launch(Dispatchers.Main) { connection.setOffloadEnabled(offload) }
    }

    /**
     * A visualizer needs decoded PCM, which audio offload bypasses. Turning one on therefore
     * turns offload off, and turning it back to [VisualizerMode.OFF] restores the battery win.
     */
    fun setVisualizerMode(mode: VisualizerMode) {
        _visualizerMode.value = mode
        val active = mode != VisualizerMode.OFF
        fftProcessor.isVisualizerActive = active
        applyOffload()
    }

    /** Called when Now Playing leaves the screen: stop doing FFT work nobody can see. */
    fun pauseVisualizer() {
        fftProcessor.isVisualizerActive = false
        // Nothing is watching, so take the battery win back — unless the current track is live,
        // which `applyOffload` is the one place that knows.
        applyOffload()
    }

    fun resumeVisualizer() {
        fftProcessor.isVisualizerActive = _visualizerMode.value != VisualizerMode.OFF
        // Setting the flag is not enough: without decoded PCM there is nothing for it to process,
        // and returning to Now Playing must undo any offload turned back on while it was away.
        applyOffload()
    }

    private data class RadioTrigger(val enabled: Boolean, val index: Int, val size: Int)

    private companion object {
        const val RADIO_LOOKAHEAD = 3
        const val RADIO_BATCH = 10
    }
}
