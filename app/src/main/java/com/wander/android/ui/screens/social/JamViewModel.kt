package com.wander.android.ui.screens.social

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.JamPlaybackController
import com.wander.android.data.repository.JamRepository
import com.wander.android.data.sources.agro.FriendJam
import com.wander.android.data.sources.agro.Jam
import com.wander.android.data.sources.agro.JamMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.ListenAlongResolver
import com.wander.android.data.sources.agro.AgroFeedApi

@Immutable
internal data class JamUiState(
    val jam: Jam? = null,
    val isPaired: Boolean = false,
    val isBusy: Boolean = false,
    val isRadioEnabled: Boolean = false,
    /** A track the room is playing that no source on this device has. */
    val unresolvable: String? = null,
    /** Jams friends have opened up, shown when you are not in one. */
    val friendJams: List<FriendJam> = emptyList(),
    /** This device is on the room's track but no longer with it — paused, or seeked away. */
    val outOfSync: Boolean = false,
    val error: String? = null
)

@HiltViewModel
internal class JamViewModel @Inject constructor(
    private val repository: JamRepository,
    private val playback: JamPlaybackController,
    private val feedApi: AgroFeedApi,
    private val musicRepository: MusicRepository,
    private val resolver: ListenAlongResolver,
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val _state = MutableStateFlow(JamUiState())
    val state: StateFlow<JamUiState> = _state.asStateFlow()

    fun shareUrl(code: String): String? {
        val configuredDomain = secureStorage.agroShareDomain.value.ifBlank { secureStorage.shareDomain.value }
        if (configuredDomain.isNotBlank()) {
            val host = configuredDomain.removePrefix("https://").removePrefix("http://").trimEnd('/')
            return "https://$host/jam?code=$code"
        }
        val server = secureStorage.agroServerUrl
        if (server.isNotBlank()) {
            val cleanServer = server.trimEnd('/')
            return if (cleanServer.startsWith("http://") || cleanServer.startsWith("https://")) {
                "$cleanServer/jam?code=$code"
            } else {
                "https://$cleanServer/jam?code=$code"
            }
        }
        return null
    }

    private var toppingUp = false

    init {
        repository.jam
            .onEach { jam ->
                _state.value = _state.value.copy(jam = jam)
                checkAutoTopUpRadio(jam)
            }
            .launchIn(viewModelScope)
        repository.isJamRadioEnabled
            .onEach { enabled ->
                _state.value = _state.value.copy(isRadioEnabled = enabled)
                checkAutoTopUpRadio(_state.value.jam)
            }
            .launchIn(viewModelScope)
        secureStorage.agroConfigured
            .onEach { paired -> _state.value = _state.value.copy(isPaired = paired) }
            .launchIn(viewModelScope)
        playback.unresolvable
            .onEach { title -> _state.value = _state.value.copy(unresolvable = title) }
            .launchIn(viewModelScope)
        playback.outOfSync
            .onEach { adrift -> _state.value = _state.value.copy(outOfSync = adrift) }
            .launchIn(viewModelScope)

        refresh()
        refreshFriendJams()
    }

    fun refresh() = run { repository.refresh() }

    fun create(mode: JamMode) = run { repository.create(mode) }

    fun join(code: String) = run { repository.join(code) }

    fun leave() = run {
        playback.reset()
        repository.leave()
    }

    /** Accepts somebody's suggestion. */
    fun approve(trackId: String) = run { repository.approve(trackId) }

    fun remove(trackId: String) = run { repository.remove(trackId) }

    fun setMode(mode: JamMode) = run { repository.setMode(mode) }

    /** Votes to skip whatever the room is playing. */
    fun voteSkip() = run { repository.voteSkip() }

    /** Opens the jam so friends can find it, or shuts it back to code-only. */
    fun setOpenToFriends(open: Boolean) = run { repository.setOpenToFriends(open) }

    fun joinFriendJam(jamId: String) = run { repository.joinFriendJam(jamId) }

    /** Refreshes the list of friends' open jams. Only meaningful when not in one. */
    fun refreshFriendJams() {
        viewModelScope.launch {
            repository.friendJams().onSuccess { open ->
                _state.value = _state.value.copy(friendJams = open)
            }
        }
    }

    fun setJamRadioEnabled(enabled: Boolean) {
        repository.setJamRadioEnabled(enabled)
    }

    private fun checkAutoTopUpRadio(jam: Jam?) {
        if (jam == null || !repository.isJamRadioEnabled.value) {
            repository.noteAutoRadioTrack(null)
            return
        }
        // Only host or single user auto-proposes blend so we don't multiply proposals
        if (!jam.isHost && jam.members.size > 1) return
        if (jam.queue.isNotEmpty() || jam.proposals.isNotEmpty() || toppingUp) return

        val now = jam.nowPlaying ?: return
        toppingUp = true
        viewModelScope.launch {
            try {
                // Multi-friend taste blend: combines Circle recap (shared top charts & anthem),
                // recent friend activity events from each participant, and seed radio.
                val candidateQueries = mutableListOf<Pair<String, String>>() // (title, artist)

                // 1. Circle recap: combined top tracks and anthem across all circle members
                val recap = feedApi.recap("MONTH").getOrNull()
                recap?.anthem?.let { anthem ->
                    candidateQueries.add(anthem.title to anthem.artist)
                }
                recap?.topTracks?.forEach { entry ->
                    candidateQueries.add(entry.name to "")
                }

                // 2. Individual friends' activity: recent milestones and repeats from all friends
                val activity = feedApi.friendActivity(days = 14, limit = 30).getOrNull().orEmpty()
                for (item in activity) {
                    val title = item.title
                    if (!title.isNullOrBlank()) {
                        candidateQueries.add(title to item.artist)
                    } else if (item.artist.isNotBlank()) {
                        candidateQueries.add("" to item.artist)
                    }
                }

                // Shuffle candidates to create an even blend of everyone's tastes
                val shuffledCandidates = candidateQueries.distinct().shuffled()
                var added = false
                for ((title, artist) in shuffledCandidates) {
                    val resolved = resolver.resolve(title, artist)
                    if (resolved != null && resolved.track.id != now.trackId) {
                        repository.add(resolved.track)
                        repository.noteAutoRadioTrack(resolved.track.id)
                        added = true
                        break
                    }
                }

                // 3. Fallback to seed radio if circle history is still sparse
                if (!added) {
                    val resolvedNow = resolver.resolve(now.title, now.artist)
                    if (resolvedNow != null) {
                        val radio = musicRepository.generateRadio(resolvedNow.track, 1)
                        if (radio.isNotEmpty()) {
                            val track = radio.first()
                            repository.add(track)
                            repository.noteAutoRadioTrack(track.id)
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                toppingUp = false
            }
        }
    }

    /**
     * Suggests a track chosen from this screen.
     *
     * Choosing one from anywhere *else* in the app goes straight to the repository, which owns the
     * proposal callback for as long as the jam lasts — see `JamRepository.wireJamProposal`.
     * Displacing an auto-radio placeholder happens there too, so both routes behave the same.
     */
    fun suggest(track: UnifiedTrack) = run { repository.add(track) }

    /** Puts this device back where the room is now. */
    fun resync() = playback.resync()

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    /**
     * Runs one call, showing whatever the server said when it refuses.
     *
     * The refusals here are the feature's own rules — "only the creator can change the mode" — so
     * they are worth showing verbatim rather than flattened into a generic failure.
     */
    private fun run(block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true)
            val result = block()
            _state.value = _state.value.copy(
                isBusy = false,
                error = result.exceptionOrNull()?.message
            )
        }
    }
}
