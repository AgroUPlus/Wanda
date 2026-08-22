package com.wander.android.ui.screens.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.data.repository.ListenAlongController
import com.wander.android.data.repository.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
internal class SocialViewModel @Inject constructor(
    private val repository: SocialRepository,
    private val listenAlong: ListenAlongController
) : ViewModel() {

    private val _state = MutableStateFlow(SocialUiState())
    val state: StateFlow<SocialUiState> = _state.asStateFlow()

    private val _search = MutableStateFlow(UserSearchState())
    val search: StateFlow<UserSearchState> = _search.asStateFlow()

    private val query = MutableStateFlow("")

    init {
        // The graph is read from Room, so the screen has content before any network call returns.
        combine(
            repository.friends,
            repository.requests,
            repository.nowPlaying,
            repository.isPaired,
            listenAlong.session
        ) { friends, requests, playing, paired, session ->
            _state.value.copy(
                isPaired = paired,
                friends = friends,
                incoming = requests.filter { !it.outgoing },
                outgoing = requests.filter { it.outgoing },
                nowPlaying = playing,
                session = session
            )
        }.onEach { _state.value = it }.launchIn(viewModelScope)

        // Typing a username is not a reason to query a public directory on every keystroke.
        query
            .debounce(SEARCH_DEBOUNCE_MS)
            .distinctUntilChanged()
            .onEach { runSearch(it) }
            .launchIn(viewModelScope)

        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(isRefreshing = true)
        viewModelScope.launch {
            val result = repository.refresh()
            _state.value = _state.value.copy(
                isRefreshing = false,
                error = result.exceptionOrNull()?.message
            )
        }
    }

    /** Re-reads only the feed. What a `FRIEND_PRESENCE` frame warrants. */
    fun refreshPresence() {
        viewModelScope.launch { repository.refreshPresence() }
    }

    fun onQueryChange(value: String) {
        query.value = value
        _search.value = _search.value.copy(query = value, error = null)
    }

    fun clearSearch() {
        query.value = ""
        _search.value = UserSearchState()
    }

    private suspend fun runSearch(term: String) {
        if (term.isBlank()) {
            _search.value = _search.value.copy(results = emptyList(), isSearching = false)
            return
        }
        _search.value = _search.value.copy(isSearching = true)
        repository.search(term).fold(
            onSuccess = { results ->
                _search.value = _search.value.copy(results = results, isSearching = false)
            },
            onFailure = { error ->
                _search.value = _search.value.copy(
                    isSearching = false,
                    error = error.message ?: "Could not search"
                )
            }
        )
    }

    /**
     * Sends a request, and says so at once.
     *
     * The server answers `false` for an existing edge, a block, or no such account without
     * distinguishing them — deliberately, so this cannot be used to probe for accounts — so there
     * is nothing more specific to report than that it did not go through.
     */
    fun sendRequest(username: String) {
        viewModelScope.launch {
            repository.sendRequest(username).onSuccess { sent ->
                _search.value = if (sent) {
                    _search.value.copy(requested = _search.value.requested + username)
                } else {
                    _search.value.copy(error = "That request could not be sent.")
                }
            }
        }
    }

    fun accept(username: String) {
        viewModelScope.launch { repository.accept(username) }
    }

    /** Declines a request, or ends a friendship — the same call for both. */
    fun remove(username: String) {
        viewModelScope.launch { repository.remove(username) }
    }

    fun startListenAlong(host: String) {
        viewModelScope.launch {
            listenAlong.start(host).onFailure { error ->
                _state.value = _state.value.copy(
                    error = error.message ?: "Could not listen along with that friend"
                )
            }
        }
    }

    fun stopListenAlong() {
        viewModelScope.launch { listenAlong.stop() }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
