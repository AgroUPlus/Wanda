package com.wander.android.ui.screens.social

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.data.repository.ListenAlongController
import com.wander.android.data.repository.SocialRepository
import com.wander.android.data.sources.agro.AgroFriendNowPlaying
import com.wander.android.data.sources.agro.AgroProfile
import com.wander.android.data.sources.agro.AgroTasteMatch
import com.wander.android.data.sources.agro.FriendState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

/**
 * One friend's page.
 *
 * [tasteMatch] being null while [profile] shows `showStats = false` is not a loading state — it is
 * the answer, and the screen says so in words rather than drawing an empty chart.
 */
@Immutable
internal data class ProfileUiState(
    val username: String = "",
    val profile: AgroProfile? = null,
    val nowPlaying: AgroFriendNowPlaying? = null,
    val tasteMatch: AgroTasteMatch? = null,
    /** Their listening, when they have opened it. Null means closed, not loading. */
    val stats: com.wander.android.data.sources.agro.AgroStats? = null,
    val isLoading: Boolean = true,
    val isListeningAlong: Boolean = false,
    val notFound: Boolean = false,
    val error: String? = null
)

@HiltViewModel
internal class ProfileViewModel @Inject constructor(
    private val repository: SocialRepository,
    private val listenAlong: ListenAlongController,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val username: String =
        URLDecoder.decode(savedStateHandle.get<String>("username").orEmpty(), "UTF-8")

    private val _state = MutableStateFlow(ProfileUiState(username = username))
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        listenAlong.session
            .onEach { session ->
                _state.value = _state.value.copy(
                    isListeningAlong = session?.host.equals(username, ignoreCase = true)
                )
            }
            .launchIn(viewModelScope)

        // Presence is pushed to the feed, so the profile can read it there rather than polling.
        repository.nowPlaying
            .onEach { feed ->
                _state.value = _state.value.copy(
                    nowPlaying = feed.firstOrNull { it.username.equals(username, ignoreCase = true) }
                )
            }
            .launchIn(viewModelScope)

        load()
    }

    fun load() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            repository.profile(username).fold(
                onSuccess = { profile ->
                    _state.value = _state.value.copy(
                        profile = profile,
                        isLoading = false,
                        notFound = profile == null
                    )
                    // Only worth asking for once we know they are a friend who shares them. Asking
                    // regardless would produce a refusal the UI would then have to explain away.
                    if (profile != null &&
                        profile.friendState == FriendState.ACCEPTED &&
                        profile.showStats
                    ) {
                        loadTasteMatch()
                    }
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = error.message ?: "Could not load that profile"
                    )
                }
            )
        }
    }

    private suspend fun loadTasteMatch() {
        repository.friendStats(username).onSuccess { friendStats ->
            _state.value = _state.value.copy(stats = friendStats)
        }
        repository.tasteMatch(username).onSuccess { match ->
            _state.value = _state.value.copy(tasteMatch = match)
        }
    }

    fun sendRequest() {
        viewModelScope.launch {
            repository.sendRequest(username)
            load()
        }
    }

    fun accept() {
        viewModelScope.launch {
            repository.accept(username)
            load()
        }
    }

    fun remove() {
        viewModelScope.launch {
            repository.remove(username)
            load()
        }
    }

    fun block() {
        viewModelScope.launch {
            repository.block(username)
            load()
        }
    }

    fun startListenAlong() {
        viewModelScope.launch {
            listenAlong.start(username).onFailure { error ->
                _state.value = _state.value.copy(
                    error = error.message ?: "Could not listen along"
                )
            }
        }
    }

    fun stopListenAlong() {
        viewModelScope.launch { listenAlong.stop() }
    }
}
