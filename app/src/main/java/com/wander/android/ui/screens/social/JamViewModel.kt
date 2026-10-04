package com.wander.android.ui.screens.social

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.JamPlaybackController
import com.wander.android.data.repository.JamMembershipWatcher
import com.wander.android.data.repository.JamRadioTopUp
import com.wander.android.data.repository.JamRecapRepository
import com.wander.android.data.repository.JamRepository
import com.wander.android.data.sources.agro.FriendJam
import com.wander.android.data.sources.agro.Jam
import com.wander.android.data.sources.agro.JamMode
import com.wander.android.data.sources.agro.StoredJamRecap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    /** Recaps of jams this account has left, newest first. Shown when not in a jam. */
    val recaps: List<StoredJamRecap> = emptyList(),
    /** Recaps already turned into a playlist this session, so the button says so. */
    val savedRecaps: Set<String> = emptySet(),
    /** The recap written when this screen's user just left, shown first rather than under the fold. */
    val justLeftRecap: String? = null,
    val error: String? = null
)

@HiltViewModel
internal class JamViewModel @Inject constructor(
    private val repository: JamRepository,
    private val playback: JamPlaybackController,
    private val radio: JamRadioTopUp,
    private val membership: JamMembershipWatcher,
    private val secureStorage: SecureStorage,
    private val recapRepository: JamRecapRepository
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

    init {
        // Both are app-wide singletons; this is merely the first place guaranteed to exist while
        // a jam can — the shell holds one of these for the app's whole life.
        radio.ensureRunning()
        membership.ensureRunning()
        repository.jam
            .onEach { jam ->
                _state.value = _state.value.copy(jam = jam)
            }
            .launchIn(viewModelScope)
        repository.isJamRadioEnabled
            .onEach { enabled ->
                _state.value = _state.value.copy(isRadioEnabled = enabled)
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
        recapRepository.recaps
            .onEach { recaps -> _state.value = _state.value.copy(recaps = recaps) }
            .launchIn(viewModelScope)

        refresh()
        refreshFriendJams()
        viewModelScope.launch {
            recapRepository.refresh().onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun refresh() = run { repository.refresh() }

    fun create(mode: JamMode) = run { repository.create(mode) }

    fun join(code: String) = run { repository.join(code) }

    fun leave() = run {
        playback.reset()
        // The server writes the recap as part of leaving, so it is there to read straight after.
        val known = _state.value.recaps.mapTo(mutableSetOf()) { it.id }
        repository.leave()
            .mapCatching { recapRepository.refresh().getOrThrow() }
            .onSuccess {
                val fresh = recapRepository.recaps.first().firstOrNull { it.id !in known }
                _state.value = _state.value.copy(justLeftRecap = fresh?.id)
            }
    }

    fun dismissRecap(id: String) = run { recapRepository.dismiss(id) }

    /** Saves a recap's tracks as a universal playlist called [title]. */
    fun saveRecap(recap: StoredJamRecap, title: String) = run {
        recapRepository.saveAsPlaylist(recap.recap, title).map {
            _state.value = _state.value.copy(savedRecaps = _state.value.savedRecaps + recap.id)
        }
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
