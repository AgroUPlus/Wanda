package com.wander.android.core.playback

import com.wander.android.R
import com.wander.android.data.model.LyricsState
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.EpisodeProgressRepository
import com.wander.android.data.repository.JamRepository
import com.wander.android.data.repository.LyricsRepository
import com.wander.android.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cross-cutting playback behaviour that does not belong in the service: lyrics for the current
 * track, starting a station, and endless-radio queue top-up.
 *
 * The class is public because view models across `ui` now start radios through it; the constructor
 * stays internal because half of what it takes is (`JamRepository`), and Hilt generates its factory
 * inside this module either way. That keeps the type's *surface* the two methods callers use rather
 * than the dependency list behind them.
 */
@Singleton
class PlaybackCoordinator @Inject internal constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val connection: PlayerConnection,
    private val musicRepository: MusicRepository,
    private val lyricsRepository: LyricsRepository,
    private val jamRepository: JamRepository,
    private val episodeProgress: EpisodeProgressRepository,
    private val secureStorage: com.wander.android.core.security.SecureStorage
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * The episode already resumed during its current stay as the playing item, if any.
     *
     * Declared before `init`, which collects flows that read it the moment they emit. Only
     * touched from that one collector, so a plain field is enough.
     *
     * Without it, pausing a resumed episode and playing it again would seek back to the saved
     * position and undo whatever the listener had just scrubbed to. It is cleared as soon as
     * something else plays: it used to be a session-long set, so an episode left for a song and
     * then reopened started from zero — and the next pause saved that zero over the real position.
     */
    private var resumedTrackId: String? = null

    /**
     * True while [startRadio] is building its first batch.
     *
     * The seed alone is a one-track queue, which trips the endless top-up below; without this the
     * two fetched a station for the same seed side by side and queued both.
     */
    @Volatile
    private var stationStarting = false

    private val _lyrics = MutableStateFlow<LyricsState>(LyricsState.Loading)
    val lyrics: StateFlow<LyricsState> = _lyrics.asStateFlow()

    init {
        connection.state
            .map { it.currentTrack }
            .distinctUntilChanged { old, new -> old?.id == new?.id }
            .onEach { track ->
                _lyrics.value = LyricsState.Loading
                if (track == null) {
                    _lyrics.value = LyricsState.Absent
                    return@onEach
                }
                com.wander.android.core.audio.fingerprint.FingerprintIndexing.enqueue(
                    context,
                    allowMobileData = secureStorage.isIndexOnMobileDataEnabled.value
                )
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
            .map { RadioTrigger(it.isRadioMode, it.playPosition, it.queue.size) }
            .distinctUntilChanged()
            .onEach { trigger ->
                if (!trigger.enabled || stationStarting) return@onEach
                if (jamRepository.jam.value != null) return@onEach
                if (trigger.size - trigger.index > RADIO_LOOKAHEAD) return@onEach
                val seed = connection.state.value.currentTrack ?: return@onEach
                // An episode is no seed for a music station: it would queue songs "like" a podcast.
                if (seed.isEpisode) return@onEach
                val more = notQueued(musicRepository.generateRadio(seed, RADIO_BATCH))
                if (more.isNotEmpty()) scope.launch(Dispatchers.Main) { connection.addToQueue(more) }
            }
            .launchIn(scope)

        // Episodes resume where they were left; songs do not. See EpisodeProgressRepository.
        // Saved by the service's player (EpisodeProgressRecorder), so a car's listening counts too.
        // Pruned once per process: nothing else ever removes a row for an episode heard once.
        scope.launch { episodeProgress.prune() }

        // Resumed on the first frame rather than at the moment the track becomes current: a seek
        // issued while the item is still preparing races the initial buffer and lands at zero —
        // the same trap `PlayerConnection` documents on its own restore path.
        connection.state
            .map { EpisodeArrival(it.currentTrack?.id, it.currentTrack?.isEpisode == true, it.isPlaying) }
            .distinctUntilChanged()
            .onEach { (trackId, isEpisode, playing) ->
                if (trackId == resumedTrackId) return@onEach
                resumedTrackId = null
                if (trackId == null || !isEpisode || !playing) return@onEach
                resumedTrackId = trackId
                val position = episodeProgress.resumePosition(trackId) ?: return@onEach
                // Only when the player is still at the top of the episode. If the listener has
                // already scrubbed somewhere, that is a deliberate choice and outranks the record.
                // Read and seek on Main: a MediaController throws if touched from any other thread.
                withContext(Dispatchers.Main) {
                    val now = connection.currentPositionMs() ?: return@withContext
                    if (now <= RESUME_GRACE_MS) connection.seekTo(position)
                }
            }
            .launchIn(scope)

        connection.controller
            .filterNotNull()
            .onEach { applyPlaybackParameters() }
            .launchIn(scope)

        // Livestreams, silence-skipped episodes and a Bluetooth output veto audio offload — see
        // `offloadAllowed`. The first two turn on what is playing, the second also on the setting,
        // and the last on earbuds connecting or going away mid-track. A change between episode and
        // music is also a change of remembered speed.
        connection.state
            .map { (it.currentTrack?.isLive == true) to (it.currentTrack?.isEpisode == true) }
            .distinctUntilChanged()
            .combine(secureStorage.isSkipSilenceEnabled) { kind, skip -> kind to skip }
            .combine(connection.bluetoothConnected) { kind, bluetooth -> kind to bluetooth }
            .onEach { applyPlaybackParameters() }
            .launchIn(scope)
    }

    /**
     * Plays [seed] and fills the queue behind it with a station built around it.
     *
     * Eight view models carried a byte-identical copy of this — play the track, generate a radio,
     * append it — which meant the behaviour had no owner and the Jam guard below had to be
     * remembered eight times. It never was: `addToQueue` writes straight to the local queue, so
     * starting a radio inside a Jam proposed the seed to the room and then quietly stuffed twenty
     * tracks into the listener's own queue behind it.
     *
     * Here it sits beside the endless top-up, which is the same job on a different trigger and
     * already stands down for a room that owns its order.
     *
     * The station is endless: radio mode is switched on once the first batch is queued, so the
     * top-up keeps extending it from whatever is playing as the queue runs low. It used to be one
     * batch of twenty and then silence. When nothing similar can be found the listener is told,
     * rather than left with the seed alone and no idea why.
     */
    suspend fun startRadio(seed: UnifiedTrack) {
        stationStarting = true
        try {
            connection.play(listOf(seed))
            // In a Jam the queue belongs to the room, and `play` above has already proposed the
            // seed to it rather than playing it here.
            if (jamRepository.jam.value != null) return
            val radio = musicRepository.generateRadio(seed)
            if (radio.isEmpty()) {
                connection.notify(context.getString(R.string.radio_nothing_similar))
                return
            }
            connection.addToQueue(radio)
            connection.setRadioMode(true)
        } finally {
            stationStarting = false
        }
    }

    /** [tracks] minus anything already in the queue, so a top-up never repeats what is lined up. */
    private fun notQueued(tracks: List<UnifiedTrack>): List<UnifiedTrack> {
        val queued = connection.state.value.queue.mapTo(HashSet()) { it.id }
        return tracks.filterNot { it.id in queued }
    }

    /**
     * Has the connection apply the speed remembered for what is playing (episodes and music keep
     * their own) and re-decide offload with it. The decision itself is `offloadAllowed`, which also
     * weighs the speed and pitch: deciding it here from the livestream flag alone switched offload
     * back on under a track playing at 1.5x.
     */
    private fun applyPlaybackParameters() {
        scope.launch(Dispatchers.Main) { connection.refreshPlaybackParameters() }
    }

    private data class RadioTrigger(val enabled: Boolean, val index: Int, val size: Int)

    private data class EpisodeArrival(
        val trackId: String?,
        val isEpisode: Boolean,
        val playing: Boolean
    )

    private companion object {
        const val RADIO_LOOKAHEAD = 3
        const val RADIO_BATCH = 10

        /** How far in an episode may already be and still count as "just started". */
        const val RESUME_GRACE_MS = 5_000L
    }
}
