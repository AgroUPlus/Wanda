package com.wander.android.ui.screens.player

import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.PlaybackMediaType
import com.wander.android.data.model.SearchKind
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.RecordingRulesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the playing track in the listener's saved form — song or music video — and reports which
 * form is playing for the toggle and the clip.
 *
 * A singleton rather than part of the player's ViewModel because the preference has to hold for
 * tracks that start while the player is docked: with Video saved, the next track is swapped to
 * its video as it starts, whether or not anyone is looking. With Song saved nothing is searched
 * until the full player opens, so the default costs no network at all in the background.
 *
 * The preference only ever changes on a tap of the toggle. A track that exists in one form only
 * plays in that form without touching it, and returning to a track re-applies the preference
 * rather than whatever that track was last played as.
 */
@Singleton
internal class MediaFormController @Inject constructor(
    private val playerConnection: PlayerConnection,
    private val musicRepository: MusicRepository,
    private val recordingRules: RecordingRulesRepository,
    private val secureStorage: SecureStorage
) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val playerOpen = MutableStateFlow(false)

    private val _toggle = MutableStateFlow<MediaToggleState?>(null)

    /** Null when the playing track's source has no video form, so the toggle is not shown at all. */
    val toggle: StateFlow<MediaToggleState?> = _toggle.asStateFlow()

    private val _clipTrack = MutableStateFlow<UnifiedTrack?>(null)

    /** The playing track while it is playing as a video — the one whose clip replaces the cover. */
    val clipTrack: StateFlow<UnifiedTrack?> = _clipTrack.asStateFlow()

    /** What each track id is and its other form, found once per session; swaps record both sides. */
    private val knownForms = HashMap<String, MediaToggleState>()

    init {
        scope.launch {
            combine(
                playerConnection.state.map { it.currentTrack }.distinctUntilChanged { a, b -> a?.id == b?.id },
                playerOpen,
                secureStorage.preferredMediaType
            ) { track, open, preferred -> Triple(track, open, preferred) }
                .collectLatest { (track, open, preferred) -> settle(track, open, preferred) }
        }
    }

    /** The full player is open: worth finding the other form even with Song saved, to offer it. */
    fun setPlayerOpen(open: Boolean) {
        playerOpen.value = open
    }

    /** The toggle's tap: saves the other form as the preference, which swaps this track to it. */
    fun swap() {
        val toggle = _toggle.value ?: return
        val playing = toggle.playing ?: return
        if (toggle.alternative == null) return
        val other = if (playing == PlaybackMediaType.SONG) PlaybackMediaType.VIDEO else PlaybackMediaType.SONG
        secureStorage.setPreferredMediaType(other)
    }

    private suspend fun settle(track: UnifiedTrack?, open: Boolean, preferred: PlaybackMediaType) {
        if (track == null || track.source != SourceType.YTMUSIC || track.isEpisode || track.isLive) {
            publish(track, null)
            return
        }
        val known = knownForms[track.id] ?: run {
            if (!open && preferred == PlaybackMediaType.SONG) {
                publish(track, null)
                return
            }
            publish(track, MediaToggleState(playing = null, alternative = null))
            findForms(track).also { knownForms[track.id] = it }
        }
        publish(track, known)
        // A Jam follower plays what the host plays; swapping is the host's to do.
        if (playerConnection.isFollowing) return
        val target = swapTarget(known, preferred) ?: return
        // Recorded before the swap so the new track arrives already known, and is not swapped back.
        knownForms[target.id] = MediaToggleState(preferred, track)
        // Moved on the tap, not when the new track arrives; the old clip goes with it.
        _toggle.value = MediaToggleState(preferred, track)
        _clipTrack.value = null
        playerConnection.replaceCurrentTrack(target)
    }

    private fun publish(track: UnifiedTrack?, state: MediaToggleState?) {
        _toggle.value = state
        _clipTrack.value = track.takeIf { state?.playing == PlaybackMediaType.VIDEO }
    }

    /**
     * YouTube Music files a song and its video as separate ids, and nothing on the track says which
     * it is, so both searches run and the playing id's list decides. The other form must match on
     * title and artist ([com.wander.android.data.repository.RecordingRules.isSameWork]); length is
     * ignored because a video runs longer than the studio cut.
     */
    private suspend fun findForms(track: UnifiedTrack): MediaToggleState {
        val rules = recordingRules.current()
        val query = "${track.artist} ${track.title}"
        val sources = setOf(track.source)
        val (songs, videos) = coroutineScope {
            val songs = async { musicRepository.searchAllSources(query, sources, SearchKind.TRACKS) }
            val videos = async { musicRepository.searchAllSources(query, sources, SearchKind.VIDEOS) }
            songs.await() to videos.await()
        }
        val isVideo = videos.any { it.id == track.id } && songs.none { it.id == track.id }
        val playing = if (isVideo) PlaybackMediaType.VIDEO else PlaybackMediaType.SONG
        val others = if (isVideo) songs else videos
        return MediaToggleState(playing, others.firstOrNull { it.id != track.id && rules.isSameWork(track, it) })
    }
}

/**
 * The track to swap to so that [preferred] plays, or null to leave the playing one alone: it is
 * already in that form, its form is not known yet, or the other form does not exist.
 */
internal fun swapTarget(state: MediaToggleState, preferred: PlaybackMediaType): UnifiedTrack? =
    state.alternative.takeIf { state.playing != null && state.playing != preferred }
