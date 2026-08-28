package com.wander.android.core.playback

import com.wander.android.data.model.LyricsData
import com.wander.android.data.repository.JamRepository
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
 * track and endless-radio queue top-up.
 */
@Singleton
internal class PlaybackCoordinator @Inject constructor(
    private val connection: PlayerConnection,
    private val musicRepository: MusicRepository,
    private val lyricsRepository: LyricsRepository,
    private val jamRepository: JamRepository
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _lyrics = MutableStateFlow<LyricsData?>(null)
    val lyrics: StateFlow<LyricsData?> = _lyrics.asStateFlow()

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

        connection.controller
            .filterNotNull()
            .onEach { applyOffload() }
            .launchIn(scope)

        // Livestreams veto audio offload — see `applyOffload`.
        connection.state
            .map { it.currentTrack?.isLive == true }
            .distinctUntilChanged()
            .onEach { applyOffload() }
            .launchIn(scope)
    }

    /**
     * Decides whether offload is on.
     *
     * A livestream wants offload off because offload hands a fixed buffer to the DSP and
     * expects a track that ends, and an HLS live window is not one.
     */
    private fun applyOffload() {
        val live = connection.state.value.currentTrack?.isLive == true
        val offload = !live
        scope.launch(Dispatchers.Main) { connection.setOffloadEnabled(offload) }
    }

    private data class RadioTrigger(val enabled: Boolean, val index: Int, val size: Int)

    private companion object {
        const val RADIO_LOOKAHEAD = 3
        const val RADIO_BATCH = 10
    }
}
