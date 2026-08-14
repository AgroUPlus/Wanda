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
@Singleton
class PlaybackCoordinator @Inject constructor(
    private val connection: PlayerConnection,
    private val musicRepository: MusicRepository,
    private val lyricsRepository: LyricsRepository,
    private val fftProcessor: AudioFftProcessor
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
        connection.state
            .filterNotNull()
            .map { RadioTrigger(it.isRadioMode, it.currentIndex, it.queue.size) }
            .distinctUntilChanged()
            .onEach { trigger ->
                if (!trigger.enabled) return@onEach
                if (trigger.size - trigger.index > RADIO_LOOKAHEAD) return@onEach
                val seed = connection.state.value.currentTrack ?: return@onEach
                val more = musicRepository.generateRadio(seed, RADIO_BATCH)
                if (more.isNotEmpty()) scope.launch(Dispatchers.Main) { connection.addToQueue(more) }
            }
            .launchIn(scope)
    }

    /**
     * A visualizer needs decoded PCM, which audio offload bypasses. Turning one on therefore
     * turns offload off, and turning it back to [VisualizerMode.OFF] restores the battery win.
     */
    fun setVisualizerMode(mode: VisualizerMode) {
        _visualizerMode.value = mode
        val active = mode != VisualizerMode.OFF
        fftProcessor.isVisualizerActive = active
        connection.setOffloadEnabled(!active)
    }

    /** Called when Now Playing leaves the screen: stop doing FFT work nobody can see. */
    fun pauseVisualizer() {
        fftProcessor.isVisualizerActive = false
    }

    fun resumeVisualizer() {
        fftProcessor.isVisualizerActive = _visualizerMode.value != VisualizerMode.OFF
    }

    private data class RadioTrigger(val enabled: Boolean, val index: Int, val size: Int)

    private companion object {
        const val RADIO_LOOKAHEAD = 3
        const val RADIO_BATCH = 10
    }
}
